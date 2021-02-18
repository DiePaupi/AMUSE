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
			Node current = new Node(input.get(c), null, null);
			clusters.add(c, current);
		}
	}
	
	
	/**
	 * Given two clusters in form of List<Integer> it merges them into one and sets the new cluster as parent to the two given ones
	 * @param inputClusterA = First of the input Clusters to be merged
	 * @param inputClusterB = Second of the input Clusters to be merged
	 * @throws NodeException Is thrown when the given clusters A and B can't be found in the Dendograms cluster List
	 */
	public void setNewMerge (List<Integer> inputClusterA, List<Integer> inputClusterB) throws NodeException {
		
		if (inputClusterA.equals(null) || inputClusterB.equals(null)) {
			throw new NodeException ("Dendogram - setNewMerge(): At least one of the given iput cluster lists was null.");
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
			for (int v=0; v<clusterA.getValue().size(); v++) {
				parentValue.add(clusterA.getValue().get(v));
			}
			parentValue.addAll(clusterB.getValue());
			
			Node parent = new Node(parentValue, clusterA, clusterB);
			clusterA.setParent(parent);
			clusterB.setParent(parent);
			
			clusters.remove(clusterA);
			clusters.remove(clusterB);
			clusters.add(parent);
		}
	}
	
	/** @return A List of Nodes which each contain a cluster */
	public List<Node> getClusters () {return this.clusters;}
	
	/** Shows the Dendogram in the AMUSE Debug Console */
	public void showClusters() {
		AmuseLogger.write("DENDOGRAM", Level.DEBUG, "BEGINNING TO SHOW DENDOGRAM");
		
		String result = "";
		for (int c=0; c < clusters.size(); c++) {
			Node current = clusters.get(c);
			
			result = result + this.showNode(current, new ArrayList<Integer>()) + "\n";
		}
		AmuseLogger.write("DENDOGRAM", Level.DEBUG, result);
		
		AmuseLogger.write("DENDOGRAM", Level.DEBUG, "FINISHED TO SHOW DENDOGRAM");
	}
	
	/** @return The Dendogram as String */
	public String printClusters() {
		String result = "";
		for (int c=0; c < clusters.size(); c++) {
			Node current = clusters.get(c);
			result = result + this.showNode(current, new ArrayList<Integer>())  + "\n \n";
		}
		return result;
	}
	
	private String showNode(Node current, List<Integer> rightDiff) {
		List<Integer> currentValue = current.getValue();
		int depth = currentValue.size();
		String resultString = "";
		
		if (depth > 1) {
			
			// Get the line to the left child
				List<Integer> rightDiffLeft = new ArrayList<Integer>();
					for (int v=0; v < rightDiff.size(); v++) {
						rightDiffLeft.add(rightDiff.get(v));
					}
				int leftDepthDiff = currentValue.size() - current.getLeft().getValue().size();
				rightDiffLeft.add(-leftDepthDiff);
			String leftNodeString =  this.showNode(current.getLeft(), rightDiffLeft);
			// That " ---i"-Part which is half as wide as the node below
			String end = this.getOnePlacer(depth, '-', 'i');
			// Add spaces and nice |s
			String addSpace = "";
				for (int i = rightDiffLeft.size()-1; i >= 0; i--) {
					addSpace = addSpace + this.getPropperPlacer(Math.abs(rightDiffLeft.get(i)), depth, ' ');
					if (rightDiffLeft.get(i) < 0) {
						addSpace = addSpace + this.getOnePlacer(depth, ' ', ' ');
					} else {
						addSpace = addSpace + this.getOnePlacer(depth, ' ', '|');
					}
					
				}
			leftNodeString = leftNodeString + end + addSpace;
			
			
			// Get the line to the right child
				List<Integer> rightDiffRight = new ArrayList<Integer>();
					for (int v=0; v < rightDiff.size(); v++) {
						rightDiffRight.add(rightDiff.get(v));
					}
				int rightDepthDiff = currentValue.size() - current.getRight().getValue().size();
				rightDiffRight.add(rightDepthDiff);
			String rightNodeString = this.showNode(current.getRight(), rightDiffRight);
			
			// Set the ResultString
			resultString = leftNodeString + "\n" + rightNodeString + " {" + currentValue.toString() + "}";
			
			// If this is the left child of some other node: Add placer to the right
			if (current.getParent() != null) {
				int LeftDifferenceInDepth = current.getParent().getValue().size() - current.getValue().size();
				String propperPlacer = this.getPropperPlacer(LeftDifferenceInDepth, depth, '-');
				propperPlacer = propperPlacer + this.getOnePlacer(depth, '-', 'i');
				
				resultString = resultString + propperPlacer;
			}
			
		} else {
			
			// Print a fitting length of fillers for the left Node
			int LeftDifferenceInDepth = current.getParent().getValue().size() - current.getValue().size();
			String propperPlacer = this.getPropperPlacer(LeftDifferenceInDepth, depth, '-');
			resultString = "{" + currentValue.toString() + "}" + propperPlacer;
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
				currentPlacer = currentPlacer + " " +placerChar+placerChar+placerChar;
			} else {
				currentPlacer = currentPlacer + " " +placerChar+placerChar;
				for (int q=0; q < 2*startDepth +1; q++) {
					currentPlacer = currentPlacer + placerChar;
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
				end = end + spacer;
			} else if (q == depth) {
				end = end + spacerChar;
			} else {
				end = end + ' ';
			}
		}
		
		return end;
	}
	
	
	/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	/** Internal Node Class -  works like a node in any tree */
	/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	private class Node {
		List<Integer> value;
		Node parent;
		Node left;
		Node right;
		
		private Node (List<Integer> value, Node left, Node right) {
			this.value = value;
			this.left = left;
			this.right = right;
			this.parent = null;
		}
		
		private List<Integer> getValue () {return this.value;}
		private Node getParent () {return this.parent;}
		private Node getLeft () {return this.left;}
		private Node getRight () {return this.right;}
		private void setValue (List<Integer> value) {this.value = value;}
		private void setParent (Node parent) {this.parent = parent;}
		private void setLeft (Node left) {this.left = left;}
		private void setRight (Node right) {this.right = right;}
	}
}
