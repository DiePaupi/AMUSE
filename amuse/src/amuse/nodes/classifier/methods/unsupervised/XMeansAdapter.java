package amuse.nodes.classifier.methods.unsupervised;


import amuse.data.io.DataSet;
import amuse.data.io.DataSetInput;
import amuse.interfaces.nodes.NodeException;
import amuse.interfaces.nodes.methods.AmuseTask;
import amuse.nodes.classifier.ClassificationConfiguration;
import amuse.nodes.classifier.interfaces.ClassifierInterface;
import amuse.preferences.AmusePreferences;
import amuse.preferences.KeysStringValue;
import amuse.util.FileOperations;
import amuse.util.LibraryInitializer;
import com.rapidminer.Process;
import com.rapidminer.example.ExampleSet;
import com.rapidminer.operator.IOContainer;
import com.rapidminer.operator.Operator;
import com.rapidminer.operator.clustering.clusterer.XMeans;
import com.rapidminer.operator.io.RepositoryStorer;
import com.rapidminer.operator.ports.InputPort;
import com.rapidminer.operator.ports.OutputPort;
import com.rapidminer.tools.OperatorService;

import java.io.File;
import java.util.StringTokenizer;

/**
 * Clusters given data by using the x-means clustering method via RapidMiner
 * @author Pauline Speckmann
 */
public class XMeansAdapter extends AmuseTask implements ClassifierInterface {

    /** Lower and upper bound for the amount of clusters to be generated by x-means */
    private int k_min;
    private int k_max;
    /** Chooses the numerical measure to be used */
    private String numericalMeasure;
    /** Upper bounds for the max number of runs and max number of optimization steps */
    private int max_runs;
    private int max_opt_steps;

    /** Not changeable parameters */
    private String measureType;
    private String clusteringAlgorithm;

    public void setParameters(String parameterString) {

        // Should the default parameters be used? Or are values given?
        if(parameterString == "" || parameterString == null) {
            k_min = 3;
            k_max = 60;
            numericalMeasure = new String("EuclideanDistance");
            max_runs = 30;
            max_opt_steps = 100;
        } else {
            StringTokenizer tok = new StringTokenizer(parameterString, "_");
            k_min = new Integer(tok.nextToken());
            k_max = new Integer(tok.nextToken());
            numericalMeasure = tok.nextToken();
            max_runs = new Integer(tok.nextToken());
            max_opt_steps = new Integer(tok.nextToken());
        }

        //  The MeasureType is in our case always numerical
        measureType = new String("NumericalMeasures");
        // The used clustering algorithm is in our case always k-Means
        clusteringAlgorithm = new String("KMeans");
    }

    /*
     * (non-Javadoc)
     * @see amuse.interfaces.AmuseTaskInterface#initialize()
     */
    public void initialize() throws NodeException {
        try {
            LibraryInitializer.initializeRapidMiner();
        } catch (Exception e) {
            throw new NodeException("Could not initialize RapidMiner: " + e.getMessage());
        }
    }

    /*
     * (non-Javadoc)
     * @see amuse.nodes.classifier.interfaces.ClassifierInterface#classify(java.lang.String, java.util.ArrayList, java.lang.String)
     */
    /* (non-Javadoc)
     * @see amuse.nodes.classifier.interfaces.ClassifierInterface#classify(java.lang.String)
     */
    //TODO: pathToModelFile String isn't needed by clustering methods so where would they get it to call this method?
    public void classify(String pathToModelFile) throws NodeException {
        /* Gets the DataSet given by the user in the Classifier AMUSE task */
        DataSet dataSetToClassify = ((DataSetInput)((ClassificationConfiguration)this.correspondingScheduler.
         getConfiguration()).getInputToClassify()).getDataSet();

        try {
            //TODO: Prepare Data (For example: create median for each feature) or don't, I don't know yet

            /* Create the RapidMiner process */
            Process process = new Process();

                // Create the XMeans Operator in RM
                Operator modelLearner = OperatorService.createOperator(XMeans.class);

                // Set the parameters like in the constructor
                    //TODO: Are all of those parameters correct?
                modelLearner.setParameter("k_min", new Integer(k_min).toString());
                modelLearner.setParameter("k_max", new Integer(k_max).toString());
                modelLearner.setParameter("numericalMeasure", this.numericalMeasure);
                modelLearner.setParameter("max_runs", new Integer(max_runs).toString());
                modelLearner.setParameter("max_optimization_steps", new Integer(max_opt_steps).toString());
                modelLearner.setParameter("measureType", this.measureType);
                modelLearner.setParameter("clusteringAlgorithm", this.clusteringAlgorithm);
                process.getRootOperator().getSubprocess(0).addOperator(modelLearner);

                // Write the cluster model
                    //TODO: is "/cluster_Model" ok?
                RepositoryStorer modelWriter = OperatorService.createOperator(RepositoryStorer.class);
                modelWriter.setParameter(RepositoryStorer.PARAMETER_REPOSITORY_ENTRY, "//" + LibraryInitializer.RAPIDMINER_REPO_NAME + "/cluster_model");
                process.getRootOperator().getSubprocess(0).addOperator(modelWriter);

                // Connect the ports so RapidMiner knows whats up
                InputPort modelLearnerInputPort = modelLearner.getInputPorts().getPortByName("example set");
                    //TODO: return the "cluster model" or the "clustered set"?
                OutputPort modelLearnerOutputPort = modelLearner.getOutputPorts().getPortByName("cluster model");
                    //TODO: the writer expects an example set as input, is this a problem here?
                InputPort modelWriterInputPort = modelWriter.getInputPorts().getPortByName("input");
                OutputPort processOutputPort = process.getRootOperator().getSubprocess(0).getInnerSources().getPortByIndex(0);

                modelLearnerOutputPort.connectTo(modelWriterInputPort);
                processOutputPort.connectTo(modelLearnerInputPort);

                // Run the RapidMiner-Process - XMeans needs an ExampleSet so it's being converted here
                process.run(new IOContainer(dataSetToClassify.convertToRapidMinerExampleSet()));

            // Copy results to the result database
                //TODO: Do I need to convert the result file to something readable? What is it even?
            String outputPath = AmusePreferences.get(KeysStringValue.AMUSE_PATH) + File.separator + "experiments" + File.separator + "XMeans_Result";
            FileOperations.copy(new File(LibraryInitializer.REPOSITORY_PATH + File.separator + "cluster_model.ioo"), new File(outputPath));

        } catch(Exception e) {
            throw new NodeException("Error clustering data: " + e.getMessage());
        }
    }
}