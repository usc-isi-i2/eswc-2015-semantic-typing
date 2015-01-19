package tfIdf;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;
import java.util.Map.Entry;
import org.apache.lucene.queryparser.classic.ParseException;
import typer.SemanticTyper;
import edu.isi.karma.modeling.semantictypes.crfmodelhandler.CRFModelHandler.Example;

public class TfIdfTyper {

	/**
	 * Indexes the given training columns
	 * 
	 * @param trainingLabelToExamplesMap
	 * @return
	 * @throws IOException 
	 */
	public static boolean indexTrainingColumns(Map<String, ArrayList<Example>> trainingLabelToExamplesMap) throws IOException
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
    	
    	indexer.addDocument(sb.toString(),entry.getKey());
    }
    
		indexer.commit();
		//System.out.println("No of docs = "+indexer.getNoOfDocs());
		indexer.closeIndexWriter();
	
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
	public static boolean predictLabelsForColumn(TopKSearcher topKSearcher, ArrayList<Example> testExamples, ArrayList<String> predictions, ArrayList<Double> confidenceScores) throws ParseException, IOException
	{
		// construct single text for test column
    StringBuilder sb = new StringBuilder();
    for (Example ex : testExamples)
    {
        sb.append(ex.getString());
        sb.append(" ");
    }
    
//    String s = sb.toString();
//    int spaces = s.length() - s.replace(" ", "").length();
//    System.out.println("No of spaces in query = "+spaces);
   
		topKSearcher.getTopK(SemanticTyper.numPred, sb.toString(), predictions, confidenceScores);
		
		return true;
	}
	
}
