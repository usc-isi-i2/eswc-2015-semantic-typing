package LuceneIndex;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import org.apache.commons.math3.linear.OpenMapRealVector;
import org.apache.commons.math3.linear.RealVectorFormat;
import org.apache.commons.math3.linear.SparseRealVector;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.DocsEnum;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.MultiFields;
import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.similarities.DefaultSimilarity;
import org.apache.lucene.search.similarities.TFIDFSimilarity;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.Bits;
import org.apache.lucene.util.BytesRef;
import Prediction.Prediction;
import Prediction.PredictionComparator;
import Additonal.Info;

public class DocumentSimilarity {

	private IndexReader indexReader = null;
	private Bits liveDocs = null;
	
	private TFIDFSimilarity tfidfSIM = null;
	private Map<String, Integer> termFreq = null; // term -> integer
	
	private DocVector[] docs = null;
	private Info info =  null;
	
	private static class DocVector {
     public Map<String,Integer> terms;
     public SparseRealVector vector;
     
     public DocVector(Map<String,Integer> terms) {
       this.terms = terms;
       this.vector = new OpenMapRealVector(terms.size());
     }
     
     public void setEntry(String term, int freq) {
       if (terms.containsKey(term)) {
         int pos = terms.get(term);
         vector.setEntry(pos, (double) freq);
       }
     }
     
     public void normalize() {
       double sum = vector.getL1Norm();
       vector = (SparseRealVector) vector.mapDivide(sum);
     }
     
     public String toString() {
       RealVectorFormat formatter = new RealVectorFormat();
       return formatter.format(vector);
     }
   }
	
	public DocumentSimilarity(Info info) throws IOException
	{
		indexReader = DirectoryReader.open(FSDirectory.open(new File(Indexer.INDEX_LOC)));
		liveDocs = MultiFields.getLiveDocs(indexReader);
		tfidfSIM = new DefaultSimilarity();
		termFreq = new HashMap<String, Integer>();
		this.info = info;
		
    TermsEnum termEnum = MultiFields.getTerms(indexReader, "content").iterator(null); 
    BytesRef bytesRef;
    int tf, pos=0;
    while ((bytesRef = termEnum.next()) != null) 
    {
        if (termEnum.seekExact(bytesRef)) 
        {
         	 String term = bytesRef.utf8ToString();   
         	 //System.out.println(term+ " "+pos);
           termFreq.put(term, new Integer(pos++));
        }
    }
    
    docs = new DocVector[info.noDocs];
    for (int docId=0; docId<info.noDocs; docId++){
      docs[docId] = new DocVector(termFreq); 
    }
      
    termEnum = MultiFields.getTerms(indexReader, "content").iterator(null); 
    while ((bytesRef = termEnum.next()) != null) 
    {
        if (termEnum.seekExact(bytesRef)) 
        {
         	 String term = bytesRef.utf8ToString();            	 
         	 
         	 DocsEnum docsEnum = termEnum.docs(liveDocs, null);
           if (docsEnum != null) 
           {
              int doc; 
              while((doc = docsEnum.nextDoc())!=DocIdSetIterator.NO_MORE_DOCS) 
               {
                   tf = (int) tfidfSIM.tf(docsEnum.freq());
                   //System.out.println(doc+" "+term+" "+tf);
                   if(tf>0){
                  	 docs[doc].setEntry(term, 1);
                   }
               } 
           } 
        }
    }
    
//    for (int docId=0; docId<noDocs; docId++){
//      docs[docId].normalize();
//    }
    
	}
	
	public void closeIndex() throws IOException
	{
		indexReader.close();
	}
	
	 public static double getCosineSimilarity(DocVector d1, DocVector d2) {
     return (d1.vector.dotProduct(d2.vector)) /
       (d1.vector.getNorm() * d2.vector.getNorm());
   }
	
	 public static double getJaccardSimilarity(DocVector d1, DocVector d2, SparseRealVector onesVector) {
		 return (d1.vector.dotProduct(d2.vector)) / (d1.vector.dotProduct(onesVector) + d2.vector.dotProduct(onesVector) - d1.vector.dotProduct(d2.vector)); 
	 }
	 
	 public void getTopK(int testIndex, int numPred, ArrayList<String> predictions, ArrayList<Double> confidenceScores)
	 {
		 int size = docs[0].vector.getDimension();
		 SparseRealVector onesVector = new OpenMapRealVector(size);
		 for(int i=0; i<size; i++)
		 {
			 onesVector.setEntry(i, 1); 
		 }
		 
		 List<Prediction> sortedPredictions = new ArrayList<Prediction>();	// descending order of jaccard similarity
		 
		 for(int i=0; i<info.noTrainingDocs; i++)
		 {
			 double jacSim = getJaccardSimilarity(docs[i], docs[info.noTrainingDocs + testIndex], onesVector);
			 Prediction pred = new Prediction(new Integer(i).toString(), jacSim);
			 if(jacSim > 0.0)
			 {
				 sortedPredictions.add(pred);
			 }
			 //System.out.println(i+" "+jacSim);
		 }
		 
		 Collections.sort(sortedPredictions, new PredictionComparator());
		 
		 //System.out.println("no of predictions = "+sortedPredictions.size());
		 
			for(int j=0; j<numPred && j<sortedPredictions.size(); j++)
			{
				predictions.add(sortedPredictions.get(j).predictionIndex);
				confidenceScores.add(sortedPredictions.get(j).confidenceScore);
			}	
			
	 }
	 
	 public void display()
	 {
		 for(int i=0; i<docs.length; i++)
		 {
			 System.out.print("document "+i+" : ");
			 for(int j=0; j<docs[i].vector.getDimension(); j++)
				 System.out.print(docs[i].vector.getEntry(j)+", ");
			 System.out.println();
		 }
	 }
	 
	 public void closeIndexReader() throws IOException
	 {
			indexReader.close();
	 }
	 
	 
}
