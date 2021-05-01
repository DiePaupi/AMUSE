package amuse.nodes.classifier.methods.unsupervised;

import java.io.File;
import java.util.ArrayList;
import java.util.StringTokenizer;

import org.apache.log4j.Level;

import com.rapidminer.Process;
import com.rapidminer.example.ExampleSet;
import com.rapidminer.operator.IOContainer;
import com.rapidminer.operator.Operator;
import com.rapidminer.operator.clustering.clusterer.DBScan;
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
import amuse.nodes.classifier.methods.unsupervised.supportclasses.Testing;
import amuse.preferences.AmusePreferences;
import amuse.preferences.KeysStringValue;
import amuse.util.AmuseLogger;
import amuse.util.LibraryInitializer;

/**
 * Clusters given data by using the DBScan Algorithm by RapidMiner
 * @author Pauline Speckmann
 */
public class DBScanAdapter extends AmuseTask implements ClassifierUnsupervisedInterface {
	
	/** Epsilon indicates the radius around any object in which other objects are considered "near" */
	private double epsilon;
	/** min_points defines the minimal number of points (objects) which need to be near each other in order to form a cluster */
	private int min_points;
	
	/** Determines the numerical distance measure to be used */
	private String measureType;

	@Override
	/** Receives the parameters given by the user and sets them accordingly */
	public void setParameters(String parameterString) throws NodeException {
		// Should the default parameters be used or are values given?
        if(parameterString == "" || parameterString == null) {
        	epsilon = 1.0;
        	min_points = 5;
        	measureType = "EuclideanDistance";
        } else {
            StringTokenizer tok = new StringTokenizer(parameterString, "_");
            epsilon = new Double(tok.nextToken());
            min_points = new Integer(tok.nextToken());
            measureType = tok.nextToken();
        }
        
        // Check if all parameters are in range
        if (epsilon < 0.0 || min_points < 1) {
        	throw new NodeException("DBScan: One of the parameters was out of range!");
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

                // Create the RapidMiner DBScan operator
                Operator clusterer = OperatorService.createOperator(DBScan.class);

                // Set the parameters
                clusterer.setParameter("epsilon", new Double(epsilon).toString());
                clusterer.setParameter("min_points", new Integer(min_points).toString());
                clusterer.setParameter("remove_unlabeled", "false");
                
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
            AmuseLogger.write("DBScanAdapter", Level.DEBUG, "RapidMiner DBScanAdapter finished successfully");

         	// Get the RapidMiner Result
            exampleSet = result.get(ExampleSet.class);
         	DataSet resultDataSet = new DataSet(exampleSet);
         	
         	
         	// Edit the result so AMUSE can work with it again -----------------------------------------------------------------------------------------------------
         	
         		// Copy the result DataSet but without the id attribute (that RapidMiner put there)
         		DataSet amuseDataSet = new DataSet("DBScanAdapterResultDataSet");
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
        		for (int i=0; i<valueAmount; i++) {
        			String currentRawCluster = (String) clusterResultAtt.getValueAt(i);
        				// The value should be something like "cluster_1" so delete the first 8 chars to get the cluster number
        			currentRawCluster = currentRawCluster.substring(8);
        			int currClusterInt = Integer.parseInt(currentRawCluster);
        			clusterResultArray[i] = currClusterInt;
        			
        			if (maxClusterValue < currClusterInt) {
        				maxClusterValue = currClusterInt;
        			}
        		}
        		if (maxClusterValue == 0) {
        			AmuseLogger.write("DBScanAdapter", Level.ERROR , "There is only 1 giant Cluster and everything is in it!");
        		}
        		AmuseLogger.write("DBScanAdapter", Level.DEBUG, "There are " + (maxClusterValue+1) + " different clusters.");
        		
        		// Create new Cluster Attributes
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
        		AmuseLogger.write("DBScanAdapter", Level.DEBUG, "DBScanAdapter successfully edited the result to AMUSE standad");
        		
        		Testing.printMinMax(amuseDataSet);
    		
    		// Give the amuseDataSet to the ClassificationConfiguration so it may be put together and saved there
            ((ClassificationConfiguration)(this.correspondingScheduler.getConfiguration())).setInputToClassify(new DataSetInput(amuseDataSet));
            
            // Save to .arff file
            //String outputPath = AmusePreferences.get(KeysStringValue.AMUSE_PATH) + File.separator + "experiments" + File.separator + "DBScanAdapter_Result.arff";
            //amuseDataSet.saveToArffFile(new File(outputPath));

        } catch(Exception e) {
            throw new NodeException("Error clustering data: " + e.getMessage());
        }
	}

}
