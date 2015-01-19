import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import org.apache.lucene.queryparser.classic.ParseException;
import Additonal.Info;
import LuceneIndex.DocumentSimilarity;
import LuceneIndex.Indexer;
import LuceneIndex.TopKSearcher;
import edu.isi.karma.modeling.semantictypes.crfmodelhandler.CRFModelHandler;
import edu.isi.karma.modeling.semantictypes.crfmodelhandler.CRFModelHandler.Example;


public class JaccardSimilarity {
	
	private static int numPred = 4;
	private static Map<Integer,HashMap<String, ArrayList<Example>>> fileLabelExampleMap;
	private static Map<String,Integer> fileIndexMap;
	private static List<String> fileNames;
	private static List<String> trainingLabelMap;
	
	/**
	 * Function to evaluate performance of Jaccard similarity on collection of datasets
	 * Ensure the dataset files are in CRFModelFile format and present in "Files" directory
	 * @throws FileNotFoundException 
	 * @throws ParseException 
	 */
	public static boolean evaluateMultipleDatasets() throws FileNotFoundException, IOException, ParseException
	{
		
		fileLabelExampleMap = new HashMap<Integer,HashMap<String, ArrayList<Example>>>();
		fileIndexMap = new HashMap<String, Integer>();
		fileNames = new ArrayList<String>();
		trainingLabelMap = new ArrayList<String>();
		
		File folder = new File("Files");
		File[] listOfFiles = folder.listFiles();
		
		int curFileNo=0;
		for (File file : listOfFiles) {
		    if (file.isFile() && !file.isHidden()) {
		    		fileNames.add(file.getName());
		    		fileIndexMap.put(file.getName(), curFileNo);
		    		
		    		HashMap<String,ArrayList<Example>> labelToExampleMap = new HashMap<String,ArrayList<Example>>();
		        readExamplesFromFile(file, labelToExampleMap); // read list of examples from file
		        
		        fileLabelExampleMap.put(curFileNo, labelToExampleMap); 
		    		curFileNo++;
		    }
		}
		
		Map<String, ArrayList<Example>> trainingLabelToExamplesMap = new HashMap<String, ArrayList<Example>>() ;
		Map<String, ArrayList<Example>> testLabelToExamplesMap = new HashMap<String, ArrayList<Example>>() ;
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
			
			// loop for 28 different tests for same test file
			while(numFilesToTrain < fileNames.size())
			{
				numFilesAdded=0;
				System.out.println("Training with "+numFilesToTrain+" training datasets");
				
				// clearing training set
				trainingLabelToExamplesMap.clear();
				
				trainingLabelMap.clear();
				
				// fill in training set
				curTrainFileNo=curTestFileNo+1;
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
				
				// Training - a.k.a Indexing
				
				final long startTime = System.currentTimeMillis();
				Info info = indexTrainingColumns(trainingLabelToExamplesMap,testLabelToExamplesMap);
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
				
				DocumentSimilarity docSim = new DocumentSimilarity(info);
				
				for (Entry<String, ArrayList<Example>> entry : testLabelToExamplesMap.entrySet()) {
					
					String label = entry.getKey();
					ArrayList<Example> examples = entry.getValue();
					System.out.println("Testing for label " +lblNo+"/"+testLabelToExamplesMap.size()+" : "+label);
					
					ArrayList<String> predictions = new ArrayList<String>();
					ArrayList<Double> confidenceScores = new ArrayList<Double>();
					
					if(predictLabelsForColumn(docSim, lblNo-1, predictions, confidenceScores)==false)
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
							System.out.println((j+1)+": "+trainingLabelMap.get(Integer.parseInt(predictions.get(j)))+" Confidence = "+confidenceScores.get(j));
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
								if(trainingLabelMap.get(Integer.parseInt(predictions.get(j))).equalsIgnoreCase(label))
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
							if(trainingLabelMap.get(Integer.parseInt(predictions.get(k))).equalsIgnoreCase(label))
							{
								topKCorrect += 1;
								mrr += (1.0/(double)(k+1));
								break;
							}
						}					
					}
					
					lblNo++;
					
				}
				
				docSim.closeIndex();
				
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
	 * Predict labels for given test columns
	 * 
	 * @param testExamples
	 * @param predictions
	 * @param confidenceScores
	 * @return
	 * @throws IOException 
	 * @throws ParseException 
	 */
	public static boolean predictLabelsForColumn(DocumentSimilarity docSim , int testIndex, ArrayList<String> predictions, ArrayList<Double> confidenceScores) throws ParseException, IOException
	{
		docSim.getTopK(testIndex, numPred, predictions, confidenceScores);
		
		return true;
	}
	
	/**
	 * Indexes the given training columns
	 * 
	 * @param trainingLabelToExamplesMap
	 * @return
	 * @throws IOException 
	 */
	public static Info indexTrainingColumns(Map<String, ArrayList<Example>> trainingLabelToExamplesMap, Map<String, ArrayList<Example>> testLabelToExamplesMap) throws IOException
	{
		// create an index
		Indexer indexer = new Indexer();
		indexer.createIndex();
		indexer.deleteDocuments(); 
		indexer.commit();
		
		//System.out.println("No of docs = "+indexer.getNoOfDocs());
		
		// add documents to index
    for (Entry<String, ArrayList<Example>> entry : trainingLabelToExamplesMap.entrySet()) {
    	StringBuilder sb = new StringBuilder();
    	for (Example ex : entry.getValue())
    	{
    	    sb.append(ex.getString());
    	    sb.append(" ");
    	}
    	
    	trainingLabelMap.add(entry.getKey());
    	indexer.addDocument(sb.toString(),entry.getKey());
    }
    
    for (Entry<String, ArrayList<Example>> entry : testLabelToExamplesMap.entrySet()) {
    	StringBuilder sb = new StringBuilder();
    	for (Example ex : entry.getValue())
    	{
    	    sb.append(ex.getString());
    	    sb.append(" ");
    	}
    	
    	indexer.addDocument(sb.toString(),"test_"+entry.getKey());
    }
    
		indexer.commit();
		//System.out.println("No of docs = "+indexer.getNoOfDocs());
		int noDocs = indexer.getNoOfDocs();
		Info info = new Info(noDocs, trainingLabelToExamplesMap.size());
		indexer.closeIndexWriter();
	
		return info;
	}
	
	
	/**
	 * Reads the label to example mpping from a given file
	 */
	public static boolean readExamplesFromFile(File file, HashMap<String,ArrayList<Example>> labelToExampleMap)
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
						
						//example = crfHandler.parseExample(br);
						
						
						example = crfHandler.parseExampleModified(br); // modified parse function used
						
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
							examples.add(example) ;
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
	
	
	public static void main(String[] args) throws FileNotFoundException, IOException, ParseException
	{
		evaluateMultipleDatasets();
	}
	
}
