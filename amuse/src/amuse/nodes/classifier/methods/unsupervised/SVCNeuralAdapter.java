package amuse.nodes.classifier.methods.unsupervised;

import java.io.File;
import java.util.ArrayList;
import java.util.StringTokenizer;

import org.apache.log4j.Level;

import com.rapidminer.Process;
import com.rapidminer.example.ExampleSet;
import com.rapidminer.operator.IOContainer;
import com.rapidminer.operator.Operator;
import com.rapidminer.operator.clustering.clusterer.SVClustering;
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
import amuse.nodes.classifier.methods.unsupervised.supportclasses.Testing;
import amuse.preferences.AmusePreferences;
import amuse.preferences.KeysStringValue;
import amuse.util.AmuseLogger;
import amuse.util.LibraryInitializer;

public class SVCNeuralAdapter extends AmuseTask implements ClassifierUnsupervisedInterface {

	/** Size of the cache for kernel evaluations in MB */
	private int cacheSize;
	/** The number of virtual sample points to check for neighborship */
	private int numSamplePoints;
	
	/** The SVM kernel parameter a (neural) */
	private double kernel_a;
	/** The SVM kernel parameter b (neural) */
	private double kernel_b;
	
	/** The minimal number of points in each cluster */
	private int min_pts;
	/** Precision on the KKT conditions */
	private double convergence_epsilon;
	/** Stop after this many iterations */
	private int max_iterations;
	/** The fraction of allowed outliers */
	private double p;
	/** Use this radius instead of the calculated one (-1 for calculated radius) */
	private double r;
	
	@Override
	public void setParameters(String parameterString) throws NodeException {
		// Should the default parameters be used? Or are values given?
        if(parameterString == "" || parameterString == null) {
        	cacheSize = 200;
        	numSamplePoints = 20;
        	
        	kernel_a = 1.0;
        	kernel_b = 0.0;
        	
        	min_pts = 2;
        	convergence_epsilon = 0.001;
        	max_iterations = 100000;
        	p = 0.0;
        	r = -1.0;
        } else {
            StringTokenizer tok = new StringTokenizer(parameterString, "_");
            cacheSize = new Integer(tok.nextToken());
            numSamplePoints = new Integer(tok.nextToken());
            
            kernel_a = new Double(tok.nextToken());
            kernel_b = new Double(tok.nextToken());
            
            min_pts = new Integer(tok.nextToken());
            convergence_epsilon = new Double(tok.nextToken());
            max_iterations = new Integer(tok.nextToken());
            p = new Double(tok.nextToken());
            r = new Double(tok.nextToken());
        }
        
      //Check if all paramters are in range
        if (cacheSize < 0 || numSamplePoints < 1
        		|| min_pts < 0 || p < 0.0 || p > 1.0 || r < -1.0) {
        	throw new NodeException("SVC: One of the parameters was out of range!");
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
                Operator clusterer = OperatorService.createOperator(SVClustering.class);

                // Set the parameters and add the clustering to the process
                clusterer.setParameter(SVClustering.PARAMETER_KERNEL_TYPE, new Integer(3).toString());
                clusterer.setParameter(SVClustering.PARAMETER_KERNEL_CACHE, new Integer(cacheSize).toString());
                clusterer.setParameter(SVClustering.PARAMETER_NUMBER_SAMPLE_POINTS, new Integer(numSamplePoints).toString());
                clusterer.setParameter(SVClustering.PARAMETER_KERNEL_A, new Double(kernel_a).toString());
                clusterer.setParameter(SVClustering.PARAMETER_KERNEL_B, new Double(kernel_b).toString());
                clusterer.setParameter(SVClustering.MIN_PTS_NAME, new Integer(min_pts).toString());
                clusterer.setParameter(SVClustering.PARAMETER_CONVERGENCE_EPSILON, new Double(convergence_epsilon).toString());
                clusterer.setParameter(SVClustering.PARAMETER_MAX_ITERATIONS, new Integer(max_iterations).toString());
                clusterer.setParameter(SVClustering.PARAMETER_P, new Double(p).toString());
                clusterer.setParameter(SVClustering.PARAMETER_R, new Double(r).toString());
                
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
            AmuseLogger.write("SVC", Level.DEBUG, "RapidMiner SVC finished successfully");

         	// Get the RapidMiner Result
            exampleSet = result.get(ExampleSet.class);
         	DataSet resultDataSet = new DataSet(exampleSet);
         	
         	// Edit the result so AMUSE can work with it again
         	
         		// Copy the result DataSet but without the id attribute (that RapidMiner put there)
         		DataSet amuseDataSet = new DataSet("SVCResultDataSet");
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
        			
        			// value should be something like "cluster_1" so delete the first 8 chars OR "noise"
        			if (currentRawCluster.contentEquals("noise")) {
        				clusterResultArray[i] = -1;
        			} else {
        				currentRawCluster = currentRawCluster.substring(8);
            			
            			int currClusterInt = Integer.parseInt(currentRawCluster);
            			clusterResultArray[i] = currClusterInt;
            			if (maxClusterValue < currClusterInt) {
            				maxClusterValue = currClusterInt;
            			}
        			}
        		}
        		if (maxClusterValue == 0) {
        			AmuseLogger.write("SVC", Level.ERROR , "There is only 1 giant Cluster and everything is in it!");
        		}
        		AmuseLogger.write("SVC", Level.DEBUG, "There are " + (maxClusterValue+1) + " different clusters.");
        		
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
        		
        		// Create an attribute for possible noise
        		ArrayList<Double> noisevalueList = new ArrayList<Double>();
    			for (int i=0; i < clusterResultArray.length; i++) {
    				int currClusterInt = clusterResultArray[i];
    				if (currClusterInt == -1) {
    					noisevalueList.add(i, 1.0);
    				} else {
    					noisevalueList.add(i, 0.0);
    				}
    			}
    			Attribute noise = new NumericAttribute("noise", noisevalueList);
    			amuseDataSet.addAttribute(noise);
    			
        		AmuseLogger.write("SVC", Level.DEBUG, "SVC successfully edited the result to AMUSE standad");
        		
        		Testing.printMinMax(amuseDataSet);
    		
    		// Give the amuseDataSet to the ClassificationConfiguration so it may be put together and saved there
        	((ClassificationConfiguration)(this.correspondingScheduler.getConfiguration())).setNoise(true);
            ((ClassificationConfiguration)(this.correspondingScheduler.getConfiguration())).setInputToClassify(new DataSetInput(amuseDataSet));
            
            // Save to .arff file
            String outputPath = AmusePreferences.get(KeysStringValue.AMUSE_PATH) + File.separator + "experiments" + File.separator + "SVC_Result.arff";
            amuseDataSet.saveToArffFile(new File(outputPath));

        } catch(Exception e) {
            throw new NodeException("Error clustering data: " + e.getMessage());
        }
	}

}
