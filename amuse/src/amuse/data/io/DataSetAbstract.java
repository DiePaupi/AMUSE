package amuse.data.io;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

import org.apache.log4j.Level;

import amuse.data.io.attributes.Attribute;
import amuse.data.io.attributes.NominalAttribute;
import amuse.data.io.attributes.NumericAttribute;
import amuse.data.io.attributes.StringAttribute;
import amuse.util.AmuseLogger;

public abstract class DataSetAbstract implements Serializable {

	/** For Serializable interface */
	private static final long serialVersionUID = -1042520685068994271L;

	/** Short description of this data set */
	protected String name = "";

	/** This List stores all attributes of this DataSet */
	protected final List<Attribute> attributes = new Vector<Attribute>();

	/**
	 * This method returns this DataSets name.
	 * 
	 * @return The name of this DataSet.
	 */
	public final String getName() {
		return name;
	}

	/**
	 * This method returns the count of values in attributes.
	 * 
	 * @return - the count of values or -1 if not attributes are present.
	 */
	public abstract int getValueCount();

	public final List<String> getAttributeNames() {
		List<String> names = new ArrayList<String>();
		for (Attribute at : attributes) {
			names.add(at.getName());
		}
		return names;
	}

	public final Attribute getAttribute(String name) throws DataSetException {
		for (Attribute at : attributes) {
			if (at.getName().equals(name)) {
				return at;
			}
		}
		throw new DataSetException("No such Attribute: " + name);
	}

	public final Attribute getAttribute(int index) throws DataSetException {
		return attributes.get(index);
	}

	public void addAttribute(Attribute attribute) throws DataSetException {
		Attribute atr = attribute;
		if (getValueCount() >= 0 && getValueCount() != atr.getValueCount()) {
			throw new DataSetException(atr.getName() + ": value count ("
					+ atr.getValueCount()
					+ ") does not match previously added Attributes!");
		}
		this.attributes.add(atr);
	}

	public final void addAttributeList(List<Attribute> attributeList)
			throws DataSetException {
		for (Attribute at : attributeList) {
			addAttribute(at);
		}
	}

	/**
	 * This method is <b> NOT</b> part of the API! <br/>
	 * This method is used to print information of this DataSet via
	 * <em>System.out.println()</em>.
	 */
	public void printSet() {
		System.out.println("DataSet: " + this.getName());
		for (Attribute at : attributes) {
			if (at instanceof NumericAttribute) {
				System.out.println("NumericAttribute: " + at.getName());
			} else if (at instanceof StringAttribute) {
				System.out.println("StringAttribute: " + at.getName());
			} else if (at instanceof NominalAttribute) {
				System.out.println("NominalAttribute: " + at.getName());
			}
		}
		System.out.println("This DataSet contains " + getValueCount()
				+ " values.");
		for (int i = 0; i < this.getAttribute(0).getValueCount(); i++) {
			for (Attribute at : attributes) {
				System.out.print(at.getValueAt(i) + "\t");
			}
			System.out.println("");
		}
	}
	
	/**
	 * Prints the DataSet in the AMUSE logger
	 */
	public void showSet() {
		AmuseLogger.write("DataSetAbstract", Level.DEBUG, "BEGINNING TO SHOW DATASET");
    	AmuseLogger.write("DataSetAbstract", Level.DEBUG, ("DataSetName: " + this.getName()));
    	
    	// Print all the attributes
		for (Attribute at : attributes) {
			if (at instanceof NumericAttribute) {
				AmuseLogger.write("DataSetAbstract", Level.DEBUG, ("NumericAttribute: " + at.getName()));
			} else if (at instanceof StringAttribute) {
				AmuseLogger.write("DataSetAbstract", Level.DEBUG, ("StringAttribute: " + at.getName()));
			} else if (at instanceof NominalAttribute) {
				AmuseLogger.write("DataSetAbstract", Level.DEBUG, ("NominalAttribute: " + at.getName()));
			}
		}
		
		// Print all values
		AmuseLogger.write("DataSetAbstract", Level.DEBUG, ("This DataSet contains " + getValueCount() + " values."));
		for (int valueNumber = 0; valueNumber < this.getAttribute(0).getValueCount(); valueNumber++) {
			for (Attribute at : attributes) {
				AmuseLogger.write("DataSetAbstract", Level.DEBUG, ("" + at.getValueAt(valueNumber) + "\t"));
			}
			AmuseLogger.write("DataSetAbstract", Level.DEBUG, ("This was value row " + (valueNumber+1) + "\n"));
		}
		AmuseLogger.write("DataSetAbstract", Level.DEBUG, "FINISHED TO SHOW DATASET");
	}

	public void checkStringAttribute(String attributeName) {
		if (!this.getAttributeNames().contains(attributeName)
				|| !(this.getAttribute(attributeName) instanceof StringAttribute)) {
			// System.out.println("No "+attributeName+" Attribute!");
			throw new DataSetException("No " + attributeName + " Attribute!");
		}
	}

	public void checkNumericAttribute(String attributeName) {
		if (!this.getAttributeNames().contains(attributeName)
				|| !(this.getAttribute(attributeName) instanceof NumericAttribute)) {
			// System.out.println("No "+attributeName+" Attribute!");
			throw new DataSetException("No " + attributeName + " Attribute!");
		}
	}

	public void checkNominalAttribute(String attributeName) {
		if (!this.getAttributeNames().contains(attributeName)
				|| !(this.getAttribute(attributeName) instanceof NominalAttribute)) {
			// System.out.println("No "+attributeName+" Attribute!");
			throw new DataSetException("No " + attributeName + " Attribute!");
		}
	}

	public int getAttributeCount() {
		return attributes.size();
	}

	public abstract void saveToArffFile(File file) throws IOException;
}