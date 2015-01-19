package prediction;

public class Prediction {

	public String predictionLabel;
	public double confidenceScore;
	
	public Prediction(String label, double score)
	{
		predictionLabel = label;
		confidenceScore = score;
	}
	
}
