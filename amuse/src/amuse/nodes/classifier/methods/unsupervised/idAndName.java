package amuse.nodes.classifier.methods.unsupervised;

/**
 * Data tuple consisting of an (int) ID and a (String) name
 * @author Pauline Speckmann
 *
 */
public class idAndName {
	private int id;
	private String name;

	public idAndName (int id, String name) {
		this.id = id;
		this.name = name;
	}
	
	public int getID () {return this.id;}
	public String getName () {return this.name;}
	public void setID (int id) {this.id = id;}
	public void setName (String name) {this.name = name;}
}