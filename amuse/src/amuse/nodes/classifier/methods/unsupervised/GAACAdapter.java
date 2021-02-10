package amuse.nodes.classifier.methods.unsupervised;

import java.util.StringTokenizer;

import amuse.data.io.DataSet;
import amuse.data.io.DataSetInput;
import amuse.interfaces.nodes.NodeException;
import amuse.interfaces.nodes.methods.AmuseTask;
import amuse.nodes.classifier.ClassificationConfiguration;
import amuse.nodes.classifier.interfaces.ClassifierUnsupervisedInterface;
import amuse.util.LibraryInitializer;

/**
 * Clusters given data by using the Group Averaged Agglomerative Clustering method
 * @author Pauline Speckmann
 *
 */
public class GAACAdapter extends AmuseTask implements ClassifierUnsupervisedInterface {
	
	/** Defines the numerical measure option (e.g. euclidean distance) */
	String numericalMeasure;

	public void setParameters(String parameterString) {

        // Should the default parameters be used? Or are values given?
        if(parameterString == "" || parameterString == null) {
        	numericalMeasure = new String("EuclideanDistance");
        } else {
        	numericalMeasure = parameterString;
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

        //TODO
	}

}
