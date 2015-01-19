package misc;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class TrigramJaccardCoefficient {
	
	static int n = 3;
	
  public static double getSimilarity(Collection<String> stringList1, Collection<String> stringList2)
  {
      // Get n-grams
      Set<String> ngrams1 = getNGrams(Arrays.asList(stringList1.toArray(new String[] {})));
      Set<String> ngrams2 = getNGrams(Arrays.asList(stringList2.toArray(new String[] {})));
      
      double sim = getNormalizedSimilarity(ngrams1, ngrams2);

      return sim;
  }
  
  protected static double getNormalizedSimilarity(Set<String> suspiciousNGrams, Set<String> originalNGrams)
  {
	  // Compare using the Jaccard similarity coefficient
	  Set<String> commonNGrams = new HashSet<String>();
	  commonNGrams.addAll(suspiciousNGrams);
	  commonNGrams.retainAll(originalNGrams);
	
	  Set<String> unionNGrams = new HashSet<String>();
	  unionNGrams.addAll(suspiciousNGrams);
	  unionNGrams.addAll(originalNGrams);
	
	  double norm = unionNGrams.size();
	  double sim = 0.0;
	
	  if (norm > 0.0) {
	      sim = commonNGrams.size() / norm;
	  }
	
	  return sim;
  }
  
  private static Set<String> getNGrams(List<String> stringList)
  {
      Set<String> ngrams = new HashSet<String>();

      for (int i=0; i<stringList.size(); i++){
      	String string = stringList.get(i);
      	for(int j=0; j<= string.length()-n; j++){
      		String tri = string.substring(j, j+n).toLowerCase();
      		ngrams.add(tri);
      	}
      }
      
      return ngrams;
  }
  
	public static void main(String[] args){
		
		List<String> list1 = new ArrayList<String>();
		List<String> list2 = new ArrayList<String>();
		
		list1.add("totalSurfacearea");
		list2.add("area");
		
		System.out.println(getSimilarity(list1,list2));
	}
}
