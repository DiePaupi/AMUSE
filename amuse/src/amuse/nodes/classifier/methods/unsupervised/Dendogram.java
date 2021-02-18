package amuse.nodes.classifier.methods.unsupervised;

import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Level;

import amuse.interfaces.nodes.NodeException;
import amuse.util.AmuseLogger;

public class Dendogram {
	
	List<Node> clusters;
	
	/**
	 * Creates a Dendogram (Tree like structure) for hierarchical clustering
	 * @param input = A List of Lists<Integer> which each contain the SongIDs belonging to a cluster
	 */
	public Dendogram (List<List<Integer>> input) {
		
		clusters = new ArrayList<Node>();
		for (int c=0; c < input.size(); c++) {
			Node current = new Node(""+c, input.get(c), null, null);
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
			Node parent = new Node(parentID, parentValue, clusterA, clusterB);
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
			for (int c=0; c < clusters.size(); c++) {
				Node current = clusters.get(c);
				result = result + this.showNode(current, new ArrayList<Integer>())  + "\n \n";
			}
		}
		return result;
	}
	
	private String showNode(Node current, List<Integer> rightDiff) {
		String resultString = "";
		if (current != null) {
			
			List<Integer> currentValue = current.getValue();
			int depth = currentValue.size();
			
			AmuseLogger.write("DENDOGRAM - showNode()", Level.DEBUG, "I was called upon with the current node (ID = " + current.getID() + ") " +
					this.integerListToString(currentValue) + " at the size of " + depth + 
					" and the difference list" + this.integerListToString(rightDiff));
			
			// ----------------------------------------------------------------------------------------------------------------------------------------
			// IF THIS IS NOT A SINGLE-SONG-CLUSTER-NODE
			// ----------------------------------------------------------------------------------------------------------------------------------------
			if (depth > 1) {
				
				// ------------------------------------------------------------------------------------------------------------------------------------
				// Get the left child
				// ------------------------------------------------------------------------------------------------------------------------------------
				
				// Copy the list of differences
				List<Integer> rightDiffLeft = new ArrayList<Integer>();
					for (int v=0; v < rightDiff.size(); v++) {
						rightDiffLeft.add(rightDiff.get(v));
					}
					
				String leftNodeString = "";
				if (current.getLeft() == null ) {
					AmuseLogger.write("DENDOGRAM", Level.WARN, "The current node (ID = " + current.getID() + 
							")" + this.integerListToString(currentValue) + " has no left child.");
				} else {
					// Get the difference from this node to the left child and save it in the list as negative (because left child)
					int leftDepthDiff = depth - current.getLeft().getValue().size();
					rightDiffLeft.add(-leftDepthDiff);
					
					// Recursive call for the left child
					leftNodeString =  this.showNode(current.getLeft(), rightDiffLeft);
					
					// That " ---i"-Part which is half as wide as the node below (but filled to full width with ' ')
					String end = this.getOnePlacer(depth, '-', 'i');
					
					// Add spaces and nice |s
					String addSpace = "";
					//for (int i = rightDiffLeft.size()-1; i >= 0; i--) {
					//	addSpace += this.getPropperPlacer(Math.abs(rightDiffLeft.get(i)), depth, ' ');
					//	if (rightDiffLeft.get(i) < 0) {
					//		addSpace += this.getOnePlacer(depth, ' ', ' ');
					//	} else {
					//		addSpace += this.getOnePlacer(depth, ' ', '|');
					//	}
					//}
					
					leftNodeString += end + addSpace;
				}
				
				
				// ------------------------------------------------------------------------------------------------------------------------------------
				// Get the right child
				// ------------------------------------------------------------------------------------------------------------------------------------
				
				// Copy the list of differences
				List<Integer> rightDiffRight = new ArrayList<Integer>();
					for (int v=0; v < rightDiff.size(); v++) {
						rightDiffRight.add(rightDiff.get(v));
					}
				
				String rightNodeString = "";
				if (current.getRight() == null) {
					AmuseLogger.write("DENDOGRAM", Level.WARN, "The current node (ID = " + current.getID() + 
							")" + this.integerListToString(currentValue) + " has no right child.");
				} else {
					// Get the difference from this node to the right child and save it in the list as positive (because right child)
					int rightDepthDiff = depth - current.getRight().getValue().size();
						rightDiffRight.add(rightDepthDiff);
						
					// Recursive call for the right child
					rightNodeString = this.showNode(current.getRight(), rightDiffRight);
				}
				
				
				// ------------------------------------------------------------------------------------------------------------------------------------
				// Get the this node
				// ------------------------------------------------------------------------------------------------------------------------------------
			
				// Set the ResultString
				resultString += leftNodeString + "\n" + rightNodeString + this.integerListToString(currentValue);
			
				// If this is the left child of some other node: Add placer to the right
				//if (current.getParent() != null) {
				//	int LeftDifferenceInDepth = current.getParent().getValue().size() - current.getValue().size();
				//	String propperPlacer = this.getPropperPlacer(LeftDifferenceInDepth, depth, '-');
				//	propperPlacer = propperPlacer + this.getOnePlacer(depth, '-', 'i');
				//
				//	resultString += propperPlacer;
				//}
		
			} 
			// ----------------------------------------------------------------------------------------------------------------------------------------
			// IF THIS IS A SINGLE-SONG-CLUSTER-NODE
			// ----------------------------------------------------------------------------------------------------------------------------------------
			else {
			
				// Print a fitting length of fillers to the right
				String propperPlacer = "";
				if (current.getParent() != null) {
					int differenceInDepth = current.getParent().getValue().size() - current.getValue().size();
					propperPlacer = this.getPropperPlacer(differenceInDepth, depth, '-');
				}
				
				resultString = this.integerListToString(currentValue) + propperPlacer;
			}
		
		}
		return resultString;
	}
	
	/**
	 * @param diff = the number of clusters you need to drow the line over
	 * @param startDepth = the depth of the cluster node which calls this method
	 * @param placerChar = the seperating char which will fill the result String, probably '-' or ' '
	 * 
	 * @return A String which draws a line or spaces over (diff)-clusters
	 */
	private String getPropperPlacer (int diff, int startDepth, char placerChar) {
		String currentPlacer = "";
		
		for (int p=0; p < (2 * diff - 1); p++) {
			if (p%2 == 0) {
				currentPlacer += " " +placerChar+placerChar+placerChar;
			} else {
				currentPlacer += " " +placerChar+placerChar;
				for (int q=0; q < 2*startDepth +1; q++) {
					currentPlacer += placerChar;
				}
			}
		}
		
		return currentPlacer;
	}
	
	/** 
	 * @param depth = the size of the given cluster list aka. how many members dies the cluster have?
	 * @param spacer = here '-' or ' ' to create a line or leave space
	 * @param spacerChar = here 'i', ' ' or '|'  to create the vertical lines
	 * 
	 * @return A String which is as long as the cluster which depth you use containing either a row of '-' or ' ' seperated by 'i' or '|'
	 */
	private String getOnePlacer (int depth, char spacer, char spacerChar) {
		String end = " ";
		
		for (int q=0; q < (depth*2 +1); q++) {
			if (q < depth) {
				end += spacer;
			} else if (q == depth) {
				end += spacerChar;
			} else {
				end += ' ';
			}
		}
		
		return end;
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
	
	
	/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	/** Internal Node Class -  works like a node in any tree */
	/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	private class Node {
		String id;
		List<Integer> value;
		Node parent;
		Node left;
		Node right;
		
		private Node (String id, List<Integer> value, Node left, Node right) {
			this.id = id;
			this.value = value;
			this.parent = null;
			this.left = left;
			this.right = right;
		}
		
		private String getID () {return this.id;}
		private List<Integer> getValue () {return this.value;}
		private Node getParent () {return this.parent;}
		private Node getLeft () {return this.left;}
		private Node getRight () {return this.right;}
		private void setID (String id) {this.id = id;}
		private void setValue (List<Integer> value) {this.value = value;}
		private void setParent (Node parent) {this.parent = parent;}
		private void setLeft (Node left) {this.left = left;}
		private void setRight (Node right) {this.right = right;}
	}
}
