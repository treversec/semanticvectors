package pitt.search.semanticvectors.experiments;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;

import pitt.search.semanticvectors.FlagConfig;
import pitt.search.semanticvectors.VectorStoreRAM;
import pitt.search.semanticvectors.vectors.RealVector;
import pitt.search.semanticvectors.vectors.Vector;
import pitt.search.semanticvectors.vectors.VectorFactory;
import pitt.search.semanticvectors.vectors.VectorType;

public class PairwiseCorrelation {

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
		
		//read reference pairs, calculate similarities, or output to .err if vectors not found
		while (inline != null)
		{
			String[] components = inline.toLowerCase().split(",");
			
			try {
				double score = termvectors.getVector(components[0]).measureOverlap(termvectors.getVector(components[1]));
				humanScores.add(Double.parseDouble(components[2]));
				modelScores.add(score);
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
		
		//calculate pearson's R
		Double[] human = humanScores.toArray(new Double[1]);
		Double[] model = modelScores.toArray(new Double[1]);
		double meanH = 0;
		double meanM = 0;
		
		//calculate means
		for (int q =0; q < human.length; q++)
			{
			 meanH += human[q];
			 meanM += model[q];
			}
		
		meanH = meanH/(double) human.length;
		meanM = meanM/(double) human.length;
		
		float[] coH = new float[human.length];
		float[] coM = new float[human.length];
		
		//subtract means
		for (int q =0; q < human.length; q++)
		{
		 coH[q] = (float) (human[q] - meanH);
		 coM[q] = (float) (model[q] - meanM);
		}
		
		//create Vectors
		RealVector vectorH = new RealVector(coH);
		RealVector vectorM = new RealVector(coM);
	
		
		//calculate cosine
		System.out.println("Pearson's r = "+vectorH.measureOverlap(vectorM));
		
		
	}

}
