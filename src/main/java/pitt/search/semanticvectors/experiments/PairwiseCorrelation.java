package pitt.search.semanticvectors.experiments;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;

import pitt.search.semanticvectors.FlagConfig;
import pitt.search.semanticvectors.VectorStoreRAM;
import pitt.search.semanticvectors.vectors.RealVector;
import pitt.search.semanticvectors.vectors.Vector;
import pitt.search.semanticvectors.vectors.VectorFactory;
import pitt.search.semanticvectors.vectors.VectorType;

public class PairwiseCorrelation {

	//return ranks of scores for Spearman rho
	//simple (not optimized) procedure, first one that came to mind and also
	//found already implemented at code.hammerpig.com/rank-numbers-in-java.html
	//when looking around
	//keeping everything as Doubles for consistency with Pearson's downstream
	
	public static ArrayList<Double> Rank(ArrayList<Double> values)
	{
		ArrayList<Double> sortedValues = new ArrayList<Double>(values);
		Collections.sort(sortedValues);
		
		ArrayList<Double> ranks = new ArrayList<Double>();
		 
		for (int i=0; i < values.size(); i++)
			ranks.add(new Double(sortedValues.indexOf(values.get(i))));
	
		return ranks;
	}
	
	
	/**
	 * @param args
	 * @throws IOException 
	 */
	public static void main(String[] args) throws IOException {
		// TODO Auto-generated method stub

		FlagConfig flagConfig = FlagConfig.getFlagConfig(args);
		VectorStoreRAM termvectors = new VectorStoreRAM(flagConfig);
		termvectors.initFromFile(flagConfig.queryvectorfile());
		
		BufferedReader theReader = new BufferedReader(new FileReader(flagConfig.remainingArgs[0]));
		String inline = theReader.readLine();
		inline = theReader.readLine(); //skip header
		
		ArrayList<Double> humanScores = new ArrayList<Double>();
		ArrayList<Double> modelScores = new ArrayList<Double>();
		
		//keep track
		int paircount  = 0;
		int foundcount = 0;
		
		//read reference pairs, calculate similarities, or output to .err if vectors not found
		while (inline != null)
		{
			String[] components = inline.toLowerCase().split(",");
			paircount++;
			
			try {
				double score = termvectors.getVector(components[0]).measureOverlap(termvectors.getVector(components[1]));
				foundcount++;
				humanScores.add(Double.parseDouble(components[2]));
				modelScores.add(score);
				
				//uncomment to output raw values
				//System.out.println(components[2]+"\t"+score);
				
			}
			catch (NullPointerException e)
			{
				
				if (!termvectors.containsVector(components[0]))
				System.err.println("Vector not found for term "+components[0]);
				
				if (!termvectors.containsVector(components[1]))
				System.err.println("Vector not found for term "+components[1]);
			
			}
			
			
			inline = theReader.readLine();
		}
		
		//get ranks for Spearman correlation
		ArrayList<Double> humanRanks = PairwiseCorrelation.Rank(humanScores);
		ArrayList<Double> modelRanks = PairwiseCorrelation.Rank(modelScores);
		
		
		//calculate Pearson's R and Spearman's RHO (i.e. Pearson's on the ranks)
		Double[] human  = humanScores.toArray(new Double[1]);
		Double[] model  = modelScores.toArray(new Double[1]);
		Double[] humanR = humanRanks.toArray(new Double[1]);
		Double[] modelR = modelRanks.toArray(new Double[1]);
		
		double meanH  = 0;
		double meanM  = 0;

		//calculate means
		for (int q =0; q < human.length; q++)
			{
			 meanH  += human[q];
			 meanM  += model[q];
				}
		
		meanH  = meanH/(double) human.length;
		meanM  = meanM/(double) human.length;
		
		float[] coH  = new float[human.length];
		float[] coM  = new float[human.length];
		double coSP = 0;
		
		//subtract means
		for (int q =0; q < human.length; q++)
		{
		 coH[q]  = (float) (human[q] - meanH);
		 coM[q]  = (float) (model[q] - meanM);
		 
		 //use standard formula for Spearman's as Pearson's on ranks disagreed with matlab in 4th decimal place
		 //as it happens, this also disagrees slightly with Matlab in the 4th decimal, but is marginally easier to 
		 //implement anyhow
		 coSP += Math.pow(humanR[q].floatValue() - modelR[q].floatValue(), 2);
		}
		
		//create Vectors
		RealVector vectorH = new RealVector(coH);
		RealVector vectorM = new RealVector(coM);
		
		//calculate cosine
		System.out.println("Found vectors for "+foundcount+" of "+paircount+" pairs");
		System.out.println("Pearson's r 	= "+vectorH.measureOverlap(vectorM));
		System.out.println("Spearman's rho  = "+  (1 - (6*coSP/(foundcount*(Math.pow(foundcount,2) -1)))));
		
		theReader.close();
	}
}
