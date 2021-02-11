package amuse.nodes.classifier.methods.unsupervised;

import java.util.StringTokenizer;
import org.apache.log4j.Level;
import amuse.data.io.DataSet;
import amuse.data.io.DataSetInput;
import amuse.data.io.attributes.Attribute;
import amuse.data.io.attributes.NominalAttribute;
import amuse.data.io.attributes.NumericAttribute;
import amuse.data.io.attributes.StringAttribute;
import amuse.interfaces.nodes.NodeException;
import amuse.interfaces.nodes.methods.AmuseTask;
import amuse.nodes.classifier.ClassificationConfiguration;
import amuse.nodes.classifier.interfaces.ClassifierUnsupervisedInterface;
import amuse.util.AmuseLogger;
import amuse.util.LibraryInitializer;

/**
 * Clusters given data by using Ward's Agglomeration
 * @author Pauline Speckmann
 *
 */
public class WardAdapter extends AmuseTask implements ClassifierUnsupervisedInterface {
	
	/** Defines the numerical measure option (e.g. euclidean distance) */
	String numericalMeasure;
	/** Desired cluster number */
	int k;

	public void setParameters(String parameterString) {

        // Should the default parameters be used? Or are values given?
        if(parameterString == "" || parameterString == null) {
        	numericalMeasure = new String("LWDissimilarityUpdateFormula");
        	// If no desired cluster number is specified the whole dendogram will be created
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
		/* Gets the DataSet given by the user in the Classifier AMUSE task */
        DataSet dataSetToClassify = ((DataSetInput)((ClassificationConfiguration)this.correspondingScheduler.
         getConfiguration()).getInputToClassify()).getDataSet();

        try {
        	//AmuseLogger.write("WardAdapter", Level.DEBUG, "BEGINNING TO SHOW DATASET");
        	//dataSetToClassify.showSet();
        	
        	//-----------------------------------------------------------------------------------------------------------------------------
        	// (1) Greife auf SongsToClassify zu, denn jedes Feature muss einen representativen Wert erhalten
        	//     Die Methode um den Wert zu berechnen vielleicht per Auswahl (avarage, median)
        	//-----------------------------------------------------------------------------------------------------------------------------
        		int attributeCount = dataSetToClassify.getAttributeCount();
        		int valueCount = dataSetToClassify.getAttribute(0).getValueCount();
        	
        		String[] attributeNames = new String[attributeCount];
        			for (int j=0; j < attributeCount; j++ ) {
        				Attribute at = dataSetToClassify.getAttribute(j);
        				attributeNames[j] = at.getName();
        				//AmuseLogger.write("WardAdapter saved feature name in attributeNames[" + j + "]", Level.DEBUG, (at.getName() + "\t"));
        			}
        			AmuseLogger.write("WardAdapter", Level.DEBUG, "Saved all " + attributeCount + " feature names.");
        			
        		double[][] allFeatureValues = new double[valueCount][attributeCount];
        			for (int i = 0; i < valueCount; i++) {
        				for (int j=0; j < attributeCount; j++ ) {
        					Attribute at = dataSetToClassify.getAttribute(j);
        					allFeatureValues[i][j] = (double) at.getValueAt(i);
        					//AmuseLogger.write("WardAdapter saved value in allFeatureValues[" + i + "][" + j + "]", Level.DEBUG, (at.getValueAt(i) + "\t"));
        				}
        				//AmuseLogger.write("WardAdapter", Level.DEBUG, ("This was value row " + i + " +1 since it starts at 0"));
        				//AmuseLogger.write("WardAdapter", Level.DEBUG, (""));
        			}
        			AmuseLogger.write("WardAdapter", Level.DEBUG, "Saved all " + valueCount + " feature values.");
        		
        //TODO: Songs trennen - bisher sind alle untereinander weg, so dass sie im folgenden Schritt zusammen gemittelt werden
        			
        		// Erstelle für jedes Attribut / Feature einen Durchschnittswert
        		double[] avaregedFeatureValues = new double[attributeCount];
        		for (int j = 0; j < attributeCount; j++ ) {
        			double sum = 0;
        			for (int i = 0; i < valueCount; i++) {
        				sum = sum + allFeatureValues[i][j];
        			}
        			//AmuseLogger.write("WardAdapter", Level.DEBUG, ("Calculating the average: " + sum + " / " + valueCount));
        			double avg = sum / valueCount;
        			avaregedFeatureValues[j] = avg;
        			AmuseLogger.write("WardAdapter", Level.DEBUG, ("The avaragedFeatureValue[" + j + "] is " + avg + " for Feature " + attributeNames[j]));
        		}
        		AmuseLogger.write("WardAdapter", Level.DEBUG, "Calculated all feature averages.");
        	
        	//-----------------------------------------------------------------------------------------------------------------------------
        	// (2) Jedes SongToClassify-VektorTeil einem eigenen Cluster zuordnen
        	//-----------------------------------------------------------------------------------------------------------------------------
        		// Wie greife ich auf die Songanzahl zu?
        		
        	//-----------------------------------------------------------------------------------------------------------------------------
        	// (3) Berechne die (un-)ähnlichkeits Matrix aller Songs mit der Lance-William Sache
        	//	   Hier vielleicht auch Wahl zwischen LW und Klassisch lassen
        	//-----------------------------------------------------------------------------------------------------------------------------
        	
        	//-----------------------------------------------------------------------------------------------------------------------------
        	// (4) Für das ähnlichste Clusterpaar:
        	//	   	- Begutachte die n-1 verbleibenden Cluster mit einer neuen Matrix und dem entsprechenden vereinigten Distanzmaß
        	//		- Soll noch ein drittes zum Cluster hinzugefügt werden oder lieber ein neues Paar erstellt?
        	//	   Wiederhole bis k erreicht oder nur noch ein großes Cluster existiert
        	//-----------------------------------------------------------------------------------------------------------------------------
        	
        } catch(Exception e) {
			throw new NodeException("Error classifying data: " + e.getMessage());
		}
	}

}
