/**
   Copyright (c) 2008, Arizona State University.

   All rights reserved.

   Redistribution and use in source and binary forms, with or without
   modification, are permitted provided that the following conditions are
   met:

 * Redistributions of source code must retain the above copyright
   notice, this list of conditions and the following disclaimer.

 * Redistributions in binary form must reproduce the above
   copyright notice, this list of conditions and the following
   disclaimer in the documentation and/or other materials provided
   with the distribution.

 * Neither the name of the University of Pittsburgh nor the names
   of its contributors may be used to endorse or promote products
   derived from this software without specific prior written
   permission.

   THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
   "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
   LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
   A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
   CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
   EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
   PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
   PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
   LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
   NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
   SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 **/

package pitt.search.semanticvectors;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.logging.Logger;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.*;
import org.apache.lucene.util.BytesRef;

import pitt.search.semanticvectors.TermTermVectorsFromLucene.PositionalMethod;
import pitt.search.semanticvectors.utils.VerbatimLogger;
import pitt.search.semanticvectors.vectors.Vector;
import pitt.search.semanticvectors.vectors.VectorFactory;

/**
 * Generates predication vectors incrementally.Requires as input an index containing 
 * documents with the fields "subject", "predicate" and "object"
 * 
 * Produces as output the files: elementalvectors.bin, predicatevectors.bin and semanticvectors.bin
 * 
 * @author Trevor Cohen, Dominic Widdows
 */
public class PSITOK {
  private static final Logger logger = Logger.getLogger(PSITOK.class.getCanonicalName());
  private FlagConfig flagConfig;
  private ElementalVectorStore elementalItemVectors;
  private VectorStoreRAM semanticItemVectors;
  private static final String SUBJECT_FIELD = "tokenized_subject";
  private static final String PREDICATE_FIELD = "tokenized_predicate";
  private static final String OBJECT_FIELD = "tokenized_object";
  private static final String PREDICATION_FIELD = "predication";
  private String[] itemFields = {SUBJECT_FIELD, OBJECT_FIELD};
  private LuceneUtils luceneUtils;

  private PSITOK() {};

  /**
   * Creates PSI vectors incrementally, using the fields "subject" and "object" from a Lucene index.
   */
  public static void createIncrementalPSIVectors(FlagConfig flagConfig) throws IOException {
    PSITOK incrementalPSIVectors = new PSITOK();
    incrementalPSIVectors.flagConfig = flagConfig;
    if (incrementalPSIVectors.luceneUtils == null) {
      incrementalPSIVectors.luceneUtils = new LuceneUtils(flagConfig);
    }
    incrementalPSIVectors.trainIncrementalPSIVectors();
  }

  private void trainIncrementalPSIVectors() throws IOException {
    // Create elemental and semantic vectors for each concept, and elemental vectors for predicates
    elementalItemVectors = new ElementalVectorStore(flagConfig);
    semanticItemVectors = new VectorStoreRAM(flagConfig);
    flagConfig.setContentsfields(itemFields);

    HashSet<String> addedConcepts = new HashSet<String>();

    for (String fieldName : itemFields) {
      Terms terms = luceneUtils.getTermsForField(fieldName);

      if (terms == null) {
        throw new NullPointerException(String.format(
            "No terms for field '%s'. Please check that index at '%s' was built correctly for use with PSI.",
            fieldName, flagConfig.luceneindexpath()));
      }

      TermsEnum termsEnum = terms.iterator(null);
      BytesRef bytes;
      while((bytes = termsEnum.next()) != null) {
        Term term = new Term(fieldName, bytes);

        if (!luceneUtils.termFilter(term)) {
          VerbatimLogger.fine("Filtering out term: " + term + "\n");
          continue;
        }

        if (!addedConcepts.contains(term.text())) {
          addedConcepts.add(term.text());
          elementalItemVectors.getVector(term.text());  // Causes vector to be created.
          semanticItemVectors.putVector(term.text(), VectorFactory.createZeroVector(
              flagConfig.vectortype(), flagConfig.dimension()));
        }
      }
    }

    // Now elemental vectors for the predicate field.
    Terms predicateTerms = luceneUtils.getTermsForField(PREDICATE_FIELD);
    String[] dummyArray = new String[] { PREDICATE_FIELD };  // To satisfy LuceneUtils.termFilter interface.
    TermsEnum termsEnum = predicateTerms.iterator(null);
    BytesRef bytes;
    while((bytes = termsEnum.next()) != null) {
      Term term = new Term(PREDICATE_FIELD, bytes);
      // frequency thresholds do not apply to predicates... but the stopword list does
      if (!luceneUtils.termFilter(term, dummyArray, 0, Integer.MAX_VALUE, Integer.MAX_VALUE, 1)) {  
        continue;
      }

      elementalItemVectors.getVector(term.text().trim());

      // Add an inverse vector for the predicates.
     // elementalItemVectors.getVector(term.text().trim()+"-INV");
    }

    String fieldName = PREDICATION_FIELD; 
    // Iterate through documents (each document = one predication).
    Terms allTerms = luceneUtils.getTermsForField(fieldName);
    termsEnum = allTerms.iterator(null);
    while((bytes = termsEnum.next()) != null) {
      int pc = 0;
      Term term = new Term(fieldName, bytes);
      pc++;

      // Output progress counter.
      if ((pc > 0) && ((pc % 10000 == 0) || ( pc < 10000 && pc % 1000 == 0 ))) {
        VerbatimLogger.info("Processed " + pc + " unique predications ... ");
      }

      DocsEnum termDocs = luceneUtils.getDocsForTerm(term);
      termDocs.nextDoc();
      Document document = luceneUtils.getDoc(termDocs.docID());

      String subject = document.get(SUBJECT_FIELD);
      String predicate = document.get(PREDICATE_FIELD);
      String object = document.get(OBJECT_FIELD);
      
      //assemble relevant elemental vectors
      Terms sTerms = luceneUtils.getTermVector(termDocs.docID(), "tokenized_subject");
      Vector tokenized_subject = processTermPositionVector(sTerms,"tokenized_subject");
         
      Terms oTerms = luceneUtils.getTermVector(termDocs.docID(), "tokenized_object");
      Vector tokenized_object = processTermPositionVector(oTerms,"tokenized_object");
    
      Terms pTerms = luceneUtils.getTermVector(termDocs.docID(), "tokenized_predicate");
      Vector tokenized_predicate = processTermPositionVector(pTerms,"tokenized_predicate");
    
      

      if (tokenized_subject.isZeroVector() || tokenized_object.isZeroVector() || tokenized_predicate.isZeroVector()) {	  
          String infoString = ("skipping predication " + subject + " | " + predicate + " | " + object);
          
          if (tokenized_subject.isZeroVector()) logger.info("--> subject zero\t"+infoString);
          if (tokenized_object.isZeroVector()) logger.info("--> object zero\t"+infoString);
          if (tokenized_predicate.isZeroVector()) logger.info("--> predicate zero\t"+infoString);
          
         
          
          continue;
        }
 
    

      processArguments(oTerms, "tokenized_object", tokenized_predicate, tokenized_subject);
      processArguments(sTerms, "tokenized_subject", tokenized_predicate, tokenized_object);
      
      
    } // Finish iterating through predications.

    //Normalize semantic vectors
    Enumeration<ObjectVector> e = semanticItemVectors.getAllVectors();
    while (e.hasMoreElements())	{
      e.nextElement().getVector().normalize();
    }

    VectorStoreWriter.writeVectors("tok"+flagConfig.elementalvectorfile(), flagConfig, elementalItemVectors);
    VectorStoreWriter.writeVectors("tok"+flagConfig.semanticvectorfile(), flagConfig, semanticItemVectors);
   
    VerbatimLogger.info("Finished writing vectors.\n");
  }

  public static void main(String[] args) throws IllegalArgumentException, IOException {
    FlagConfig flagConfig = FlagConfig.getFlagConfig(args);
    args = flagConfig.remainingArgs;

    if (flagConfig.luceneindexpath().isEmpty()) {
      throw (new IllegalArgumentException("-luceneindexpath argument must be provided."));
    }

    VerbatimLogger.info("Building PSI model from index in: " + flagConfig.luceneindexpath() + "\n");
    VerbatimLogger.info("Minimum frequency = " + flagConfig.minfrequency() + "\n");
    VerbatimLogger.info("Maximum frequency = " + flagConfig.maxfrequency() + "\n");
    VerbatimLogger.info("Number non-alphabet characters = " + flagConfig.maxnonalphabetchars() + "\n");

    createIncrementalPSIVectors(flagConfig);
  }
  
  /**
   * For each term, add term index vector
   * for any term occurring within a window of size windowSize such
   * that for example if windowSize = 5 with the window over the
   * phrase "your life is your life" the index vectors for terms
   * "your" and "life" would each be added to the term vector for
   * "is" twice.
   *
   * TermPositionVectors contain arrays of (1) terms as text (2)
   * term frequencies and (3) term positions within a
   * document. The index of a particular term within this array
   * will be referred to as the 'local index' in comments.
   * @throws IOException 
   */
  private Vector processTermPositionVector(Terms terms, String field)
      throws ArrayIndexOutOfBoundsException, IOException {
   
    ArrayList<String> localTerms = new ArrayList<String>();
    ArrayList<Integer> freqs = new ArrayList<Integer>();
    Hashtable<Integer, Integer> localTermPositions = new Hashtable<Integer, Integer>();

    Vector semanticVector = VectorFactory.createZeroVector(flagConfig.vectortype(), flagConfig.dimension());
    if (terms == null) return semanticVector;

    
    TermsEnum termsEnum = terms.iterator(null);
    BytesRef text;
    int termcount = 0;

    while((text = termsEnum.next()) != null) {
      String theTerm = text.utf8ToString();
      if (!elementalItemVectors.containsVector(theTerm)) continue;
      DocsAndPositionsEnum docsAndPositions = termsEnum.docsAndPositions(null, null);
      if (docsAndPositions == null) return semanticVector;
      docsAndPositions.nextDoc();
      freqs.add(docsAndPositions.freq());
      localTerms.add(theTerm); 

      for (int x = 0; x < docsAndPositions.freq(); x++) {
        localTermPositions.put(new Integer(docsAndPositions.nextPosition()), termcount);
      }

      termcount++;
    }

    // Iterate through positions adding index vectors of terms
    // occurring within window to term vector for focus term
    for (int cursor = 0; cursor < localTermPositions.size(); ++cursor) {
      if (localTermPositions.get(cursor) == null) continue;
      
        if (localTermPositions.get(cursor) == null) continue;
        String coterm = localTerms.get(localTermPositions.get(cursor));
        if (coterm == null) continue;
        Vector toSuperpose = elementalItemVectors.getVector(coterm);
        
        float globalweight = luceneUtils.getGlobalTermWeight(new Term(field, coterm));
        
        //weight according to distance from focusterm
        double rampedweight = 1;
      
          semanticVector.superpose(toSuperpose, globalweight*rampedweight, null);
      
      } //end of current sliding window   
  
    semanticVector.normalize();
    return semanticVector;
    
  }
  
  /**
   * For each term, add term index vector
   * for any term occurring within a window of size windowSize such
   * that for example if windowSize = 5 with the window over the
   * phrase "your life is your life" the index vectors for terms
   * "your" and "life" would each be added to the term vector for
   * "is" twice.
   *
   * TermPositionVectors contain arrays of (1) terms as text (2)
   * term frequencies and (3) term positions within a
   * document. The index of a particular term within this array
   * will be referred to as the 'local index' in comments.
   * @throws IOException 
   */
  private void processArguments(Terms terms, String field, Vector predicateVector, Vector argumentVector)
      throws ArrayIndexOutOfBoundsException, IOException {
   
	  	Vector boundProduct = predicateVector.copy();
	  	boundProduct.bind(argumentVector);
	  
	    ArrayList<String> localTerms = new ArrayList<String>();
	    ArrayList<Integer> freqs = new ArrayList<Integer>();
	    Hashtable<Integer, Integer> localTermPositions = new Hashtable<Integer, Integer>();

	    TermsEnum termsEnum = terms.iterator(null);
	    BytesRef text;
	    int termcount = 0;

	    while((text = termsEnum.next()) != null) {
	      String theTerm = text.utf8ToString();
	      if (!semanticItemVectors.containsVector(theTerm)) continue;
	      DocsAndPositionsEnum docsAndPositions = termsEnum.docsAndPositions(null, null);
	      if (docsAndPositions == null) return;
	      docsAndPositions.nextDoc();
	      freqs.add(docsAndPositions.freq());
	      localTerms.add(theTerm); 

	      for (int x = 0; x < docsAndPositions.freq(); x++) {
	        localTermPositions.put(new Integer(docsAndPositions.nextPosition()), termcount);
	      }

	      termcount++;
	    }

	    // Iterate through positions adding index vectors of terms
	    // occurring within window to term vector for focus term
	    for (int cursor = 0; cursor < localTermPositions.size(); ++cursor) {
	      if (localTermPositions.get(cursor) == null) continue;
	      
	         String coterm = localTerms.get(localTermPositions.get(cursor));
	         if (coterm == null) continue;
	        
	        float globalweight = luceneUtils.getGlobalTermWeight(new Term(field, coterm));
	          semanticItemVectors.getVector(coterm).superpose(boundProduct, globalweight, null);
	      
	      } //end of current sliding window   
	  
	  
    
  }
  
}
