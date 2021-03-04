package amuse.nodes.classifier.methods.unsupervised;

import java.io.File;
import java.util.ArrayList;
import java.util.StringTokenizer;

import org.apache.log4j.Level;

import com.rapidminer.Process;
import com.rapidminer.example.ExampleSet;
import com.rapidminer.operator.IOContainer;
import com.rapidminer.operator.Operator;
import com.rapidminer.operator.clustering.clusterer.KMeans;
import com.rapidminer.operator.clustering.clusterer.KMedoids;
import com.rapidminer.operator.ports.InputPort;
import com.rapidminer.operator.ports.OutputPort;
import com.rapidminer.tools.OperatorService;
import com.rapidminer.tools.math.similarity.DistanceMeasures;

import amuse.data.io.DataSet;
import amuse.data.io.DataSetInput;
import amuse.data.io.attributes.Attribute;
import amuse.data.io.attributes.NumericAttribute;
import amuse.interfaces.nodes.NodeException;
import amuse.interfaces.nodes.methods.AmuseTask;
import amuse.nodes.classifier.ClassificationConfiguration;
import amuse.nodes.classifier.interfaces.ClassifierUnsupervisedInterface;
import amuse.preferences.AmusePreferences;
import amuse.preferences.KeysStringValue;
import amuse.util.AmuseLogger;
import amuse.util.LibraryInitializer;

/**
 * Clusters given data by using the K-Medioids Algorithm by RapidMiner
 * @author Pauline Speckmann
 */
public class KMedoidsAdapter extends AmuseTask implements ClassifierUnsupervisedInterface {

	/** k determines the number of clusters */ 
	private int k;
	/** Determines the upper bound for the optimization steps */
	private int maxOptimizationSteps;
	/** Determines the upper bound for the number of runs */
	private int maxRuns;
	
	/** Determines the numerical distance measure to be used */
	private String measureType;
	
	/** Should a local random seed be used? */
	private boolean use_local_random_seed;
	/** Value of the local random seed */
	private int local_random_seed;
	
	@Override
	public void setParameters(String parameterString) throws NodeException {
		// Should the default parameters be used? Or are values given?
        if(parameterString == "" || parameterString == null) {
            k = 5;
            maxOptimizationSteps = 100;
            maxRuns = 10;
            measureType = "EuclideanDistance";
            use_local_random_seed = false;
            local_random_seed = 1992;
        } else {
            StringTokenizer tok = new StringTokenizer(parameterString, "_");
            k = new Integer(tok.nextToken());
            maxOptimizationSteps = new Integer(tok.nextToken());
            maxRuns = new Integer(tok.nextToken());
            measureType = tok.nextToken();
            use_local_random_seed = Boolean.parseBoolean(tok.nextToken());
            local_random_seed = new Integer(tok.nextToken());
        }
        
      //Check if all paramters are in range
        if (k < 2 || maxRuns < 1 || maxOptimizationSteps < 1 || local_random_seed < 1) {
        	throw new NodeException("KMedoidsAdapter: One of the parameters was out of range!");
        }
	}

	@Override
	public void initialize() throws NodeException {
		try {
            LibraryInitializer.initializeRapidMiner();
        } catch (Exception e) {
            throw new NodeException("Could not initialize RapidMiner: " + e.getMessage());
        }
	}

	@Override
	public void classify() throws NodeException {
		/* Gets the DataSet given by the user in the Classifier AMUSE task */
        DataSet dataSetToClassify = ((DataSetInput)((ClassificationConfiguration)this.correspondingScheduler.
         getConfiguration()).getInputToClassify()).getDataSet();

        try {
            /* Create the RapidMiner process */
            Process process = new Process();

                // Create the XMeans Operator in RM
                Operator clusterer = OperatorService.createOperator(KMedoids.class);

                // Set the parameters and add the clustering to the process
                clusterer.setParameter("remove_unlabeled", "false");
                clusterer.setParameter("k", new Integer(k).toString());
                clusterer.setParameter("max_optimization_steps", new Integer(maxOptimizationSteps).toString());
                clusterer.setParameter("max_runs", new Integer(maxRuns).toString());
                clusterer.setParameter("use_local_random_seed", String.valueOf(use_local_random_seed));
                clusterer.setParameter("local_random_seed", new Integer(local_random_seed).toString());
                
                // Set the distance measure
                clusterer.setParameter(DistanceMeasures.PARAMETER_MEASURE_TYPES, DistanceMeasures.MEASURE_TYPES[DistanceMeasures.NUMERICAL_MEASURES_TYPE]);
                clusterer.setParameter(DistanceMeasures.PARAMETER_NUMERICAL_MEASURE, measureType);
                
                process.getRootOperator().getSubprocess(0).addOperator(clusterer);

                // Connect the ports so RapidMiner knows whats up
                InputPort clustererInputPort = clusterer.getInputPorts().getPortByName("example set");
                	// Return the the "clustered set" and not the "cluster model"
                OutputPort clustererOutputPort = clusterer.getOutputPorts().getPortByName("clustered set");
                InputPort processInputPort = process.getRootOperator().getSubprocess(0).getInnerSinks().getPortByIndex(0);
                OutputPort processOutputPort = process.getRootOperator().getSubprocess(0).getInnerSources().getPortByIndex(0);
                processOutputPort.connectTo(clustererInputPort);
                clustererOutputPort.connectTo(processInputPort);
                	//AmuseLogger.write("XMeansAdapter", Level.DEBUG, "Ports were connected");

            // Run the RapidMiner-Process - XMeans needs an ExampleSet so it's being converted here
            ExampleSet exampleSet = dataSetToClassify.convertToRapidMinerExampleSet();
            IOContainer result = process.run(new IOContainer(exampleSet));
            AmuseLogger.write("KMedoidsAdapter", Level.DEBUG, "RapidMiner KMedoidsAdapter finished successfully");

         	// Get the RapidMiner Result
            exampleSet = result.get(ExampleSet.class);
         	DataSet resultDataSet = new DataSet(exampleSet);
         	
         	// Edit the result so AMUSE can work with it again
         	
         		// Copy the result DataSet but without the id attribute (that RapidMiner put there)
         		DataSet amuseDataSet = new DataSet("KMedoidsAdapterResultDataSet");
    			for (int j=0; j<resultDataSet.getAttributeCount(); j++) {
    				// If the attribute is NOT the id copy the attribute to the amuseDataSet
    				if (!resultDataSet.getAttribute(j).getName().equals("id") && !resultDataSet.getAttribute(j).getName().equals("cluster")) {
    					amuseDataSet.addAttribute(resultDataSet.getAttribute(j));
    				}
    			}
    			
    			// Get the cluster numbers from the resultDataSet and 
    			// count how many different clusters there are (because that's how many new attributes are needed)
    			int valueAmount = resultDataSet.getAttribute(0).getValueCount();
    			Attribute clusterResultAtt = resultDataSet.getAttribute("cluster");
    			int[] clusterResultArray = new int[valueAmount];
    			
    			int maxClusterValue = 0;
        		for (int i=0; i<valueAmount; i++) {
        			String currentRawCluster = (String) clusterResultAtt.getValueAt(i);
        				// value should be something like "cluster_1" so delete the first 8 chars
        			currentRawCluster = currentRawCluster.substring(8);
        			
        			int currClusterInt = Integer.parseInt(currentRawCluster);
        			clusterResultArray[i] = currClusterInt;
        			if (maxClusterValue < currClusterInt) {
        				maxClusterValue = currClusterInt;
        			}
        		}
        		if (maxClusterValue == 0) {
        			AmuseLogger.write("KMedoidsAdapter", Level.ERROR , "There is only 1 giant Cluster and everything is in it!");
        		}
        		AmuseLogger.write("KMedoidsAdapter", Level.DEBUG, "There are " + maxClusterValue + "+1 different clusters.");
        		
        		// Create new Cluster Attributes
        		for (int c=0; c<maxClusterValue+1; c++) {
        			ArrayList<Double> clusterXvalueList = new ArrayList<Double>();
        			for (int i=0; i < clusterResultArray.length; i++) {
        				int currClusterInt = clusterResultArray[i];
        				if (currClusterInt == c) {
        					clusterXvalueList.add(i, 1.0);
        				} else {
        					clusterXvalueList.add(i, 0.0);
        				}
        			}
        			Attribute clusterX = new NumericAttribute("cluster_" + c, clusterXvalueList);
        			amuseDataSet.addAttribute(clusterX);
        		}
        		AmuseLogger.write("KMedoidsAdapter", Level.DEBUG, "KMedoidsAdapter successfully edited the result to AMUSE standad");
    		
    		// Give the amuseDataSet to the ClassificationConfiguration so it may be put together and saved there
            ((ClassificationConfiguration)(this.correspondingScheduler.getConfiguration())).setInputToClassify(new DataSetInput(amuseDataSet));
            
            // Save to .arff file
            String outputPath = AmusePreferences.get(KeysStringValue.AMUSE_PATH) + File.separator + "experiments" + File.separator + "KMedoidsAdapter_Result.arff";
            amuseDataSet.saveToArffFile(new File(outputPath));

        } catch(Exception e) {
            throw new NodeException("Error clustering data: " + e.getMessage());
        }
	}

}
