package amuse.nodes.classifier.methods.unsupervised.supportclasses;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Formatter;
import java.util.List;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Level;

import amuse.interfaces.nodes.NodeException;
import amuse.util.AmuseLogger;

public class Dendrogram {
	
	List<Node> clusters;
	
	/**
	 * Creates a Dendogram (Tree like structure) for hierarchical clustering
	 * @param input = A List of Lists<Integer> which each contain the SongIDs belonging to a cluster
	 */
	public Dendrogram (List<List<Integer>> clusterInput, List<idAndName> songIdsAndNames) {
		
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
			
			//AmuseLogger.write("DENDOGRAM", Level.DEBUG, "The clusters " + this.integerListToString(clusterA.getValue()) +
			//		" and " +this.integerListToString(clusterB.getValue()) + " have been merged into " + this.integerListToString(parentValue));
		}
	}
	
	
	/** Shows the Dendogram in the AMUSE Debug Console */
	public void showClusters() {
		String result = this.printClusters();
		AmuseLogger.write("DENDOGRAM", Level.DEBUG, "Showing the dendogram for "+ clusters.size() +" different clusters: \n" +result);
	}
	
	
	/** @return The Dendogram as String */ //----------------------------------------------------------------------------------------------------------------------
	public String printClusters() {
		
		String result = "";
		
		if (clusters.size() > 0) {
			
			// Get the dendograms for every cluster and get their nice strings
			for (int c=0; c < clusters.size(); c++) {
				Node current = clusters.get(c);
				
				// Get the cluster specific dendogram array
				int maxHightofThisCluster = this.findHeight(current);
				
				String[][] outputStrings = new String[maxHightofThisCluster][current.getValue().size()];
				int[][] kindOfEntries = new int[maxHightofThisCluster][current.getValue().size()];
				this.fillOutputArray(outputStrings, maxHightofThisCluster-1, current.getValue().size()-1, current, kindOfEntries);
				
				// Get the dendogram array as string and add it to the result string
				String formatInfo = this.makeMatrixPretty(outputStrings, kindOfEntries);
				
				result += this.getMatrixAsFormattedString(outputStrings, formatInfo) + "\n";
			}
			
			// Get the ids and song names of all leafs at the end
			result += "The song ids in the dendogram correspond to: \n \n";
			for (int c=0; c < clusters.size(); c++) {
				Node current = clusters.get(c);
				result += this.showNamesOfClusterSongs(current);
			}
		}
		
		return result;
	}
	
	
	private void fillOutputArray (String[][] output, int column, int row, Node currentNode, int[][] kindOfEntry) {
		
		if (currentNode != null && output != null) {
			
			// Fill corresponding table entry for this node
			output[column][row] = this.integerListToString(currentNode.getValue());
			
			if (currentNode.getLeft() != null && currentNode.getRight() != null) {
				kindOfEntry[column][row] = 2;
				
				int rowsToLeaveFree = currentNode.getValue().size() - (currentNode.getLeft().getValue().size());
				
				// Go up through the entries until row - rowsToLeaveFree and set the kindOfEntry
				if (rowsToLeaveFree > 0) {
					for (int e=row-1; e > row - rowsToLeaveFree; e--) {
						kindOfEntry[column][e] = 6;
					}
				}
				kindOfEntry[column][row - rowsToLeaveFree] = 5;
				
				// Tell the left child that it should fill out its table entry
				this.fillOutputArray(output, column-1, (row - rowsToLeaveFree), currentNode.getLeft(), kindOfEntry);
			
				// Tell the right child that it should fill out its table entry
				this.fillOutputArray(output, column-1, row, currentNode.getRight(), kindOfEntry);
			} else {
				kindOfEntry[column][row] = 1;
			}
		}
	}
	
	
	private String getMatrixAsFormattedString (String[][] matrix,  String formatInfo) {
		
		String result = "";
		
		for (int x=0; x < matrix[0].length; x++) {
			String[] currentRow = new String[matrix.length];
			
			for (int i=0; i < matrix.length; i++) {
				currentRow[i] = matrix[i][x];
			}
			
			String rowResult = String.format(formatInfo, currentRow);
			result += rowResult + "\n";
		}
		
		return result;
	}
	
	
	private String makeMatrixPretty (String[][] matrix, int[][] kindOFEntry) throws IndexOutOfBoundsException {
		
		String formatInfo = "";
		
		// Go trough each column
		for (int s=0; s < matrix.length; s++) {
			
			// Get the maximal char count in this column -------------------------------------------------------------------------------------------
			int maxLengthOfThisColumn = 0;
			String maxStringOfThisColumn = "";
			
			for (int z=0; z < matrix[0].length; z++) {
				String currentString = matrix[s][z];
				
				//TODO: Is the default for a String[][] "" and not null?
				if (currentString != null && !currentString.equals("")) {
					if (currentString.length() > maxLengthOfThisColumn) {
						maxLengthOfThisColumn = currentString.length();
						maxStringOfThisColumn = currentString;
					}
				}
			}
			
			int columnSize = maxLengthOfThisColumn+3;
			//String ThisColumnFormat = ("%"+columnSize+"."+columnSize+"s");  // fixed size, right aligned
			String ThisColumnFormat = ("%"+s+"$"+columnSize+"s");
			formatInfo += ThisColumnFormat + " ";
			
			// Adjust every entry of this column
			for (int z=0; z < matrix[0].length; z++) {
				
				// If this entry has a cluster entry: -----------------------------------------------------------------------------------------------
				if (matrix[s][z] != null && !matrix[s][z].equals("")) {
					
					// Get this entrys char count
					int thisEntrysCharCount = matrix[s][z].length();
					String thisEntrysString = matrix[s][z];
					
					if (maxStringOfThisColumn.length() > thisEntrysCharCount) {
						
						//int fillersToBeSet = (int) (1.5*(maxLengthOfThisColumn - thisEntrysCharCount -commaCount -2) + commaCount+2);
						int fillersToBeSet = maxLengthOfThisColumn - thisEntrysCharCount;
						
						// Figure out if the left space should be filled with blanks or lines 
						// ( (kindOFEntry[s][z] == 2) means it's a middle node, so it has to be lines )
						if (kindOFEntry[s][z] == 2) {
							
							// Fill lines: for every char of difference (minus commas) add two - before the actual string
							// For commas there'll be only one - added
							for (int v=0; v < fillersToBeSet-1; v++) {
								thisEntrysString = "-" + thisEntrysString;
							}
							thisEntrysString = " " + thisEntrysString;
						}
						// ( (kindOFEntry[s][z] == 1) means it's a leaf node, so spaces are in order )
						// Fill blanks: for every char of difference (minus commas) add two spaces before the actual string
						// For commas there'll be only one space added
						else if (kindOFEntry[s][z] == 1) {
							for (int v=0; v < fillersToBeSet+3; v++) {
								thisEntrysString = " " + thisEntrysString;
							}
						}
					}
					
					// Add a nice ending IF this is not he last column
					if (s != matrix.length - 1) {
						thisEntrysString += " --";
					}
					
					matrix[s][z] = StringUtils.center(thisEntrysString, columnSize);
				}
				// If this is an empty entry: --------------------------------------------------------------------------------------------------------
				else {
					
					if (s >= 0 && s < matrix.length) {
						
						String filler = "";
						
						//TODO: Is 0 the default entry for a int[][]?
						// kindOFEntry[s][z] == 0: It's a completely empty entry
						if (kindOFEntry[s][z] == 0 ) {
							for (int f=0; f < columnSize; f++) {
								filler += " ";
							}
						}
						// kindOFEntry[s][z] == 5: Here needs to be a merge!
						else if (kindOFEntry[s][z] == 5) {
							
							char[] fill = new char[columnSize];
							for (int f=0; f<columnSize; f++) {
								if (f < columnSize/2) {
									filler += "-";
								} else if (f == columnSize) {
									filler += ",";
								} else {
									filler += " ";
								}
							}
							
							for (int f=0; f < fill.length; f++) {
								filler += fill[f];
							}
							
						}
						// kindOFEntry[s][z] == 6: Here needs to be a pipe!
						else if (kindOFEntry[s][z] == 6) {
							
							//for (int f=0; f < (maxLengthOfThisColumn -2) - (2*commaCount); f++) {
							//	filler += " ";
							//}
							filler = "||";
							//for (int f=0; f < 2*commaCount; f++) {
							//	filler += " ";
							//}
							
						}
						else {
							throw new IndexOutOfBoundsException("Dendogram - makeMatrixPretty(...): "
									+ "There was no matching kindOFEntry[s][z] entry!");
						}
						
						matrix[s][z] = StringUtils.center(filler, columnSize);
						
					}
					else {
						throw new IndexOutOfBoundsException ("Dendogram - makeMatrixPretty(...): " +
								"The column variable was out of bounds.");
					}
				}
			}
			
			// --
		}
		// --
		return formatInfo;
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
							") " + this.integerListToString(currentNode.getValue()) + " has no left child.");
				} else {
					leftChildNames = this.showNamesOfClusterSongs(currentNode.getLeft());
				}
				
				String rightChildNames = "";
				if (currentNode.getRight() == null) {
					AmuseLogger.write("DENDOGRAM", Level.WARN, "The current node (ID = " + currentNode.getID() + 
							") " + this.integerListToString(currentNode.getValue()) + " has no right child.");
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
			result = "{";
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
	/** Print the dendogram as Tikz Forest in LaTeX */
	/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	public void printTikzDendogram (String path) throws NodeException {
		
		File dendogramFile = new File(path + "Ward_DENDOGRAM.tex");
		BufferedWriter fileWriter;
		try {
			fileWriter = new BufferedWriter(new FileWriter(dendogramFile));
			String fileHead = "\\documentclass[12pt,border=10pt]{standalone} \n"
					+ "\\usepackage[edges]{forest} \n"
					+ "\\forestset{ \n"
					+ "   my tree/.style={ \n"
					+ "      forked edges, \n"
					+ "      for tree={ \n"
					+ "         grow'=0, \n"
					+ "         draw, \n"
					+ "         minimum height=5mm, \n"
					+ "         text centered, \n"
					+ "         tier/.option=level, \n"
					+ "      }, \n"
					+ "      delay={ \n"
					+ "         where content={}{content=\\phantom{X},draw=none, child anchor=children}{} \n"
					+ "      }, \n"
					+ "   }, \n"
					+ "} \n \n"
					+ "\\begin{document} \n";
			fileWriter.append( fileHead );
			
			// Add a new Dendogram for every cluster
			for (int c=0; c < clusters.size(); c++) {
				fileWriter.append( "\\begin{forest} \n" + "   my tree \n" );
				
				//
				Node current = clusters.get(c);
				String currentDendogram = this.getNodeStructure(current, current.getValue().size());
				fileWriter.append( currentDendogram );
				
				// End the cluster (and if it's NOT the last, add some space)
				fileWriter.append( "\\end{forest} \n \n" );
				if (c < clusters.size() -1) {
					fileWriter.append( "\\vspace*{1cm} \n \n" );
				}
			}
			
			fileWriter.append( "\\end{document}" );
			
			fileWriter.close();
		} catch (IOException e) {
			AmuseLogger.write("DENDOGRAM", Level.WARN, "Couldn't initialize the file writer.");
		}
	}
	
	
	private String getNodeStructure (Node current, int maxDepth) {
		String result = "";
		
		if (current != null) {
			
			String indent = "   ";
			for (int v = current.getValue().size(); v < maxDepth; v++) {
				indent += "   ";
			}
			
			String openNode = indent + "[";
			
			// If this is a leaf node
			if (current.getValue().size() == 1) {
				openNode += "(" + current.getValue().get(0) + ") " + this.getSongnameFromPath(current);
				return openNode + "] \n";
			}
			// If this is a middle node wit a left and right child
			else if (current.getLeft() != null && current.getRight() != null) {
				openNode += this.integerListToString(current.getValue());
				
				String leftChildsString = this.getNodeStructure(current.getLeft(), maxDepth);
				String rightChildsString = this.getNodeStructure(current.getRight(), maxDepth);
				
				String endNode = indent + "] \n";
				return openNode + "\n" + leftChildsString + rightChildsString + endNode;
			}
			// Else
			else {
				AmuseLogger.write("DENDOGRAM", Level.WARN, "Something went wrong while printing the dendogram structure.");
			}
		}
		
		return result;
	}
	
	
	/**
	 * @param current = A leaf node
	 * @return Only the song name of this node (without the rest of the path)
	 */
	private String getSongnameFromPath (Node current) {
		String result = "";
		
		// Only if it's a leaf node
		if (current.getValue().size() == 1) {
			char[] name = current.getName().toCharArray();
			// Ignore the last 4 chars as they are something like ".mp3" or ".wav"
			for (int c = name.length -5; c >= 0; c--) {
				// If we have reached the end of the song name there'll be a /
				if (name[c] == '/') {
					break;
				} else {
					result = name[c] + result;
				}
			}
		}
		
		return result;
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
