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
import amuse.preferences.AmusePreferences;
import amuse.preferences.KeysStringValue;
import amuse.util.AmuseLogger;
import amuse.util.LibraryInitializer;

/**
 * Clusters given data by using the DBScan Algorithm by RapidMiner* 
 * @author Pauline Speckmann
 */
public class DBScanAdapter extends AmuseTask implements ClassifierUnsupervisedInterface {
	
	private double epsilon;
	private int min_points;
	
	/** Determines the numerical distance measure to be used */
	private String measureType;

	@Override
	public void setParameters(String parameterString) throws NodeException {
		// Should the default parameters be used? Or are values given?
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
        
        //Check if all paramters are in range
        if (epsilon < 0.0 || min_points < 1) {
        	throw new NodeException("DBScan: One of the parameters was out of range!");
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
                Operator clusterer = OperatorService.createOperator(DBScan.class);

                // Set the parameters and add the clustering to the process
                clusterer.setParameter("epsilon", new Double(epsilon).toString());
                clusterer.setParameter("min_points", new Integer(min_points).toString());
                clusterer.setParameter("remove_unlabeled", "false");
                
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
                	//AmuseLogger.write("DBScanAdapter", Level.DEBUG, "Ports were connected");

            // Run the RapidMiner-Process - XMeans needs an ExampleSet so it's being converted here
            ExampleSet exampleSet = dataSetToClassify.convertToRapidMinerExampleSet();
            IOContainer result = process.run(new IOContainer(exampleSet));
            AmuseLogger.write("DBScanAdapter", Level.DEBUG, "RapidMiner DBScanAdapter finished successfully");

         	// Get the RapidMiner Result
            exampleSet = result.get(ExampleSet.class);
         	DataSet resultDataSet = new DataSet(exampleSet);
         	
         	// Edit the result so AMUSE can work with it again
         	
         		// Copy the result DataSet but without the id attribute (that RapidMiner put there)
         		DataSet amuseDataSet = new DataSet("DBScanAdapterResultDataSet");
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
        			AmuseLogger.write("DBScanAdapter", Level.ERROR , "There is only 1 giant Cluster and everything is in it!");
        		}
        		AmuseLogger.write("DBScanAdapter", Level.DEBUG, "There are " + maxClusterValue + "+1 different clusters.");
        		
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
        		AmuseLogger.write("DBScanAdapter", Level.DEBUG, "DBScanAdapter successfully edited the result to AMUSE standad");
    		
    		// Give the amuseDataSet to the ClassificationConfiguration so it may be put together and saved there
            ((ClassificationConfiguration)(this.correspondingScheduler.getConfiguration())).setInputToClassify(new DataSetInput(amuseDataSet));
            
            // Save to .arff file
            String outputPath = AmusePreferences.get(KeysStringValue.AMUSE_PATH) + File.separator + "experiments" + File.separator + "DBScanAdapter_Result.arff";
            amuseDataSet.saveToArffFile(new File(outputPath));

        } catch(Exception e) {
            throw new NodeException("Error clustering data: " + e.getMessage());
        }
	}

}
