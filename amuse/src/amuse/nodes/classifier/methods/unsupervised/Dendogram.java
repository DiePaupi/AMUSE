package amuse.nodes.classifier.methods.unsupervised;

import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Level;

import amuse.interfaces.nodes.NodeException;
import amuse.util.AmuseLogger;

public class Dendogram {
	
	List<Node> clusters;
	
	public Dendogram (List<List<Integer>> input) {
		
		clusters = new ArrayList<Node>();
		for (int c=0; c < input.size(); c++) {
			Node current = new Node(input.get(c), null, null);
			clusters.add(c, current);
		}
	}
	
	
	/** Given two clusters in form of List<Integer> it merges them into one and sets the new cluster as parent to the two given ones */
	public void setNewMerge (List<Integer> inputClusterA, List<Integer> inputClusterB) throws NodeException {
		
		// Search the input clusters in the own List clusters
		Node clusterA = null, clusterB = null;
		for (int c=0; c < clusters.size(); c++) {
			if (clusters.get(c).equals(inputClusterA)) {
				clusterA = clusters.get(c);
			} else if (clusters.get(c).equals(inputClusterB)) {
				clusterB = clusters.get(c);
			}
		}
		
		// Check if the input clusters couldn't be found in the own List clusters
		if (clusterA.equals(null) || clusterB.equals(null)) {
			throw new NodeException ("Couldn't find the clusters to merge in the dendograms own cluster list.");
		} else {
			
			List<Integer> parentValue = new ArrayList<Integer>();
			parentValue = clusterA.getValue();
			parentValue.addAll(clusterB.getValue());
			
			Node parent = new Node(parentValue, clusterA, clusterB);
			clusterA.setParent(parent);
			clusterB.setParent(parent);
			
			clusters.remove(clusterA);
			clusters.remove(clusterB);
			clusters.add(parent);
		}
	}
	
	public List<Node> getClusters () {return this.clusters;}
	
	public void showClusters() {
		AmuseLogger.write("DENDOGRAM", Level.DEBUG, "BEGINNING TO SHOW DENDOGRAM");
		
		for (int c=0; c < clusters.size(); c++) {
			Node current = clusters.get(c);
			
			this.showNode(current, "", "");
		}
		
		AmuseLogger.write("DENDOGRAM", Level.DEBUG, "FINISHED TO SHOW DENDOGRAM");
	}
	
	private String showNode(Node current, String leftString, String rightString) {
		List<Integer> currentValue = current.getValue();
		int depth = currentValue.size();
		String resultString = "";
		String placer = " ----- ";
		
		if (depth > 1) {
			resultString = resultString + this.showNode(current.getLeft(), "", "");
			
			// Print a fitting length of fillers for the left Node
			int LeftDifferenceInDepth = current.getValue().size() - current.getLeft().getValue().size();
			String currentLeftPlacer = "";
			String currentEndPlacer = "";
			for (int p=0; p < (2 * LeftDifferenceInDepth - 1); p++) {
				currentLeftPlacer = currentLeftPlacer + placer;
				currentEndPlacer = currentEndPlacer + "--";
			}
			currentLeftPlacer = resultString + currentLeftPlacer + currentEndPlacer;
			
			resultString = resultString + this.showNode(current.getRight(), "", "");
			
			// Print a fitting length of fillers for the right Node
			int RightDifferenceInDepth = current.getValue().size() - current.getRight().getValue().size();
			String currentRightPlacer = "";
			for (int p=0; p < (2 * RightDifferenceInDepth - 1); p++) {
				currentRightPlacer = currentRightPlacer + placer;
			}
			
			AmuseLogger.write("DENDOGRAM", Level.DEBUG, currentRightPlacer + "{" + currentValue.toString() + "}");
		} else {
			resultString = resultString + "{" + currentValue.toString() + "}";
		}
		
		return resultString;
	}
	
	
	/** //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	 * Internal Node Class -  works like a node in any tree
	 *  //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	 */
	private class Node {
		List<Integer> value;
		Node parent;
		Node left;
		Node right;
		
		private Node (List<Integer> value, Node left, Node right) {
			this.value = value;
			this.left = left;
			this.right = right;
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
