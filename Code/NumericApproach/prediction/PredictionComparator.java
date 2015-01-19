package prediction;

import java.util.Comparator;

	/* Comparator for priority queue */
	public class PredictionComparator implements Comparator{
	
		public int compare(Object c1, Object c2) {
			Prediction node1 = (Prediction)c1;
			Prediction node2 = (Prediction)c2;
			if(node1.confidenceScore > node2.confidenceScore) {
				return -1;
			}
			else if(node1.confidenceScore == node2.confidenceScore) {
				return 0;
			}
			else return 1;
		}
	}
