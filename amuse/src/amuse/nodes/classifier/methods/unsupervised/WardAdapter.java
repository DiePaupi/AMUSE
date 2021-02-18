package amuse.nodes.classifier.methods.unsupervised;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
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
import amuse.nodes.classifier.ClassifierNodeScheduler;
import amuse.nodes.classifier.interfaces.ClassifierUnsupervisedInterface;
import amuse.preferences.AmusePreferences;
import amuse.preferences.KeysStringValue;
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
        	dataSetToClassify.showSet();
        	
        	//-----------------------------------------------------------------------------------------------------------------------------
        	// (1) Greife auf ArrayList<SongPartitionsDescription> descriptionOfClassifierInput aus dem ClassifierNodeScheduler zu
        	//-----------------------------------------------------------------------------------------------------------------------------
        	 ArrayList<SongPartitionsDescription> descriptionOfClassifierInput = 
        			 ((ClassifierNodeScheduler)this.correspondingScheduler).getDescriptionOfClassifierInput();
        	
        	int numberOfSongs = descriptionOfClassifierInput.size();
        	// TODO - Wert eventuell -1 wegen Id oder so
        	int numberOfFeatures = dataSetToClassify.getAttributeCount(); 
        	
        	if (k < 0 || k >= numberOfSongs) {
        		throw new NodeException("WardAdapter - classify(): Your given k wasn't in range. "
        				+ "Try something bigger than 0 and smaller than your number of songs.");
        	}
        	
        	//-----------------------------------------------------------------------------------------------------------------------------
        	// (2) Alle Partitionen eines Songs aus dem descriptionOfClassifierInput einem Cluster zuordnen
        	//-----------------------------------------------------------------------------------------------------------------------------
        	
        	/** allValues contains for each song every feature value based on descriptionOfClassifierInput - [songID][featureValuesOfOneSong] */
    		double[][] allValues = new double[numberOfSongs][numberOfFeatures];
    		
    		// Bilde den Durchschnitt (TODO oder Median) aller Partitionswerte
    		int partitionsAlreadySeen = 0;
    		for (int i=0; i < numberOfSongs; i++) {
    			int numberOfPartitionsForSongI = descriptionOfClassifierInput.get(i).getStartMs().length;
    			
    			for (int j= partitionsAlreadySeen; j < numberOfPartitionsForSongI; j++) {
    				for (int x=0; x < numberOfFeatures; x++) {
    					allValues[i][x] += (double) dataSetToClassify.getAttribute(x).getValueAt(j);
    				}
    			}
    			for (int x=0; x < numberOfFeatures; x++) {
					allValues[i][x] = allValues[i][x] / numberOfPartitionsForSongI;
				}
    			
    			partitionsAlreadySeen += numberOfPartitionsForSongI;
    		}
    		
    		/** clusterAffiliation is a list of all clusters which each hold a list with all songs that belong to it */
        	List<List<Integer>> clusterAffiliation = new ArrayList<List<Integer>>(numberOfSongs);
        	for (int i=0; i < clusterAffiliation.size(); i++) {
        		ArrayList<Integer> songsInThatCluster = new ArrayList<Integer>();
        		songsInThatCluster.add(i);
        		clusterAffiliation.set(i, songsInThatCluster);
        	}
        	Dendogram dendo = new Dendogram(clusterAffiliation);
        	
        	
        	
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
                			
                			double dissimilarity = 0.0;
                			double[] centroidN = this.calculateCentroid(allValues, clusterAffiliation.get(n));
            				double[] centroidM = this.calculateCentroid(allValues, clusterAffiliation.get(m));
            				
                			if (numericalMeasure.equals("LWDissimilarityUpdateFormula")) {
                				
                				//dissimilarity = this.calculateLWDissimilarity(
                    			//		centroidN, clusterAffiliation.get(n).size(), 
                    			//		centroidM, clusterAffiliation.get(m).size(), 
                    			//		centroidC, sizeC)
                    			
                			} else if (numericalMeasure.equals("Classic")) {
                				
                				dissimilarity = this.calculateClassicDissimilarity(
                    					centroidN, clusterAffiliation.get(n).size(), 
                    					centroidM, clusterAffiliation.get(m).size());
                				
                			} else {
                				throw new NodeException("Error classifying data with the WardAdapter: The numerical measure couldn't be identified");
                			}
                			
                			dissimilarityMatrix[n][m] = dissimilarity;
                		}
                	}
            	}
            	
            	// Get the minimum and the corresponding m and n values
            	double[] dissimilarityMatrixMinValues = calculateMininimum (dissimilarityMatrix);
            	
            	// Merge clusters
            	List<Integer> clusterToBeMergedInto = clusterAffiliation.get((int) dissimilarityMatrixMinValues[1]);
            	List<Integer> clusterToBeAnnexed = clusterAffiliation.get((int) dissimilarityMatrixMinValues[0]);
            	
            	dendo.setNewMerge(clusterToBeMergedInto, clusterToBeAnnexed);
            	
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
                    			
                    			double dissimilarity = 0.0;
                    			double[] centroidN = this.calculateCentroid(allValues, clusterAffiliation.get(n));
                				double[] centroidM = this.calculateCentroid(allValues, clusterAffiliation.get(m));
                				
                    			if (numericalMeasure.equals("LWDissimilarityUpdateFormula")) {
                    				
                    				// TODO
                    				//dissimilarity = this.calculateLWDissimilarity(
                        			//		centroidN, clusterAffiliation.get(n).size(), 
                        			//		centroidM, clusterAffiliation.get(m).size(), 
                        			//		centroidC, sizeC)
                        			
                    			} else if (numericalMeasure.equals("Classic")) {
                    				
                    				dissimilarity = this.calculateClassicDissimilarity(
                        					centroidN, clusterAffiliation.get(n).size(), 
                        					centroidM, clusterAffiliation.get(m).size());
                    				
                    			} else {
                    				throw new NodeException("Error classifying data with the WardAdapter: The numerical measure couldn't be identified");
                    			}
                    			
                    			dMatrixTwo[n][m] = dissimilarity;
                    		}
                    	}
                	}
                	
                	// Get the next minimum and the corresponding m and n values of the new dMatrixTwo
                	double[] dMatrixTwoMinValues = calculateMininimum(dMatrixTwo);
                	
                	
                	// Does the next best merge contain the just merged cluster?
                	// Yes: Merge the third cluster into it, too, an continue to the next iteration.
                	if (clusterToBeMergedInto.equals(clusterAffiliation.get((int) dMatrixTwoMinValues[0])) ) {
                		
                		clusterToBeAnnexed = clusterAffiliation.get((int) dMatrixTwoMinValues[1]);
                		dendo.setNewMerge(clusterToBeMergedInto, clusterToBeAnnexed);
                    	
                    	clusterToBeMergedInto.addAll(clusterToBeAnnexed);
                    	clusterAffiliation.remove((int) dissimilarityMatrixMinValues[1]);
                		
                	} else if (clusterToBeMergedInto.equals(clusterAffiliation.get((int) dMatrixTwoMinValues[1]))) {
                		
                		clusterToBeAnnexed = clusterAffiliation.get((int) dMatrixTwoMinValues[0]);
                		dendo.setNewMerge(clusterToBeMergedInto, clusterToBeAnnexed);
                    	
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
            		
            		dendo.setNewMerge(clusterToBeMergedInto, clusterToBeAnnexed);
                	
                	clusterToBeMergedInto.addAll(clusterToBeAnnexed);
                	clusterAffiliation.remove(1);
            		
            	} else if (clusterAffiliation.size() < 1) {
            		throw new NodeException("WardAdapter - classify(): Somehow there aren't ANY clusters. What did you do?");
            	}
            }
            
    		
    		//-----------------------------------------------------------------------------------------------------------------------------
        	// (5) SAVE
        	//-----------------------------------------------------------------------------------------------------------------------------
    		String outputPath = AmusePreferences.get(KeysStringValue.AMUSE_PATH) + File.separator + "experiments" + File.separator;
    		
    		DataSet amuseDataSet = new DataSet("WardResult_DataSet");
    		
    		// TODO - Eventuell muss x und x < ... angepasst werden.
    		for (int x=0; x < numberOfFeatures; x++ ) {
    			List<Double> featureXList = new ArrayList<Double>();
    			
    			for (int i=0; i < numberOfSongs; i++) {
    				featureXList.add(allValues[i][x]);
    			}
    			
    			Attribute featureX = new NumericAttribute( dataSetToClassify.getAttribute(x).getName() , featureXList);
    			amuseDataSet.addAttribute(featureX);
    			
    		}
    		for (int c=0; c < clusterAffiliation.size(); c++ ) {
    			List<Double> clusterCList = new ArrayList<Double>();
    			
    			for (int i=0; i < numberOfSongs; i++) {
    				
    				if (clusterAffiliation.get(c).contains(i)) {
    					clusterCList.add(i, 1.0);
    				} else {
    					clusterCList.add(i, 0.0);
    				}
    			}
    			
    			Attribute clusterX = new NumericAttribute("cluster_" + c, clusterCList);
    			amuseDataSet.addAttribute(clusterX);
    		}
    		
    		// Give the amuseDataSet to the ClassificationConfiguration so it may be put together and saved there
        	// The ClassifierNodeScheduler proceedTask(...) returns or saves an ArrayList<ClassifiedSongPartitionsDescription>
            ((ClassificationConfiguration)(this.correspondingScheduler.getConfiguration())).setInputToClassify(new DataSetInput(amuseDataSet));
    		amuseDataSet.saveToArffFile(new File(outputPath + "Ward_Result.arff"));
    		
    		// Show the Dendogram in the AMUSE logger
    		AmuseLogger.write("WardAdapter", Level.DEBUG, "Printing the dendogramm:");
    			dendo.showClusters();
    		// Save Dendogram print as .text
    		File dendogramFile = new File(outputPath + "Ward_DENDOGRAM.txt");
    			BufferedWriter fileWriter = new BufferedWriter(new FileWriter(dendogramFile));
    			fileWriter.append( dendo.printClusters() );
    			fileWriter.close();
      
        } catch(Exception e) {
			throw new NodeException("Error classifying data with the WardAdapter: " + e.getMessage());
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
	
	
	/**
	 * Calculates the dissimilarity using the centroid of clusters
	 * ClusterA and ClusterB are the ones to be merged 
	 * 
	 * @param centroidA = Centroid of ClusterA (One of the two to be merged)
	 * @param sizeA = Cardinality (size) of ClsuterA
	 * @param centroidB = Centroid of ClusterB (One of the two to be merged)
	 * @param sizeB = Cardinality (size) of ClsuterB
	 * 
	 * @return A double containing the calculated dissimilarity with the classic Ward criterion
	 */
	private double calculateClassicDissimilarity (double[] centroidA, int sizeA, double[] centroidB, int sizeB) {
		double cardinality = (sizeA * sizeB) / (sizeA + sizeB);
		double distanceMeasure = this.d(centroidA, centroidB);
		return cardinality * distanceMeasure;
	}
	
	/**
	 * Calculates the dissimilarity using the centroid of clusters
	 * ClusterA and ClusterB are the ones to be merged and ClusterC is the "old" reference so the increase can be determined as dissimilarity
	 * 
	 * @param centroidA = Centroid of ClusterA (One of the two to be merged)
	 * @param sizeA = Cardinality (size) of ClsuterA
	 * @param centroidB = Centroid of ClusterB (One of the two to be merged)
	 * @param sizeB = Cardinality (size) of ClsuterB
	 * @param centroidC = Centroid of ClusterC (the reference cluster)
	 * @param sizeC = Cardinality (size) of ClsuterC
	 * 
	 * @return A double containing the calculated dissimilarity with the Lance-Williams Dissimilarity Update Formula
	 */
	private double calculateLWDissimilarity (double[] centroidA, int sizeA, double[] centroidB, int sizeB, double[] centroidC, int sizeC) {
		double alphaFirst = (sizeA + sizeC) / (sizeA + sizeB + sizeC);
		alphaFirst = alphaFirst * this.d(centroidA, centroidC);
		
		double alphaSecond = (sizeB + sizeC) / (sizeA + sizeB + sizeC);
		alphaSecond = alphaSecond * this.d(centroidB, centroidC);
		
		double beta = (sizeC) / (sizeA + sizeB + sizeC);
		beta = beta * this.d(centroidA, centroidB);
		
		return alphaFirst + alphaSecond - beta;
	}
	
	
	/**
	 * Calculates the distance between to centroids (double[])
	 * @param centroidA
	 * @param centroidB
	 * @return A double value containing the distance of the given centroids
	 */
	private double d (double[] centroidA, double[] centroidB) {
		
		double distanceMeasure = 0.0;
		
		// Using the squared euclidean distance as distance measure
		for(int x=0; x < centroidA.length; x ++) {
			double difference = centroidA[x] - centroidB[x];
			distanceMeasure += difference * difference;
		}
		
		return distanceMeasure;		
	}
		
	
	/** 
	 * Calculates the centroid for one Cluster
	 * @param allValues = Matrix which contains for each song and every feature the corresponding value
	 * @param cluster = The cluster (List<Integer>) which centroid should be calculated
	 * @return An array of doubles which contains for every feature (index) the value of the centroid 
	 */
	private double[] calculateCentroid (double[][] allValues, List<Integer> cluster) {
		
		double[] centroid = new double[allValues[0].length];
		
		// Durchlaufe alle Features, um in centroid[z] die Summe und später den Durchschnitt zu bilden
		for (int z=0; z < centroid.length; z++) {
			centroid[z] = 0;
			
			// Nimm von jedem Song im Cluster den Wert von Feature z und addiere ihn zum centroid[z] hinzu
			for (int i=0; i < cluster.size(); i++) {
				int currentSongID = (int) cluster.get(i);
				centroid[z] += allValues[currentSongID][z];
			}
			centroid[z] = centroid[z] / cluster.size();
		}
		
		return centroid;
	}
	

}
