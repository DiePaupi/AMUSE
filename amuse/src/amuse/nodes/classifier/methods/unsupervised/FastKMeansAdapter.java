package amuse.nodes.classifier.methods.unsupervised;

import java.io.File;
import java.util.ArrayList;
import java.util.StringTokenizer;
import org.apache.log4j.Level;
import com.rapidminer.Process;
import com.rapidminer.example.ExampleSet;
import com.rapidminer.operator.IOContainer;
import com.rapidminer.operator.Operator;
import com.rapidminer.operator.clustering.clusterer.FastKMeans;
import com.rapidminer.operator.ports.InputPort;
import com.rapidminer.operator.ports.OutputPort;
import com.rapidminer.tools.OperatorService;
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
 * Clusters given data by using the FastKMeans Algorithm by RapidMiner
 * 
 * TODO: Distance Measure choice
 * 
 * @author Pauline Speckmann
 */
public class FastKMeansAdapter extends AmuseTask implements ClassifierUnsupervisedInterface {

	/** k determines the number of clusters and the others the upper bound for the optimization steps and runs */
	private int k;
	private int max_opt_steps;
	private int max_runs;
	
	/** Should RapidMiner determine good start values and / or remove unlabeled attributes? */
	private boolean determine_good_start_values;
	private boolean remove_unlabeled;
	
	@Override
	public void setParameters(String parameterString) throws NodeException {
		// Should the default parameters be used? Or are values given?
        if(parameterString == "" || parameterString == null) {
            k = 5;
            max_opt_steps = 100;
            max_runs = 10;
            determine_good_start_values = true;
            remove_unlabeled = false;
        } else {
            StringTokenizer tok = new StringTokenizer(parameterString, "_");
            k = new Integer(tok.nextToken());
            max_opt_steps = new Integer(tok.nextToken());
            max_runs = new Integer(tok.nextToken());
            determine_good_start_values = Boolean.parseBoolean(tok.nextToken());
            remove_unlabeled = Boolean.parseBoolean(tok.nextToken());
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
                Operator clusterer = OperatorService.createOperator(FastKMeans.class);

                // Set the parameters and add the clustering to the process
                clusterer.setParameter("determine_good_start_values", String.valueOf(determine_good_start_values));
                clusterer.setParameter("remove_unlabeled", String.valueOf(remove_unlabeled));
                clusterer.setParameter("k", new Integer(k).toString());
                clusterer.setParameter("max_optimization_steps", new Integer(max_opt_steps).toString());
                clusterer.setParameter("max_runs", new Integer(max_runs).toString());
                	//clusterer.setParameter("numericalMeasure", this.numericalMeasure);
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
            AmuseLogger.write("FastKMeansAdapter", Level.DEBUG, "RapidMiner FastKMeansAdapter finished successfully");

         	// Get the RapidMiner Result
            exampleSet = result.get(ExampleSet.class);
         	DataSet resultDataSet = new DataSet(exampleSet);
         	
         	// Edit the result so AMUSE can work with it again
         	
         		// Copy the result DataSet but without the id attribute (that RapidMiner put there)
         		DataSet amuseDataSet = new DataSet("FastKMeansAdapterResultDataSet");
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
        			AmuseLogger.write("FastKMeansAdapter", Level.ERROR , "There is only 1 giant Cluster and everything is in it!");
        		}
        		AmuseLogger.write("FastKMeansAdapter", Level.DEBUG, "There are " + maxClusterValue + "+1 different clusters.");
        		
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
        		AmuseLogger.write("FastKMeansAdapter", Level.DEBUG, "FastKMeansAdapter successfully edited the result to AMUSE standad");
    		
    		// Give the amuseDataSet to the ClassificationConfiguration so it may be put together and saved there
            ((ClassificationConfiguration)(this.correspondingScheduler.getConfiguration())).setInputToClassify(new DataSetInput(amuseDataSet));
            
            // Save to .arff file
            String outputPath = AmusePreferences.get(KeysStringValue.AMUSE_PATH) + File.separator + "experiments" + File.separator + "FastKMeansAdapter_Result";
            amuseDataSet.saveToArffFile(new File(outputPath));

        } catch(Exception e) {
            throw new NodeException("Error clustering data: " + e.getMessage());
        }
	}

}
