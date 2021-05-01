package amuse.nodes.classifier.methods.unsupervised;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;
import org.apache.log4j.Level;
import com.rapidminer.Process;
import com.rapidminer.example.ExampleSet;
import com.rapidminer.operator.IOContainer;
import com.rapidminer.operator.Operator;
import com.rapidminer.operator.clustering.clusterer.FastKMeans;
import com.rapidminer.operator.ports.InputPort;
import com.rapidminer.operator.ports.OutputPort;
import com.rapidminer.parameter.ParameterType;
import com.rapidminer.tools.OperatorService;
import com.rapidminer.tools.math.similarity.DistanceMeasures;

import amuse.data.datasets.ClassifierConfigSet;
import amuse.data.io.DataSet;
import amuse.data.io.DataSetInput;
import amuse.data.io.attributes.Attribute;
import amuse.data.io.attributes.NumericAttribute;
import amuse.interfaces.nodes.NodeException;
import amuse.interfaces.nodes.methods.AmuseTask;
import amuse.nodes.classifier.ClassificationConfiguration;
import amuse.nodes.classifier.interfaces.ClassifierUnsupervisedInterface;
import amuse.nodes.classifier.methods.unsupervised.supportclasses.Testing;
import amuse.preferences.AmusePreferences;
import amuse.preferences.KeysStringValue;
import amuse.util.AmuseLogger;
import amuse.util.LibraryInitializer;

/**
 * Clusters given data by using the FastKMeans Algorithm by RapidMiner
 * @author Pauline Speckmann
 */
public class FastKMeansAdapter extends AmuseTask implements ClassifierUnsupervisedInterface {

	/** k determines the number of clusters */
	private int k;
	/** Determines the upper bound for the optimization steps */
	private int max_opt_steps;
	/** Determines the upper bound for the number of runs */
	private int max_runs;
	
	/** Determines the numerical distance measure to be used */
	private String measureType;
	
	/** Indicates if RapidMiner should determine good start values */
	private boolean determine_good_start_values;
	/** Indicates if a local random seed should be used */
	private boolean use_local_random_seed;
	/** Value of the local random seed */
	private int local_random_seed;
	
	
	
	@Override
	/** Receives the parameters given by the user and sets them accordingly */
	public void setParameters(String parameterString) throws NodeException {
		// Should the default parameters be used or are values given?
        if(parameterString == "" || parameterString == null) {
            k = 5;
            max_opt_steps = 100;
            max_runs = 10;
            measureType = "EuclideanDistance";
            determine_good_start_values = true;
            use_local_random_seed = false;
            local_random_seed = 1992;
        } else {
            StringTokenizer tok = new StringTokenizer(parameterString, "_");
            k = new Integer(tok.nextToken());
            max_opt_steps = new Integer(tok.nextToken());
            max_runs = new Integer(tok.nextToken());
            measureType = tok.nextToken();
            determine_good_start_values = Boolean.parseBoolean(tok.nextToken());
            use_local_random_seed = Boolean.parseBoolean(tok.nextToken());
            local_random_seed = new Integer(tok.nextToken());
        }
        
        // Check if all parameters are in range
        if (k < 2 || max_runs < 1 || max_opt_steps < 1 || local_random_seed < 1) {
        	throw new NodeException("FastKMeans: One of the parameters was out of range!");
        }
	}

	
	@Override
	/** Initializes the RapidMiner library */
	public void initialize() throws NodeException {
		try {
            LibraryInitializer.initializeRapidMiner();
        } catch (Exception e) {
            throw new NodeException("Could not initialize RapidMiner: " + e.getMessage());
        }
	}

	@Override
	/** Creates a RapidMiner process, sets the parameters, connects the ports, runs the process and edits the RapidMiner results to a AMUSE compatible format */
	public void classify() throws NodeException {
		
		/** DataSet of music (partitions) to be classified */
        DataSet dataSetToClassify = ((DataSetInput)((ClassificationConfiguration)this.correspondingScheduler.getConfiguration()).getInputToClassify()).getDataSet();

        try {
        	// Create the RapidMiner process
            Process process = new Process();

                // Create the RapidMiner FastKMeans operator
                Operator clusterer = OperatorService.createOperator(FastKMeans.class);

                // Set the parameters
                clusterer.setParameter("determine_good_start_values", String.valueOf(determine_good_start_values));
                clusterer.setParameter("remove_unlabeled", "false");
                clusterer.setParameter("k", new Integer(k).toString());
                clusterer.setParameter("max_optimization_steps", new Integer(max_opt_steps).toString());
                clusterer.setParameter("max_runs", new Integer(max_runs).toString());
                clusterer.setParameter("use_local_random_seed", String.valueOf(use_local_random_seed));
                clusterer.setParameter("local_random_seed", new Integer(local_random_seed).toString());
                
                // Set the distance measure
                clusterer.setParameter(DistanceMeasures.PARAMETER_MEASURE_TYPES, DistanceMeasures.MEASURE_TYPES[DistanceMeasures.NUMERICAL_MEASURES_TYPE]);
                clusterer.setParameter(DistanceMeasures.PARAMETER_NUMERICAL_MEASURE, measureType);
                
                // Add the clustering operator to the process
                process.getRootOperator().getSubprocess(0).addOperator(clusterer);

                // Connect the ports so RapidMiner knows what's up
                InputPort clustererInputPort = clusterer.getInputPorts().getPortByName("example set");
                	// Return the the "clustered set" and not the "cluster model"
                OutputPort clustererOutputPort = clusterer.getOutputPorts().getPortByName("clustered set");
                InputPort processInputPort = process.getRootOperator().getSubprocess(0).getInnerSinks().getPortByIndex(0);
                OutputPort processOutputPort = process.getRootOperator().getSubprocess(0).getInnerSources().getPortByIndex(0);
                processOutputPort.connectTo(clustererInputPort);
                clustererOutputPort.connectTo(processInputPort);

            // The RapidMiner operator needs the input as an ExampleSet (so it's being converted here)
            ExampleSet exampleSet = dataSetToClassify.convertToRapidMinerExampleSet();
            // Run the RapidMiner-Process 
            IOContainer result = process.run(new IOContainer(exampleSet));
            AmuseLogger.write("FastKMeansAdapter", Level.DEBUG, "RapidMiner FastKMeansAdapter finished successfully");

         	// Get the RapidMiner Result
            exampleSet = result.get(ExampleSet.class);
         	DataSet resultDataSet = new DataSet(exampleSet);
         	
         	
         	// Edit the result so AMUSE can work with it again -----------------------------------------------------------------------------------------------------
         	
         		// Copy the result DataSet but without the id attribute (that RapidMiner put there)
         		DataSet amuseDataSet = new DataSet("FastKMeansAdapterResultDataSet");
    			for (int attributeNumber=0; attributeNumber<resultDataSet.getAttributeCount(); attributeNumber++) {
    				// If the attribute is NOT the id or cluster indication: copy the attribute to the amuseDataSet
    				if (!resultDataSet.getAttribute(attributeNumber).getName().equals("id") && !resultDataSet.getAttribute(attributeNumber).getName().equals("cluster")) {
    					amuseDataSet.addAttribute(resultDataSet.getAttribute(attributeNumber));
    				}
    			}
    			
    			// Get the cluster numbers from the resultDataSet and  count how many different clusters there are 
    			// (because that's how many new attributes are needed)
    			Attribute clusterResultAtt = resultDataSet.getAttribute("cluster");
    			int valueAmount = clusterResultAtt.getValueCount();
    			int[] clusterResultArray = new int[valueAmount];
    			
    			int maxClusterValue = 0;
        		for (int valueNumber=0; valueNumber<valueAmount; valueNumber++) {
        			String currentRawCluster = (String) clusterResultAtt.getValueAt(valueNumber);
        				// The value should be something like "cluster_1" so delete the first 8 chars to get the cluster number
        			currentRawCluster = currentRawCluster.substring(8);
        			int currClusterInt = Integer.parseInt(currentRawCluster);
        			clusterResultArray[valueNumber] = currClusterInt;
        			
        			if (maxClusterValue < currClusterInt) {
        				maxClusterValue = currClusterInt;
        			}
        		}
        		if (maxClusterValue == 0) {
        			AmuseLogger.write("FastKMeansAdapter", Level.ERROR , "There is only 1 giant Cluster and everything is in it!");
        		}
        		AmuseLogger.write("FastKMeansAdapter", Level.DEBUG, "There are " + (maxClusterValue+1) + " different clusters.");
        		
        		// Create new cluster attributes
        		for (int clusterNumber=0; clusterNumber<maxClusterValue+1; clusterNumber++) {
        			ArrayList<Double> clusterXvalueList = new ArrayList<Double>();
        			// Go through the partitions and check their assigned cluster
        			for (int partitionNumber=0; partitionNumber < clusterResultArray.length; partitionNumber++) {
        				// If the current partitions assigned cluster number matches this newly created cluster, set the value to 1 (otherwise to 0)
        				if (clusterResultArray[partitionNumber] == clusterNumber) {
        					clusterXvalueList.add(partitionNumber, 1.0);
        				} else {
        					clusterXvalueList.add(partitionNumber, 0.0);
        				}
        			}
        			Attribute clusterX = new NumericAttribute("cluster_" + clusterNumber, clusterXvalueList);
        			amuseDataSet.addAttribute(clusterX);
        		}
        		AmuseLogger.write("FastKMeansAdapter", Level.DEBUG, "FastKMeansAdapter successfully edited the result to AMUSE standad");
        		
        		//Testing.printMinMax(amuseDataSet);
            
            // Save the DataSet to .arff file
            //String outputPath = AmusePreferences.get(KeysStringValue.AMUSE_PATH) + File.separator + "experiments" + File.separator + "FastKMeansAdapter_Result.arff";
            //amuseDataSet.saveToArffFile(new File(outputPath));
            
            // Give the amuseDataSet to the ClassificationConfiguration so it may be put together and saved there
            ((ClassificationConfiguration)(this.correspondingScheduler.getConfiguration())).setInputToClassify(new DataSetInput(amuseDataSet));

        } catch(Exception e) {
            throw new NodeException("Error clustering data: " + e.getMessage());
        }
	}

}
