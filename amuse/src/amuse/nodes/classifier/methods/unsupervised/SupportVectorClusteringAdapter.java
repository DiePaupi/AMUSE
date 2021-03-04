package amuse.nodes.classifier.methods.unsupervised;

import java.util.StringTokenizer;

import amuse.interfaces.nodes.NodeException;
import amuse.interfaces.nodes.methods.AmuseTask;
import amuse.nodes.classifier.interfaces.ClassifierUnsupervisedInterface;
import amuse.util.LibraryInitializer;

public class SupportVectorClusteringAdapter extends AmuseTask implements ClassifierUnsupervisedInterface {

	/** The SVM kernel type */
	private int kernelType; //0=dot, 1=radial, 2=polynomial, 3=neural
	/** Size of the cache for kernel evaluations in MB */
	private int cacheSize;
	/** The number of virtual sample points to check for neighborship */
	private int numSamplePoints;
	
	/** The SVM kernel parameter gamma (radial) */
	private double kernel_gamma;
	/** The SVM kernel parameter degree (polynomial) */
	private int kernel_degree;
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
        	kernelType = 1; 
        	cacheSize = 200;
        	numSamplePoints = 20;
        	
        	kernel_gamma = 1.0;
        	kernel_degree = 2;
        	kernel_a = 1.0;
        	kernel_b = 0.0;
        	
        	min_pts = 2;
        	convergence_epsilon = 0.001;
        	p = 0.0;
        	r = -1.0;
        } else {
            StringTokenizer tok = new StringTokenizer(parameterString, "_");
            kernelType = new Integer(tok.nextToken());
            cacheSize = new Integer(tok.nextToken());
            numSamplePoints = new Integer(tok.nextToken());
            
            kernel_gamma = new Double(tok.nextToken());
            kernel_degree = new Integer(tok.nextToken());
            kernel_a = new Double(tok.nextToken());
            kernel_b = new Double(tok.nextToken());
            
            min_pts = new Integer(tok.nextToken());
            convergence_epsilon = new Double(tok.nextToken());
            max_iterations = new Integer(tok.nextToken());
            p = new Double(tok.nextToken());
            r = new Double(tok.nextToken());
        }
        
      //Check if all paramters are in range
        if (kernelType < 0 || kernelType > 3 
        		|| cacheSize < 0 || numSamplePoints < 1 || kernel_gamma < 0 || kernel_degree < 0
        		|| min_pts < 0 || p < 0.0 || p > 1.0 || r < -1.0) {
        	throw new NodeException("FastKMeans: One of the parameters was out of range!");
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
		// TODO Auto-generated method stub

	}

}
