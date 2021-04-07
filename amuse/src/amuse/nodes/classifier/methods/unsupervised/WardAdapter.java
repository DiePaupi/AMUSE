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
import amuse.nodes.classifier.methods.unsupervised.supportclasses.Dendrogram;
import amuse.nodes.classifier.methods.unsupervised.supportclasses.Testing;
import amuse.nodes.classifier.methods.unsupervised.supportclasses.idAndName;
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
	private String numericalMeasure;
	/** Desired cluster number */
	private int k;
	/** If there's only one song, keep the partitions separated for music segmentation */
	private boolean keepPartitions = false;
	
	/** Contains the values for every song and their features */
	private double[][] allValues;
	/** */
	private List<List<Integer>> clusterAffiliation;
	/** */
	private Dendrogram dendo;

	public void setParameters(String parameterString) {

        // Should the default parameters be used? Or are values given?
        if(parameterString == "" || parameterString == null) {
        	numericalMeasure = new String("Classic");
        	// If no desired cluster number is specified the whole dendrogram will be created
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
		
		try {
        	//-----------------------------------------------------------------------------------------------------------------------------
        	// (1) Greife auf ArrayList<SongPartitionsDescription> descriptionOfClassifierInput aus dem ClassifierNodeScheduler zu
        	//-----------------------------------------------------------------------------------------------------------------------------
			
			/* Gets the DataSet given by the user in the Classifier AMUSE task */
	        DataSet dataSetToClassify = ((DataSetInput)((ClassificationConfiguration)this.correspondingScheduler.
	         getConfiguration()).getInputToClassify()).getDataSet();
	        
	        // TESTING
	        //dataSetToClassify = Testing.createTestDataSet(dataSetToClassify);
	        
        	 ArrayList<SongPartitionsDescription> descriptionOfClassifierInput = 
        			 ((ClassifierNodeScheduler)this.correspondingScheduler).getDescriptionOfClassifierInput();
        	
        	int numberOfSongs = descriptionOfClassifierInput.size();
        	if (numberOfSongs == 1 ) {
        		keepPartitions = true;
        	}
        	int numberOfFeatures = dataSetToClassify.getAttributeCount();
        	AmuseLogger.write("WardAdapter - initializing", Level.DEBUG, "There are " + numberOfFeatures + " to be saved.");
        	
        	if (!keepPartitions && (k < 0 || k >= numberOfSongs)) {
        		throw new NodeException("WardAdapter - classify(): Your given k wasn't in range. "
        				+ "Try something bigger than 0 and smaller than your number of songs.");
        	} else if (keepPartitions && (k < 0 || k >= descriptionOfClassifierInput.get(0).getStartMs().length)) {
        		throw new NodeException("WardAdapter - classify(): Your given k wasn't in range. "
        				+ "Try something bigger than 0 and smaller than your number of songpartitions.");
        	}
        	
        	
        	//-----------------------------------------------------------------------------------------------------------------------------
        	// (2) Alle Partitionen eines Songs aus dem descriptionOfClassifierInput einem Cluster zuordnen
        	//-----------------------------------------------------------------------------------------------------------------------------
        	
        	List<idAndName> songIdsAndNames = new ArrayList<idAndName>();
        	
        	if (keepPartitions) {
        		int numberOfPartitions = descriptionOfClassifierInput.get(0).getStartMs().length;
        		allValues = new double[numberOfPartitions][numberOfFeatures];
        		
        		for (int i=0; i<numberOfPartitions; i++) {
        			idAndName current = new idAndName(i, "Partition "+i);
        			songIdsAndNames.add(current);
        			
        			for (int x=0; x < numberOfFeatures; x++) {
    					allValues[i][x] = allValues[i][x] + (double) dataSetToClassify.getAttribute(x).getValueAt(i);
    				}
        		}
        		
        	} else {
        		/** allValues contains for each song every feature value based on descriptionOfClassifierInput - [songID][featureValuesOfOneSong] */
        		allValues = new double[numberOfSongs][numberOfFeatures];
        		
        		// Bilde den Durchschnitt (TODO oder Median) aller Partitionswerte
        		int partitionsAlreadySeen = 0;
        		for (int i=0; i < numberOfSongs; i++) {
        			
        			int numberOfPartitionsForSongI = descriptionOfClassifierInput.get(i).getStartMs().length;
        			// Save the song path with it's id
        			idAndName current = new idAndName(i, descriptionOfClassifierInput.get(i).getPathToMusicSong());
        			songIdsAndNames.add(current);
        			
        			for (int j= partitionsAlreadySeen; j < partitionsAlreadySeen+numberOfPartitionsForSongI; j++) {
        				for (int x=0; x < numberOfFeatures; x++) {
        					allValues[i][x] = allValues[i][x] + (double) dataSetToClassify.getAttribute(x).getValueAt(j);
        				}
        			}
        			for (int x=0; x < numberOfFeatures; x++) {
    					allValues[i][x] = allValues[i][x] / numberOfPartitionsForSongI;
    				}
        			
        			partitionsAlreadySeen += numberOfPartitionsForSongI;
        		}
        	}
        	
    		/** clusterAffiliation is a list of all clusters which each hold a list with all songs that belong to it */
        	clusterAffiliation = new ArrayList<List<Integer>>();
        	int initialClusterNumber = 0;
        	if (keepPartitions) {
        		initialClusterNumber = descriptionOfClassifierInput.get(0).getStartMs().length;
        	} else {
        		initialClusterNumber = numberOfSongs;
        	}
        	for (int i=0; i < initialClusterNumber; i++) {
        		ArrayList<Integer> songsInThatCluster = new ArrayList<Integer>();
        		songsInThatCluster.add(i);
        		
        		clusterAffiliation.add(songsInThatCluster);
        	}
        	dendo = new Dendrogram(clusterAffiliation, songIdsAndNames);
        	
        	
        	
        	if (numericalMeasure.equals("Classic")) {
        		
        		// UNTIL ALL CLUSTERS HAVE BEEN MERGED
            	int mergeUntilThisClusterNumber;
            	if (k == 0 || k == 1) { mergeUntilThisClusterNumber = 2; } 
            	else { mergeUntilThisClusterNumber = k; }
            	
            	AmuseLogger.write("WardAdapter", Level.DEBUG, "(3) The merge until this cluster number parameter is " +mergeUntilThisClusterNumber+ " - "
            			+ "Starting the while loop with: while (" +clusterAffiliation.size()+ " > " +mergeUntilThisClusterNumber+ ");");
        		
        		while (clusterAffiliation.size() > mergeUntilThisClusterNumber) {        		
        		//-----------------------------------------------------------------------------------------------------------------------------
            	// (3) Berechne die (un-)ähnlichkeits Matrix aller Songs mit der Lance-William Sache
            	//	   Hier vielleicht auch Wahl zwischen LW und Klassisch lassen
            	//-----------------------------------------------------------------------------------------------------------------------------
        		
        			/** The dissimilarityMatrix stores the ESS values for the centroid of cluster m united with cluster n */
        			double[][] dissimilarityMatrix = calculateDissimilarityMatrix();
            	
        			// Get the minimum (in the first row) and the second minimum (in the second row) and the corresponding m and n values
        			double[][] dissimilarityMatrixMinValues = calculateMininimum (dissimilarityMatrix);
            	
        			// Merge clusters
        			List<Integer> clusterToBeMergedA = clusterAffiliation.get((int) dissimilarityMatrixMinValues[1][0]);
        			List<Integer> clusterToBeMergedB = clusterAffiliation.get((int) dissimilarityMatrixMinValues[0][0]);
        			List<Integer> mergedCluster = new ArrayList<Integer>();
        			mergedCluster.addAll(clusterToBeMergedA);
        			mergedCluster.addAll(clusterToBeMergedB);
            	
        			try {
        				dendo.setNewMerge(clusterToBeMergedA, clusterToBeMergedB, mergedCluster);
        			} catch (Exception e) {
        				AmuseLogger.write("WardAdapter - dissMatrix", Level.WARN, "The dendogram coudn't set a new merge: " + e.getMessage());
        			}
            	
        			clusterAffiliation.add(mergedCluster);
        			clusterAffiliation.remove(clusterToBeMergedA);
        			clusterAffiliation.remove(clusterToBeMergedB);
            	
            	//-----------------------------------------------------------------------------------------------------------------------------
            	// (4) Für das ähnlichste Clusterpaar:
            	//	   	- Begutachte die n-1 verbleibenden Cluster mit einer neuen Matrix und dem entsprechenden vereinigten Distanzmaß
            	//		- Soll noch ein drittes zum Cluster hinzugefügt werden oder lieber ein neues Paar erstellt?
            	//-----------------------------------------------------------------------------------------------------------------------------
            	
        			// If the desired cluster number hasn't been reached yet
        			if (mergeUntilThisClusterNumber < clusterAffiliation.size()) {
            		
        				/** The dissimilarityMatrixTwo stores the ESS values for the centroid of cluster m united with cluster n */
        				// The last cluster will not be evaluated since it it the same as the just merged cluster
        				double[] dissimilarityBetweenMergedClusterAndTheOthers = new double[clusterAffiliation.size() -1];
        				double[] centroidMergedCluster = this.calculateCentroid(mergedCluster);
                		
                	
        				for (int m=0; m < dissimilarityBetweenMergedClusterAndTheOthers.length; m++) {
                			
        					//AmuseLogger.write("WardAdapter", Level.DEBUG, "Filling the dissimilarity array at position " +m
        					//		+ " of " + (dissimilarityBetweenMergedClusterAndTheOthers.length));
                		
        					double dissimilarity = 0.0;
        					double[] centroidM = this.calculateCentroid(clusterAffiliation.get(m));
                				
        					dissimilarity = this.calculateClassicDissimilarity(
        							centroidMergedCluster, mergedCluster.size(), 
        							centroidM, clusterAffiliation.get(m).size());
                    	
        					if (dissimilarity == 0.0) {
        						AmuseLogger.write("WardAdapter - dissArray", Level.WARN, "The dissimilarity wasn't calculated correctly!");
        					}
        					dissimilarityBetweenMergedClusterAndTheOthers[m] = dissimilarity;
        				}
                	
                	
        				// Get the next minimum and the corresponding m and n values of the new dissimilarityBetweenMergedClusterAndTheOthers
        				int minClusterNumber = 0;
        				double minDisValue = dissimilarityBetweenMergedClusterAndTheOthers[0];
        				for (int m=1; m < dissimilarityBetweenMergedClusterAndTheOthers.length; m++) {
        					if (minDisValue > dissimilarityBetweenMergedClusterAndTheOthers[m]) {
        						minClusterNumber = m;
                				minDisValue = dissimilarityBetweenMergedClusterAndTheOthers[m];
        					}
        				}
                	
        				// Is the next best merge is with the justMerged cluster, merge again!
        				// If not, then continue to the next iteration
        				if (minDisValue < dissimilarityMatrixMinValues[2][1]) {
                		
        					List<Integer> clusterToBeMergedC = clusterAffiliation.get(minClusterNumber);
        					List<Integer> BigMergedCluster = new ArrayList<Integer>();
        					BigMergedCluster.addAll(mergedCluster);
        					BigMergedCluster.addAll(clusterToBeMergedC);
                		
        					try {
        						dendo.setNewMerge(mergedCluster, clusterToBeMergedC, BigMergedCluster);
        					} catch (Exception e) {
        						AmuseLogger.write("WardAdapter", Level.WARN, "The dendogram coudn't set a new merge: " + e.getMessage());
        					}
                    	
        					clusterAffiliation.add(BigMergedCluster);
        					clusterAffiliation.remove(mergedCluster);
        					clusterAffiliation.remove(clusterToBeMergedC);
        				}
        			}
            	
        			// IF ONLY TWO CLUSTERS ARE LEFT AND WE WANT ONLY ONE BIG CLUSTER
        			// JUST MERGE THEM NOW
        			if (clusterAffiliation.size() == 2 && (k == 0 || k == 1)) {
            		
        				List<Integer> firstCluster = clusterAffiliation.get(0);
        				List<Integer> secondCluster = clusterAffiliation.get(1);
        				List<Integer> lastCluster = new ArrayList<Integer>();
        				lastCluster.addAll(firstCluster);
        				lastCluster.addAll(secondCluster);
            		
        				try {
        					dendo.setNewMerge(firstCluster, secondCluster, lastCluster);
        				} catch (Exception e) {
        					AmuseLogger.write("WardAdapter", Level.WARN, "The dendogram coudn't set a new merge: " + e.getMessage());
        				}
                	
        				clusterAffiliation.add(lastCluster);
        				clusterAffiliation.remove(firstCluster);
        				clusterAffiliation.remove(secondCluster);
            		
        			} else if (clusterAffiliation.size() < 1) {
        				throw new NodeException("WardAdapter - classify(): Somehow there aren't ANY clusters. What did you do?");
        			}
        		}
        	}
        	else if (numericalMeasure.equals("LWDissimilarityUpdateFormula")) {
        		
        		/** The dissimilarityMatrix stores the ESS values for the centroid of cluster m united with cluster n */
    			double[][] dissimilarityMatrix = calculateDissimilarityMatrix();
        	
    			String matrix = "";
    	    	for (int n=0; n < dissimilarityMatrix.length; n++) {
    	    		matrix += n + ": ";
    	    		for (int m=0; m < dissimilarityMatrix.length; m++) {
    	    			matrix += dissimilarityMatrix[m][n] + ", ";
    	    		}
    	    		matrix += "\n";
    	    	}
    	    	AmuseLogger.write("WardAdapter - LWUF", Level.DEBUG, "Starting the LWUF recursion with: \n" + matrix);
    	    	
    			this.updateDissimilarityMatrix(dissimilarityMatrix);
        	}
            
    		
    		//-----------------------------------------------------------------------------------------------------------------------------
        	// (5) SAVE
        	//-----------------------------------------------------------------------------------------------------------------------------
    		AmuseLogger.write("WardAdapter", Level.DEBUG, "(5) Now saving.");
    		String outputPath = AmusePreferences.get(KeysStringValue.AMUSE_PATH) + File.separator + "experiments" + File.separator;
    		
    		DataSet amuseDataSet = new DataSet("WardResult_DataSet");
    		
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
    		((ClassificationConfiguration)(this.correspondingScheduler.getConfiguration())).setPartitionsAlreadySummerized(true);
            ((ClassificationConfiguration)(this.correspondingScheduler.getConfiguration())).setInputToClassify(new DataSetInput(amuseDataSet));
    		amuseDataSet.saveToArffFile(new File(outputPath + "Ward_Result.arff"));
    		
    		// Show the Dendogram in the AMUSE logger
    		dendo.showClusters();
    		// Save Dendogram print as .tex
    		dendo.printTikzDendogram(outputPath);
        } catch(Exception e) {
			throw new NodeException("Error classifying data with the WardAdapter: " + e.getMessage());
		}
	}
	
	
	//---------------------------------------------------------------------------------------------------------------------------------
	// Private help methods to calculate things so that the main method classify() stays nice
	//---------------------------------------------------------------------------------------------------------------------------------
	
	private double[][] calculateDissimilarityMatrix () {
		
		/** The dissimilarityMatrix stores the ESS values for the centroid of cluster m united with cluster n */
    	double[][] dissimilarityMatrix = new double[clusterAffiliation.size()][clusterAffiliation.size()];
    		
    	// m sind die Splaten und n die Zeilen
    	for (int m=0; m < clusterAffiliation.size(); m++) {
    		
    		// Damit der centroid nicht jedes Mal neu berechnet werden muss geschieht dies einmal hier 
    		double[] centroidM = this.calculateCentroid(clusterAffiliation.get(m));
    		
    		for (int n=0; n < clusterAffiliation.size(); n++) {
    			
    			// Falls es sich um dasselbe Cluster handelt, wird der Wert in der Matirx auf 0 gesetzt
        		if (n == m) {
        			dissimilarityMatrix[m][n] = 0.0;
        		} 
        		// Falls die MatrixDiagonale überschritten wird doppeln sich die Werte und werden aus Effizenzgründen nicht erneut berechnet
        		else if (n < m) {
        			dissimilarityMatrix[m][n] = dissimilarityMatrix[n][m];
        		} else {
        			
        			//AmuseLogger.write("WardAdapter", Level.DEBUG, "Statring to calculate the DISSIMILARITY for n=" +n+ " and m=" +m);
        			
        			double dissimilarity = 0.0;
        			double[] centroidN = this.calculateCentroid(clusterAffiliation.get(n));
    				
        			dissimilarity = this.calculateClassicDissimilarity(
            				centroidN, clusterAffiliation.get(n).size(), 
            				centroidM, clusterAffiliation.get(m).size());
        			
        			
        			if (dissimilarity == 0.0) {
        				AmuseLogger.write("WardAdapter", Level.WARN, "The dissimilarity wasn't calculated correctly!");
        			}
        			
        			dissimilarityMatrix[m][n] = dissimilarity;
        		}
        	}
    	}
    	
    	return dissimilarityMatrix;
	}
	
	/**
	 * Recursive method which updates a given dissimilarity Matrix according to the LWUF until only k (or 1) cluster is left
	 * @param dissimilarityMatrix
	 */
	private void updateDissimilarityMatrix (double[][] dissimilarityMatrix) {
		
		if ((k > 0 && k == clusterAffiliation.size()) || (k == 0 && clusterAffiliation.size() == 1)) {
			AmuseLogger.write("WardAdapter - LUWF", Level.DEBUG, "The LWUF recursion finished!");
		} else if ((k==0 || k==1) && clusterAffiliation.size() == 2) {
			AmuseLogger.write("WardAdapter - LWUF", Level.DEBUG, "There are only 2 clusters left, so they'll be merged!");
			
			// Merge clusters
			List<Integer> clusterToBeMergedA = clusterAffiliation.get(0);
			List<Integer> clusterToBeMergedB = clusterAffiliation.get(1);
			List<Integer> mergedCluster = new ArrayList<Integer>();
			mergedCluster.addAll(clusterToBeMergedA);
			mergedCluster.addAll(clusterToBeMergedB);
		
			try {
				dendo.setNewMerge(clusterToBeMergedA, clusterToBeMergedB, mergedCluster);
			} catch (Exception e) {
				AmuseLogger.write("WardAdapter - dissMatrix", Level.WARN, "The dendogram coudn't set a new merge: " + e.getMessage());
			}
		
			clusterAffiliation.remove(clusterToBeMergedA);
			clusterAffiliation.remove(clusterToBeMergedB);
			clusterAffiliation.add(mergedCluster);
			AmuseLogger.write("WardAdapter - LUWF", Level.DEBUG, "The LWUF recursion finished!");
		} else {
			
			// Get the minimum (in the first row) and the second minimum (in the second row) and the corresponding m and n values
			// dissimilarityMatrixMinValues[1][0] = true minM, dissimilarityMatrixMinValues[0][0] = true minN, dissimilarityMatrixMinValues[2][0] = true minValue
			double[][] dissimilarityMatrixMinValues = calculateMininimum (dissimilarityMatrix);
			
			// Calculate the centroids of the clusters to be merged
			double[] centroidM = this.calculateCentroid(clusterAffiliation.get((int) dissimilarityMatrixMinValues[1][0]));
			int sizeM = clusterAffiliation.get((int) dissimilarityMatrixMinValues[1][0]).size();
			double[] centroidN = this.calculateCentroid(clusterAffiliation.get((int) dissimilarityMatrixMinValues[0][0]));
			int sizeN = clusterAffiliation.get((int) dissimilarityMatrixMinValues[0][0]).size();

			int minIndex = 0;
			int maxIndex = 0;
			if (dissimilarityMatrixMinValues[1][0] < dissimilarityMatrixMinValues[0][0]) { // m<n
				minIndex = (int) dissimilarityMatrixMinValues[1][0];
				maxIndex = (int) dissimilarityMatrixMinValues[0][0];
			} else {  //m>n
				minIndex = (int) dissimilarityMatrixMinValues[0][0];
				maxIndex = (int) dissimilarityMatrixMinValues[1][0];
			}
			AmuseLogger.write("WardAdapter - LUWF", Level.DEBUG, "The current indizes are min = " + minIndex + " and max = " + maxIndex);
			AmuseLogger.write("WardAdapter - LWUF", Level.DEBUG, "Amount of clusters before merge: " + clusterAffiliation.size());
		
			// Merge clusters
			List<Integer> clusterToBeMergedA = clusterAffiliation.get((int) dissimilarityMatrixMinValues[1][0]);
			List<Integer> clusterToBeMergedB = clusterAffiliation.get((int) dissimilarityMatrixMinValues[0][0]);
			List<Integer> mergedCluster = new ArrayList<Integer>();
			mergedCluster.addAll(clusterToBeMergedA);
			mergedCluster.addAll(clusterToBeMergedB);
		
			try {
				dendo.setNewMerge(clusterToBeMergedA, clusterToBeMergedB, mergedCluster);
			} catch (Exception e) {
				AmuseLogger.write("WardAdapter - dissMatrix", Level.WARN, "The dendogram coudn't set a new merge: " + e.getMessage());
			}
		
			clusterAffiliation.remove(clusterToBeMergedA);
			clusterAffiliation.remove(clusterToBeMergedB);
			// Das Cluster wird an dem minIndex eingefügt
			clusterAffiliation.add(minIndex, mergedCluster); 
			AmuseLogger.write("WardAdapter - LWUF", Level.DEBUG, "The clusters were merged and added to postiton " + minIndex);
			AmuseLogger.write("WardAdapter - LWUF", Level.DEBUG, "Amount of clusters after merge: " + clusterAffiliation.size());
			
			// UPDATE THE MATRIX
			double[][] updatedMatrix = new double[clusterAffiliation.size()][clusterAffiliation.size()];
			
			// m sind die Splaten und n die Zeilen
	    	for (int m=0; m < clusterAffiliation.size(); m++) {
	    		for (int n=0; n < clusterAffiliation.size(); n++) {
	    			
	    			// Falls es sich um dasselbe Cluster handelt, wird der Wert in der Matirx auf 0 gesetzt
	        		if (n == m) {
	        			updatedMatrix[m][n] = 0.0;
	        		} 
	        		// Falls die MatrixDiagonale überschritten wird doppeln sich die Werte und werden aus Effizenzgründen nicht erneut berechnet
	        		else if (n < m) {
	        			updatedMatrix[m][n] = updatedMatrix[n][m];
	        		} else {
	        			
	        			// Ist eins der betrachteten Cluster das gemergede? Dann Updaten!
	        			if (m == minIndex || n == minIndex) {
	        				double[] centroidK;
	        				int sizeK;
	        				if (m == minIndex) {
	        					centroidK = this.calculateCentroid(clusterAffiliation.get(m));
	        					sizeK = clusterAffiliation.get(m).size();
	        				} else { 
	        					centroidK = this.calculateCentroid(clusterAffiliation.get(n));
	        					sizeK = clusterAffiliation.get(n).size();
	        				}
	        				
	        				updatedMatrix[m][n] = this.calculateLWDissimilarity(centroidM, sizeM, centroidN, sizeN, centroidK, sizeK);
	        				if (updatedMatrix[m][n] == 0.0) {
        						AmuseLogger.write("WardAdapter - LWDU", Level.WARN, "The updated dissimilarity wasn't calculated correctly!");
        					}
	        				AmuseLogger.write("WardAdapter - LWUF", Level.DEBUG, "Update at " +m+" and "+n+" to "+ updatedMatrix[m][n]);
	        			} 
	        			// Ansonsten den Wert aus der ursprünglichen matirx übernehmen!
	        			else {
	        				// Falls m/n größer als der maxIndex ist, dann muss wegen des Wegfallens dieses Clusters in der UpdateMatrix +1 in der Originalen beachtet werden
	        				if (m < maxIndex && n < maxIndex) {
	        					updatedMatrix[m][n] = dissimilarityMatrix[m][n];
	        				} else {
	        					int updateM = m;
	        					if (m >= maxIndex) { updateM = m+1; }
	        					int updateN = n;
	        					if (n >= maxIndex) { updateN = n+1; }
	        					
	        					updatedMatrix[m][n] = dissimilarityMatrix[updateM][updateN];
	        					//AmuseLogger.write("WardAdapter - LWUF", Level.DEBUG, "Copied value at " +m+" (updated to "+updateM+") and "+
	        					//		n+" (updated to "+updateN+") from "+ dissimilarityMatrix[updateM][updateN]);
	        				}
	        			}
	        			
	        		}
	    		}
			}
	    	
	    	String matrix = "";
	    	for (int n=0; n < updatedMatrix.length; n++) {
	    		matrix += n + ": ";
	    		for (int m=0; m < updatedMatrix.length; m++) {
	    			matrix += updatedMatrix[m][n] + ", ";
	    		}
	    		matrix += "\n";
	    	}
	    	AmuseLogger.write("WardAdapter - LWUF", Level.DEBUG, "The matrix was updated: \n" + matrix);
	    	
	    	this.updateDissimilarityMatrix(updatedMatrix);
		}
	}
	
	/** Calculates the minimum value and its corresponding indices of a given double matrix */
	private double[][] calculateMininimum (double[][] matrix) {
		
		double[][] minResult = new double[3][2];
		
		if (matrix[0][1] > matrix[0][2]) {
			minResult[1][0] = 0; //true minM
			minResult[0][0] = 2; //true minN
			minResult[2][0] = matrix[0][2]; //true minValue
			
			minResult[1][1] = 0; //second minM
			minResult[0][1] = 1; //second minN
			minResult[2][1] = matrix[0][1]; //second minValue
		} else {
			minResult[1][0] = 0; //true minM
			minResult[0][0] = 1; //true minN
			minResult[2][0] = matrix[0][1]; //true minValue
			
			minResult[1][1] = 0; //second minM
			minResult[0][1] = 2; //second minN
			minResult[2][1] = matrix[0][2]; //second minValue
		}
		
		
    	for (int m=0; m < matrix.length; m++) {
    		for (int n=0; n < matrix[0].length; n++) {
    			
    			if (n>m && 
    				matrix[n][m] > 0.0 && 
    				(matrix[n][m] < minResult[2][0] || matrix[n][m] < minResult[2][0])) {
    				
    				// If it's smaller that the true min: Set the second min = true min, and than the true min = current
    				if (matrix[n][m] < minResult[2][0]) {
    					minResult[1][1] = minResult[1][0];
        				minResult[0][1] = minResult[0][0];
        				minResult[2][1] = minResult[2][0];
        				
        				minResult[1][0] = m; //true minM
        				minResult[0][0] = n; //true minN
        				minResult[2][0] = matrix[n][m]; //true minValue
    				}
    				// Else current must be bigger than true min but smaller than second min, so set second min = current
    				else {
    					minResult[1][1] = m;
        				minResult[0][1] = n;
        				minResult[2][1] = minResult[n][m];
    				}
    				
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
	private double calculateClassicDissimilarity (double[] centroidA, double sizeA, double[] centroidB, double sizeB) {
		
		double cardinality = (sizeA * sizeB) / (sizeA + sizeB);
		double distance = this.squaredEuclideanDistance(centroidA, centroidB);
		double result = cardinality * distance;
		return result;
		
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
	private double calculateLWDissimilarity (double[] centroidA, double sizeA, double[] centroidB, double sizeB, double[] centroidC, double sizeC) {
		double alphaFirst = (sizeA + sizeC) / (sizeA + sizeB + sizeC);
		//AmuseLogger.write("WardAdapter - LWDU", Level.DEBUG, "Calculating alphaFirst as: (" + sizeA + " + " + sizeC + ") / (" + sizeA+" + "+sizeB+" + "+sizeC+")");
		double sedAF = this.squaredEuclideanDistance(centroidA, centroidC);
		//AmuseLogger.write("WardAdapter - LWDU", Level.DEBUG, "Calculating alphaFirst as: " + alphaFirst + " * " + sedAF);
		alphaFirst = alphaFirst * sedAF;
		
		double alphaSecond = (sizeB + sizeC) / (sizeA + sizeB + sizeC);
		//AmuseLogger.write("WardAdapter - LWDU", Level.DEBUG, "Calculating alphaSecond as: (" + sizeB + " + " + sizeC + ") / (" + sizeA+" + "+sizeB+" + "+sizeC+")");
		double sedAS = this.squaredEuclideanDistance(centroidB, centroidC);
		//AmuseLogger.write("WardAdapter - LWDU", Level.DEBUG, "Calculating alphaSecond as: " + alphaFirst + " * " + sedAS);
		alphaSecond = alphaSecond * sedAS;
		
		double beta = (sizeC) / (sizeA + sizeB + sizeC);
		//AmuseLogger.write("WardAdapter - LWDU", Level.DEBUG, "Calculating beta as: (" +sizeC+ ") / (" + sizeA+" + "+sizeB+" + "+sizeC+")");
		double sedB = this.squaredEuclideanDistance(centroidA, centroidB);
		//AmuseLogger.write("WardAdapter - LWDU", Level.DEBUG, "Calculating beta as: " + beta + " * " + sedB);
		beta = beta * sedB;
		
		//AmuseLogger.write("WardAdapter - LWDU", Level.DEBUG, "Updated dissimilarity will be: " + alphaFirst + " + " + alphaSecond + " - " + beta);
		return alphaFirst + alphaSecond - beta;
	}
	
	
	/**
	 * Calculates the distance between to centroids (double[])
	 * @param centroidA
	 * @param centroidB
	 * @return A double value containing the distance of the given centroids
	 */
	private double squaredEuclideanDistance (double[] centroidA, double[] centroidB) {
		
		//AmuseLogger.write("WardAdapter - squaredEuclideanDistance", Level.DEBUG, "Calculating for centroid length of " + centroidA.length);
		double distance = 0.0;
		
		// Using the squared euclidean distance as distance measure
		for(int x=0; x < centroidA.length; x ++) {
			double difference = centroidA[x] - centroidB[x];
			distance += difference * difference;
			//AmuseLogger.write("WardAdapter - squaredEuclideanDistance", Level.DEBUG, "Difference = " + distance);
		}
		
		if (distance == 0.0) {
			AmuseLogger.write("WardAdapter - squaredEuclideanDistance", Level.WARN, "The squaredEuclideanDistance wasn't calculated correctly!");
		}
		return distance;		
	}
		
	
	/** 
	 * Calculates the centroid for one Cluster
	 * @param cluster = The cluster (List<Integer>) which centroid should be calculated
	 * @return An array of doubles which contains for every feature (index) the value of the centroid 
	 */
	private double[] calculateCentroid (List<Integer> cluster) {
		
		double[] centroid = new double[allValues[0].length];
		
		// Durchlaufe alle Features, um in centroid[z] die Summe und später den Durchschnitt zu bilden
		for (int x=0; x < centroid.length; x++) {
			centroid[x] = 0;
			
			// Nimm von jedem Song im Cluster den Wert von Feature z und addiere ihn zum centroid[z] hinzu
			for (int i=0; i < cluster.size(); i++) {
				int currentSongID = (int) cluster.get(i);
				centroid[x] += allValues[currentSongID][x];
			}
			centroid[x] = centroid[x] / cluster.size();
		}
		
		return centroid;
	}
	
}
