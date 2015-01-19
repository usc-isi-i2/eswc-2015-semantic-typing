package LuceneIndex;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.Version;

public class TopKSearcher {

	private IndexSearcher indexSearcher = null;
	private Analyzer analyzer = null;
	private QueryParser parser = null;
	
	public TopKSearcher() throws IOException
	{
		IndexReader reader = DirectoryReader.open(FSDirectory.open(new File(Indexer.INDEX_LOC)));
		indexSearcher = new IndexSearcher(reader);
		analyzer = new StandardAnalyzer(Version.LUCENE_48);
		parser = new QueryParser(Version.LUCENE_48, Indexer.CONTENT_FIELD_NAME, analyzer);
	}
	
	public void getTopK(int k, String content, ArrayList<String> predictions, ArrayList<Double> confidenceScores) throws ParseException, IOException 
	{
		int spaces = content.length() - content.replace(" ", "").length();
		if(spaces > BooleanQuery.getMaxClauseCount())
		{
			BooleanQuery.setMaxClauseCount(spaces);
		}		
		
		Query query = parser.parse(QueryParser.escape(content));
		//System.out.println("Searching for: " + query.toString(content));
		
		TopDocs results = indexSearcher.search(query, k);
		ScoreDoc[] hits = results.scoreDocs;
		
		//System.out.println("Results:");
		for(int i=0; i<hits.length; i++)
		{
			Document doc = indexSearcher.doc(hits[i].doc);
			//String contentString = doc.get(Indexer.CONTENT_FIELD_NAME);
			String labelString = doc.get(Indexer.LABEL_FIELD_NAME);
			predictions.add(labelString);
			confidenceScores.add((double) hits[i].score);
			//System.out.println((i+1)+": Content= "+contentString+"\n Label= "+labelString);
		}
		
	}
	
}
