package pitt.search.lucene;
import static pitt.search.semanticvectors.LuceneUtils.LUCENE_VERSION;

import org.apache.lucene.analysis.core.WhitespaceAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexOptions;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.FSDirectory;

import pitt.search.semanticvectors.FlagConfig;
import pitt.search.semanticvectors.utils.VerbatimLogger;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;

/** 
 * This class takes as input a single text file with each line following the format 
 * <subject>\t<predicate>\t<object> and produces a Lucene index, in which each 
 * "document" is a single tab-delimited predication (or triple) with the fields subject, 
 * predicate, and object.
 */
public class LuceneIndexFromReverb {

	/***
	 * Example of tokenized output file format
	 
	 0	/data/2015_medline_pubmed_working_dir/abstracts/0000/10000.txt
1	5
2	These results
3	strongly suggested
4	the likelihood
5	0
6	2
7	2
8	4
9	4
10	6
11	0.24971844275487717
12	These results strongly suggested the likelihood that alpha1-PI would be chemically and physically unchanged as a result of exposure to acid starch gel electrophoresis .
13	DT NNS RB VBD DT NN IN NNS MD VB RB CC RB JJ IN DT NN IN NN TO NN NN NN NN .
14	B-NP I-NP B-ADVP B-VP B-NP I-NP B-SBAR B-NP B-VP I-VP I-VP O B-ADJP I-ADJP B-PP B-NP I-NP I-NP I-NP B-PP B-NP I-NP I-NP I-NP O
15	these results
16	suggest
17	the likelihood
		 
	 */
	
	
  private LuceneIndexFromReverb() {}

  static Path INDEX_DIR = FileSystems.getDefault().getPath("reverb_index");

  /** Index all text files under a directory. */
  public static void main(String[] args) {
    String usage = "java pitt.search.lucene.LuceneIndexFromReverb [reverb output file] ";
    if (args.length == 0) {
      System.err.println("Usage: " + usage);
      System.exit(1);
    }
    FlagConfig flagConfig = FlagConfig.getFlagConfig(args);
    // Allow for the specification of a directory to write the index to.
    if (flagConfig.luceneindexpath().length() > 0) {
      INDEX_DIR = FileSystems.getDefault().getPath(flagConfig.luceneindexpath());
    }
    if (Files.exists(INDEX_DIR)) {
       throw new IllegalArgumentException(
           "Cannot save index to '" + INDEX_DIR + "' directory, please delete it first");
    }

    try {
      // Create IndexWriter using WhiteSpaceAnalyzer without any stopword list.
      IndexWriterConfig writerConfig = new IndexWriterConfig(new WhitespaceAnalyzer());
      IndexWriter writer = new IndexWriter(FSDirectory.open(INDEX_DIR), writerConfig);

      final File triplesTextFile = new File(args[0]);
      if (!triplesTextFile.exists() || !triplesTextFile.canRead()) {
        writer.close();
        throw new IOException("Document file '" + triplesTextFile.getAbsolutePath() +
            "' does not exist or is not readable, please check the path");
      }

      System.out.println("Indexing to directory '" +INDEX_DIR+ "'...");
      indexDoc(writer, triplesTextFile);
      writer.close();       
    } catch (IOException e) {
      System.out.println(" caught a " + e.getClass() +
          "\n with message: " + e.getMessage());
    }
  }

  /**
   * This class indexes the file passed as a parameter, writing to the index passed as a parameter.
   * Each predication is indexed as an individual document, with the fields "subject", "predicate", and "object"

   * @throws IOException
   */
  static void indexDoc(IndexWriter fsWriter, File triplesTextFile) throws IOException {
    BufferedReader theReader = new BufferedReader(new FileReader(triplesTextFile));
    int linecnt = 0;
    String lineIn = "";
    while ((lineIn  = theReader.readLine()) != null)  {   
    
    	// Output progress counter.
      if( ( ++linecnt % 10000 == 0 ) || ( linecnt < 10000 && linecnt % 1000 == 0 ) ){
        VerbatimLogger.info((linecnt) + " ... ");
      }
      
          
      String[] tokenizedLine = lineIn.split("\t");
           
      try {
        if (tokenizedLine.length < 18) {
          VerbatimLogger.warning(
              "Ignoring line in predication file that probably does not have a triple: " + lineIn + "\n");
          continue;
        }

        String subject = tokenizedLine[15].trim().toLowerCase();
        String predicate = tokenizedLine[16].trim();
        String object = tokenizedLine[17].trim().toLowerCase();
        String source = tokenizedLine[12];
        String PMID = tokenizedLine[0];
        
         try {PMID=PMID.substring(PMID.lastIndexOf('/')+1, PMID.lastIndexOf('.')); }//.replace(".txt", ""); }
        catch (Exception e) {}
    
         Document doc = new Document();
     	
		doc.add(new TextField("PMID",PMID, Field.Store.YES));
        
        String subject_copy   = subject.replaceAll(" ", "_");
        String predicate_copy = predicate.replaceAll(" ", "_").toUpperCase();
        String object_copy 	  = object.replaceAll(" ", "_");
         
        doc.add(new TextField("subject", subject_copy, Field.Store.YES));
        doc.add(new TextField("predicate", predicate_copy, Field.Store.YES));
        doc.add(new TextField("object", object_copy, Field.Store.YES));
        
        doc.add(new TextField("predication",subject_copy+predicate_copy+object_copy, Field.Store.NO));
        
		//create new FieldType to store term positions (TextField is not sufficiently configurable)
		FieldType ft = new FieldType();
		//ft.setIndexed(true);
		ft.setIndexOptions(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS);
		ft.setStored(true);
		ft.setTokenized(true);
		ft.setStoreTermVectors(true);
		ft.setStoreTermVectorPositions(true);
		Field contentsField = new Field("source", source, ft);
		Field tsubField = new Field("tokenized_subject", subject, ft);
		Field tobjField = new Field("tokenized_object", object, ft);
		Field tpredField = new Field("tokenized_predicate", predicate, ft);
		  
		doc.add(tsubField);
	    doc.add(tobjField);
	    doc.add(tpredField);
	    doc.add(contentsField);
		fsWriter.addDocument(doc);
      }
      catch (Exception e) {
        System.out.println(lineIn);
        e.printStackTrace();
      }
      
  
    }
    VerbatimLogger.info("\n");  // Newline after line counter prints.
    theReader.close();
  }
}
