package typer;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import misc.TrigramJaccardCoefficient;
import numeric.KSTest;
import org.apache.lucene.queryparser.classic.ParseException;
import sun.misc.Cleaner;
import tfIdf.TfIdfTyper;
import tfIdf.TopKSearcher;
import edu.isi.karma.modeling.semantictypes.crfmodelhandler.CRFModelHandler;
import edu.isi.karma.modeling.semantictypes.crfmodelhandler.CRFModelHandler.ColumnFeature;
import edu.isi.karma.modeling.semantictypes.crfmodelhandler.CRFModelHandler.Example;

public class SemanticTyper {
	
	public static int numPred = 4;
	private static Map<Integer,HashMap<String, ArrayList<Example>>> fileLabelExampleMap;
	private static Map<Integer,HashMap<String, Integer>> fileLabelNumericCountMap;
	private static Map<String,Integer> fileIndexMap;
	private static List<String> fileNames;
	
	/*
	 * While training,
	 * 	if numericCount >= pureNumeric -> train as NUMERIC COLUMN
	 * 	else if numericCount <= pureText -> train as TEXTUAL COLUMN
	 * 	else (pureText<numericCount<pureNumeric) -> train as NUMERIC as well as TEXTUAL 
	 * 
	 * While testing,
	 * 	if numericCount >= pureNumeric -> test as NUMERIC COLUMN
	 * 	else (numericCount<pureNumeric) -> test as TEXTUAL COLUMN
	 */
	private static double pureNumeric = 0.8;
	private static double pureText = 0.6;
	
	/**
	 * Function to evaluate performance of NEW Semantic Typer on collection of datasets
	 * Ensure the dataset files are in CRFModelFile format and present in "Files" directory
	 * @throws FileNotFoundException 
	 * @throws ParseException 
	 */
	public static boolean evaluateMultipleDatasets() throws FileNotFoundException, IOException, ParseException
	{
		
		fileLabelExampleMap = new HashMap<Integer,HashMap<String, ArrayList<Example>>>();
		fileLabelNumericCountMap = new HashMap<Integer,HashMap<String, Integer>>();
		fileIndexMap = new HashMap<String, Integer>();
		fileNames = new ArrayList<String>();
		
		File folder = new File("Files");
		File[] listOfFiles = folder.listFiles();
		
		int curFileNo=0;
		for (File file : listOfFiles) {
		    if (file.isFile() && !file.isHidden()) {
		    		fileNames.add(file.getName());
		    		fileIndexMap.put(file.getName(), curFileNo);
		    		
		    		HashMap<String,ArrayList<Example>> labelToExampleMap = new HashMap<String,ArrayList<Example>>();
		    		HashMap<String,Integer> labelToNumCountMap = new HashMap<String,Integer>();
		    		readExamplesFromFile(file, labelToExampleMap, labelToNumCountMap); // read list of examples from file
		        
		        fileLabelExampleMap.put(curFileNo, labelToExampleMap); 
		        fileLabelNumericCountMap.put(curFileNo, labelToNumCountMap);
		    		curFileNo++;
		    }
		}
		
		Map<String, ArrayList<Example>> trainingLabelToExamplesMap = new HashMap<String, ArrayList<Example>>() ;
		Map<String, ArrayList<Example>> testLabelToExamplesMap = new HashMap<String, ArrayList<Example>>() ;
		
		Map<String, ArrayList<Double>> numericTrainingLabelToExamplesMap = new HashMap<String, ArrayList<Double>>() ;
		Map<String, ArrayList<Example>> textualTrainingLabelToExamplesMap = new HashMap<String, ArrayList<Example>>() ;
		
		int numFilesToTrain, numFilesAdded, curTrainFileNo, curTestFileNo;
		
		// parameter to choose which file to test below, run next time directly, already modified
		curFileNo=0;
		
		double topKCorrect_trainingWithK[] = new double[fileNames.size()-1];
		double MRR_trainingWithK[] = new double[fileNames.size()-1];
		for(int i=0; i<fileNames.size()-1; i++)
		{
			topKCorrect_trainingWithK[i]=0.0;
			MRR_trainingWithK[i]=0.0;
		}
		
		while(curFileNo<fileNames.size())
		{
			curTestFileNo = curFileNo;
			numFilesToTrain = 1;	
			
			System.out.println();
			System.out.println("Testing file name: "+fileNames.get(curTestFileNo));
			System.out.println();
			
		  // clearing test set
			testLabelToExamplesMap.clear();
			// fill in test set
			HashMap<String, ArrayList<Example>> labelExampleMap2 = fileLabelExampleMap.get(curTestFileNo);			
			testLabelToExamplesMap.putAll(labelExampleMap2);
			
			/*
			List<Double> avgAccSum = new ArrayList<Double>(numPred);
			for(int i=0;i<numPred;i++)
			{
				avgAccSum.add(0.0);
			}
			long totalTime=0;
			*/
			
			double level2MRR = 0.0;
			double level2TopKCorrect = 0.0;
			long totalTime=0;
			
			// loop for fileNames.size()-1 different tests for same test file
			while(numFilesToTrain < fileNames.size())
			{
				numFilesAdded=0;
				System.out.println("Training with "+numFilesToTrain+" training datasets");
				
				// clearing training set
				trainingLabelToExamplesMap.clear();
				numericTrainingLabelToExamplesMap.clear();
				textualTrainingLabelToExamplesMap.clear();
				
				// fill in training set
				curTrainFileNo=curTestFileNo+1;
				while(numFilesAdded<numFilesToTrain && curTrainFileNo<fileNames.size())
				{
					numFilesAdded++;
		    		
		    	HashMap<String, ArrayList<Example>> trainlabelExampleMap = fileLabelExampleMap.get(curTrainFileNo);
		    	System.out.println(fileNames.get(curTrainFileNo));
		    	  		
			    for (Entry<String, ArrayList<Example>> entryCurFile : trainlabelExampleMap.entrySet()) {
			    	if(trainingLabelToExamplesMap.containsKey(entryCurFile.getKey())) // label already present in training set
			    	{
			    		ArrayList<Example> examplesTrainSet = trainingLabelToExamplesMap.get(entryCurFile.getKey());
			    		ArrayList<Example> examplesCurFile = trainlabelExampleMap.get(entryCurFile.getKey());
			    		examplesTrainSet.addAll(examplesCurFile);
			    	}
			    	else
			    	{
			    		ArrayList<Example> examples = new ArrayList<Example>();
			    		examples.addAll(entryCurFile.getValue());
			    		trainingLabelToExamplesMap.put(entryCurFile.getKey(), examples);
			    	}
			    }
			    	
		    	curTrainFileNo++;
				}
				curTrainFileNo=0;
				while(numFilesAdded<numFilesToTrain && curTrainFileNo<fileNames.size())
				{
		    	numFilesAdded++;
		    	
		    	HashMap<String, ArrayList<Example>> trainlabelExampleMap = fileLabelExampleMap.get(curTrainFileNo);
		    	
			    for (Entry<String, ArrayList<Example>> entryCurFile : trainlabelExampleMap.entrySet()) {
			    	if(trainingLabelToExamplesMap.containsKey(entryCurFile.getKey())) // label already present in training set
			    	{
			    		ArrayList<Example> examplesTrainSet = trainingLabelToExamplesMap.get(entryCurFile.getKey());
			    		ArrayList<Example> examplesCurFile = trainlabelExampleMap.get(entryCurFile.getKey());
			    		examplesTrainSet.addAll(examplesCurFile);
			    	}
			    	else
			    	{
			    		ArrayList<Example> examples = new ArrayList<Example>();
			    		examples.addAll(entryCurFile.getValue());
			    		trainingLabelToExamplesMap.put(entryCurFile.getKey(), examples);
			    	}
			    }
			    	
		    	curTrainFileNo++;
				}
				
				// splitting into numeric and textual training columns
				
				for (Entry<String, ArrayList<Example>> entry : trainingLabelToExamplesMap.entrySet()) {
					String label = entry.getKey();
					ArrayList<Example> exampleList = entry.getValue();
					
					int numericCount = 0;
					int noNonNULLValues = 0;
					for(int counter=0; counter<exampleList.size(); counter++) {
						if(exampleList.get(counter).getType().equalsIgnoreCase("NUM")) {
							numericCount++;
						}
						if(!exampleList.get(counter).getString().equalsIgnoreCase("NULL") && !exampleList.get(counter).getString().equalsIgnoreCase("")) {
							noNonNULLValues++;
						}
					}
					
					double fractionNumeric = ((double)(numericCount+0.0))/((double)(noNonNULLValues+0.0));
					
					if( fractionNumeric >= pureNumeric)
					{
						ArrayList<Double> exampleListNumeric = new ArrayList<Double>();
						cleanExamplesNumeric(exampleList, exampleListNumeric);
						numericTrainingLabelToExamplesMap.put(label, exampleListNumeric);
						
						System.out.println(label +" : NUMERIC");
					}
					else if( fractionNumeric <= pureText)
					{
						textualTrainingLabelToExamplesMap.put(label, exampleList);
						
						System.out.println(label +" : TEXTUAL");
					}
					else
					{
						textualTrainingLabelToExamplesMap.put(label, exampleList);
						
						ArrayList<Double> exampleListNumeric = new ArrayList<Double>();
						cleanExamplesNumeric(exampleList, exampleListNumeric);
						numericTrainingLabelToExamplesMap.put(label, exampleListNumeric);
						
						System.out.println(label +" : NUMERIC + TEXTUAL");
					}
					
				}
				
				// Training
				
				final long startTime = System.currentTimeMillis();
				// Index TEXTUAL training columns
				if(TfIdfTyper.indexTrainingColumns(textualTrainingLabelToExamplesMap)==false)
				{
					System.exit(1);
				}
				// No training for NUMERIC training columns
				final long endTime = System.currentTimeMillis();
				System.out.println();
				
				// Testing
				
				int lblNo=1;
				
				double topKCorrect = 0.0;
				double mrr = 0.0;
				
				/*
				List<Integer> perLabelCorrect = new ArrayList<Integer>(numPred); // per-label
				List<Integer> TopKCorrect = new ArrayList<Integer>(numPred);
				List<Integer> TopKTotal = new ArrayList<Integer>(numPred);
				for(int i=0;i<numPred;i++)
				{
					TopKCorrect.add(0);
					TopKTotal.add(0);
				}
				*/
				
				TopKSearcher topKSearcher = new TopKSearcher();   // for TF-IDF
				KSTest ksTest = new KSTest(); 										// for Kolmogorov-Smirnov Test
				
				for (Entry<String, ArrayList<Example>> entry : testLabelToExamplesMap.entrySet()) {
					
					String label = entry.getKey();
					ArrayList<Example> examples = entry.getValue();
					System.out.println("Testing for label " +lblNo+"/"+testLabelToExamplesMap.size()+" : "+label);
					
					ArrayList<String> predictions = new ArrayList<String>();
					ArrayList<Double> confidenceScores = new ArrayList<Double>();
					
					// choosing to test as numeric or textual column
				
					int numericCount = 0;
					int noNonNULLExamples = 0;
					for(int counter=0; counter<examples.size(); counter++) {
						if(examples.get(counter).getType().equalsIgnoreCase("NUM")) {
							numericCount++;
						}
						if(!examples.get(counter).getString().equalsIgnoreCase("NULL") && !examples.get(counter).getString().equalsIgnoreCase("")) {
							noNonNULLExamples++;
						}
					}
					double fractionNumeric = ((double)(numericCount+0.0))/((double)(noNonNULLExamples+0.0));
					if( fractionNumeric >= pureNumeric)
					{
		    		ArrayList<Double> exampleListNumeric = new ArrayList<Double>();
						cleanExamplesNumeric(examples, exampleListNumeric);
						
						System.out.println(exampleListNumeric.size());
						if(ksTest.predictLabelsForColumn(numericTrainingLabelToExamplesMap , exampleListNumeric, numPred, predictions, confidenceScores)==false)
						{
							System.exit(1);
						}
						
						System.out.println(label +" : NUMERIC");
					}
					else
					{
						if(TfIdfTyper.predictLabelsForColumn(topKSearcher, examples, predictions, confidenceScores)==false)
						{
							System.exit(1);
						}
						
						System.out.println(label +" : TEXTUAL");
					}
			
					// evaluation
					int predictionsSize = predictions.size();
					System.out.println("Predictions:");
					if(predictionsSize==0)
					{
						System.out.println("Nil");
					}
					else
					{
						for(int j=0;j<predictionsSize; j++)
						{
							System.out.println((j+1)+": "+predictions.get(j)+" confidenceScore = "+confidenceScores.get(j));
						}
					}
					
					/*
					perLabelCorrect.clear();
					for(int i=0;i<numPred;i++)
					{
						perLabelCorrect.add(0);
					}
					
					
						for(int k=1; k<=numPred; k++)
						{
								TopKTotal.set(k-1, (TopKTotal.get(k-1))+1);
						}
						for(int k=0; k<predictionsSize; k++)
						{
							if(predictions.get(k).equalsIgnoreCase(label))
							{
								for(int j=k; j<numPred; j++)
								{
									perLabelCorrect.set(j, 1);
									TopKCorrect.set(j, (TopKCorrect.get(j))+1);									
								}
								break; // important bug
							}
						}
					
					
					for(int i=0;i<numPred;i++)
					{
						System.out.println("Top-"+(i+1)+" Accuracy = "+perLabelCorrect.get(i));
					}
					*/
					
					if(!trainingLabelToExamplesMap.containsKey(label)) // label testing on hasn't been trained on
					{
						if(predictions.size()==0) // identified that no such label is present
						{
							topKCorrect += 1;
							mrr += 1.0;
						}
					}
					else
					{
						for(int k=0; k<predictionsSize; k++)
						{
							if(predictions.get(k).equalsIgnoreCase(label))
							{
								topKCorrect += 1;
								mrr += (1.0/(double)(k+1));
								break;
							}
						}
					}
					
					lblNo++;
				}
				
				System.out.println();
				System.out.println("Results for training with "+numFilesToTrain+" datasets");
				
				topKCorrect_trainingWithK[numFilesToTrain-1] += (topKCorrect/testLabelToExamplesMap.size());
				MRR_trainingWithK[numFilesToTrain-1] += (mrr/testLabelToExamplesMap.size());
				
				level2MRR += (mrr/testLabelToExamplesMap.size());
				level2TopKCorrect += (topKCorrect/testLabelToExamplesMap.size());
				System.out.println("MRR: "+(mrr/testLabelToExamplesMap.size()));
				System.out.println("Top-4 Accuracy: "+(topKCorrect/testLabelToExamplesMap.size()));
				
				System.out.println();
				totalTime+=(endTime - startTime);
				System.out.println("Total training time: " + ((double)(endTime - startTime + 0.0))/1000.0 +" s" );
				System.out.println();

				numFilesToTrain++;
				
				//break;
			}
		
			System.out.println();
			System.out.println();
			System.out.println("Aggregated results for " + (fileNames.size()-1) +" runs");
			System.out.println("Average MRR: "+level2MRR/(fileNames.size()-1));
			System.out.println("Average Top-4 Accuracy: "+level2TopKCorrect/(fileNames.size()-1));
			System.out.println();
			System.out.println("Avg training time: "+((double)(totalTime+0.0))/((double)(fileNames.size()-1)*1000.0)+" s");
			
			curFileNo++;
			
			System.out.println("------------------------------------------------------------------------------------------");
			
			//break;
		}
		
		System.out.println("*******************************************************************************************");
		System.out.println();
		for(int i=0; i<fileNames.size()-1; i++)
		{
			System.out.println("Aggregated Results for training with "+(i+1)+" files:");
			System.out.println("Average MRR: "+(MRR_trainingWithK[i]/fileNames.size()));
			System.out.println("Average Top-4 Accuracy: "+(topKCorrect_trainingWithK[i]/fileNames.size()));
			System.out.println();
		}
		
		return true;
	}
	
	/**
	 * Evaluation of new dataset
	 */
	public static boolean evaluateRoundRobin() throws FileNotFoundException, IOException, ParseException
	{
		
		Map<Integer,List<String>> trainList = new HashMap<Integer,List<String>>();
		List<String> trainListFord1 = new ArrayList<String>(Arrays.asList("d2","f1","g1","w1","d3","f2","g2","w2","g3","w3","g4","w4","g5"));
		trainList.put(0, trainListFord1);
		List<String> trainListFord2 = new ArrayList<String>(Arrays.asList("d1","f1","g1","w1","d3","f2","g2","w2","g3","w3","g4","w4","g5"));
		trainList.put(1, trainListFord2);
		List<String> trainListFord3 = new ArrayList<String>(Arrays.asList("d1","f1","g1","w1","d2","f2","g2","w2","g3","w3","g4","w4","g5"));
		trainList.put(2, trainListFord3);
		List<String> trainListForf1 = new ArrayList<String>(Arrays.asList("f2","g1","w1","d1","g2","w2","d2","g3","w3","d3","g4","w4","g5"));
		trainList.put(3, trainListForf1);
		List<String> trainListForf2 = new ArrayList<String>(Arrays.asList("f1","g1","w1","d1","g2","w2","d2","g3","w3","d3","g4","w4","g5"));
		trainList.put(4, trainListForf2);
		List<String> trainListForg1 = new ArrayList<String>(Arrays.asList("g2","w1","d1","f1","g3","w2","d2","f2","g4","w3","d3","g5","w4"));
		trainList.put(5, trainListForg1);
		List<String> trainListForg2 = new ArrayList<String>(Arrays.asList("g1","w1","d1","f1","g3","w2","d2","f2","g4","w3","d3","g5","w4"));
		trainList.put(6, trainListForg2);
		List<String> trainListForg3 = new ArrayList<String>(Arrays.asList("g1","w1","d1","f1","g2","w2","d2","f2","g4","w3","d3","g5","w4"));
		trainList.put(7, trainListForg3);
		List<String> trainListForg4 = new ArrayList<String>(Arrays.asList("g1","w1","d1","f1","g2","w2","d2","f2","g3","w3","d3","g5","w4"));
		trainList.put(8, trainListForg4);
		List<String> trainListForg5 = new ArrayList<String>(Arrays.asList("g1","w1","d1","f1","g2","w2","d2","f2","g3","w3","d3","g4","w4"));
		trainList.put(9, trainListForg5);
		List<String> trainListForw1 = new ArrayList<String>(Arrays.asList("w2","d1","f1","g1","w3","d2","f2","g2","w4","d3","g3","g4","g5"));
		trainList.put(10, trainListForw1);
		List<String> trainListForw2 = new ArrayList<String>(Arrays.asList("w1","d1","f1","g1","w3","d2","f2","g2","w4","d3","g3","g4","g5"));
		trainList.put(11, trainListForw2);
		List<String> trainListForw3 = new ArrayList<String>(Arrays.asList("w1","d1","f1","g1","w2","d2","f2","g2","w4","d3","g3","g4","g5"));
		trainList.put(12, trainListForw3);
		List<String> trainListForw4 = new ArrayList<String>(Arrays.asList("w1","d1","f1","g1","w2","d2","f2","g2","w3","d3","g3","g4","g5"));
		trainList.put(13, trainListForw4);
		
		fileLabelExampleMap = new HashMap<Integer,HashMap<String, ArrayList<Example>>>();
		fileLabelNumericCountMap = new HashMap<Integer,HashMap<String, Integer>>();
		fileIndexMap = new HashMap<String, Integer>();
		fileNames = new ArrayList<String>();
		
		File folder = new File("Files");
		File[] listOfFiles = folder.listFiles();
		
		int curFileNo=0;
		for (File file : listOfFiles) {
		    if (file.isFile() && !file.isHidden()) {
		    		fileNames.add(file.getName());
		    		fileIndexMap.put(file.getName(), curFileNo);
		    		
		    		HashMap<String,ArrayList<Example>> labelToExampleMap = new HashMap<String,ArrayList<Example>>();
		    		HashMap<String,Integer> labelToNumCountMap = new HashMap<String,Integer>();
		    		readExamplesFromFile(file, labelToExampleMap, labelToNumCountMap); // read list of examples from file
		        
		        fileLabelExampleMap.put(curFileNo, labelToExampleMap); 
		        fileLabelNumericCountMap.put(curFileNo, labelToNumCountMap);
		    		curFileNo++;
		    }
		}
		
		Map<String, ArrayList<Example>> trainingLabelToExamplesMap = new HashMap<String, ArrayList<Example>>() ;
		Map<String, ArrayList<Example>> testLabelToExamplesMap = new HashMap<String, ArrayList<Example>>() ;
		
		Map<String, ArrayList<Double>> numericTrainingLabelToExamplesMap = new HashMap<String, ArrayList<Double>>() ;
		Map<String, ArrayList<Example>> textualTrainingLabelToExamplesMap = new HashMap<String, ArrayList<Example>>() ;
		
		int numFilesToTrain, numFilesAdded, curTrainFileNo, curTestFileNo;
		
		// parameter to choose which file to test below, run next time directly, already modified
		curFileNo=0;
		
		double topKCorrect_trainingWithK[] = new double[fileNames.size()-1];
		double MRR_trainingWithK[] = new double[fileNames.size()-1];
		for(int i=0; i<fileNames.size()-1; i++)
		{
			topKCorrect_trainingWithK[i]=0.0;
			MRR_trainingWithK[i]=0.0;
		}
		
		while(curFileNo<fileNames.size())
		{
			curTestFileNo = curFileNo;
			numFilesToTrain = 1;	
			
			System.out.println();
			System.out.println("Testing file name: "+fileNames.get(curTestFileNo));
			System.out.println();
			
		  // clearing test set
			testLabelToExamplesMap.clear();
			// fill in test set
			HashMap<String, ArrayList<Example>> labelExampleMap2 = fileLabelExampleMap.get(curTestFileNo);			
			testLabelToExamplesMap.putAll(labelExampleMap2);
			
			/*
			List<Double> avgAccSum = new ArrayList<Double>(numPred);
			for(int i=0;i<numPred;i++)
			{
				avgAccSum.add(0.0);
			}
			long totalTime=0;
			*/
			
			double level2MRR = 0.0;
			double level2TopKCorrect = 0.0;
			long totalTime=0;
			
			// loop for fileNames.size()-1 different tests for same test file
			while(numFilesToTrain < fileNames.size())
			{
				numFilesAdded=0;
				System.out.println("Training with "+numFilesToTrain+" training datasets");
				
				// clearing training set
				trainingLabelToExamplesMap.clear();
				numericTrainingLabelToExamplesMap.clear();
				textualTrainingLabelToExamplesMap.clear();
				
				List<String> trainListForCurTestFile = trainList.get(curTestFileNo);
				
				// fill in training set
				while(numFilesAdded<numFilesToTrain)
				{						
		    	HashMap<String, ArrayList<Example>> trainlabelExampleMap = fileLabelExampleMap.get(fileIndexMap.get(trainListForCurTestFile.get(numFilesAdded)
		    			+".txt"));
		    	System.out.println(trainListForCurTestFile.get(numFilesAdded)+".txt");
		    	  		
			    for (Entry<String, ArrayList<Example>> entryCurFile : trainlabelExampleMap.entrySet()) {
			    	if(trainingLabelToExamplesMap.containsKey(entryCurFile.getKey())) // label already present in training set
			    	{
			    		ArrayList<Example> examplesTrainSet = trainingLabelToExamplesMap.get(entryCurFile.getKey());
			    		ArrayList<Example> examplesCurFile = trainlabelExampleMap.get(entryCurFile.getKey());
			    		examplesTrainSet.addAll(examplesCurFile);
			    	}
			    	else
			    	{
			    		ArrayList<Example> examples = new ArrayList<Example>();
			    		examples.addAll(entryCurFile.getValue());
			    		trainingLabelToExamplesMap.put(entryCurFile.getKey(), examples);
			    	}
			    }
			    	
			    numFilesAdded++;
				}
				
				
				// splitting into numeric and textual training columns
				
				for (Entry<String, ArrayList<Example>> entry : trainingLabelToExamplesMap.entrySet()) {
					String label = entry.getKey();
					ArrayList<Example> exampleList = entry.getValue();
					
					int numericCount = 0;
					int noNonNULLValues = 0;
					for(int counter=0; counter<exampleList.size(); counter++) {
						if(exampleList.get(counter).getType().equalsIgnoreCase("NUM")) {
							numericCount++;
						}
						if(!exampleList.get(counter).getString().equalsIgnoreCase("NULL") && !exampleList.get(counter).getString().equalsIgnoreCase("")) {
							noNonNULLValues++;
						}
					}
					
					double fractionNumeric = ((double)(numericCount+0.0))/((double)(noNonNULLValues+0.0));
					
					if( fractionNumeric >= pureNumeric)
					{
						ArrayList<Double> exampleListNumeric = new ArrayList<Double>();
						cleanExamplesNumeric(exampleList, exampleListNumeric);
						numericTrainingLabelToExamplesMap.put(label, exampleListNumeric);
						
						System.out.println(label +" : NUMERIC");
					}
					else if( fractionNumeric <= pureText)
					{
						textualTrainingLabelToExamplesMap.put(label, exampleList);
						
						System.out.println(label +" : TEXTUAL");
					}
					else
					{
						textualTrainingLabelToExamplesMap.put(label, exampleList);
						
						ArrayList<Double> exampleListNumeric = new ArrayList<Double>();
						cleanExamplesNumeric(exampleList, exampleListNumeric);
						numericTrainingLabelToExamplesMap.put(label, exampleListNumeric);
						
						System.out.println(label +" : NUMERIC + TEXTUAL");
					}
					
				}
				
				// Training
				
				final long startTime = System.currentTimeMillis();
				// Index TEXTUAL training columns
				if(TfIdfTyper.indexTrainingColumns(textualTrainingLabelToExamplesMap)==false)
				{
					System.exit(1);
				}
				// No training for NUMERIC training columns
				final long endTime = System.currentTimeMillis();
				System.out.println();
				
				// Testing
				
				int lblNo=1;
				
				double topKCorrect = 0.0;
				double mrr = 0.0;
				
				/*
				List<Integer> perLabelCorrect = new ArrayList<Integer>(numPred); // per-label
				List<Integer> TopKCorrect = new ArrayList<Integer>(numPred);
				List<Integer> TopKTotal = new ArrayList<Integer>(numPred);
				for(int i=0;i<numPred;i++)
				{
					TopKCorrect.add(0);
					TopKTotal.add(0);
				}
				*/
				
				TopKSearcher topKSearcher = new TopKSearcher();   // for TF-IDF
				KSTest ksTest = new KSTest(); 										// for Kolmogorov-Smirnov Test
				
				for (Entry<String, ArrayList<Example>> entry : testLabelToExamplesMap.entrySet()) {
					
					String label = entry.getKey();
					ArrayList<Example> examples = entry.getValue();
					System.out.println("Testing for label " +lblNo+"/"+testLabelToExamplesMap.size()+" : "+label);
					
					ArrayList<String> predictions = new ArrayList<String>();
					ArrayList<Double> confidenceScores = new ArrayList<Double>();
					
					// choosing to test as numeric or textual column
				
					int numericCount = 0;
					int noNonNULLExamples = 0;
					for(int counter=0; counter<examples.size(); counter++) {
						if(examples.get(counter).getType().equalsIgnoreCase("NUM")) {
							numericCount++;
						}
						if(!examples.get(counter).getString().equalsIgnoreCase("NULL") && !examples.get(counter).getString().equalsIgnoreCase("")) {
							noNonNULLExamples++;
						}
					}
					double fractionNumeric = ((double)(numericCount+0.0))/((double)(noNonNULLExamples+0.0));
					if( fractionNumeric >= pureNumeric)
					{
		    		ArrayList<Double> exampleListNumeric = new ArrayList<Double>();
						cleanExamplesNumeric(examples, exampleListNumeric);
						
						System.out.println(exampleListNumeric.size());
						if(ksTest.predictLabelsForColumn(numericTrainingLabelToExamplesMap , exampleListNumeric, numPred, predictions, confidenceScores)==false)
						{
							System.exit(1);
						}
						
						System.out.println(label +" : NUMERIC");
					}
					else
					{
						if(TfIdfTyper.predictLabelsForColumn(topKSearcher, examples, predictions, confidenceScores)==false)
						{
							System.exit(1);
						}
						
						System.out.println(label +" : TEXTUAL");
					}
			
					// evaluation
					int predictionsSize = predictions.size();
					System.out.println("Predictions:");
					if(predictionsSize==0)
					{
						System.out.println("Nil");
					}
					else
					{
						for(int j=0;j<predictionsSize; j++)
						{
							System.out.println((j+1)+": "+predictions.get(j)+" confidenceScore = "+confidenceScores.get(j));
						}
					}
					
					/*
					perLabelCorrect.clear();
					for(int i=0;i<numPred;i++)
					{
						perLabelCorrect.add(0);
					}
					
					
						for(int k=1; k<=numPred; k++)
						{
								TopKTotal.set(k-1, (TopKTotal.get(k-1))+1);
						}
						for(int k=0; k<predictionsSize; k++)
						{
							if(predictions.get(k).equalsIgnoreCase(label))
							{
								for(int j=k; j<numPred; j++)
								{
									perLabelCorrect.set(j, 1);
									TopKCorrect.set(j, (TopKCorrect.get(j))+1);									
								}
								break; // important bug
							}
						}
					
					
					for(int i=0;i<numPred;i++)
					{
						System.out.println("Top-"+(i+1)+" Accuracy = "+perLabelCorrect.get(i));
					}
					*/
					
					if(!trainingLabelToExamplesMap.containsKey(label)) // label testing on hasn't been trained on
					{
						if(predictions.size()==0) // identified that no such label is present
						{
							topKCorrect += 1;
							mrr += 1.0;
						}
					}
					else
					{
						for(int k=0; k<predictionsSize; k++)
						{
							if(predictions.get(k).equalsIgnoreCase(label))
							{
								topKCorrect += 1;
								mrr += (1.0/(double)(k+1));
								break;
							}
						}
					}
					
					lblNo++;
				}
				
				System.out.println();
				System.out.println("Results for training with "+numFilesToTrain+" datasets");
				
				topKCorrect_trainingWithK[numFilesToTrain-1] += (topKCorrect/testLabelToExamplesMap.size());
				MRR_trainingWithK[numFilesToTrain-1] += (mrr/testLabelToExamplesMap.size());
				
				level2MRR += (mrr/testLabelToExamplesMap.size());
				level2TopKCorrect += (topKCorrect/testLabelToExamplesMap.size());
				System.out.println("MRR: "+(mrr/testLabelToExamplesMap.size()));
				System.out.println("Top-4 Accuracy: "+(topKCorrect/testLabelToExamplesMap.size()));
				
				System.out.println();
				totalTime+=(endTime - startTime);
				System.out.println("Total training time: " + ((double)(endTime - startTime + 0.0))/1000.0 +" s" );
				System.out.println();

				numFilesToTrain++;
				
				//break;
			}
		
			System.out.println();
			System.out.println();
			System.out.println("Aggregated results for " + (fileNames.size()-1) +" runs");
			System.out.println("Average MRR: "+level2MRR/(fileNames.size()-1));
			System.out.println("Average Top-4 Accuracy: "+level2TopKCorrect/(fileNames.size()-1));
			System.out.println();
			System.out.println("Avg training time: "+((double)(totalTime+0.0))/((double)(fileNames.size()-1)*1000.0)+" s");
			
			curFileNo++;
			
			System.out.println("------------------------------------------------------------------------------------------");
			
			//break;
		}
		
		System.out.println("*******************************************************************************************");
		System.out.println();
		for(int i=0; i<fileNames.size()-1; i++)
		{
			System.out.println("Aggregated Results for training with "+(i+1)+" files:");
			System.out.println("Average MRR: "+(MRR_trainingWithK[i]/fileNames.size()));
			System.out.println("Average Top-4 Accuracy: "+(topKCorrect_trainingWithK[i]/fileNames.size()));
			System.out.println();
		}
		
		return true;
	}
	
	/**
	 * Function to evaluate performance of NEW Semantic Typer on collection of datasets
	 * Ensure the dataset files are in CRFModelFile format and present in "Files" directory
	 * @throws FileNotFoundException 
	 * @throws ParseException 
	 */
	public static boolean evaluateMultipleDatasetsWithColumnInfo() throws FileNotFoundException, IOException, ParseException
	{
		
		fileLabelExampleMap = new HashMap<Integer,HashMap<String, ArrayList<Example>>>();
		fileLabelNumericCountMap = new HashMap<Integer,HashMap<String, Integer>>();
		fileIndexMap = new HashMap<String, Integer>();
		fileNames = new ArrayList<String>();
		
		File folder = new File("Files");
		File[] listOfFiles = folder.listFiles();
		
		int curFileNo=0;
		for (File file : listOfFiles) {
		    if (file.isFile() && !file.isHidden()) {
		    		fileNames.add(file.getName());
		    		fileIndexMap.put(file.getName(), curFileNo);
		    		
		    		HashMap<String,ArrayList<Example>> labelToExampleMap = new HashMap<String,ArrayList<Example>>();
		    		HashMap<String,Integer> labelToNumCountMap = new HashMap<String,Integer>();
		    		readExamplesFromFileWithColumn(file, labelToExampleMap, labelToNumCountMap); // read list of examples from file
		        
		        fileLabelExampleMap.put(curFileNo, labelToExampleMap); 
		        fileLabelNumericCountMap.put(curFileNo, labelToNumCountMap);
		    		curFileNo++;
		    }
		}
		
		Map<String, ArrayList<Example>> trainingLabelToExamplesMap = new HashMap<String, ArrayList<Example>>() ;
		Map<String, ArrayList<Example>> testLabelToExamplesMap = new HashMap<String, ArrayList<Example>>() ;
		
		Map<String, ArrayList<Double>> numericTrainingLabelToExamplesMap = new HashMap<String, ArrayList<Double>>() ;
		Map<String, ArrayList<Example>> textualTrainingLabelToExamplesMap = new HashMap<String, ArrayList<Example>>() ;
		
		Map<String, ArrayList<String>> trainingLabelToColumnNameMap = new HashMap<String, ArrayList<String>>();
		TrigramJaccardCoefficient tjc = new TrigramJaccardCoefficient(); // used for column name integration
		
		int numFilesToTrain, numFilesAdded, curTrainFileNo, curTestFileNo;
		
		// parameter to choose which file to test below, run next time directly, already modified
		curFileNo=0;
		
		while(curFileNo<fileNames.size())
		{
			curTestFileNo = curFileNo;
			numFilesToTrain = 1;	
			
			System.out.println();
			System.out.println("Testing file name: "+fileNames.get(curTestFileNo));
			System.out.println();
			
		  // clearing test set
			testLabelToExamplesMap.clear();
			// fill in test set
			HashMap<String, ArrayList<Example>> labelExampleMap2 = fileLabelExampleMap.get(curTestFileNo);			
			testLabelToExamplesMap.putAll(labelExampleMap2);
			
			List<Double> avgAccSum = new ArrayList<Double>(numPred);
			for(int i=0;i<numPred;i++)
			{
				avgAccSum.add(0.0);
			}
			long totalTime=0;
			
			// loop for fileNames.size()-1 different tests for same test file
			while(numFilesToTrain < fileNames.size())
			{
				numFilesAdded=0;
				System.out.println("Training with "+numFilesToTrain+" training datasets");
				
				// clearing training set
				trainingLabelToExamplesMap.clear();
				numericTrainingLabelToExamplesMap.clear();
				textualTrainingLabelToExamplesMap.clear();
				trainingLabelToColumnNameMap.clear();
				
				// fill in training set
				curTrainFileNo=curTestFileNo+1;
				while(numFilesAdded<numFilesToTrain && curTrainFileNo<fileNames.size())
				{
					numFilesAdded++;
		    		
		    	HashMap<String, ArrayList<Example>> trainlabelExampleMap = fileLabelExampleMap.get(curTrainFileNo);
		    	System.out.println(fileNames.get(curTrainFileNo));
		    	  		
			    for (Entry<String, ArrayList<Example>> entryCurFile : trainlabelExampleMap.entrySet()) {
			    	if(trainingLabelToExamplesMap.containsKey(entryCurFile.getKey())) // label already present in training set
			    	{
			    		ArrayList<Example> examplesTrainSet = trainingLabelToExamplesMap.get(entryCurFile.getKey());
			    		ArrayList<Example> examplesCurFile = trainlabelExampleMap.get(entryCurFile.getKey());
			    		examplesTrainSet.addAll(examplesCurFile);
			    		
			    		ArrayList<String> columnNames = trainingLabelToColumnNameMap.get(entryCurFile.getKey());
			    		columnNames.add(examplesCurFile.get(0).getValueForColumnFeature(ColumnFeature.ColumnHeaderName)); // assuming one column header for a label in a file
			    	}
			    	else
			    	{
			    		ArrayList<Example> examples = new ArrayList<Example>();
			    		examples.addAll(entryCurFile.getValue());
			    		trainingLabelToExamplesMap.put(entryCurFile.getKey(), examples);
			    		
			    		ArrayList<String> columnNames = new ArrayList<String>();
			    		columnNames.add(entryCurFile.getValue().get(0).getValueForColumnFeature(ColumnFeature.ColumnHeaderName));
			    		trainingLabelToColumnNameMap.put(entryCurFile.getKey(), columnNames);
			    	}
			    }
			    	
		    	curTrainFileNo++;
				}
				curTrainFileNo=0;
				while(numFilesAdded<numFilesToTrain && curTrainFileNo<fileNames.size())
				{
		    	numFilesAdded++;
		    	
		    	HashMap<String, ArrayList<Example>> trainlabelExampleMap = fileLabelExampleMap.get(curTrainFileNo);
		    	
			    for (Entry<String, ArrayList<Example>> entryCurFile : trainlabelExampleMap.entrySet()) {
			    	if(trainingLabelToExamplesMap.containsKey(entryCurFile.getKey())) // label already present in training set
			    	{
			    		ArrayList<Example> examplesTrainSet = trainingLabelToExamplesMap.get(entryCurFile.getKey());
			    		ArrayList<Example> examplesCurFile = trainlabelExampleMap.get(entryCurFile.getKey());
			    		examplesTrainSet.addAll(examplesCurFile);
			    		
			    		ArrayList<String> columnNames = trainingLabelToColumnNameMap.get(entryCurFile.getKey());
			    		columnNames.add(examplesCurFile.get(0).getValueForColumnFeature(ColumnFeature.ColumnHeaderName)); // assuming one column header for a label in a file
			    	}
			    	else
			    	{
			    		ArrayList<Example> examples = new ArrayList<Example>();
			    		examples.addAll(entryCurFile.getValue());
			    		trainingLabelToExamplesMap.put(entryCurFile.getKey(), examples);
			    		
			    		ArrayList<String> columnNames = new ArrayList<String>();
			    		columnNames.add(entryCurFile.getKey()); // add label as well
			    		columnNames.add(entryCurFile.getValue().get(0).getValueForColumnFeature(ColumnFeature.ColumnHeaderName));
			    		trainingLabelToColumnNameMap.put(entryCurFile.getKey(), columnNames);
			    	}
			    }
			    	
		    	curTrainFileNo++;
				}
				
				// splitting into numeric and textual training columns
				
				for (Entry<String, ArrayList<Example>> entry : trainingLabelToExamplesMap.entrySet()) {
					String label = entry.getKey();
					ArrayList<Example> exampleList = entry.getValue();
					
					int numericCount = 0;
					for(int counter=0; counter<exampleList.size(); counter++) {
						if(exampleList.get(counter).getType().equalsIgnoreCase("NUM")) {
							numericCount++;
						}
					}
					
					double fractionNumeric = ((double)(numericCount+0.0))/((double)(exampleList.size()+0.0));
					
					if( fractionNumeric >= pureNumeric)
					{
						ArrayList<Double> exampleListNumeric = new ArrayList<Double>();
						cleanExamplesNumeric(exampleList, exampleListNumeric);
						numericTrainingLabelToExamplesMap.put(label, exampleListNumeric);
						
						System.out.println(label +" : NUMERIC");
					}
					else if( fractionNumeric <= pureText)
					{
						textualTrainingLabelToExamplesMap.put(label, exampleList);
						
						System.out.println(label +" : TEXTUAL");
					}
					else
					{
						textualTrainingLabelToExamplesMap.put(label, exampleList);
						
						ArrayList<Double> exampleListNumeric = new ArrayList<Double>();
						cleanExamplesNumeric(exampleList, exampleListNumeric);
						numericTrainingLabelToExamplesMap.put(label, exampleListNumeric);
						
						System.out.println(label +" : NUMERIC + TEXTUAL");
					}
					
				}
				
				// Training
				
				final long startTime = System.currentTimeMillis();
				// Index TEXTUAL training columns
				if(TfIdfTyper.indexTrainingColumns(textualTrainingLabelToExamplesMap)==false)
				{
					System.exit(1);
				}
				// No training for NUMERIC training columns
				final long endTime = System.currentTimeMillis();
				System.out.println();
				
				// Testing
				
				int lblNo=1;
				
				List<Integer> perLabelCorrect = new ArrayList<Integer>(numPred); // per-label
				List<Integer> TopKCorrect = new ArrayList<Integer>(numPred);
				List<Integer> TopKTotal = new ArrayList<Integer>(numPred);
				for(int i=0;i<numPred;i++)
				{
					TopKCorrect.add(0);
					TopKTotal.add(0);
				}
				
				TopKSearcher topKSearcher = new TopKSearcher();   // for TF-IDF
				KSTest ksTest = new KSTest(); 										// for Kolmogorov-Smirnov Test
				
				for (Entry<String, ArrayList<Example>> entry : testLabelToExamplesMap.entrySet()) {
					
					String label = entry.getKey();
					ArrayList<Example> examples = entry.getValue();
					System.out.println("Testing for label " +lblNo+"/"+testLabelToExamplesMap.size()+" : "+label);
					
					ArrayList<String> predictions = new ArrayList<String>();
					ArrayList<Double> confidenceScores = new ArrayList<Double>();
					
					String testColumnName = entry.getValue().get(0).getValueForColumnFeature(ColumnFeature.ColumnHeaderName);
					List<String> testColumnNameList = new ArrayList<String>();
					testColumnNameList.add(testColumnName);
					
					// choosing to test as numeric or textual column
				
					int numericCount = 0;
					for(int counter=0; counter<examples.size(); counter++) {
						if(examples.get(counter).getType().equalsIgnoreCase("NUM")) {
							numericCount++;
						}
					}
					double fractionNumeric = ((double)(numericCount+0.0))/((double)(examples.size()+0.0));
					if( fractionNumeric >= pureNumeric)
					{
		    		ArrayList<Double> exampleListNumeric = new ArrayList<Double>();
						cleanExamplesNumeric(examples, exampleListNumeric);
						
						if(ksTest.predictLabelsForColumn(numericTrainingLabelToExamplesMap , exampleListNumeric, numPred, predictions, confidenceScores)==false)
						{
							System.exit(1);
						}
						
						System.out.println(label +" : NUMERIC");
					}
					else
					{
						if(TfIdfTyper.predictLabelsForColumn(topKSearcher, examples, predictions, confidenceScores)==false)
						{
							System.exit(1);
						}
						
						System.out.println(label +" : TEXTUAL");
					}
			
					// evaluation
					int predictionsSize = predictions.size();
					System.out.println("Predictions:");
					if(predictionsSize==0)
					{
						System.out.println("Nil");
					}
					else
					{
						for(int j=0;j<predictionsSize; j++)
						{
							System.out.println((j+1)+": "+predictions.get(j)+" confidenceScore = "+confidenceScores.get(j)+" jaccardCoef_ColumnName = "+
									tjc.getSimilarity(trainingLabelToColumnNameMap.get(predictions.get(j)), testColumnNameList));
						}
					}
					
					perLabelCorrect.clear();
					for(int i=0;i<numPred;i++)
					{
						perLabelCorrect.add(0);
					}
					
					for(int k=1; k<=numPred; k++)
					{
							TopKTotal.set(k-1, (TopKTotal.get(k-1))+1);
					}
					for(int k=0; k<predictionsSize; k++)
					{
						if(predictions.get(k).equalsIgnoreCase(label))
						{
							for(int j=k; j<numPred; j++)
							{
								perLabelCorrect.set(j, 1);
								TopKCorrect.set(j, (TopKCorrect.get(j))+1);									
							}
							break; // important bug
						}
					}
					
					for(int i=0;i<numPred;i++)
					{
						System.out.println("Top-"+(i+1)+" Accuracy = "+perLabelCorrect.get(i));
					}
					
					lblNo++;
				}
				
				System.out.println();
				System.out.println("Results for training with "+numFilesToTrain+" datasets");
				
				for(int i=0; i<numPred; i++)
				{
					Double acc = ((double)(TopKCorrect.get(i)+0.0))/((double)(TopKTotal.get(i)+0.0));
					avgAccSum.set(i, (avgAccSum.get(i))+acc);
					System.out.println("Top-"+(i+1)+" Accuracy = "+acc);
				}
				System.out.println();
				totalTime+=(endTime - startTime);
				System.out.println("Total training time: " + ((double)(endTime - startTime + 0.0))/1000.0 +" s" );
				System.out.println();

				numFilesToTrain++;
				
				//break;
			}
		
			System.out.println();
			System.out.println();
			System.out.println("Aggregated results for "+ (fileNames.size()-1) +" runs");
			for(int i=0;i<numPred;i++)
			{
				System.out.println("Top-"+(i+1)+" Accuracy = "+(avgAccSum.get(i)/((double)(fileNames.size()-1))));
			}
			System.out.println();
			System.out.println("Avg training time: "+((double)(totalTime+0.0))/((double)(fileNames.size()-1)*1000.0)+" s");
			
			curFileNo++;
			
			break;
		}
		
		return true;
	}
	
	
	/**
	 * Reads the label to example mpping from a given file
	 */
	public static boolean readExamplesFromFile(File file, HashMap<String,ArrayList<Example>> labelToExampleMap, HashMap<String,Integer> labelToNumCountMap)
	{
		
		//System.out.println("File name: "+file.getName());
		
		CRFModelHandler crfHandler = new CRFModelHandler();
		
		BufferedReader br=null;
		String line=null;
		int numLabels=-1;
		boolean emptyFile;
		String curColName = null;
		
		if (file == null) {
			System.out.println("Invalid argument value. Argument @file is null.") ;
			file = null ;
			return false ;
		}
		try {
			br = new BufferedReader(new FileReader(file)) ;
			emptyFile = true ;
			while((line = br.readLine()) != null) {
				if (line.trim().length() != 0) {
					emptyFile = false ;
					break ;
				}
			}
			br.close() ;
		}
		catch(Exception e) {
			System.out.println("Error reading model file " + file + ".") ;
			file = null ;
			return false ;
		}
		
		if(emptyFile)
			return false;
		else
		{
			try {
				br = new BufferedReader(new FileReader(file)) ;
				// Read the number of labels in the model file
				numLabels = Integer.parseInt(br.readLine().trim()) ;
				br.readLine();
				// read numLabels labels and their examples
				for(int labelNumber = 0 ; labelNumber < numLabels ; labelNumber++) {
					String newLabel;
					ArrayList<Example> examples; 
					int numericCount = 0; // numeric count in column
					int numExamples;
					newLabel = br.readLine().trim();
					// if teh label was already read in this file
					if (labelToExampleMap.containsKey(newLabel)) {
						System.out.println("The label " + newLabel + " was already added to the model. " +
								"Later in the file, we found another list that had the same label and a set of examples underneath it. This is an error. " + 
								"A label can only occur one in the file. All its examples have to be listed underneath it at one place.") ;
						file = null ;
						br.close() ;
						return false ;
					}
					examples = new ArrayList<Example>() ;
					numExamples = Integer.parseInt(br.readLine().trim()) ;
					for(int egNumber = 0 ; egNumber < numExamples ; egNumber++) {
						Example example;
						
						example = crfHandler.parseExampleWithType(br);
						
						
						//example = crfHandler.parseExampleModified(br); // modified parse function used
						
//						// added by nandu
//						if(example.getValueForColumnFeature(ColumnFeature.ColumnHeaderName)==null)
//						{
//							if(!curColName.equalsIgnoreCase("column_name_not_identified"))
//								example.addColumnFeature(ColumnFeature.ColumnHeaderName, curColName);
//							else
//								example.addColumnFeature(ColumnFeature.ColumnHeaderName, "");
//						}
//						else
//						{
//							curColName = new String(example.getValueForColumnFeature(ColumnFeature.ColumnHeaderName));
//							if(curColName.equalsIgnoreCase("column_name_not_identified"))
//								example.addColumnFeature(ColumnFeature.ColumnHeaderName, "");
//						}
						
					  //System.out.println(example.getString()+" "+example.getValueForColumnFeature(ColumnFeature.ColumnHeaderName));
						//System.out.println(curColName);
					  
						if (example == null) {
							System.out.println("Parsing of file failed. Could not parse an example.");
							br.close();
							file = null;
							return false;
						}
						else {
							if(!example.getString().equalsIgnoreCase("") && !example.getString().equalsIgnoreCase("NULL")){
								examples.add(example) ;
								// increment numeric count if example is numeric
								if(example.getType().equalsIgnoreCase("NUM")) {
									numericCount++;
								}
							}
						}
					}
					labelToExampleMap.put(newLabel, examples) ;
					labelToNumCountMap.put(newLabel, new Integer(numericCount));
					br.readLine() ; // consuming the empty line after each list of label and its examples
				}
				
				br.close() ;
				return true ;
			}
			catch(Exception e) {
				e.printStackTrace();
				System.out.println("Error parsing model file " + file + ".") ;
				file = null ;
				// SHOULD I CLOSE br HERE ?
				return false ;
			}
		}
	}
	
	
	/**
	 * Reads the label to example mpping from a given file with column details
	 */
	public static boolean readExamplesFromFileWithColumn(File file, HashMap<String,ArrayList<Example>> labelToExampleMap, HashMap<String,Integer> labelToNumCountMap)
	{
		
		//System.out.println("File name: "+file.getName());
		
		CRFModelHandler crfHandler = new CRFModelHandler();
		
		BufferedReader br=null;
		String line=null;
		int numLabels=-1;
		boolean emptyFile;
		String curColName = null;
		
		if (file == null) {
			System.out.println("Invalid argument value. Argument @file is null.") ;
			file = null ;
			return false ;
		}
		try {
			br = new BufferedReader(new FileReader(file)) ;
			emptyFile = true ;
			while((line = br.readLine()) != null) {
				if (line.trim().length() != 0) {
					emptyFile = false ;
					break ;
				}
			}
			br.close() ;
		}
		catch(Exception e) {
			System.out.println("Error reading model file " + file + ".") ;
			file = null ;
			return false ;
		}
		
		if(emptyFile)
			return false;
		else
		{
			try {
				br = new BufferedReader(new FileReader(file)) ;
				// Read the number of labels in the model file
				numLabels = Integer.parseInt(br.readLine().trim()) ;
				br.readLine();
				// read numLabels labels and their examples
				for(int labelNumber = 0 ; labelNumber < numLabels ; labelNumber++) {
					String newLabel;
					String columnName; // added 
					ArrayList<Example> examples; 
					int numericCount = 0; // numeric count in column
					int numExamples;
					newLabel = br.readLine().trim();
					// if teh label was already read in this file
					if (labelToExampleMap.containsKey(newLabel)) {
						System.out.println("The label " + newLabel + " was already added to the model. " +
								"Later in the file, we found another list that had the same label and a set of examples underneath it. This is an error. " + 
								"A label can only occur one in the file. All its examples have to be listed underneath it at one place.") ;
						file = null ;
						br.close() ;
						return false ;
					}
					
					columnName = br.readLine().trim(); // added
					
					examples = new ArrayList<Example>() ;
					numExamples = Integer.parseInt(br.readLine().trim()) ;
					for(int egNumber = 0 ; egNumber < numExamples ; egNumber++) {
						Example example;
						
						example = crfHandler.parseExampleWithType(br);
						
						example.addColumnFeature(ColumnFeature.ColumnHeaderName, columnName); // added
						
						//example = crfHandler.parseExampleModified(br); // modified parse function used
						
//						// added by nandu
//						if(example.getValueForColumnFeature(ColumnFeature.ColumnHeaderName)==null)
//						{
//							if(!curColName.equalsIgnoreCase("column_name_not_identified"))
//								example.addColumnFeature(ColumnFeature.ColumnHeaderName, curColName);
//							else
//								example.addColumnFeature(ColumnFeature.ColumnHeaderName, "");
//						}
//						else
//						{
//							curColName = new String(example.getValueForColumnFeature(ColumnFeature.ColumnHeaderName));
//							if(curColName.equalsIgnoreCase("column_name_not_identified"))
//								example.addColumnFeature(ColumnFeature.ColumnHeaderName, "");
//						}
						
					  //System.out.println(example.getString()+" "+example.getValueForColumnFeature(ColumnFeature.ColumnHeaderName));
						//System.out.println(curColName);
					  
						if (example == null) {
							System.out.println("Parsing of file failed. Could not parse an example.");
							br.close();
							file = null;
							return false;
						}
						else {
							if(!example.getString().equalsIgnoreCase("") && !example.getString().equalsIgnoreCase("NULL")){
								examples.add(example) ;
								// increment numeric count if example is numeric
								if(example.getType().equalsIgnoreCase("NUM")) {
									numericCount++;
								}
							}
						}
					}
					labelToExampleMap.put(newLabel, examples) ;
					labelToNumCountMap.put(newLabel, new Integer(numericCount));
					br.readLine() ; // consuming the empty line after each list of label and its examples
				}
				
				br.close() ;
				return true ;
			}
			catch(Exception e) {
				e.printStackTrace();
				System.out.println("Error parsing model file " + file + ".") ;
				file = null ;
				// SHOULD I CLOSE br HERE ?
				return false ;
			}
		}
	}
	
	
	public static boolean cleanExamplesNumeric(ArrayList<Example> exampleList, ArrayList<Double> exampleListNumeric)
	{
		Iterator<Example> itr = exampleList.iterator();
		
		while(itr.hasNext())
		{
			Example ex = itr.next();
			if(ex.getType().equalsIgnoreCase("NUM"))
			{
				exampleListNumeric.add(Double.parseDouble(ex.getString()));
			}
		}
		
		return true;
	}
	
	public static void main(String[] args) throws FileNotFoundException, IOException, ParseException
	{
		//evaluateRoundRobin();
		evaluateMultipleDatasets();
	}
	
}
