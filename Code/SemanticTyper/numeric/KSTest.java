package numeric;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import jsc.independentsamples.SmirnovTest;
import jsc.tests.H1;
import org.apache.commons.math3.stat.inference.KolmogorovSmirnovTest;

public class KSTest {
	
	public boolean predictLabelsForColumn(Map<String, ArrayList<Double>> trainingLabelToExamplesMap,
			ArrayList<Double> testExamples, int numPred, ArrayList<String> predictions, ArrayList<Double> confidenceScores) {

		List<Prediction> sortedPredictions = new ArrayList<Prediction>();	// descending order of p-Value
		
		//KolmogorovSmirnovTest test = new KolmogorovSmirnovTest();
		
  	double pValue;
    
  	double[] sample1 = new double[testExamples.size()];
  	for(int i = 0; i < testExamples.size(); i++){
      sample1[i] = testExamples.get(i);
  	}
    
    for (Entry<String, ArrayList<Double>> entry : trainingLabelToExamplesMap.entrySet()) {
    	
    	String label = entry.getKey();
    	ArrayList<Double> trainExamples = entry.getValue();
    	
    	System.out.println(label+" "+trainExamples.size());
    	double[] sample2 = new double[trainExamples.size()];
    	for(int i = 0; i < trainExamples.size(); i++){
        sample2[i] = trainExamples.get(i);
    	} 	

  		SmirnovTest test = new SmirnovTest(sample1, sample2, H1.NOT_EQUAL, true);
    	pValue = test.getSP();
  		
    	//pValue = test.kolmogorovSmirnovTest(sample1, sample2);
    	
    	
    	Prediction pred = new Prediction(label, pValue);

    	sortedPredictions.add(pred);
   
    }
   
		// sorting based on p-Value
		Collections.sort(sortedPredictions, new PredictionComparator());
    
		for(int j=0; j<numPred && j<sortedPredictions.size(); j++)
		{
			predictions.add(sortedPredictions.get(j).predictionLabel);
			confidenceScores.add(sortedPredictions.get(j).confidenceScore);
		}
		
		return true;
	}
	
}
