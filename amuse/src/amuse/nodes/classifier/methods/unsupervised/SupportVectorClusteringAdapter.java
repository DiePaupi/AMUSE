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

public class SupportVectorClusteringAdapter extends AmuseTask implements ClassifierUnsupervisedInterface {

	/** The SVM kernel type */
	private int kernelType; //0=dot, 1=radial, 2=polynomial, (3=neural)
	/** Size of the cache for kernel evaluations in MB */
	private int cacheSize;
	/** The number of virtual sample points to check for neighborship */
	private int numSamplePoints;
	
	/** The SVM kernel parameter gamma (radial, default 1.0) */
	private double kernel_gamma;
	/** The SVM kernel parameter degree (polynomial, default 2) */
	private int kernel_degree;
	
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
	/** Receives the parameters given by the user and sets them accordingly */
	public void setParameters(String parameterString) throws NodeException {
		// Should the default parameters be used or are values given?
        if(parameterString == "" || parameterString == null) {
        	kernelType = 1; 
        	cacheSize = 200;
        	numSamplePoints = 20;
        	
        	kernel_gamma = 1.0;
        	
        	min_pts = 2;
        	convergence_epsilon = 0.001;
        	max_iterations = 100000;
        	p = 0.0;
        	r = -1.0;
        } else {
            StringTokenizer tok = new StringTokenizer(parameterString, "_");
            
            String kernelTypeName = tok.nextToken();
            if (kernelTypeName.equals("dot")) {
            	kernelType = 0;
            } else if (kernelTypeName.equals("radial")) {
            	kernelType = 1;
            } else if (kernelTypeName.equals("polynomial")) {
            	kernelType = 2;
            } else {
            	throw new NodeException("SVC: The kernel type wasn't definied correctly.");
            }
            
            cacheSize = new Integer(tok.nextToken());
            numSamplePoints = new Integer(tok.nextToken());
            
            if (kernelType == 1) {
            	kernel_gamma = new Double(tok.nextToken());
            } else if (kernelType == 2) {
            	kernel_degree = new Integer(tok.nextToken());
            } else {
            	tok.nextToken(); // For kernel type = dot, skip this parameter
            }
            
            min_pts = new Integer(tok.nextToken());
            convergence_epsilon = new Double(tok.nextToken());
            max_iterations = new Integer(tok.nextToken());
            p = new Double(tok.nextToken());
            r = new Double(tok.nextToken());
        }
        
        // Check if all parameters are in range
        if (kernelType < 0 || kernelType > 2 || cacheSize < 0 || numSamplePoints < 1 || min_pts < 0 || p < 0.0 || p > 1.0 || r < -1.0) {
        	throw new NodeException("SVC: One of the parameters was out of range!");
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

            	// Create the RapidMiner SVClustering operator
                Operator clusterer = OperatorService.createOperator(SVClustering.class);

                // Set the parameters
                clusterer.setParameter(SVClustering.PARAMETER_KERNEL_TYPE, new Integer(kernelType).toString());
                clusterer.setParameter(SVClustering.PARAMETER_KERNEL_CACHE, new Integer(cacheSize).toString());
                clusterer.setParameter(SVClustering.PARAMETER_NUMBER_SAMPLE_POINTS, new Integer(numSamplePoints).toString());
                clusterer.setParameter(SVClustering.MIN_PTS_NAME, new Integer(min_pts).toString());
                clusterer.setParameter(SVClustering.PARAMETER_CONVERGENCE_EPSILON, new Double(convergence_epsilon).toString());
                clusterer.setParameter(SVClustering.PARAMETER_MAX_ITERATIONS, new Integer(max_iterations).toString());
                clusterer.setParameter(SVClustering.PARAMETER_P, new Double(p).toString());
                clusterer.setParameter(SVClustering.PARAMETER_R, new Double(r).toString());
                
                // Set the kernel value according to the kernel type
                if (kernelType == 1) {
                	clusterer.setParameter(SVClustering.PARAMETER_KERNEL_GAMMA, new Double(kernel_gamma).toString());
                } else if (kernelType == 2) {
                	clusterer.setParameter(SVClustering.PARAMETER_KERNEL_DEGREE, new Integer(kernel_degree).toString());
                }
                
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
            AmuseLogger.write(this.getClass().getName(), Level.DEBUG, "RapidMiner SVC finished successfully");

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
    			int valueAmount = resultDataSet.getAttribute(0).getValueCount();
    			Attribute clusterResultAtt = resultDataSet.getAttribute("cluster");
    			int[] clusterResultArray = new int[valueAmount];
    			
    			int maxClusterValue = 0;
        		for (int i=0; i<valueAmount; i++) {
        			String currentRawCluster = (String) clusterResultAtt.getValueAt(i);
        			
        			// value should be something like "cluster_1" (so delete the first 8 chars) OR "noise"
        			if (currentRawCluster.contentEquals("noise")) {
        				clusterResultArray[i] = -1;
        				((ClassificationConfiguration)(this.correspondingScheduler.getConfiguration())).setNoise(true);
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
        			AmuseLogger.write(this.getClass().getName(), Level.ERROR , "There is only 1 giant Cluster and everything is in it!");
        		}
        		AmuseLogger.write(this.getClass().getName(), Level.DEBUG, "There are " + (maxClusterValue+1) + " different clusters.");
        		
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
    			
        		AmuseLogger.write(this.getClass().getName(), Level.DEBUG, "SVC successfully edited the result to AMUSE standard");
        		
        		
    		// Give the amuseDataSet to the ClassificationConfiguration so it may be put together and saved there
            ((ClassificationConfiguration)(this.correspondingScheduler.getConfiguration())).setInputToClassify(new DataSetInput(amuseDataSet));
            
            //Testing.printMinMax(amuseDataSet);
            
            // Save to .arff file
            String outputPath = AmusePreferences.get(KeysStringValue.AMUSE_PATH) + File.separator + "experiments" + File.separator + "SVC_Result.arff";
            amuseDataSet.saveToArffFile(new File(outputPath));

        } catch(Exception e) {
            throw new NodeException("Error clustering data: " + e.getMessage());
        }
	}

}
