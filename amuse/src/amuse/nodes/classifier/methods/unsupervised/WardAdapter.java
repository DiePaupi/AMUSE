package amuse.nodes.classifier.methods.unsupervised;

import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;
import org.apache.log4j.Level;

import amuse.data.annotation.SongPartitionsDescription;
import amuse.data.io.DataSet;
import amuse.data.io.DataSetInput;
import amuse.data.io.attributes.Attribute;
import amuse.data.io.attributes.NominalAttribute;
import amuse.data.io.attributes.NumericAttribute;
import amuse.data.io.attributes.StringAttribute;
import amuse.interfaces.nodes.NodeException;
import amuse.interfaces.nodes.methods.AmuseTask;
import amuse.nodes.classifier.ClassificationConfiguration;
import amuse.nodes.classifier.interfaces.ClassifierUnsupervisedInterface;
import amuse.util.AmuseLogger;
import amuse.util.LibraryInitializer;

/**
 * Clusters given data by using Ward's Agglomeration
 * @author Pauline Speckmann
 *
 */
public class WardAdapter extends AmuseTask implements ClassifierUnsupervisedInterface {
	
	/** Defines the numerical measure option (e.g. euclidean distance) */
	String numericalMeasure;
	/** Desired cluster number */
	int k;

	public void setParameters(String parameterString) {

        // Should the default parameters be used? Or are values given?
        if(parameterString == "" || parameterString == null) {
        	numericalMeasure = new String("LWDissimilarityUpdateFormula");
        	// If no desired cluster number is specified the whole dendogram will be created
        	k = 0;
        } else {
        	StringTokenizer tok = new StringTokenizer(parameterString, "_");
        	numericalMeasure = tok.nextToken();
        	k = new Integer(tok.nextToken());
        }
    }

    /*
     * (non-Javadoc)
     * @see amuse.interfaces.AmuseTaskInterface#initialize()
     */
    public void initialize() throws NodeException {
        // Nothing to do here
    }
    
    /*
     * (non-Javadoc)
     * @see amuse.nodes.classifier.interfaces.ClassifierInterface#classify(java.lang.String, java.util.ArrayList, java.lang.String)
     */
    /* (non-Javadoc)
     * @see amuse.nodes.classifier.interfaces.ClassifierInterface#classify(java.lang.String)
     */
	@Override
	public void classify() throws NodeException {
		/* Gets the DataSet given by the user in the Classifier AMUSE task */
        DataSet dataSetToClassify = ((DataSetInput)((ClassificationConfiguration)this.correspondingScheduler.
         getConfiguration()).getInputToClassify()).getDataSet();

        try {
        	//AmuseLogger.write("WardAdapter", Level.DEBUG, "BEGINNING TO SHOW DATASET");
        	//dataSetToClassify.showSet();
        	
        	//-----------------------------------------------------------------------------------------------------------------------------
        	// (1) Greife auf ArrayList<SongPartitionsDescription> descriptionOfClassifierInput aus dem ClassifierNodeScheduler zu
        	//-----------------------------------------------------------------------------------------------------------------------------
        	int numberOfSongs = ; // TODO - Wert auf descriptionOfClassifierInput nehmen
        	int numberOfFeatures = ; // TODO - Wert auf descriptionOfClassifierInput nehmen
        	
        	if (k < 0 || k > numberOfSongs) {
        		throw new NodeException("WardAdapter - classify(): Your given k wasn't in range. "
        				+ "Try something bigger than 0 and smaller than your number of songs.");
        	}
        	
        	//-----------------------------------------------------------------------------------------------------------------------------
        	// (2) Alle Partitionen eines Songs aus dem descriptionOfClassifierInput einem Cluster zuordnen
        	//-----------------------------------------------------------------------------------------------------------------------------
        	
        	/** allValues contains for each song every feature value based on descriptionOfClassifierInput - [songID][featureValuesOfOneSong] */
    		double[][] allValues = new double[numberOfSongs][numberOfFeatures];
    		
    		// Bilde den Durchschnitt (TODO oder Median) aller Partitionswerte
    		for (int i=0; i < numberOfSongs; i++) {
    			
    			// TODO 
    			// Mit Hilfe von descriptionOfClassifierInput BEFÜLLEN
    		}
    		
    		/** clusterAffiliation is a list of all clusters which each hold a list with all songs that belong to it */
        	List<List<Integer>> clusterAffiliation = new ArrayList<List<Integer>>(numberOfSongs);
        	for (int i=0; i < clusterAffiliation.size(); i++) {
        		ArrayList<Integer> songsInThatCluster = new ArrayList<Integer>();
        		songsInThatCluster.add(i);
        		clusterAffiliation.set(i, songsInThatCluster);
        	}
        	
        	
        	
        	if (k == numberOfSongs) {
        		// TODO
        		// return just for every song one cluster
        	}
        	
        	// UNTIL ALL CLUSTERS HAVE BEEN MERGED
        	int mergeUntilThisClusterNumber;
        	if (k == 0 || k == 1) {
        		mergeUntilThisClusterNumber = 2;
        	} else {
        		mergeUntilThisClusterNumber = k;
        	}
        	
    		while (clusterAffiliation.size() > mergeUntilThisClusterNumber) {
        		
        		//-----------------------------------------------------------------------------------------------------------------------------
            	// (3) Berechne die (un-)ähnlichkeits Matrix aller Songs mit der Lance-William Sache
            	//	   Hier vielleicht auch Wahl zwischen LW und Klassisch lassen
            	//-----------------------------------------------------------------------------------------------------------------------------
        		
        		/** The dissimilarityMatrix stores the ESS values for the centroid of cluster m united with cluster n */
            	double[][] dissimilarityMatrix = new double[clusterAffiliation.size()][clusterAffiliation.size()];
            		
            	// m sind die Splaten und n die Zeilen
            	for (int m=0; m < clusterAffiliation.size(); m++) {
            		for (int n=0; n < clusterAffiliation.size(); n++) {
            			
            			// Falls es sich um dasselbe Cluster handelt, wird der Wert in der Matirx auf 0 gesetzt
                		if (n == m) {
                			dissimilarityMatrix[n][m] = 0.0;
                		} 
                		// Falls die MatrixDiagonale überschritten wird doppeln sich die Werte und werden aus Effizenzgründen nicht erneut berechnet
                		else if (n < m) {
                			dissimilarityMatrix[n][m] = dissimilarityMatrix[m][n];
                		} else {
                			
                			double[] centroid = this.calculateCentroid(m, n, allValues, clusterAffiliation);
                			double ess = this.calculateESS();
                			dissimilarityMatrix[n][m] = ess;
                		}
                	}
            	}
            	
            	// Get the minimum and the corresponding m and n values
            	double[] dissimilarityMatrixMinValues = calculateMininimum (dissimilarityMatrix);
            	
            	// Merge clusters
            	List<Integer> clusterToBeMergedInto = clusterAffiliation.get((int) dissimilarityMatrixMinValues[1]);
            	List<Integer> clusterToBeAnnexed = clusterAffiliation.get((int) dissimilarityMatrixMinValues[0]);
            	
            	clusterToBeMergedInto.addAll(clusterToBeAnnexed);
            	clusterAffiliation.remove((int) dissimilarityMatrixMinValues[0]);
            	
            	//-----------------------------------------------------------------------------------------------------------------------------
            	// (4) Für das ähnlichste Clusterpaar:
            	//	   	- Begutachte die n-1 verbleibenden Cluster mit einer neuen Matrix und dem entsprechenden vereinigten Distanzmaß
            	//		- Soll noch ein drittes zum Cluster hinzugefügt werden oder lieber ein neues Paar erstellt?
            	//	   Wiederhole bis k erreicht oder nur noch ein großes Cluster existiert
            	//-----------------------------------------------------------------------------------------------------------------------------
            	
            	// If the desired cluster number hasn't been reached yet
            	if (mergeUntilThisClusterNumber < clusterAffiliation.size()) {
            		
            		/** The dissimilarityMatrixTwo stores the ESS values for the centroid of cluster m united with cluster n */
                	double[][] dMatrixTwo = new double[clusterAffiliation.size()][clusterAffiliation.size()];
                		
                	// m sind die Splaten und n die Zeilen
                	for (int m=0; m < clusterAffiliation.size(); m++) {
                		for (int n=0; n < clusterAffiliation.size(); n++) {
                			
                			// Falls es sich um dasselbe Cluster handelt, wird der Wert in der Matirx auf 0 gesetzt
                    		if (n == m) {
                    			dMatrixTwo[n][m] = 0.0;
                    		} 
                    		// Falls die MatrixDiagonale überschritten wird doppeln sich die Werte und werden aus Effizenzgründen nicht erneut berechnet
                    		else if (n < m) {
                    			dMatrixTwo[n][m] = dMatrixTwo[m][n];
                    		} else {
                    			
                    			double[] centroid = this.calculateCentroid(m, n, allValues, clusterAffiliation);
                    			double ess = this.calculateESS();
                    			dMatrixTwo[n][m] = ess;
                    		}
                    	}
                	}
                	
                	// Get the next minimum and the corresponding m and n values of the new dMatrixTwo
                	double[] dMatrixTwoMinValues = calculateMininimum(dMatrixTwo);
                	
                	
                	// Does the next best merge contain the just merged cluster?
                	// Yes: Merge the third cluster into it, too, an continue to the next iteration.
                	if (clusterToBeMergedInto.equals(clusterAffiliation.get((int) dMatrixTwoMinValues[0])) ) {
                		
                		clusterToBeAnnexed = clusterAffiliation.get((int) dMatrixTwoMinValues[1]);
                    	
                    	clusterToBeMergedInto.addAll(clusterToBeAnnexed);
                    	clusterAffiliation.remove((int) dissimilarityMatrixMinValues[1]);
                		
                	} else if (clusterToBeMergedInto.equals(clusterAffiliation.get((int) dMatrixTwoMinValues[1]))) {
                		
                		clusterToBeAnnexed = clusterAffiliation.get((int) dMatrixTwoMinValues[0]);
                    	
                    	clusterToBeMergedInto.addAll(clusterToBeAnnexed);
                    	clusterAffiliation.remove((int) dissimilarityMatrixMinValues[0]);
                		
                		
                	}
            	}
            	
            	// IF ONLY TWO CLUSTERS ARE LEFT AND WE WANT ONLY ONE BIG CLUSTER
        		// JUST MERGE THEM NOW
            	if (clusterAffiliation.size() == 2 &&
            			(k == 0 || k == 1)) {
            		
            		clusterToBeMergedInto = clusterAffiliation.get(0);
            		clusterToBeAnnexed = clusterAffiliation.get(1);
                	
                	clusterToBeMergedInto.addAll(clusterToBeAnnexed);
                	clusterAffiliation.remove(1);
            		
            	} else if (clusterAffiliation.size() < 1) {
            		throw new NodeException("WardAdapter - classify(): Somehow there aren't ANY clusters. What did you do?");
            	}
            }
            
        	// NOW THERE ARE (HAVE TO BE) ONLY THE NUMBER OF DESIRED CLUSTERS LEFT
        	// SAVE
            	
        	
        	
      
        } catch(Exception e) {
			throw new NodeException("Error classifying data: " + e.getMessage());
		}
	}
	
	
	//---------------------------------------------------------------------------------------------------------------------------------
	// Private help methods to calculate things so that the main method classify() stays nice
	//---------------------------------------------------------------------------------------------------------------------------------
	
	/** Calculates the minimum value and its corresponding indices of a given matrix */
	private double[] calculateMininimum (double[][] matrix) {
		
		double[] minResult = new double[3];
		minResult[1] = 0; //minM
		minResult[0] = 1; //minN
		minResult[2] = matrix[0][1]; //minValue
		
    	for (int m=0; m < matrix.length; m++) {
    		for (int n=0; n < matrix[0].length; n++) {
    			
    			if (n>m && 
    					matrix[n][m] > 0.0 && 
    					matrix[n][m] < minResult[2]) {
    				
    				minResult[1] = m;
    				minResult[0] = n;
    				minResult[2] = matrix[n][m];
    			} 
    		}
    	}
    	
    	return minResult;
	}
	
	/** Calculates the centroid for the Cluster (Cluster m unified Cluster n) */
	private double[] calculateCentroid (int m, int n, double[][] allValues, List<List<Integer>> clusterAffiliation) {
		
		double[] centroid = new double[allValues[0].length];
		
		// Durchlaufe alle Features, um in centroid[z] die Summe und später den Durchschnitt zu bilden
		for (int z=0; z < centroid.length; z++) {
			centroid[z] = 0;
			
			// Nimm von jedem Song in Cluster m den Wert von Feature z und addiere ihn zum centroid[z] hinzu
			for (int i=0; i < clusterAffiliation.get(m).size(); i++) {
				int currentSongID = (int) clusterAffiliation.get(m).get(i);
				centroid[z] += allValues[currentSongID][z];
			}
			// Nimm von jedem Song in Cluster n den Wert von Feature z und addiere ihn zum centroid[z] hinzu
			for (int i=0; i < clusterAffiliation.get(n).size(); i++) {
				int currentSongID = (int) clusterAffiliation.get(n).get(i);
				centroid[z] += allValues[currentSongID][z];
			}
			centroid[z] = centroid[z] / (clusterAffiliation.get(m).size() + clusterAffiliation.get(n).size());
		}
		
		return centroid;
	}
	
	// TODO
	private double calculateESS () {
		
		return -1.0;
	}

}
