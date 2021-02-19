package amuse.nodes.classifier.methods.unsupervised;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.log4j.Level;

import amuse.interfaces.nodes.NodeException;
import amuse.nodes.classifier.methods.unsupervised.WardAdapter.idAndName;
import amuse.util.AmuseLogger;

public class Dendogram {
	
	List<Node> clusters;
	
	/**
	 * Creates a Dendogram (Tree like structure) for hierarchical clustering
	 * @param input = A List of Lists<Integer> which each contain the SongIDs belonging to a cluster
	 */
	public Dendogram (List<List<Integer>> clusterInput, List<idAndName> songIdsAndNames) {
		
		clusters = new ArrayList<Node>();
		
		for (int c=0; c < clusterInput.size(); c++) {
			Node current = new Node(""+c, songIdsAndNames.get(c).getName(), clusterInput.get(c), null, null);
			clusters.add(c, current);
		}
	}
	
	
	/**
	 * Given two clusters in form of List<Integer> it merges them into one and sets the new cluster as parent to the two given ones
	 * @param inputClusterA = First of the input Clusters to be merged
	 * @param inputClusterB = Second of the input Clusters to be merged
	 * @throws NodeException Is thrown when the given clusters A and B can't be found in the Dendograms cluster List
	 */
	public void setNewMerge (List<Integer> inputClusterA, List<Integer> inputClusterB, List<Integer> inputMergedCluster) throws NodeException {
		
		if (inputClusterA.equals(null) || inputClusterB.equals(null) || inputMergedCluster.equals(null)) {
			throw new NodeException ("Dendogram - setNewMerge(): At least one of the given input cluster lists was null.");
		}
		
		// Search the input clusters in the own List clusters
		Node clusterA = null, clusterB = null;
		for (int c=0; c < clusters.size(); c++) {
			if (clusters.get(c).getValue().equals(inputClusterA)) {
				clusterA = clusters.get(c);
			} else if (clusters.get(c).getValue().equals(inputClusterB)) {
				clusterB = clusters.get(c);
			}
		}
		
		// Check if the input clusters couldn't be found in the own List clusters
		if (clusterA.equals(null) || clusterB.equals(null)) {
			throw new NodeException ("Dendogram - setNewMerge(): Couldn't find the clusters to merge in the dendograms own cluster list.");
		} else {
			
			List<Integer> parentValue = new ArrayList<Integer>();
			parentValue.addAll(inputMergedCluster);
			
			String parentID = clusterA.getID() + "+" + clusterB.getID() +";";
			String parentName = clusterA.getName() + "\n" + clusterB.getName();
			Node parent = new Node(parentID, parentName, parentValue, clusterA, clusterB);
			clusterA.setParent(parent);
			clusterB.setParent(parent);
			
			clusters.add(parent);
			clusters.remove(clusterA);
			clusters.remove(clusterB);
			
			AmuseLogger.write("DENDOGRAM", Level.DEBUG, "The clusters" + this.integerListToString(clusterA.getValue()) +
					" and" +this.integerListToString(clusterB.getValue()) + " have been merged into" + this.integerListToString(parentValue));
		}
	}
	
	
	/** @return A List of Nodes which each contain a cluster */
	public List<Node> getClusters () {return this.clusters;}
	
	/** Shows the Dendogram in the AMUSE Debug Console */
	public void showClusters() {
		AmuseLogger.write("DENDOGRAM", Level.DEBUG, "BEGINNING TO SHOW DENDOGRAM");
		
		String result = this.printClusters();
		AmuseLogger.write("DENDOGRAM", Level.DEBUG, "Showing the dendogram for "+ clusters.size() +" different clusters: \n" +result);
		
		AmuseLogger.write("DENDOGRAM", Level.DEBUG, "FINISHED TO SHOW DENDOGRAM");
	}
	
	
	/** @return The Dendogram as String */
	public String printClusters() {
		
		String result = "";
		
		if (clusters.size() > 0) {
			
			// ----------------------------------------------------------------------------------------------------------------------------------------
			// Get the dendogram of all clusters
			// ----------------------------------------------------------------------------------------------------------------------------------------
			
			// Get the max dendogram hight
			int maxHightOfAllClusters = 0;
			for (int c=0; c < clusters.size(); c++) {
				maxHightOfAllClusters = Math.max(maxHightOfAllClusters, this.findHeight(clusters.get(c)));
			}
			
			// Create a dendogram array for all clusters
			String[][] dendogramMatrix = new String[maxHightOfAllClusters][clusters.size() + (clusters.size() -1)];
			int currentRow = 0;
			
			// Get the dendograms for every cluster and fill them into the big one
			for (int c=0; c < clusters.size(); c++) {
				Node current = clusters.get(c);
				
				// Get the cluster specific dendogram array
				int maxHightofThisCluster = this.findHeight(current);
				String[][] outputStrings = new String[maxHightofThisCluster][current.getValue().size()];
				this.fillOutputArray(outputStrings, maxHightofThisCluster, current.getValue().size(), current);
				
				// Transfer the cluster dendogram array into the big one
				for (int s=0; s < outputStrings.length; s++) {
					for (int z=0; z < outputStrings[0].length; z ++) {
						dendogramMatrix[s][z + currentRow] = outputStrings[s][z];
					}
				}
				
				// Set the new row to start for the next cluster but with an empty row so it looks more structured
				currentRow += outputStrings[0].length +1;
			}
			// Get the dendogram array as string and add it to the result string
			this.makeMatrixPretty(dendogramMatrix);
			result += this.getMatrixAsString(dendogramMatrix) + "\n \n";
			
			// Get the ids and song names of all leafs at the end
			result += "The song ids in the dendogram correspond to: \n \n";
			for (int c=0; c < clusters.size(); c++) {
				Node current = clusters.get(c);
				result += this.showNamesOfClusterSongs(current);
			}
		}
		
		return result;
	}
	
	
	private void fillOutputArray (String[][] output, int column, int row, Node currentNode) {
		
		if (currentNode != null && output != null) {
			
			// Fill corresponding table entry for this node
			output[column][row] = this.integerListToString(currentNode.getValue());
			
			if (currentNode.getLeft() != null && currentNode.getRight() != null) {
				
				// Tell the left child that it should fill out its table entry
				int rowsToLeaveFree = currentNode.getValue().size() - currentNode.getRight().getValue().size();
				this.fillOutputArray(output, column-1, row - rowsToLeaveFree, currentNode.getLeft());
			
				// Tell the right child that it should fill out its table entry
				this.fillOutputArray(output, column-1, row, currentNode.getRight());
			}
		}
	}
	
	
	private String getMatrixAsString (String[][] matrix) {
		String result = "";
		
		// TODO
		
		return result;
	}
	
	
	private void makeMatrixPretty (String[][] matrix) throws IndexOutOfBoundsException {
		
		// Go trough each column
		for (int s=0; s < matrix.length; s++) {
			
			// Get the maximal char count in this column
			int maxLengthOfThisColumn = 0;
			for (int z=0; z < matrix[0].length; z++) {
				if (matrix[s][z] != null || !matrix[s][z].equals("")) {
					if (matrix[s][z].length() > maxLengthOfThisColumn) {
						maxLengthOfThisColumn = matrix[s][z].length();
					}
				}
			}
			
			// Adjust every entry of this column
			for (int z=0; z < matrix[0].length; z++) {
				
				// If this entry has a cluster entry: -----------------------------------------------------------------------------------------------
				if (matrix[s][z] != null || !matrix[s][z].equals("")) {
					
					// Get this entrys char count
					int thisEntrysCharCount = matrix[s][z].length();
					String thisEntrysString = matrix[s][z];
					
					// figure out if the left space should be filled with blanks or lines
					boolean lines = false;
					for (int l=0; l < s; l++) { // Go trough the columns (same row)!
						if (matrix[l][z] != null || !matrix[l][z].equals("")) {
							lines = true;
						}
					}
					
					// Fill lines: for every char of difference add two - before the actual string
					if (lines) {
						for (int v=0; v < maxLengthOfThisColumn - thisEntrysCharCount; v++) {
							thisEntrysString = "--" + thisEntrysString;
						}
					}
					// Fill blanks: for every char of difference add two spaces before the actual string
					else {
						for (int v=0; v < maxLengthOfThisColumn - thisEntrysCharCount; v++) {
							thisEntrysString = "  " + thisEntrysString;
						}
					}
					
					// Add a nice ending IF this is not he last column
					if (s != matrix.length - 1) {
						thisEntrysString += " -- ";
					}
					
				}
				// If this is an empty entry: --------------------------------------------------------------------------------------------------------
				else {
					
					// If this is the first column just fill the entry up with blanks (two per char of the maxLengthOfThisColumn)
					if (s == 0) {
						String filler = "";
						for (int f=0; f < maxLengthOfThisColumn; f++) {
							filler += "  ";
						}
						matrix[s][z] = filler;
					}
					// If this is a middle column we need to figure out if this entry should be blank or have a line or a merge
					else if (s > 0 && s < matrix.length - 1) {
						
						// If there is a value to the right (same row, different column) there needs to be a line!
						boolean lines = false;
						for (int r=s; r < matrix.length; r++) { // Go trough the columns (same row)!
							if (matrix[r][z] != null || !matrix[r][z].equals("")) {
								lines = true;
							}
						}
						
						if (lines) {
							// TODO
						}
						
						// TODO
						// if (matrix[s-1][z] != null) //boolean merge = false;
						
					}
					// If this the last column we need to figure out if this entry should be blank or a merge
					else if (s == matrix.length - 1) {
						// figure out if the left space should be filled with blanks or lines
						boolean lines = false;
						for (int r=s; r < matrix.length; r++) { // Go trough the columns (same row)!
							if (matrix[r][z] != null || !matrix[r][z].equals("")) {
								
								//TODO
								
							}
						}
					}
					else {
						throw new IndexOutOfBoundsException ("Dendogram - makeMatrixPretty(...): " +
								"The column variable was out of bounds.");
					}
				}
			}
			
		}
		
	}
	
	
	/**
	 * @param currentNode
	 * @return A String listing all the leaf nodes with their id and then their name (path)
	 */
	private String showNamesOfClusterSongs (Node currentNode) {
		
		String result = "";
		if (currentNode != null) {
			
			int depth = currentNode.getValue().size();
			if (depth == 1) {
				result += currentNode.getID() + ": " + currentNode.getName() + "\n";
			} else {
				
				String leftChildNames = "";
				if (currentNode.getLeft() == null) {
					AmuseLogger.write("DENDOGRAM", Level.WARN, "The current node (ID = " + currentNode.getID() + 
							")" + this.integerListToString(currentNode.getValue()) + " has no left child.");
				} else {
					leftChildNames = this.showNamesOfClusterSongs(currentNode.getLeft());
				}
				
				String rightChildNames = "";
				if (currentNode.getRight() == null) {
					AmuseLogger.write("DENDOGRAM", Level.WARN, "The current node (ID = " + currentNode.getID() + 
							")" + this.integerListToString(currentNode.getValue()) + " has no right child.");
				} else {
					rightChildNames = this.showNamesOfClusterSongs(currentNode.getRight());
				}
				result += leftChildNames + rightChildNames;
			}
		}
		
		return result;
	}
	
	
	/**
	 * @param list = Any list of type <Integer>
	 * @return A string showing this list (like "{9,298,747,2,-90}")
	 */
	public String integerListToString (List<Integer> list) {
		String result = "";
		
		if (list.size() == 0) {
			result = "{ }";
		} else {
			result = " {";
			for (int v=0; v < list.size(); v ++) {
				if (v == list.size() -1) {
					result += list.get(v) + "}";
				} else {
					result += list.get(v) + ",";
				}
			}
		}
		
		return result;
	}
	
	
	private int findHeight(Node node) {
		  if (node == null) return 0;
		  return 1 + Math.max(findHeight(node.getLeft()), findHeight(node.getRight()));
		}
	
	
	/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	/** Internal Node Class -  works like a node in any tree */
	/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	private class Node {
		private String id;
		private String name;
		private List<Integer> value;
		private Node parent;
		private Node left;
		private Node right;
		
		private Node (String id, String name, List<Integer> value, Node left, Node right) {
			this.id = id;
			this.name = name;
			this.value = value;
			this.parent = null;
			this.left = left;
			this.right = right;
		}
		
		private String getID () {return this.id;}
		private String getName () {return this.name;}
		private List<Integer> getValue () {return this.value;}
		private Node getParent () {return this.parent;}
		private Node getLeft () {return this.left;}
		private Node getRight () {return this.right;}
		private void setID (String id) {this.id = id;}
		private void setName (String name) {this.id = name;}
		private void setValue (List<Integer> value) {this.value = value;}
		private void setParent (Node parent) {this.parent = parent;}
		private void setLeft (Node left) {this.left = left;}
		private void setRight (Node right) {this.right = right;}
	}
	
}
