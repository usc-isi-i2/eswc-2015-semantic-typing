import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import edu.isi.karma.modeling.semantictypes.crfmodelhandler.CRFModelHandler;
import edu.isi.karma.modeling.semantictypes.crfmodelhandler.CRFModelHandler.Example;
import tests.ApacheKSTest;
import tests.KSTest;
import tests.MannWhitneyTest;
import tests.WelchTTest;

public class NumericTest {
	
	private static int numPred = 4;
	private static Map<Integer,HashMap<String, ArrayList<Double>>> fileLabelExampleMap;
	private static Map<String,Integer> fileIndexMap;
	private static List<String> fileNames;
	
	/**
	 * Function to evaluate performance of statistical tests on collection of datasets
	 * Ensure the dataset files are in CRFModelFile format and present in "Files" directory
	 */
	public static boolean evaluateMultipleDatasets() 
	{
		
		fileLabelExampleMap = new HashMap<Integer,HashMap<String, ArrayList<Double>>>();
		fileIndexMap = new HashMap<String, Integer>();
		fileNames = new ArrayList<String>();
		
		File folder = new File("Files");
		File[] listOfFiles = folder.listFiles();
		
		int curFileNo=0;
		for (File file : listOfFiles) {
		    if (file.isFile() && !file.isHidden()) {
		    		fileNames.add(file.getName());
		    		fileIndexMap.put(file.getName(), curFileNo);
		    		
		    		HashMap<String,ArrayList<Double>> labelToExampleMap = new HashMap<String,ArrayList<Double>>();
		        readExamplesFromFile(file, labelToExampleMap); // read list of examples from file
		        
		        fileLabelExampleMap.put(curFileNo, labelToExampleMap); 
		    		curFileNo++;
		    }
		}
		
		Map<String, ArrayList<Double>> trainingLabelToExamplesMap = new HashMap<String, ArrayList<Double>>() ;
		Map<String, ArrayList<Double>> testLabelToExamplesMap = new HashMap<String, ArrayList<Double>>() ;
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
			HashMap<String, ArrayList<Double>> labelExampleMap2 = fileLabelExampleMap.get(curTestFileNo);
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
			
			// loop for (fileSize-1) different tests for same test file
			while(numFilesToTrain < fileNames.size())
			{
				numFilesAdded=0;
				System.out.println("Training with "+numFilesToTrain+" training datasets");
				
				// clearing training set
				trainingLabelToExamplesMap.clear();
				
				// fill in training set
				curTrainFileNo=curTestFileNo+1;
				while(numFilesAdded<numFilesToTrain && curTrainFileNo<fileNames.size())
				{
					numFilesAdded++;
		    		
		    	HashMap<String, ArrayList<Double>> trainlabelExampleMap = fileLabelExampleMap.get(curTrainFileNo);
		    	  		
			    for (Entry<String, ArrayList<Double>> entryCurFile : trainlabelExampleMap.entrySet()) {
			    	if(trainingLabelToExamplesMap.containsKey(entryCurFile.getKey())) // label already present in training set
			    	{
			    		ArrayList<Double> examplesTrainSet = trainingLabelToExamplesMap.get(entryCurFile.getKey());
			    		ArrayList<Double> examplesCurFile = trainlabelExampleMap.get(entryCurFile.getKey());
			    		examplesTrainSet.addAll(examplesCurFile);
			    	}
			    	else
			    	{
			    		ArrayList<Double> examples = new ArrayList<Double>();
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
		    	
		    	HashMap<String, ArrayList<Double>> trainlabelExampleMap = fileLabelExampleMap.get(curTrainFileNo);
		    	
			    for (Entry<String, ArrayList<Double>> entryCurFile : trainlabelExampleMap.entrySet()) {
			    	if(trainingLabelToExamplesMap.containsKey(entryCurFile.getKey())) // label already present in training set
			    	{
			    		ArrayList<Double> examplesTrainSet = trainingLabelToExamplesMap.get(entryCurFile.getKey());
			    		ArrayList<Double> examplesCurFile = trainlabelExampleMap.get(entryCurFile.getKey());
			    		examplesTrainSet.addAll(examplesCurFile);
			    	}
			    	else
			    	{
			    		ArrayList<Double> examples = new ArrayList<Double>();
			    		examples.addAll(entryCurFile.getValue());
			    		trainingLabelToExamplesMap.put(entryCurFile.getKey(), examples);
			    	}
			    }
			    	
		    	curTrainFileNo++;
				}
				
				// No training
				
				final long startTime = System.currentTimeMillis();
				final long endTime = System.currentTimeMillis();
//				System.out.println();
				
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
	
				WelchTTest test = new WelchTTest(); // Welch's t-test
				//KSTest test = new KSTest(); 					// Kolmogorov-Smirnov Test
				//ApacheKSTest test = new ApacheKSTest(); 
				//MannWhitneyTest test = new MannWhitneyTest(); // MannWhitney U Test
				
				for (Entry<String, ArrayList<Double>> entry : testLabelToExamplesMap.entrySet()) {
					
					String label = entry.getKey();
					ArrayList<Double> examples = entry.getValue();
					System.out.println("Testing for label " +lblNo+"/"+testLabelToExamplesMap.size()+" : "+label);
					
					ArrayList<String> predictions = new ArrayList<String>();
					ArrayList<Double> confidenceScores = new ArrayList<Double>();
					
					if(test.predictLabelsForColumn(trainingLabelToExamplesMap , examples, numPred, predictions, confidenceScores)==false)
					{
						System.exit(1);
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
							System.out.println((j+1)+": "+predictions.get(j)+" p-Value = "+confidenceScores.get(j));
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
						if(predictionsSize>=k)
						{
							TopKTotal.set(k-1, (TopKTotal.get(k-1))+1);
							for(int j=0; j<k; j++)
							{
								if(predictions.get(j).equalsIgnoreCase(label))
								{
									perLabelCorrect.set(k-1, 1);
									TopKCorrect.set(k-1, (TopKCorrect.get(k-1))+1);
									break;
								}
							}
						}
						else
						{
							break;
						}
					}
					
					for(int i=0;i<numPred;i++)
					{
						System.out.println("Top-"+(i+1)+" Accuracy = "+perLabelCorrect.get(i));
					}
					*/
					
					for(int k=0; k<predictionsSize; k++)
					{
						if(predictions.get(k).equalsIgnoreCase(label))
						{
							topKCorrect += 1;
							mrr += (1.0/(double)(k+1));
							break;
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
	
	
	/*
	 * Evaluate based on round robin
	 * */
	public static void evaluateRoundRobin()
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
		
		fileLabelExampleMap = new HashMap<Integer,HashMap<String, ArrayList<Double>>>();
		fileIndexMap = new HashMap<String, Integer>();
		fileNames = new ArrayList<String>();
		
		File folder = new File("Files");
		File[] listOfFiles = folder.listFiles();
		
		int curFileNo=0;
		for (File file : listOfFiles) {
		    if (file.isFile() && !file.isHidden()) {
		    		fileNames.add(file.getName());
		    		fileIndexMap.put(file.getName(), curFileNo);
		    		
		    		HashMap<String,ArrayList<Double>> labelToExampleMap = new HashMap<String,ArrayList<Double>>();
		        readExamplesFromFile(file, labelToExampleMap); // read list of examples from file
		        
		        fileLabelExampleMap.put(curFileNo, labelToExampleMap); 
		    		curFileNo++;
		    }
		}
		
		Map<String, ArrayList<Double>> trainingLabelToExamplesMap = new HashMap<String, ArrayList<Double>>() ;
		Map<String, ArrayList<Double>> testLabelToExamplesMap = new HashMap<String, ArrayList<Double>>() ;
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
			HashMap<String, ArrayList<Double>> labelExampleMap2 = fileLabelExampleMap.get(curTestFileNo);
			testLabelToExamplesMap.putAll(labelExampleMap2);
			
			double level2MRR = 0.0;
			double level2TopKCorrect = 0.0;
			long totalTime=0;
			
			// loop for (fileSize-1) different tests for same test file
			while(numFilesToTrain < fileNames.size())
			{
				numFilesAdded=0;
				System.out.println("Training with "+numFilesToTrain+" training datasets");
				
				// clearing training set
				trainingLabelToExamplesMap.clear();
				
				List<String> trainListForCurTestFile = trainList.get(curTestFileNo);
				
				// fill in training set
				while(numFilesAdded<numFilesToTrain)
				{						
		    	HashMap<String, ArrayList<Double>> trainlabelExampleMap = fileLabelExampleMap.get(fileIndexMap.get(trainListForCurTestFile.get(numFilesAdded)
		    			+".txt"));
		    	System.out.println(trainListForCurTestFile.get(numFilesAdded)+".txt");
		    	  		
			    for (Entry<String, ArrayList<Double>> entryCurFile : trainlabelExampleMap.entrySet()) {
			    	if(trainingLabelToExamplesMap.containsKey(entryCurFile.getKey())) // label already present in training set
			    	{
			    		ArrayList<Double> examplesTrainSet = trainingLabelToExamplesMap.get(entryCurFile.getKey());
			    		ArrayList<Double> examplesCurFile = trainlabelExampleMap.get(entryCurFile.getKey());
			    		examplesTrainSet.addAll(examplesCurFile);
			    	}
			    	else
			    	{
			    		ArrayList<Double> examples = new ArrayList<Double>();
			    		examples.addAll(entryCurFile.getValue());
			    		trainingLabelToExamplesMap.put(entryCurFile.getKey(), examples);
			    	}
			    }
			    	
			    numFilesAdded++;
				}
				
				// No training
				
				final long startTime = System.currentTimeMillis();
				final long endTime = System.currentTimeMillis();
//				System.out.println();
				
				// Testing
				
				int lblNo=1;
				
				double topKCorrect = 0.0;
				double mrr = 0.0;
				
				//WelchTTest test = new WelchTTest(); // Welch's t-test
				KSTest test = new KSTest(); 					// Kolmogorov-Smirnov Test
				//ApacheKSTest test = new ApacheKSTest(); 
				//MannWhitneyTest test = new MannWhitneyTest(); // MannWhitney U Test
				
				for (Entry<String, ArrayList<Double>> entry : testLabelToExamplesMap.entrySet()) {
					
					String label = entry.getKey();
					ArrayList<Double> examples = entry.getValue();
					System.out.println("Testing for label " +lblNo+"/"+testLabelToExamplesMap.size()+" : "+label);
					
					ArrayList<String> predictions = new ArrayList<String>();
					ArrayList<Double> confidenceScores = new ArrayList<Double>();
					
					if(test.predictLabelsForColumn(trainingLabelToExamplesMap , examples, numPred, predictions, confidenceScores)==false)
					{
						System.exit(1);
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
							System.out.println((j+1)+": "+predictions.get(j)+" p-Value = "+confidenceScores.get(j));
						}
					}
					
					for(int k=0; k<predictionsSize; k++)
					{
						if(predictions.get(k).equalsIgnoreCase(label))
						{
							topKCorrect += 1;
							mrr += (1.0/(double)(k+1));
							break;
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
		
	}

	/**
	 * Reads the label to example mapping from a given file
	 */
	public static boolean readExamplesFromFile(File file, HashMap<String,ArrayList<Double>> labelToExampleMap)
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
					ArrayList<Double> examples;
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
					examples = new ArrayList<Double>() ;
					numExamples = Integer.parseInt(br.readLine().trim()) ;
					for(int egNumber = 0 ; egNumber < numExamples ; egNumber++) {
						Example example;
						example = crfHandler.parseNumericExample(br);
						
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
							System.out.println(file.getName()+" "+labelNumber+" "+egNumber);
							System.out.println("Parsing of file failed. Could not parse an example.");
							br.close();
							file = null;
							return false;
						}
						else {
							if(!example.getString().equalsIgnoreCase("") && !example.getString().equalsIgnoreCase("NULL")){
								examples.add(Double.parseDouble(example.getString()));
							}
						}
					}
					labelToExampleMap.put(newLabel, examples) ;
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
	
	public static void main(String[] args)
	{
		//evaluateRoundRobin();
		evaluateMultipleDatasets();
	}
	
}
