package amuse.nodes.classifier.methods.unsupervised.supportclasses;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Level;

import amuse.data.io.DataSet;
import amuse.data.io.attributes.Attribute;
import amuse.data.io.attributes.NumericAttribute;
import amuse.interfaces.nodes.NodeException;
import amuse.preferences.AmusePreferences;
import amuse.preferences.KeysStringValue;
import amuse.util.AmuseLogger;

public class Testing {
	
	public static void printMinMax (DataSet data) {
		String minMaxValues = "";
		double minAverage = 0.0;
		double minTrue = (double) data.getAttribute(0).getValueAt(0);
		double maxAverage = 0.0;
		double maxTrue = 0.0;
		
		ArrayList<String> attributes = (ArrayList<String>) data.getAttributeNames();
		for (int attr=0; attr < attributes.size(); attr++) {
			
			Attribute currentAttribute = data.getAttribute(attr);
			String nameSubstring = "";
			try {
				nameSubstring = currentAttribute.getName().substring(0, 7);
			}  catch(Exception e) {
				AmuseLogger.write("Testing", Level.WARN, "Error getting the substring 0-7 from attribute " + currentAttribute.getName());
	        }
			
			
			if (currentAttribute.getTypeStr().equals("NUMERIC") && !nameSubstring.equals("cluster")) {
				
				minMaxValues += attributes.get(attr) + ": ";
				double min = (double) currentAttribute.getValueAt(0);
				double max = (double) currentAttribute.getValueAt(0);
				
				for (int value=1; value < data.getValueCount(); value++) {
					double currentValue = (double) currentAttribute.getValueAt(value);
					if (currentValue < min) { min = currentValue; }
					if (currentValue > max) { max = currentValue; }
				}
				
				if (min < minTrue) { minTrue = min; }
				if (max > maxTrue) { maxTrue = max; }
				
				minAverage += min;
				maxAverage += max;
				minMaxValues += min + " - " + max + "\n";
			}
		}
		minAverage = minAverage / attributes.size();
		maxAverage = maxAverage / attributes.size();
		//AmuseLogger.write("Testing", Level.DEBUG, "The attributes have the following ranges: \n" + minMaxValues);
		AmuseLogger.write("Testing", Level.DEBUG, "The true min value is " + minTrue + " and the true max value is " + maxTrue);
		AmuseLogger.write("Testing", Level.DEBUG, "On average there is a min value of " + minAverage + " and a max value of " + maxAverage);
	}
	
}
