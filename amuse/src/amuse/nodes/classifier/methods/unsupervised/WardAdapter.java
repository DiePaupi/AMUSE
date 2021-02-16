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
        	
        	//-----------------------------------------------------------------------------------------------------------------------------
        	// (2) Alle Partitionen eines Songs aus dem descriptionOfClassifierInput einem Cluster zuordnen
        	//-----------------------------------------------------------------------------------------------------------------------------
        	
        	/** allValues contains for each song every feature value based on descriptionOfClassifierInput - [songID][featureValuesOfOneSong] */
    		double[][] allValues = new double[numberOfSongs][numberOfFeatures];
    		
    		// Bilde den Durchschnitt (TODO oder Median) aller Partitionswerte
    		for (int i=0; i < numberOfSongs; i++) {
    			// TODO - Mit Hilfe von descriptionOfClassifierInput BEFÜLLEN
    		}
    		
    		/** clusterAffiliation is a list of all clusters which each hold a list with all songs that belong to it */
        	List<List<Integer>> clusterAffiliation = new ArrayList<List<Integer>>(numberOfSongs);
        	for (int i=0; i < clusterAffiliation.size(); i++) {
        		ArrayList<Integer> songsInThatCluster = new ArrayList<Integer>();
        		songsInThatCluster.add(i);
        		clusterAffiliation.set(i, songsInThatCluster);
        	}
        	
        	// UNTIL ALL CLUSTERS HAVE BEEN MERGED
    		int numberOfClusters = numberOfSongs;
        	while (numberOfClusters > 0) {
        		
        		//-----------------------------------------------------------------------------------------------------------------------------
            	// (3) Berechne die (un-)ähnlichkeits Matrix aller Songs mit der Lance-William Sache
            	//	   Hier vielleicht auch Wahl zwischen LW und Klassisch lassen
            	//-----------------------------------------------------------------------------------------------------------------------------
        		
        		/** The dissimilarityMatrix stores the ESS values for the centroid of cluster m united with cluster n */
            	double[][] dissimilarityMatrix = new double[numberOfClusters][numberOfClusters];
            		
            	// m sind die Splaten und n die Zeilen
            	for (int m=0; m < numberOfClusters; m++) {
            		for (int n=0; n < numberOfClusters; n++) {
            			
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
            	int minM = 0;
            	int minN = 1;
            	double minValue = dissimilarityMatrix[0][1];
            	for (int m=0; m < dissimilarityMatrix.length; m++) {
            		for (int n=0; n < dissimilarityMatrix[0].length; n++) {
            			
            			if (n>m && 
            				dissimilarityMatrix[m][n] > 0.0 && 
            				dissimilarityMatrix[m][n] < minValue) {
            				
            				minM = m;
            				minN = n;
            				minValue = dissimilarityMatrix[m][n];
            			} 
            		}
            	}
            	
            	// Merge clusters
            	List<Integer> clusterToBeMergedInto = clusterAffiliation.get(minM);
            	List<Integer> clusterToBeAnnexed = clusterAffiliation.get(minN);
            	
            	clusterToBeMergedInto.addAll(clusterToBeAnnexed);
            	clusterAffiliation.remove(minN);
            	
            	//-----------------------------------------------------------------------------------------------------------------------------
            	// (4) Für das ähnlichste Clusterpaar:
            	//	   	- Begutachte die n-1 verbleibenden Cluster mit einer neuen Matrix und dem entsprechenden vereinigten Distanzmaß
            	//		- Soll noch ein drittes zum Cluster hinzugefügt werden oder lieber ein neues Paar erstellt?
            	//	   Wiederhole bis k erreicht oder nur noch ein großes Cluster existiert
            	//-----------------------------------------------------------------------------------------------------------------------------
            	
            	//TODO
            	
            	// Wenn der richtige Clustermerge gefunden wurde: 
            	// 	Die Änderung in clusterAffiliation speichern, Nummer der Cluster um 1 reduzieren und von vorne beginnen
            	//clusterAffiliation[Songs die nun zu einem anderen Cluster gehören] = NeuesCluster;
            	numberOfClusters--;
        	}
      
        } catch(Exception e) {
			throw new NodeException("Error classifying data: " + e.getMessage());
		}
	}
	
	
	//---------------------------------------------------------------------------------------------------------------------------------
	// Private help methods to calculate things so that the main method classify() stays nice
	//---------------------------------------------------------------------------------------------------------------------------------
	
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
