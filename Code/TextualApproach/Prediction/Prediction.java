package Prediction;

public class Prediction {

	public String predictionIndex;
	public double confidenceScore;
	
	public Prediction(String label, double score)
	{
		predictionIndex = label;
		confidenceScore = score;
	}
	
}
