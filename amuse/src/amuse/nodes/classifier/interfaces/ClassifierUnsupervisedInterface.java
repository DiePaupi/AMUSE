package amuse.nodes.classifier.interfaces;

import amuse.interfaces.nodes.NodeException;

/**
 * This interface defines the operations which should be supported by all supervised classifiers.
 * @author Pauline Speckmann
 *
 */
public interface ClassifierUnsupervisedInterface extends ClassifierInterface {
	
	/**
	 * Classifies the music data from ClassificationConfiguration of the corresponding scheduler.
	 * Labels are written directly to this data set
	 * @throws NodeException
	 */
	public void classify() throws NodeException;
}
