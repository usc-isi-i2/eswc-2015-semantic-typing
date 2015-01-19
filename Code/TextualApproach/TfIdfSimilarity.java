import java.io.File;
import java.io.IOException;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.Version;
import Additonal.Info;
import LuceneIndex.DocumentSimilarity;
import LuceneIndex.Indexer;
import LuceneIndex.TfIdfSearcher;
import LuceneIndex.TopKSearcher;

public class TfIdfSimilarity {

	public static void main(String[] args) throws IOException, ParseException {
		
		// create an index
		Indexer indexer = new Indexer();
		indexer.createIndex();
		indexer.deleteDocuments(); 
		indexer.commit();
		
		// add documents to index
		indexer.addDocument("my my name is blah:","abc|def"); //0
		
		//indexer.addDocument("52.1 x 71.4 cm (20 1/2 x 28 1/8 in.) 9 3/4 x 7 9/16 in. H:  19 x W:  15 1/4 x D:  8 1/4 in.", "blah");
		
		//indexer.addDocument("blah blah blah blah blah blah!","b"); //1
		//indexer.commit();
		//System.out.println("Committed 1\n");
		
		//TfIdfSearcher searcher = new TfIdfSearcher();		
		//searcher.getTfIdfs();
		//searcher.closeIndexReader();
		
		indexer.addDocument("ram -is a guy","ghi|klm"); //2
//		indexer.addDocument("Council%E2%80%93manager_government","c"); //3
		indexer.commit();
//		System.out.println("\nCommitted 2\n");
//		
		TfIdfSearcher searcher2 = new TfIdfSearcher();		
		searcher2.getTfIdfs();
		searcher2.closeIndexReader();
		
		
		// search for semantic label
		String labelToQuery = "ghi|klm";
		
		IndexReader reader = DirectoryReader.open(FSDirectory.open(new File(Indexer.INDEX_LOC)));
		IndexSearcher indexSearcher = new IndexSearcher(reader);
		Analyzer analyzer = new StandardAnalyzer(Version.LUCENE_48);
		//QueryParser parser = new QueryParser(Version.LUCENE_48, Indexer.LABEL_FIELD_NAME, analyzer);
		//Query query = parser.parse(QueryParser.escape(labelToQuery));
		
		BooleanQuery qry = new BooleanQuery();
		qry.add(new TermQuery(new Term(Indexer.LABEL_FIELD_NAME, labelToQuery)), BooleanClause.Occur.MUST);
		
		TopDocs results = indexSearcher.search(qry, 1);
		ScoreDoc[] hits = results.scoreDocs;
		System.out.println("\nNo of hits="+hits.length);
		if(hits.length>0)
		{
			Document doc = indexSearcher.doc(hits[0].doc);
			String labelString = doc.get(Indexer.LABEL_FIELD_NAME);
			IndexableField existingField = doc.getField(Indexer.CONTENT_FIELD_NAME);
			if(labelString.equalsIgnoreCase(labelToQuery))
			{
				System.out.println("Found exact match!");
				indexer.updateDocument(existingField, "i achieved it", labelString);
				indexer.commit();
			}
			else
			{
				System.out.println("Found inexact match!");
			}
		}
		else
		{
			System.out.println("no documents found with given label!");
		}

		
//		DocumentSimilarity docSim = new DocumentSimilarity(new Info(indexer.getNoOfDocs(),indexer.getNoOfDocs()));
//		docSim.display();
//		docSim.closeIndexReader();
	
//		System.out.println("no4");
		
//		indexer.addDocument("Lucene in Action");
//		indexer.addDocument("Lucene for Dummies");
//		indexer.addDocument("Managing Gigabytes");
//		indexer.addDocument("The Art of Computer Science");
		
		//indexer.closeIndexWriter();
			
		// query tf-idf's
		
		System.out.println("\nNo of docs ="+ indexer.getNoOfDocs()+"\n");
		
		TfIdfSearcher searcher = new TfIdfSearcher();		
		searcher.getTfIdfs();
		searcher.closeIndexReader();
		
		// finding top-k similar documents
//		TopKSearcher topKSearcher = new TopKSearcher();
//		topKSearcher.getTopK(2, "blah ram");
//		topKSearcher.getTopK(2, "98");
//		topKSearcher.getTopK(2, "b");
		
		
	}

}
