package LuceneIndex;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.DocsEnum;
import org.apache.lucene.index.Fields;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.MultiFields;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.similarities.DefaultSimilarity;
import org.apache.lucene.search.similarities.TFIDFSimilarity;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.Bits;
import org.apache.lucene.util.BytesRef;

public class TfIdfSearcher {
	
	private IndexReader indexReader = null;
	private Bits liveDocs = null;
	
	private TFIDFSimilarity tfidfSIM = null;
	private Map<String, Float> inverseDocFreq = null; // term -> idf
	private Map<String, HashMap<Integer,Float>> tf_Idf_Weights = null; // term -> map(doc->tf-idf)

	public TfIdfSearcher() throws IOException
	{
		indexReader = DirectoryReader.open(FSDirectory.open(new File(Indexer.INDEX_LOC)));
		liveDocs = MultiFields.getLiveDocs(indexReader);
		
		tfidfSIM = new DefaultSimilarity();
		inverseDocFreq = new HashMap<String, Float>();
		tf_Idf_Weights = new HashMap<String, HashMap<Integer,Float>>();
	}
	
	/**
	 * 
	 * @param reader
	 * @return Map of term and its inverse document frequency
	 * 
	 * @throws IOException
	 */
	public Map<String, Float> getIdfs(IndexReader reader) throws IOException
	{
	     Fields fields = MultiFields.getFields(reader); //get the fields of the index 
	  
	     for (String field: fields) 
	     {	 
	         TermsEnum termEnum = MultiFields.getTerms(reader, field).iterator(null);
	    
	         BytesRef bytesRef;
	         while ((bytesRef = termEnum.next()) != null) 
	         {
	             if (termEnum.seekExact(bytesRef)) 
	             {
	            	 String term = bytesRef.utf8ToString(); 
	            	 float idf = tfidfSIM.idf( termEnum.docFreq(), reader.numDocs() );
	            	 inverseDocFreq.put(term, idf);    
	            	 System.out.println(term +" idf= "+ idf);
	             }
	         }
	     }
	 
      return inverseDocFreq;
	}
	
//	/**
//	 * 
//	 * @param reader
//	 * @return Map of term and its tf-Idf
//	 * @throws IOException
//	 */
//	public Map<String, Float> getTfIdfs() throws IOException
//	{
//		getIdfs(indexReader); // get Idf's
//		
//    Fields fields = MultiFields.getFields(indexReader); //get the fields of the index 
//	  
//    for (String field: fields) 
//    {
//			for (int docID=0; docID< indexReader.maxDoc(); docID++)
//			{      
//				termFrequencies.clear();
//				
//			  TermsEnum termsEnum = MultiFields.getTerms(indexReader, field).iterator(null);
//			  DocsEnum docsEnum = null;
//			  
//			  Terms vector = indexReader.getTermVector(docID, field);
//			    
//			  try
//			  {
//			   termsEnum = vector.iterator(termsEnum);
//			  } 
//			  catch (NullPointerException e) 
//			  {
//			   e.printStackTrace();
//			  }
//			  
//			  BytesRef bytesRef = null;
//			  while ((bytesRef = termsEnum.next()) != null) 
//			  {
//			    if (termsEnum.seekExact(bytesRef)) 
//			    {
//			      String term = bytesRef.utf8ToString(); 
//			      float tf = 0; 
//			           
//			      docsEnum = termsEnum.docs(null, null, DocsEnum.FLAG_FREQS);
//			      while (docsEnum.nextDoc() != DocIdSetIterator.NO_MORE_DOCS) 
//			      {
//			         tf =  tfidfSIM.tf(docsEnum.freq()); 
//			         termFrequencies.put(term, tf); 
//			      }  
//			              
//			      float idf = docFrequencies.get(term);
//			      float w = tf * idf;
//			      tf_Idf_Weights.put(term, w); 
//			      
//			      System.out.println("Term="+term+" DocId="+docID+" Tf="+tf+" Idf="+idf+" Tf-Idf="+w);
//			      
//			    } 
//			  } 
//			}
//    }
//		return tf_Idf_Weights;
//	}
	
	public Map<String, HashMap<Integer, Float>> getTfIdfs() throws IOException
	{
	  float tf, idf, tfidf_score;
		
    Fields fields = MultiFields.getFields(indexReader); //get the fields of the index 
	  
    for (String field: fields) 
    {	 
        TermsEnum termEnum = MultiFields.getTerms(indexReader, field).iterator(null);
   
        BytesRef bytesRef;
        while ((bytesRef = termEnum.next()) != null) 
        {
            if (termEnum.seekExact(bytesRef)) 
            {
	           	 String term = bytesRef.utf8ToString(); 
	           	 idf = tfidfSIM.idf( termEnum.docFreq(), indexReader.numDocs() );
	           	 inverseDocFreq.put(term, idf);    
	           	 
	           	 System.out.println("Term = "+term);
	           	 //System.out.println("idf= "+ idf);
	           	 
          		 HashMap<Integer,Float> docTfIdf = new HashMap<Integer,Float>();
	           	 
	           	 DocsEnum docsEnum = termEnum.docs(liveDocs, null);
               if (docsEnum != null) 
               {
                  int doc; 
                  while((doc = docsEnum.nextDoc())!=DocIdSetIterator.NO_MORE_DOCS) 
                   {
                       tf = tfidfSIM.tf(docsEnum.freq());
                       tfidf_score = tf*idf; 
                       
                       docTfIdf.put(docsEnum.docID(), tfidf_score);

                       System.out.println("doc= "+ docsEnum.docID()+" tfidf_score= " + tfidf_score);
                   }
                  
                  tf_Idf_Weights.put(term, docTfIdf);
               } 
            }
        }
    }
		
		return tf_Idf_Weights;
	}
	
	public IndexReader getReader()
	{
		return indexReader;
	}
	
	public void closeIndexReader() throws IOException
	{
		indexReader.close();
	}
}