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
 * Clusters given data by using Wards Agglomeration
 * @author Pauline Speckmann
 */
public class WardAdapter extends AmuseTask implements ClassifierUnsupervisedInterface {
	
	/** Defines the method to be used ("classic" (iterative) or "LWDissimilarityUpdateFormula" (recursive)) */
	private String method;
	/** Desired cluster number */
	private int k;
	/** If there's only one song, keep the partitions separated for music segmentation */
	private boolean keepPartitions = false;
	
	/** Contains the values for every song and their features */
	private double[][] allValues;
	/** Contains lists of integers where each represents a cluster with the contained integers representing the songs (partitions) belonging to that cluster */
	private List<List<Integer>> clusterAffiliation;
	/** A dendrogram representing the current clusters */
	private Dendrogram dendo;

	
	@Override
	/** Receives the parameters given by the user and sets them accordingly */
	public void setParameters(String parameterString) throws NodeException {
		// Should the default parameters be used or are values given?
        if(parameterString == "" || parameterString == null) {
        	method = new String("Classic");
        	// If no desired cluster number is specified the whole dendrogram will be created
        	k = 0;
        } else {
        	StringTokenizer tok = new StringTokenizer(parameterString, "_");
        	method = tok.nextToken();
        	k = new Integer(tok.nextToken());
        }
        
        // Check if all parameters are in range
        if (k < 0 || (!method.equals("Classic") && !method.equals("LWDissimilarityUpdateFormula"))) {
        	throw new NodeException("Ward: One of the parameters was out of range!");
        }
    }

	@Override
    public void initialize() throws NodeException {
        // Nothing to do here
    }
    
	
    @Override
    /**  */
	public void classify() throws NodeException {
		
		try {
        	//------------------------------------------------------------------------------------------------------------------------------------------------------
        	// (1) Get ArrayList<SongPartitionsDescription> descriptionOfClassifierInput to get information about the number of songs.
        	//------------------------------------------------------------------------------------------------------------------------------------------------------
			
			/** DataSet of music (partitions) to be classified */
	        DataSet dataSetToClassify = ((DataSetInput)((ClassificationConfiguration)this.correspondingScheduler.getConfiguration()).getInputToClassify()).getDataSet();
	        
	        // The descriptionOfClassifierInput holds information about the number of songs and their partitions
	        ArrayList<SongPartitionsDescription> descriptionOfClassifierInput =  ((ClassifierNodeScheduler)this.correspondingScheduler).getDescriptionOfClassifierInput();
        	
        	int numberOfSongs = descriptionOfClassifierInput.size();
        	// If there is only one song the partitions should be kept for music segmentation
        	if (numberOfSongs == 1 ) { keepPartitions = true; }
        	// If not the partitions will be summarized
        	else {((ClassificationConfiguration)(this.correspondingScheduler.getConfiguration())).setPartitionsAlreadySummerized(true);}
        	int numberOfFeatures = dataSetToClassify.getAttributeCount();
        	//AmuseLogger.write("WardAdapter - initializing", Level.DEBUG, "There are " + numberOfFeatures + " to be saved.");
        	
        	// Check the range of parameter k again
        	if (!keepPartitions && k >= numberOfSongs) {
        		throw new NodeException("WardAdapter - classify(): Your given k wasn't in range. "
        				+ "Try something bigger than 0 and smaller than your number of songs.");
        	} else if (keepPartitions && k >= descriptionOfClassifierInput.get(0).getStartMs().length) {
        		throw new NodeException("WardAdapter - classify(): Your given k wasn't in range. "
        				+ "Try something bigger than 0 and smaller than your number of song partitions.");
        	}
        	
        	
        	//------------------------------------------------------------------------------------------------------------------------------------------------------
        	// (2) Fill songIdsAnNames and allValues. Sort all songs / partitions into their own cluster via clusterAffiliation.
        	//------------------------------------------------------------------------------------------------------------------------------------------------------
        	
        	List<idAndName> songIdsAndNames = new ArrayList<idAndName>();
        	
        	// If the partitions should be kept each partition will be treated as if it were a whole own song
        	if (keepPartitions) {
        		int numberOfPartitions = descriptionOfClassifierInput.get(0).getStartMs().length;
        		allValues = new double[numberOfPartitions][numberOfFeatures];
        		
        		for (int partitionNumber=0; partitionNumber<numberOfPartitions; partitionNumber++) {
        			idAndName current = new idAndName(partitionNumber, "Partition "+partitionNumber);
        			songIdsAndNames.add(current);
        			
        			for (int featureNumber=0; featureNumber < numberOfFeatures; featureNumber++) {
    					allValues[partitionNumber][featureNumber] = allValues[partitionNumber][featureNumber] + 
    							(double) dataSetToClassify.getAttribute(featureNumber).getValueAt(partitionNumber);
    				}
        		}
        		
        	} 
        	// Otherwise all partitions belonging to a song will be summarized
        	else {
        		/** allValues contains for each song every feature value based on descriptionOfClassifierInput - [songID][featureValuesOfOneSong] */
        		allValues = new double[numberOfSongs][numberOfFeatures];
        		
        		// Calculate the mean (TODO or median) of all partition values belonging to a song
        		int partitionsAlreadySeen = 0;
        		for (int songNumber=0; songNumber < numberOfSongs; songNumber++) {
        			
        			int numberOfPartitionsForSongI = descriptionOfClassifierInput.get(songNumber).getStartMs().length;
        			// Save the song path with it's id
        			idAndName current = new idAndName(songNumber, descriptionOfClassifierInput.get(songNumber).getPathToMusicSong());
        			songIdsAndNames.add(current);
        			
        			// For all partitions belonging to song number <songNumber> 
        			for (int partitionNumber = partitionsAlreadySeen; partitionNumber < partitionsAlreadySeen+numberOfPartitionsForSongI; partitionNumber++) {
        				for (int featureNumber=0; featureNumber < numberOfFeatures; featureNumber++) {
        					allValues[songNumber][featureNumber] = allValues[songNumber][featureNumber] + 
        							(double) dataSetToClassify.getAttribute(featureNumber).getValueAt(partitionNumber);
        				}
        			}
        			for (int featureNumber=0; featureNumber < numberOfFeatures; featureNumber++) {
    					allValues[songNumber][featureNumber] = allValues[songNumber][featureNumber] / numberOfPartitionsForSongI;
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
        	
        	
        	//------------------------------------------------------------------------------------------------------------------------------------------------------
        	// (3) Start the merging process with the chosen method.
        	//------------------------------------------------------------------------------------------------------------------------------------------------------
        	
        	if (method.equals("Classic")) {
        		
        		// Set the number of clusters where until it's been reached they'll be merged
            	int mergeUntilThisClusterNumber;
            	if (k == 0 || k == 1) { mergeUntilThisClusterNumber = 2; } 
            	else { mergeUntilThisClusterNumber = k; }
            	
            	AmuseLogger.write("WardAdapter", Level.DEBUG, "(4) The merge until this cluster number parameter is " +mergeUntilThisClusterNumber+ " - "
            			+ "Starting the while loop with: while (" +clusterAffiliation.size()+ " > " +mergeUntilThisClusterNumber+ ");");
        		
        		while (clusterAffiliation.size() > mergeUntilThisClusterNumber) {       
        			
        			//-----------------------------------------------------------------------------------------------------------------------------------------
        			// (3.1) Calculate the dissimilarity matrix, find the true and second minimum and merge the clusters with the true minimum dissimilarity.
        			//-----------------------------------------------------------------------------------------------------------------------------------------
        		
        			/** The dissimilarityMatrix stores the ESS values for the centroid of cluster m united with cluster n */
        			double[][] dissimilarityMatrix = calculateDissimilarityMatrix();
            	
        			// Get the minimum (in the first row) and the second minimum (in the second row) and the corresponding m and n values
        			double[][] dissimilarityMatrixMinValues = calculateMininimum (dissimilarityMatrix);
            	
        			// Merge clusters
        			List<Integer> clusterToBeMergedA = clusterAffiliation.get((int) dissimilarityMatrixMinValues[1][0]);
        			List<Integer> clusterToBeMergedB = clusterAffiliation.get((int) dissimilarityMatrixMinValues[0][0]);
        			List<Integer> mergedCluster = this.setMerge(clusterToBeMergedA, clusterToBeMergedB);
            	
        			//-----------------------------------------------------------------------------------------------------------------------------------------
        			// (3.2) For the most similar pair of clusters (which have just been merged):
        			//       - Look at the remaining n-1 clusters by building a new matrix (actually an array) just for the merges between the mergesCluster
        			//         and the remaining ones
        			//	   	 - If the minimum of this new small matrix (array) is smaller than the second minimum value of the original matrix:
        			//         Merge this third cluster with the mergedCluster to get the minimal dissimilarity increase for n-2 clusters
        			//		   Otherwise skip this and begin a new iteration to merge a new pair of clusters
        			//-----------------------------------------------------------------------------------------------------------------------------------------
            	
        			// If the desired cluster number hasn't been reached yet
        			if (mergeUntilThisClusterNumber < clusterAffiliation.size()) {
            		
        				// The last cluster will not be evaluated since it it the same as the just merged cluster
        				/** The dissimilarityMatrixTwo stores the ESS values for the centroid of cluster m united with cluster n */
        				double[] dissimilarityBetweenMergedClusterAndTheOthers = new double[clusterAffiliation.size() -1];
        				double[] centroidMergedCluster = this.calculateCentroid(mergedCluster);
                	
        				// Fill the new dissimilarity matrix (array)
        				for (int columnNumber=0; columnNumber < dissimilarityBetweenMergedClusterAndTheOthers.length; columnNumber++) {
                			
        					double dissimilarity = 0.0;
        					double[] centroidColumn = this.calculateCentroid(clusterAffiliation.get(columnNumber));
                				
        					dissimilarity = this.calculateWardsCriterion(
        							centroidMergedCluster, mergedCluster.size(), 
        							centroidColumn, clusterAffiliation.get(columnNumber).size());
                    	
        					if (dissimilarity == 0.0) {
        						AmuseLogger.write("WardAdapter - dissArray", Level.WARN, "The dissimilarity wasn't calculated correctly!");
        					}
        					dissimilarityBetweenMergedClusterAndTheOthers[columnNumber] = dissimilarity;
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
        					this.setMerge(mergedCluster, clusterToBeMergedC);
        				}

        			}
            	
        			//-----------------------------------------------------------------------------------------------------------------------------------------
        			// (3.3) If only two clusters are left and a big cluster is wished for: Merge them now without calculations
        			//-----------------------------------------------------------------------------------------------------------------------------------------
        			if (clusterAffiliation.size() == 2 && (k == 0 || k == 1)) {
            		
        				List<Integer> firstCluster = clusterAffiliation.get(0);
        				List<Integer> secondCluster = clusterAffiliation.get(1);
        				this.setMerge(firstCluster, secondCluster);
            		
        			} else if (clusterAffiliation.size() < 1) {
        				throw new NodeException("WardAdapter - classify(): Somehow there aren't ANY clusters. What did you do?");
        			}
        		}
        	}
        	// If the LWDUF method was chosen: 
        	// The method works recursive so here the recursive help method is called here after the initial dissimilarity matrix was calculated
        	else if (method.equals("LWDissimilarityUpdateFormula")) {
        		
        		/** The dissimilarityMatrix stores the ESS values for the centroid of cluster m united with cluster n */
    			double[][] dissimilarityMatrix = calculateDissimilarityMatrix();
        	
    			//String matrix = "";
    			//for (int n=0; n < dissimilarityMatrix.length; n++) {
    			//	matrix += n + ": ";
    			//	for (int m=0; m < dissimilarityMatrix.length; m++) {
    			//		matrix += dissimilarityMatrix[m][n] + ", ";
    			//	}
    			//	matrix += "\n";
    			//}
    			//AmuseLogger.write("WardAdapter - LWUF", Level.DEBUG, "Starting the LWUF recursion with: \n" + matrix);
    	    	
    			this.updateDissimilarityMatrix(dissimilarityMatrix);
        	}
            
    		
    		//-----------------------------------------------------------------------------------------------------------------------------
        	// (4) SAVE
        	//-----------------------------------------------------------------------------------------------------------------------------
    		AmuseLogger.write("WardAdapter", Level.DEBUG, "(4) Now saving.");
    		String outputPath = AmusePreferences.get(KeysStringValue.AMUSE_PATH) + File.separator + "experiments" + File.separator;
    		
    		// Create a DateSet for the results
    		DataSet amuseDataSet = new DataSet("WardResult_DataSet");
    		
    		// Create the feature attributes, fill them according to the values of allValues and add them to the result DataSet
    		for (int featureNumber=0; featureNumber < numberOfFeatures; featureNumber++ ) {
    			List<Double> featureXList = new ArrayList<Double>();
    			
    			// allValues.length is used to determine the song number, so in case of music segmentation the partition number is used instead
    			for (int songNumber=0; songNumber < allValues.length; songNumber++) {
    				featureXList.add(allValues[songNumber][featureNumber]);
    			}
    			
    			Attribute featureX = new NumericAttribute( dataSetToClassify.getAttribute(featureNumber).getName() , featureXList);
    			amuseDataSet.addAttribute(featureX);
    		}
    		
    		// Create cluster attributes, fill them accordingly and add them to the result DataSet
    		for (int clusterNumber=0; clusterNumber < clusterAffiliation.size(); clusterNumber++ ) {
    			List<Double> clusterCList = new ArrayList<Double>();
    			
    			for (int songNumber=0; songNumber < allValues.length; songNumber++) {
    				
    				if (clusterAffiliation.get(clusterNumber).contains(songNumber)) {
    					clusterCList.add(songNumber, 1.0);
    				} else {
    					clusterCList.add(songNumber, 0.0);
    				}
    			}
    			
    			Attribute clusterX = new NumericAttribute("cluster_" + clusterNumber, clusterCList);
    			amuseDataSet.addAttribute(clusterX);
    		}
    		
    		Testing.printMinMax(amuseDataSet);
    		
    		// Give the amuseDataSet to the ClassificationConfiguration so it may be put together and saved there
        	// The ClassifierNodeScheduler proceedTask(...) returns or saves an ArrayList<ClassifiedSongPartitionsDescription>
            ((ClassificationConfiguration)(this.correspondingScheduler.getConfiguration())).setInputToClassify(new DataSetInput(amuseDataSet));
    		//amuseDataSet.saveToArffFile(new File(outputPath + "Ward_Result.arff"));
    		
    		// Show the Dendogram in the AMUSE logger
    		//dendo.showClusters();
            
    		// Save Dendogram print as .tex
    		dendo.printTikzDendrogram(outputPath);
        } catch(Exception e) {
			throw new NodeException("Error classifying data with the WardAdapter: " + e.getMessage());
		}
	}
	
	
	//--------------------------------------------------------------------------------------------------------------------------------------------------------------
	// Private help methods to calculate things so that the main method classify() stays nice
	//--------------------------------------------------------------------------------------------------------------------------------------------------------------
	
    /**
     * Calculates the dissimilarity matrix based on the current clusters (clusterAffiliation is global)
     * @return The finished dissimilarity matrix
     */
	private double[][] calculateDissimilarityMatrix () {
		
		/** The dissimilarityMatrix stores the ESS values for the centroid of cluster m united with cluster n */
    	double[][] dissimilarityMatrix = new double[clusterAffiliation.size()][clusterAffiliation.size()];
    		
    	// Fill the matrix by going through the columns and for each column through the rows
    	for (int columnNumber=0; columnNumber < clusterAffiliation.size(); columnNumber++) {
    		
    		// Calculate the centroid of the cluster represented by this column
    		double[] centroidColumn = this.calculateCentroid(clusterAffiliation.get(columnNumber));
    		
    		for (int rowNumber=0; rowNumber < clusterAffiliation.size(); rowNumber++) {
    			
    			// If the columnNumber is the same as the rowNumber (so it is the same cluster): Set the value to zero
        		if (rowNumber == columnNumber) {
        			dissimilarityMatrix[columnNumber][rowNumber] = 0.0;
        		} 
        		// If we're to the right of the diagonal of the matrix, there is no need to calculate the value as it already has been calculated 
        		// on the matrix field to the left of the diagonal (with row and column number switched)
        		else if (rowNumber < columnNumber) {
        			dissimilarityMatrix[columnNumber][rowNumber] = dissimilarityMatrix[rowNumber][columnNumber];
        		} 
        		// Else calculate the dissimilarity
        		else {
        			
        			double dissimilarity = 0.0;
        			double[] centroidRow = this.calculateCentroid(clusterAffiliation.get(rowNumber));
    				
        			dissimilarity = this.calculateWardsCriterion(
            				centroidRow, clusterAffiliation.get(rowNumber).size(), 
            				centroidColumn, clusterAffiliation.get(columnNumber).size());
        			
        			
        			if (dissimilarity == 0.0) {
        				AmuseLogger.write("WardAdapter", Level.WARN, "The dissimilarity wasn't calculated correctly!");
        			}
        			
        			// Set the dissimilarity!
        			dissimilarityMatrix[columnNumber][rowNumber] = dissimilarity;
        		}
        	}
    	}
    	
    	return dissimilarityMatrix;
	}
	
	
	/**
	 * Given two clusters a new merged will be created, the dendrogram will set the merge accordingly and 
	 * the old clusters will be removed from clusterAffiliation while the merged one is added
	 * @param clusterA - Cluster A to be merged with cluster B
	 * @param clusterB - Cluster B to be merged with cluster A
	 * @return The merged cluster in form of a List<Integer>
	 */
	private List<Integer> setMerge (List<Integer> clusterA, List<Integer> clusterB) {
		List<Integer> mergedCluster = new ArrayList<Integer>();
		mergedCluster.addAll(clusterA);
		mergedCluster.addAll(clusterB);
	
		try {
			dendo.setNewMerge(clusterA, clusterB, mergedCluster);
		} catch (Exception e) {
			AmuseLogger.write("WardAdapter", Level.WARN, "The dendogram coudn't set a new merge: " + e.getMessage());
		}
	
		clusterAffiliation.add(mergedCluster);
		clusterAffiliation.remove(clusterA);
		clusterAffiliation.remove(clusterB);
		return mergedCluster;
	}
	
	
	/**
	 * Recursive method which updates a given dissimilarity Matrix according to the LWUF until only k (or 1) cluster is left
	 * @param dissimilarityMatrix = The initial dissimilarity matrix calculated by the wards criterion
	 */
	private void updateDissimilarityMatrix (double[][] dissimilarityMatrix) {
		
		// Break condition for the recursion
		if ((k > 0 && k == clusterAffiliation.size()) || (k == 0 && clusterAffiliation.size() == 1)) {
			AmuseLogger.write("WardAdapter - LUWF", Level.DEBUG, "The LWDUF recursion finished!");
		} 
		// The last two clusters (if one big cluster is wished for) can be merged without any calculations
		else if ((k==0 || k==1) && clusterAffiliation.size() == 2) {
			AmuseLogger.write("WardAdapter - LWUF", Level.DEBUG, "There are only 2 clusters left, so they'll be merged!");
			
			// Merge clusters
			List<Integer> clusterToBeMergedA = clusterAffiliation.get(0);
			List<Integer> clusterToBeMergedB = clusterAffiliation.get(1);
			this.setMerge(clusterToBeMergedA, clusterToBeMergedB);
			AmuseLogger.write("WardAdapter - LUWF", Level.DEBUG, "The LWDUF recursion finished!");
		} 
		// Recursive call to update the dissimilarity matrix
		else {
			
			// Get the minimum (in the first row) and the second minimum (in the second row) and the corresponding m and n values
			// dissimilarityMatrixMinValues[1][0] = true minM, dissimilarityMatrixMinValues[0][0] = true minN, dissimilarityMatrixMinValues[2][0] = true minValue
			double[][] dissimilarityMatrixMinValues = calculateMininimum (dissimilarityMatrix);
			
			// Calculate the centroids of the clusters to be merged
			double[] centroidA = this.calculateCentroid(clusterAffiliation.get((int) dissimilarityMatrixMinValues[1][0]));
			int sizeA = clusterAffiliation.get((int) dissimilarityMatrixMinValues[1][0]).size();
			double[] centroidB = this.calculateCentroid(clusterAffiliation.get((int) dissimilarityMatrixMinValues[0][0]));
			int sizeB = clusterAffiliation.get((int) dissimilarityMatrixMinValues[0][0]).size();

			// Find out the indices of the clusters to be merged
			int minIndex = 0;
			int maxIndex = 0;
			if (dissimilarityMatrixMinValues[1][0] < dissimilarityMatrixMinValues[0][0]) { // m<n
				minIndex = (int) dissimilarityMatrixMinValues[1][0];
				maxIndex = (int) dissimilarityMatrixMinValues[0][0];
			} else {  //m>n
				minIndex = (int) dissimilarityMatrixMinValues[0][0];
				maxIndex = (int) dissimilarityMatrixMinValues[1][0];
			}
			//AmuseLogger.write("WardAdapter - LUWF", Level.DEBUG, "The current indices are min = " + minIndex + " and max = " + maxIndex);
		
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
			// Merge into the cluster with the smaller index
			clusterAffiliation.add(minIndex, mergedCluster); 
			//AmuseLogger.write("WardAdapter - LWUF", Level.DEBUG, "The clusters were merged and added to position " + minIndex);
			
			
			// UPDATE THE MATRIX -----------------------------------------------------------------------------------------------------------------------------------
			double[][] updatedMatrix = new double[clusterAffiliation.size()][clusterAffiliation.size()];
			
			for (int columnNumber=0; columnNumber < clusterAffiliation.size(); columnNumber++) {
	    		for (int rowNumber=0; rowNumber < clusterAffiliation.size(); rowNumber++) {
	    			
	    			// If the columnNumber is the same as the rowNumber (so it is the same cluster): Set the value to zero
	    			if (rowNumber == columnNumber) {
	        			updatedMatrix[columnNumber][rowNumber] = 0.0;
	        		} 
	    			// If we're to the right of the diagonal of the matrix, there is no need to calculate the value as it already has been calculated 
	        		// on the matrix field to the left of the diagonal (with row and column number switched)
	        		else if (rowNumber < columnNumber) {
	        			updatedMatrix[columnNumber][rowNumber] = updatedMatrix[rowNumber][columnNumber];
	        		} 
	        		// Else get the new dissimilarity
	    			else {
	        			
	        			// If one of the current clusters (column or row) is one of the two merged ones the matrix field needs to be updated
	        			if (columnNumber == minIndex || rowNumber == minIndex) {
	        				double[] centroidClusterC;
	        				int sizeClusterC;
	        				// If the column is the merged cluster, get the other one (row) as cluster C! Or otherwise the other way around.
	        				if (columnNumber == minIndex) {
	        					centroidClusterC = this.calculateCentroid(clusterAffiliation.get(rowNumber));
	        					sizeClusterC = clusterAffiliation.get(rowNumber).size();
	        				} else { 
	        					centroidClusterC = this.calculateCentroid(clusterAffiliation.get(columnNumber));
	        					sizeClusterC = clusterAffiliation.get(columnNumber).size();
	        				}
	        				
	        				// Update the matrix field
	        				updatedMatrix[columnNumber][rowNumber] = this.calculateLWDissimilarity(centroidA, sizeA, centroidB, sizeB, centroidClusterC, sizeClusterC);
	        				if (updatedMatrix[columnNumber][rowNumber] == 0.0) {
        						AmuseLogger.write("WardAdapter - LWDU", Level.WARN, "The updated dissimilarity wasn't calculated correctly!");
        					}
	        				AmuseLogger.write("WardAdapter - LWUF", Level.DEBUG, "Update at " +columnNumber+" and "+rowNumber+" to "+ updatedMatrix[columnNumber][rowNumber]);
	        			} 
	        			// Otherwise copy the dissimilarity value from the old matrix
	        			else {
	        				// If the column or row number is higher than the maxIndex we need to add 1 to the column/row index due to the reduction of the matrix by one cluster
	        				if (columnNumber < maxIndex && rowNumber < maxIndex) {
	        					updatedMatrix[columnNumber][rowNumber] = dissimilarityMatrix[columnNumber][rowNumber];
	        				} else {
	        					int updateColumnIndex = columnNumber;
	        					if (columnNumber >= maxIndex) { updateColumnIndex = columnNumber+1; }
	        					int updateRowIndex = rowNumber;
	        					if (rowNumber >= maxIndex) { updateRowIndex = rowNumber+1; }
	        					
	        					updatedMatrix[columnNumber][rowNumber] = dissimilarityMatrix[updateColumnIndex][updateRowIndex];
	        					//AmuseLogger.write("WardAdapter - LWUF", Level.DEBUG, "Copied value at " +m+" (updated to "+updateM+") and "+
	        					//		n+" (updated to "+updateN+") from "+ dissimilarityMatrix[updateM][updateN]);
	        				}
	        			}
	        			
	        		}
	    		}
			}
	    	
	    	//String matrix = "";
	    	//for (int n=0; n < updatedMatrix.length; n++) {
	    	//	matrix += n + ": ";
	    	//	for (int m=0; m < updatedMatrix.length; m++) {
	    	//		matrix += updatedMatrix[m][n] + ", ";
	    	//	}
	    	//	matrix += "\n";
	    	//}
	    	//AmuseLogger.write("WardAdapter - LWUF", Level.DEBUG, "The matrix was updated: \n" + matrix);
	    	
	    	this.updateDissimilarityMatrix(updatedMatrix);
		}
	}
	
	/** Calculates the minimum value and its corresponding indices of a given double matrix */
	private double[][] calculateMininimum (double[][] matrix) {
		
		double[][] minResult = new double[3][2];
		
		if (matrix[0][1] > matrix[0][2]) {
			minResult[1][0] = 0; //true minM (column)
			minResult[0][0] = 2; //true minN (row)
			minResult[2][0] = matrix[0][2]; //true minValue
			
			minResult[1][1] = 0; //second minM (column)
			minResult[0][1] = 1; //second minN (row)
			minResult[2][1] = matrix[0][1]; //second minValue
		} else {
			minResult[1][0] = 0; //true minM (column)
			minResult[0][0] = 1; //true minN (row)
			minResult[2][0] = matrix[0][1]; //true minValue
			
			minResult[1][1] = 0; //second minM (column)
			minResult[0][1] = 2; //second minN (row)
			minResult[2][1] = matrix[0][2]; //second minValue
		}
		
		
    	for (int columnNumber=0; columnNumber < matrix.length; columnNumber++) {
    		for (int rowNumber=0; rowNumber < matrix[0].length; rowNumber++) {
    			
    			if (rowNumber>columnNumber && 
    				matrix[rowNumber][columnNumber] > 0.0 && 
    				(matrix[rowNumber][columnNumber] < minResult[2][0] || matrix[rowNumber][columnNumber] < minResult[2][0])) {
    				
    				// If it's smaller that the true min: Set the second min = true min, and than the true min = current
    				if (matrix[rowNumber][columnNumber] < minResult[2][0]) {
    					minResult[1][1] = minResult[1][0];
        				minResult[0][1] = minResult[0][0];
        				minResult[2][1] = minResult[2][0];
        				
        				minResult[1][0] = columnNumber; //true minM
        				minResult[0][0] = rowNumber; //true minN
        				minResult[2][0] = matrix[rowNumber][columnNumber]; //true minValue
    				}
    				// Else current must be bigger than true min but smaller than second min, so set second min = current
    				else {
    					minResult[1][1] = columnNumber;
        				minResult[0][1] = rowNumber;
        				minResult[2][1] = minResult[rowNumber][columnNumber];
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
	private double calculateWardsCriterion (double[] centroidA, double sizeA, double[] centroidB, double sizeB) {
		
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
		// Squared Euclidean Distance of Alpha First
		double sedAF = this.squaredEuclideanDistance(centroidA, centroidC);
		alphaFirst = alphaFirst * sedAF;
		
		double alphaSecond = (sizeB + sizeC) / (sizeA + sizeB + sizeC);
		// Squared Euclidean Distance of Alpha Second
		double sedAS = this.squaredEuclideanDistance(centroidB, centroidC);
		alphaSecond = alphaSecond * sedAS;
		
		double beta = (sizeC) / (sizeA + sizeB + sizeC);
		// Squared Euclidean Distance of Beta
		double sedB = this.squaredEuclideanDistance(centroidA, centroidB);
		beta = beta * sedB;
		
		double result = alphaFirst + alphaSecond - beta;
		AmuseLogger.write("WardAdapter - LWDU", Level.DEBUG, "Updated dissimilarity will be: " +alphaFirst+" + "+alphaSecond+" - "+beta +" = "+result);
		return result;
	}
	
	
	/**
	 * Calculates the distance between to centroids (double[])
	 * @param centroidA
	 * @param centroidB
	 * @return A double value containing the distance of the given centroids
	 */
	private double squaredEuclideanDistance (double[] centroidA, double[] centroidB) {
		
		double distance = 0.0;
		
		// Using the squared euclidean distance as distance measure
		for(int featureNumber=0; featureNumber < centroidA.length; featureNumber ++) {
			double difference = centroidA[featureNumber] - centroidB[featureNumber];
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
