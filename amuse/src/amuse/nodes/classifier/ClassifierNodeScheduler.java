/** 
 * This file is part of AMUSE framework (Advanced MUsic Explorer).
 * 
 * Copyright 2006-2010 by code authors
 * 
 * Created at TU Dortmund, Chair of Algorithm Engineering
 * (Contact: <http://ls11-www.cs.tu-dortmund.de>) 
 *
 * AMUSE is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * AMUSE is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with AMUSE. If not, see <http://www.gnu.org/licenses/>.
 * 
 * Creation date: 29.02.2008
 */ 
package amuse.nodes.classifier;

import java.io.BufferedWriter;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.apache.log4j.Level;

import weka.core.Attribute;
import weka.core.Instance;
import weka.core.converters.ArffLoader;
import amuse.data.Feature;
import amuse.data.FileTable;
import amuse.data.InputFeatureType;
import amuse.data.ModelType.LabelType;
import amuse.data.ModelType.MethodType;
import amuse.data.ModelType.RelationshipType;
import amuse.data.annotation.ClassifiedSongPartitions;
import amuse.data.annotation.SongPartitionsDescription;
import amuse.data.io.ArffDataSet;
import amuse.data.io.DataInputInterface;
import amuse.data.io.DataSet;
import amuse.data.io.DataSetAbstract;
import amuse.data.io.DataSetException;
import amuse.data.io.DataSetInput;
import amuse.data.io.FileListInput;
import amuse.data.io.attributes.NumericAttribute;
import amuse.interfaces.nodes.NodeEvent;
import amuse.interfaces.nodes.NodeException;
import amuse.interfaces.nodes.NodeScheduler;
import amuse.interfaces.nodes.TaskConfiguration;
import amuse.interfaces.nodes.methods.AmuseTask;
import amuse.nodes.classifier.ClassificationConfiguration.InputSourceType;
import amuse.nodes.classifier.interfaces.ClassifierInterface;
import amuse.nodes.classifier.interfaces.ClassifierSupervisedInterface;
import amuse.nodes.classifier.interfaces.ClassifierUnsupervisedInterface;
import amuse.nodes.processor.ProcessingConfiguration;
import amuse.nodes.processor.ProcessorNodeScheduler;
import amuse.nodes.trainer.TrainingConfiguration;
import amuse.preferences.AmusePreferences;
import amuse.preferences.KeysStringValue;
import amuse.scheduler.gui.algorithm.Algorithm;
import amuse.util.AmuseLogger;

/**
 * ClassifierNodeScheduler is responsible for the classifier node. The given music files
 * are classified with the previously learned classification model. 
 * 
 * @author Igor Vatolkin
 * @version $Id: ClassifierNodeScheduler.java 245 2018-09-27 12:53:32Z frederik-h $
 */
public class ClassifierNodeScheduler extends NodeScheduler { 

	/** Classifier adapter */
	ClassifierInterface commonInterface = null;
	ClassifierSupervisedInterface cSinterface = null;
	ClassifierUnsupervisedInterface cUinterface = null;
	
	/** Parameters for classification algorithm if required */
	private String requiredParameters = null;
	
	/** Music category id and name separated by '-', e.g. "15-Music_for_Inspiration" */
	private String categoryDescription = null;
	
	/** Here the description of data instances (from what music files and intervals) is saved */
	private ArrayList<SongPartitionsDescription> descriptionOfClassifierInput = null;
	
	/** Number of categories that are classified / clusters */
	private int numberOfCategories;
	
	/** Saves the methodType (Regression not included) */
	private boolean isSupervised;
	
	
	/**
	 * Constructor
	 */
	public ClassifierNodeScheduler(String folderForResults) throws NodeException {
		super(folderForResults);
		requiredParameters = new String();
		categoryDescription = new String();
	}
	
	/**
	 * Main method for classification
	 * @param args Classification configuration
	 */
	public static void main(String[] args) {
		
		// Create the node scheduler
		ClassifierNodeScheduler thisScheduler = null;
		try {
			thisScheduler = new ClassifierNodeScheduler(args[0] + File.separator + "input" + File.separator + "task_" + args[1]);
		} catch(NodeException e) {
			AmuseLogger.write(ClassifierNodeScheduler.class.getName(), Level.ERROR,
					"Could not create folder for classifier node intermediate results: " + e.getMessage());
			return;
		}
		
		// Proceed the task
		AmuseLogger.write("ClassifierNodeScheduler", Level.DEBUG, "Starting proceedTask()");
		thisScheduler.proceedTask(args);
		AmuseLogger.write("ClassifierNodeScheduler", Level.DEBUG, "Finished proceedTask()");
		
		// Remove the folder for input and intermediate results
		try {
			thisScheduler.removeInputFolder();
		} catch(NodeException e) {
				AmuseLogger.write(ClassifierNodeScheduler.class.getClass().getName(), Level.WARN,
					"Could not remove properly the folder with intermediate results '" + 
					thisScheduler.nodeHome + File.separator + "input" + File.separator + "task_" + thisScheduler.jobId + 
					"; please delete it manually! (Exception: "+ e.getMessage() + ")");
		}
	}
	
	/**
	 * Proceeds classification task and returns the results as ArrayList<ClassifiedSongPartitionsDescription>
	 * OR saves them to file
	 * TODO sollte einheitlich sein: Ergebnisse sollen in ClassificationConfiguration gespeichert werden
	 */
	public ArrayList<ClassifiedSongPartitions> proceedTask(String nodeHome, long jobId, TaskConfiguration classificationConfiguration,
			boolean saveToFile) throws NodeException {
		
		// --------------------------------------------
		// (I): Configure classification node scheduler
		// --------------------------------------------
		this.nodeHome = nodeHome;
		if(this.nodeHome.startsWith(AmusePreferences.get(KeysStringValue.AMUSE_PATH))) {
			this.directStart = true;
		}
		this.jobId = new Long(jobId);
		this.taskConfiguration = classificationConfiguration;
		isSupervised = ((ClassificationConfiguration)taskConfiguration).getMethodType() == MethodType.SUPERVISED;
		// If this node is started directly, the properties are loaded from AMUSEHOME folder;
		// if this node is started via command line (e.g. in a grid, the properties are loaded from
		// %classifier home folder%/input
		if(!this.directStart) {
			File preferencesFile = new File(this.nodeHome + File.separator + "input" + File.separator + "task_" + this.jobId + File.separator + "amuse.properties");
			AmusePreferences.restoreFromFile(preferencesFile);
		}
		
		AmuseLogger.write(this.getClass().getName(), Level.INFO, "Classifier node scheduler started");
		
		// ----------------------------------------------------------------
		// (II): Convert feature vectors + descriptions to classifier input
		// ----------------------------------------------------------------
		try {
			this.prepareClassifierInput();
		} catch(NodeException e) {
			throw new NodeException("Could not prepare classifier input: " + e.getMessage()); 
		}
		
		// ------------------------------------------
		// (III): Configure the classification method
		// ------------------------------------------
		try {
			this.configureClassificationMethod();
		} catch(NodeException e) {
			throw new NodeException("Configuration of classifier failed: " + e.getMessage()); 
		}
		
		// -------------------------------------
		// (IV): Start the classification method
		// -------------------------------------
		ArrayList<ClassifiedSongPartitions> classifierResult = null;
		try {
			this.classify();
			classifierResult = createClassifiedSongPartitionDescriptions();
			if(saveToFile) {
				saveClassifierResultToFile(classifierResult);
			}
		} catch(NodeException e) {
			throw new NodeException("Classification failed: " + e.getMessage()); 
		}
		
		// ---------------------------------------------------------------------------------
		// (V) If started directly, remove generated data and fire event for Amuse scheduler
		// ---------------------------------------------------------------------------------
		if(this.directStart) {
			try {
				this.cleanInputFolder();
			} catch(NodeException e) {
				throw new NodeException("Could not remove properly the intermediate results '" + 
					this.nodeHome + File.separator + "input" + File.separator + "task_" + this.jobId + "; please delete it manually! (Exception: "+ e.getMessage() + ")");
			}
			this.fireEvent(new NodeEvent(NodeEvent.CLASSIFICATION_COMPLETED, this));
		}
		
		// If the classification result is saved to file, returns null here
		return classifierResult;
	}
	
	/*
	 * (non-Javadoc)
	 * @see amuse.interfaces.nodes.NodeSchedulerInterface#proceedTask(java.lang.String, long, amuse.interfaces.nodes.TaskConfiguration)
	 */
	public void proceedTask(String nodeHome, long jobId, TaskConfiguration classificationConfiguration) {
		
		// Since ClassifierNodeScheduler may output the result as ArrayList<ClassifiedSongPartitionsDescription>
		// and not only as file output, here the file output is set
		try {
			proceedTask(nodeHome, jobId, classificationConfiguration, true);
		} catch (NodeException e) {
			AmuseLogger.write(this.getClass().getName(), Level.ERROR, 
					"Could not proceed classification task: " + e.getMessage());
			errorDescriptionBuilder.append(taskConfiguration.getDescription());
			this.fireEvent(new NodeEvent(NodeEvent.CLASSIFICATION_FAILED, this));
		}
	}

	/*
	 * (non-Javadoc)
	 * @see amuse.interfaces.nodes.NodeSchedulerInterface#proceedTask(java.lang.String[])
	 */
	public void proceedTask(String[] args) {
		if(args.length < 2) {
			AmuseLogger.write(this.getClass().getName(), Level.FATAL, 2 - args.length + 
					" arguments are missing; The usage is 'ClassifierNodeScheduler %1 %2', where: \n" +
					"%1 - Home folder of this node\n" +
					"%2 - Unique (for currently running Amuse instance) task Id\n"); 
			System.exit(1);
		}
		
		// Load the task configuration from %CLASSIFIERHOME%/task.ser
		ClassificationConfiguration[] classifierConfig = null;
		FileInputStream fis = null;
		ObjectInputStream in = null;
		try {
			fis = new FileInputStream(args[0] + File.separator + "task_" + args[1] + ".ser");
			in = new ObjectInputStream(fis);
			Object o = in.readObject();
			classifierConfig = (ClassificationConfiguration[])o;
		    in.close();
		} catch(IOException ex) {
		    ex.printStackTrace();
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		}
		
		// Proceed the classification task(s)
		for(int i=0;i<classifierConfig.length;i++) {
			proceedTask(args[0],new Long(args[1]),classifierConfig[i]);
			AmuseLogger.write(this.getClass().getName(), Level.INFO, "Classifier node is going to start job " + 
					(i+1) + File.separator + classifierConfig.length);
		}
	}
	
	/**
	 * Converts feature vectors + descriptions to classifier input
	 * @throws NodeException
	 */
	private void prepareClassifierInput() throws NodeException {
		descriptionOfClassifierInput = new ArrayList<SongPartitionsDescription>();
		
		if(! (((ClassificationConfiguration)this.getConfiguration()).getInputToClassify() instanceof DataSetInput)) {
			
			//Check if the settings are supported
			if(((ClassificationConfiguration)this.taskConfiguration).getMethodType() == MethodType.REGRESSION){
				throw new NodeException("Regression classification isn't supported yet.");
			}
			
			List<Integer> attributesToIgnore = ((ClassificationConfiguration)this.taskConfiguration).getAttributesToIgnore();
			String attIgn = "";
			for (int att=0; att < attributesToIgnore.size(); att++) {
				attIgn +=  attributesToIgnore.get(att) + ", ";
			}
			AmuseLogger.write("ClassifierNodeScheduler", Level.DEBUG, "Attributes to ignore: " + attIgn);
			
			// Load the categoryDescription if no model path is given
			DataSetAbstract categoryList = null;
			try {
				categoryList = new ArffDataSet(new File(AmusePreferences.getMultipleTracksAnnotationTablePath()));
			} catch (IOException e) {
				throw new NodeException("Could not load the category table: " + e.getMessage()); 
			}
			
			// Load classify - needs probably to be changed if regression is implemented
			if(((ClassificationConfiguration)this.taskConfiguration).getMethodType() == MethodType.SUPERVISED){
				List<Integer> attributesToPredict = ((ClassificationConfiguration)this.taskConfiguration).getAttributesToPredict();
				
				// Load the categoryDescription if no model path is given
				if(((ClassificationConfiguration)this.taskConfiguration).getPathToInputModel() == null
						|| ((ClassificationConfiguration)this.taskConfiguration).getPathToInputModel().equals(new String("-1"))){
					int i=0;
					while(i < categoryList.getValueCount()) {
						Integer id = new Double(categoryList.getAttribute("Id").getValueAt(i).toString()).intValue();
						if(id == ((ClassificationConfiguration)this.taskConfiguration).getGroundTruthCategoryId()) {
							this.categoryDescription = ((ClassificationConfiguration)this.taskConfiguration).getGroundTruthCategoryId() + 
									"-" + categoryList.getAttribute("CategoryName").getValueAt(i).toString();
						
							DataSetAbstract groundTruth = null;
							try {
								groundTruth = new ArffDataSet(new File(categoryList.getAttribute("Path").getValueAt(i).toString()));
							} catch(IOException e) {
								throw new NodeException("Could not load the category table: " + e.getMessage()); 
							}
						
						
							this.categoryDescription += File.separator;
							int j = 0;
							for(int category : attributesToPredict) {
								if(j!=0) {
									this.categoryDescription += "_";
								}
								this.categoryDescription += groundTruth.getAttribute(5 + category).getName();
								j++;
							}
							break;
						}
						i++;
					}
					// If the category id could not be found and no model path is given, throw an exception
					if(categoryDescription.equals("")) {
						throw new NodeException("Category Id " + ((ClassificationConfiguration)this.taskConfiguration).getGroundTruthCategoryId() + 
								" could not be found and no model path was given.");
					}
				}
			}// else if (it's unsupervised) {there is nothing to do, I guess}
			
			
			DataSet inputForClassification = null;
		
			try {
			
				// If the input for classification has been previously prepared, it is almost ready!
				if(((ClassificationConfiguration)this.getConfiguration()).getInputSourceType().
						equals(ClassificationConfiguration.InputSourceType.READY_INPUT)) {
					
					
					DataSet completeInput = new DataSet(((FileListInput)((ClassificationConfiguration)this.taskConfiguration).getInputToClassify()).getInputFiles().get(0));
					
						inputForClassification = new DataSet("ClassificationSet");
					
						// Add the attributes (except for attributes that are to be ignored and the Id)
						for(int i = 0; i < completeInput.getAttributeCount(); i++) {
								if(!attributesToIgnore.contains(i) && !completeInput.getAttribute(i).getName().equals("Id")) {
									if(completeInput.getAttribute(i).getName().equals("NumberOfCategories")) {
										AmuseLogger.write(ClassifierNodeScheduler.class.getName(), Level.WARN, "NumberOfCategories is not an allowed attribute name. "
												+ "The attribute will be ignored.");
									}
									else {
										inputForClassification.addAttribute(completeInput.getAttribute(i));
									}
								}
							}
						
					// Prepare the description of the classifier input
					boolean startAndEnd = true;
					try {
						completeInput.getAttribute("Start").getValueAt(0);
						completeInput.getAttribute("End").getValueAt(0);
					}
					catch(DataSetException e) {
						startAndEnd = false;
						AmuseLogger.write(ClassifierNodeScheduler.class.getName(), Level.WARN, "Missing Start and/or End attributes.");
					}
					int id = (int)((double)completeInput.getAttribute("Id").getValueAt(0));
					List<Double> partitionStarts = new ArrayList<Double>();
					List<Double> partitionEnds = new ArrayList<Double>();
					for(int i = 0; i<completeInput.getValueCount(); i++) {
						int newId = (int)((double)completeInput.getAttribute("Id").getValueAt(i));
						
						double start = 0;
						double end = -1;
						
						if(startAndEnd) {
							start = (double)completeInput.getAttribute("Start").getValueAt(i);
							end = (double)completeInput.getAttribute("End").getValueAt(i);
						}
						
						if(newId != id) {
							Double[] partitionStartsAsArray = new Double[partitionStarts.size()];
							Double[] partitionEndsAsArray = new Double[partitionEnds.size()];
							
							for(int j = 0; j < partitionStarts.size(); j++) {
								partitionStartsAsArray[j] = partitionStarts.get(j);
								partitionEndsAsArray[j] = partitionEnds.get(j);
							}						
							descriptionOfClassifierInput.add(new SongPartitionsDescription("", id, partitionStartsAsArray, partitionEndsAsArray));
							partitionStarts = new ArrayList<Double>();
							partitionEnds = new ArrayList<Double>();
						}
						
						id = newId;
						partitionStarts.add(start);
						partitionEnds.add(end);						
					}
					
					Double[] partitionStartsAsArray = new Double[partitionStarts.size()];
					Double[] partitionEndsAsArray = new Double[partitionEnds.size()];
					
					for(int j = 0; j < partitionStarts.size(); j++) {
						partitionStartsAsArray[j] = partitionStarts.get(j);
						partitionEndsAsArray[j] = partitionEnds.get(j);
					}						
					descriptionOfClassifierInput.add(new SongPartitionsDescription("", id, partitionStartsAsArray, partitionEndsAsArray));
					
				} else if(((ClassificationConfiguration)this.getConfiguration()).getInputFeatureType() == InputFeatureType.PROCESSED_FEATURES) {
					

					inputForClassification = new DataSet("ClassificationSet");
					
					DataInputInterface inputToClassify = ((ClassificationConfiguration)this.taskConfiguration).getInputToClassify();
					
					if( (((ClassificationConfiguration)this.taskConfiguration).getMethodType() == MethodType.SUPERVISED) &&
							(((ClassificationConfiguration)this.taskConfiguration).getInputSourceType() == ClassificationConfiguration.InputSourceType.CATEGORY_ID)) {
						// Search for the category file
						Integer categoryId = new Integer(inputToClassify.toString());
						for(int i=0;i<categoryList.getValueCount();i++) {
							Double currentCategoryId = new Double(categoryList.getAttribute("Id").getValueAt(i).toString());
							if(new Integer(currentCategoryId.intValue()).equals(categoryId)) {
								String inputPath = new String(categoryList.getAttribute("Path").getValueAt(i).toString());
								DataSetAbstract inputFileSet = new ArffDataSet(new File(inputPath));
								List<Integer> ids = new ArrayList<Integer>(inputFileSet.getValueCount());
								List<File> input = new ArrayList<File>(inputFileSet.getValueCount());
								for(int j=0;j<inputFileSet.getValueCount();j++) {
									ids.add(new Double(inputFileSet.getAttribute("Id").getValueAt(j).toString()).intValue());
									input.add(new File(inputFileSet.getAttribute("Path").getValueAt(j).toString()));
								}
								inputToClassify = new FileListInput(input, ids);
								break;
							}
						}
					} else {
						//TODO: AMUSE logger info
					}
					
					// Load the processed feature files for a given music file list
					// Load the first classifier input for attributes information
					String currentInputFile = ((FileListInput)inputToClassify).
						getInputFiles().get(0).toString();
					
					if(currentInputFile.startsWith(AmusePreferences.get(KeysStringValue.MUSIC_DATABASE))) {
						currentInputFile = 
							((ClassificationConfiguration)this.getConfiguration()).getProcessedFeatureDatabase()
							+ File.separator 
							+ currentInputFile.substring(new File(AmusePreferences.get(KeysStringValue.MUSIC_DATABASE)).getPath().length() + 1,
									currentInputFile.lastIndexOf("."))
							+ File.separator
							+ currentInputFile.substring(currentInputFile.lastIndexOf(File.separator) + 1,
									currentInputFile.lastIndexOf("."))
							+ "_" 
							+ ((ClassificationConfiguration)this.taskConfiguration).getInputFeatures() + ".arff";
					}
					else{
						currentInputFile = 
							((ClassificationConfiguration)this.getConfiguration()).getProcessedFeatureDatabase()
							+ File.separator 
							+ currentInputFile.substring(0,
									currentInputFile.lastIndexOf(".")) 
							+ File.separator 
							+ currentInputFile.substring(currentInputFile.lastIndexOf(File.separator) + 1,
									currentInputFile.lastIndexOf(".")) 
							+ "_" 
							+ ((ClassificationConfiguration)this.taskConfiguration).getInputFeatures() + ".arff";
					}
					currentInputFile = currentInputFile.replaceAll(File.separator + "+", File.separator);
					
					ArffLoader classifierInputLoader = new ArffLoader();
					Instance inputInstance;
					classifierInputLoader.setFile(new File(currentInputFile));
					inputInstance = classifierInputLoader.getNextInstance(classifierInputLoader.getStructure());
						
					// Save the attributes omitting UNIT, START and END attributes (they describe the partition for modeled features)
					for(int i=0;i<classifierInputLoader.getStructure().numAttributes()-3;i++) {
						
						//Also omit the attributes that are supposed to be ignored
						if(!attributesToIgnore.contains(i)) {
							inputForClassification.addAttribute(new NumericAttribute(inputInstance.attribute(i).name(),
									new ArrayList<Double>()));
						}
						
					}
						
					// Save the processed features for classifier
					for(int k=0;k<((FileListInput)inputToClassify).getInputFiles().size();k++) {
						currentInputFile = ((FileListInput)inputToClassify).getInputFiles().get(k).toString();
						ArrayList<Double> partitionStarts = new ArrayList<Double>();
						ArrayList<Double> partitionEnds = new ArrayList<Double>();
						
						// Save the name of music file for later conversion of classification output
						String currentInputSong = new String(currentInputFile);
						if(currentInputFile.startsWith(AmusePreferences.get(KeysStringValue.MUSIC_DATABASE))) {
							currentInputFile = 
								((ClassificationConfiguration)this.getConfiguration()).getProcessedFeatureDatabase()
								+ File.separator 
								+ currentInputFile.substring(new File(AmusePreferences.get(KeysStringValue.MUSIC_DATABASE)).getPath().length() + 1,
										currentInputFile.lastIndexOf("."))
								+ File.separator
								+ currentInputFile.substring(currentInputFile.lastIndexOf(File.separator) + 1,
										currentInputFile.lastIndexOf("."))
								+ "_" 
								+ ((ClassificationConfiguration)this.taskConfiguration).getInputFeatures() + ".arff";
						}
						else{
							currentInputFile = 
								((ClassificationConfiguration)this.getConfiguration()).getProcessedFeatureDatabase()
								+ File.separator 
								+ currentInputFile.substring(0,
										currentInputFile.lastIndexOf(".")) 
								+ File.separator 
								+ currentInputFile.substring(currentInputFile.lastIndexOf(File.separator) + 1,
										currentInputFile.lastIndexOf(".")) 
								+ "_" 
								+ ((ClassificationConfiguration)this.taskConfiguration).getInputFeatures() + ".arff";
						}
						currentInputFile = currentInputFile.replaceAll(File.separator + "+", File.separator);
						
						AmuseLogger.write(this.getClass().getName(), Level.DEBUG, "Loading:  " + currentInputFile);
							
						// Load processed features of the current file and save them to classifier input file
						ArffLoader processedFeaturesLoader = new ArffLoader();
						Instance processedFeaturesInstance;
						processedFeaturesLoader.setFile(new File(currentInputFile));
						processedFeaturesInstance = processedFeaturesLoader.getNextInstance(processedFeaturesLoader.getStructure());
						while(processedFeaturesInstance != null) {
							double startPosition = processedFeaturesInstance.value(processedFeaturesLoader.getStructure().attribute("Start"));
							double endPosition = processedFeaturesInstance.value(processedFeaturesLoader.getStructure().attribute("End"));
							partitionStarts.add(startPosition);
							partitionEnds.add(endPosition);
							
							// Save the processed features (attributes) omitting UNIT, START and END attributes 
							// (they describe the partition for modeled features)
							int currentAttribute = 0;
							for(int i=0;i<processedFeaturesInstance.numAttributes()-3;i++) {
								Double val = processedFeaturesInstance.value(i);
								//omit the features that are supposed to be ignored
								if(!attributesToIgnore.contains(i)) {
									inputForClassification.getAttribute(currentAttribute).addValue(val);
									currentAttribute++;
								}
							}
							
							processedFeaturesInstance = processedFeaturesLoader.getNextInstance(processedFeaturesLoader.getStructure());
						}
						
						// Add descriptions of the partitions of the current song
						Double[] partitionStartsAsArray = new Double[partitionStarts.size()];
						Double[] partitionEndsAsArray = new Double[partitionEnds.size()];
						for(int l=0;l<partitionStarts.size();l++) {
							partitionStartsAsArray[l] = partitionStarts.get(l);
							partitionEndsAsArray[l] = partitionEnds.get(l);
						}
						int currentInputSongId = ((FileListInput)inputToClassify).getInputFileIds().get(k);
						descriptionOfClassifierInput.add(new SongPartitionsDescription(currentInputSong,currentInputSongId,
								partitionStartsAsArray,partitionEndsAsArray));
					}
				} else {
					// load the raw features
					inputForClassification = new DataSet("ClassificationSet");
					// Load the classifier description
					DataInputInterface inputToClassify = ((ClassificationConfiguration)this.taskConfiguration).getInputToClassify();
					
					if(((ClassificationConfiguration)this.taskConfiguration).getInputSourceType() == ClassificationConfiguration.InputSourceType.CATEGORY_ID) {
						// Search for the category file
						Integer categoryId = new Integer(inputToClassify.toString());
						for(int i=0;i<categoryList.getValueCount();i++) {
							Double currentCategoryId = new Double(categoryList.getAttribute("Id").getValueAt(i).toString());
							if(new Integer(currentCategoryId.intValue()).equals(categoryId)) {
								String inputPath = new String(categoryList.getAttribute("Path").getValueAt(i).toString());
								DataSetAbstract inputFileSet = new ArffDataSet(new File(inputPath));
								List<Integer> ids = new ArrayList<Integer>(inputFileSet.getValueCount());
								List<File> input = new ArrayList<File>(inputFileSet.getValueCount());
								for(int j=0;j<inputFileSet.getValueCount();j++) {
									ids.add(new Double(inputFileSet.getAttribute("Id").getValueAt(j).toString()).intValue());
									input.add(new File(inputFileSet.getAttribute("Path").getValueAt(j).toString()));
								}
								inputToClassify = new FileListInput(input, ids);
								break;
							}
						}
					}
					
					// load the first classifier input for attributes information
					String currentInputFile = ((FileListInput)inputToClassify).
							getInputFiles().get(0).toString();
					
					List<Feature> features = getHarmonizedFeatures(currentInputFile);
					
					// find out how many values per window were used
					int numberOfValuesPerWindow = -1;
					int numberOfAttributesToIgnore = 0;
					for(int i = 0; i < features.size(); i++) {
						if(attributesToIgnore.contains(i)) {
							numberOfAttributesToIgnore++;
						}
						if(features.get(i).getHistoryAsString().charAt(6) == '2' || i == features.size()-1) {
							numberOfValuesPerWindow = i;
							break;
						}
					}
					// set the numberOfValuesPerWindow in the ClassficationConfiguration for the classification algorithm
					// (the classification algorithm needs the size of the windows after the attributesToIgnore have been removed)
					((ClassificationConfiguration)this.getConfiguration()).setNumberOfValuesPerWindow(numberOfValuesPerWindow - numberOfAttributesToIgnore);
					
					// create the attributes
					// and save the numberOfValuesPerWindow
					for(int i = 0; i < features.size(); i++) {
						//Omit the attributes that are supposed to be ignored
						if(!attributesToIgnore.contains(i%numberOfValuesPerWindow)) {
							inputForClassification.addAttribute(new NumericAttribute(features.get(i).getHistoryAsString(), new ArrayList<Double>()));
						}
					}
					
					int partSize = ((ClassificationConfiguration)this.getConfiguration()).getClassificationWindowSize();
					int partStep = partSize - ((ClassificationConfiguration)this.getConfiguration()).getClassificationWindowOverlap();
							
					// Create the labeled data
					for(int i=0;i<((FileListInput)inputToClassify).getInputFiles().size();i++) {
						currentInputFile = ((FileListInput)inputToClassify).getInputFiles().get(i).toString();
						ArrayList<Double> partitionStarts = new ArrayList<Double>();
						ArrayList<Double> partitionEnds = new ArrayList<Double>();
						// load the next features
						if(i != 0) {
							features = getHarmonizedFeatures(currentInputFile);
						}
						// Save the name of music file for later conversion of classification output
						String currentInputSong = new String(currentInputFile);
						
						// TODO Consider only the partitions up to 6 minutes of a music track; should be a parameter?
						int numberOfMaxPartitions = features.get(0).getValues().size();
						for(int j=1;j<features.size();j++) {
							if(features.get(j).getValues().size() < numberOfMaxPartitions) {
								numberOfMaxPartitions = features.get(j).getValues().size();
							}
						}
						if((numberOfMaxPartitions * (((ClassificationConfiguration)this.taskConfiguration).getClassificationWindowSize() - 
								((ClassificationConfiguration)this.taskConfiguration).getClassificationWindowOverlap())) > 360000) {
							numberOfMaxPartitions = 360000 / (((ClassificationConfiguration)this.taskConfiguration).getClassificationWindowSize() - 
									((ClassificationConfiguration)this.taskConfiguration).getClassificationWindowOverlap());
							AmuseLogger.write(this.getClass().getName(), Level.WARN, 
					   				"Number of partitions after processing reduced from " + features.get(0).getValues().size() + 
					   				" to " + numberOfMaxPartitions);
						}
						
						for(int j = 0; j < numberOfMaxPartitions; j++) {
							double startPosition = j*partStep;
							double endPosition = j*partStep+partSize;
							partitionStarts.add(startPosition);
							partitionEnds.add(endPosition);
							int currentAttribute = 0;
							for(int k = 0; k < features.size(); k++) {
								// Omit the attributes that are supposed to be ignored
								if(!attributesToIgnore.contains(k%numberOfValuesPerWindow)) {
									Double val = features.get(k).getValues().get(j)[0];
									inputForClassification.getAttribute(currentAttribute).addValue(val);
									currentAttribute++;
								}
							}
						}
						
						// Add descriptions of the partitions of the current song
						Double[] partitionStartsAsArray = new Double[partitionStarts.size()];
						Double[] partitionEndsAsArray = new Double[partitionEnds.size()];
						for(int l=0;l<partitionStarts.size();l++) {
							partitionStartsAsArray[l] = partitionStarts.get(l);
							partitionEndsAsArray[l] = partitionEnds.get(l);
						}
						int currentInputSongId = ((FileListInput)inputToClassify).getInputFileIds().get(i);
						descriptionOfClassifierInput.add(new SongPartitionsDescription(currentInputSong,currentInputSongId,
								partitionStartsAsArray,partitionEndsAsArray));
						
					}
				}
			} catch(IOException e) {
				throw new NodeException(e.getMessage());
			}
			
			// Replace the input to classify by the data set input loaded into memory
			((ClassificationConfiguration)this.taskConfiguration).setInputToClassify(new DataSetInput(inputForClassification));
		}
		
		// Load only the song information if the data is already prepared
		else {
			if(((ClassificationConfiguration)this.getConfiguration()).getInputToClassify() instanceof DataSetInput) {
				// TODO v0.2: input sets to classify may be without ids!
				amuse.data.io.attributes.Attribute idAttribute = ((DataSetInput)((ClassificationConfiguration)this.getConfiguration()).
						getInputToClassify()).getDataSet().getAttribute("Id");
				
				Integer currentSongId = new Double(idAttribute.getValueAt(0).toString()).intValue();
				Integer numberOfPartitionsInCurrentSong = 0;
				for(int i=0;i<idAttribute.getValueCount();i++) {
					Integer newSongId = new Double(idAttribute.getValueAt(i).toString()).intValue();
					
					// New song is reached
					if(!newSongId.equals(currentSongId)) {
						SongPartitionsDescription newSongDesc = new SongPartitionsDescription("", 
								currentSongId, new Double[numberOfPartitionsInCurrentSong], new Double[numberOfPartitionsInCurrentSong]);
						descriptionOfClassifierInput.add(newSongDesc);
						currentSongId = newSongId;
						numberOfPartitionsInCurrentSong = 0;
					}
					numberOfPartitionsInCurrentSong++;
				}
				
				// For the last song
				SongPartitionsDescription newSongDesc = new SongPartitionsDescription("", 
						currentSongId, new Double[numberOfPartitionsInCurrentSong], new Double[numberOfPartitionsInCurrentSong]);
				descriptionOfClassifierInput.add(newSongDesc);
			} 
		}
	}
	
	/**
	 * Harmonizes the raw features using the ProcessorNodeScheduler
	 * @param currentInputFile
	 * @return the harmonized features
	 * @throws NodeException
	 */
	private List<Feature> getHarmonizedFeatures(String currentInputFile) throws NodeException {
		List<File> currentInputFileList = new ArrayList<File>();
		currentInputFileList.add(new File(currentInputFile));
		
		ProcessingConfiguration pConf = new ProcessingConfiguration(new FileTable(currentInputFileList),
				((ClassificationConfiguration)this.getConfiguration()).getInputFeatureList(),
				"",
				((ClassificationConfiguration)this.getConfiguration()).getClassificationWindowSize(),
				((ClassificationConfiguration)this.getConfiguration()).getClassificationWindowOverlap(),
				"6",
				"");
		
		ProcessorNodeScheduler processorNodeScheduler = new ProcessorNodeScheduler(this.nodeHome + File.separator + "input" + File.separator + "task_" + 
				this.jobId + File.separator + "processor");
		
		// proceed raw feature processing
		processorNodeScheduler.proceedTask(this.nodeHome, this.jobId, pConf, false);
		
		// return the processed features
		return processorNodeScheduler.getProcessedFeatures();
	}
	
	/**
	 * Configures the classification method
	 * @throws NodeException
	 */
	private void configureClassificationMethod() throws NodeException {
		Integer requiredAlgorithm; 
		// Check if it's supervised or unsupervised - regression not included
		if (isSupervised != (((ClassificationConfiguration)taskConfiguration).getMethodType() == MethodType.SUPERVISED)) {
			throw new NodeException("ClassifierNodeScheduler - configureClassificationMethod(): "
					+ "The method type set in proceedTask() doesn't match the current one.");
		}

		// If parameter string for this algorithm exists..
		if(((ClassificationConfiguration)taskConfiguration).getAlgorithmDescription().contains("[") && 
				((ClassificationConfiguration)taskConfiguration).getAlgorithmDescription().contains("]")) {
			requiredAlgorithm = new Integer(((ClassificationConfiguration)taskConfiguration).
					getAlgorithmDescription().substring(0,((ClassificationConfiguration)taskConfiguration).
							getAlgorithmDescription().indexOf("[")));
			this.requiredParameters = ((ClassificationConfiguration)taskConfiguration).getAlgorithmDescription().
				substring(((ClassificationConfiguration)taskConfiguration).getAlgorithmDescription().indexOf("[")+1,
						((ClassificationConfiguration)taskConfiguration).getAlgorithmDescription().lastIndexOf("]")); 
		} else {
			requiredAlgorithm = new Integer(((ClassificationConfiguration)taskConfiguration).getAlgorithmDescription());
			this.requiredParameters = null;
		}
		boolean algorithmFound = false;
		try {
	    	ArffLoader classifierTableLoader = new ArffLoader();
	    	if(this.directStart) {
	    		classifierTableLoader.setFile(new File(AmusePreferences.getClassifierAlgorithmTablePath()));
	    	} else {
	    		classifierTableLoader.setFile(new File(this.nodeHome + File.separator + "input" + File.separator + "task_" + this.jobId + File.separator + "classifierAlgorithmTable.arff"));
	    	}
			Instance currentInstance = classifierTableLoader.getNextInstance(classifierTableLoader.getStructure());
			Attribute idAttribute = classifierTableLoader.getStructure().attribute("Id");
			Attribute nameAttribute = classifierTableLoader.getStructure().attribute("Name");
			Attribute classifierAdapterClassAttribute = classifierTableLoader.getStructure().attribute("ClassifierAdapterClass");
			Attribute homeFolderAttribute = classifierTableLoader.getStructure().attribute("HomeFolder");
			Attribute startScriptAttribute = classifierTableLoader.getStructure().attribute("StartScript");
			Attribute inputBaseClassificationBatchAttribute = classifierTableLoader.getStructure().attribute("InputBaseClassificationBatch");
			Attribute inputClassificationBatchAttribute = classifierTableLoader.getStructure().attribute("InputClassificationBatch");
			Attribute supportsBinaryAttribute = classifierTableLoader.getStructure().attribute("SupportsBinary");
			Attribute supportsContinuousAttribute = classifierTableLoader.getStructure().attribute("SupportsContinuous");
			Attribute supportsMulticlassAttribute = classifierTableLoader.getStructure().attribute("SupportsMulticlass");
			Attribute supportsMultilabelAttribute = classifierTableLoader.getStructure().attribute("SupportsMultilabel");
			Attribute supportsSinglelabelAttribute = classifierTableLoader.getStructure().attribute("SupportsSinglelabel");
			Attribute supportsSupervisedAttribute = classifierTableLoader.getStructure().attribute("SupportsSupervised");
			Attribute supportsUnsupervisedAttribute = classifierTableLoader.getStructure().attribute("SupportsUnsupervised");
			Attribute supportsRegressionAttribute = classifierTableLoader.getStructure().attribute("SupportsRegression");
			while(currentInstance != null) {
				Integer idOfCurrentAlgorithm = new Double(currentInstance.value(idAttribute)).intValue();
				if(idOfCurrentAlgorithm.equals(requiredAlgorithm)) {
					
					// Configure the adapter class
					try {
						//Check if the method supports the settings
						boolean supportsBinary = new Double(currentInstance.value(supportsBinaryAttribute)) != 0;
						boolean supportsContinuous = new Double(currentInstance.value(supportsContinuousAttribute)) != 0;
						boolean supportsMulticlass = new Double(currentInstance.value(supportsMulticlassAttribute)) != 0;
						boolean supportsMultilabel = new Double(currentInstance.value(supportsMultilabelAttribute)) != 0;
						boolean supportsSinglelabel = new Double(currentInstance.value(supportsSinglelabelAttribute)) != 0;
						boolean supportsSupervised = new Double(currentInstance.value(supportsSupervisedAttribute)) != 0;
						boolean supportsUnsupervised = new Double(currentInstance.value(supportsUnsupervisedAttribute)) != 0;
						boolean supportsRegression = new Double(currentInstance.value(supportsRegressionAttribute)) != 0;
						
						switch(((ClassificationConfiguration)this.taskConfiguration).getRelationshipType()) {
						case BINARY:
							if(!supportsBinary) {
								throw new NodeException("This method does not support binary relationships.");
							}
							break;
						case CONTINUOUS:
							if(!supportsContinuous) {
								throw new NodeException("This method does not support continuous relationships.");
							}
							break;
						}
						
						switch(((ClassificationConfiguration)this.taskConfiguration).getLabelType()) {
						case MULTICLASS:
							if(!supportsMulticlass) {
								throw new NodeException("This method does not support multiclass classification.");
							}
							break;
						case MULTILABEL:
							if(!supportsMultilabel) {
								throw new NodeException("This method does not support multilabel classification.");
							}
							break;
						case SINGLELABEL:
							if(!supportsSinglelabel) {
								throw new NodeException("This method does not support singlelabel classification.");
							}
							break;
						}
						
						switch(((ClassificationConfiguration)this.taskConfiguration).getMethodType()) {
						case SUPERVISED:
							if(!supportsSupervised) {
								throw new NodeException("This method does not support supervised classification.");
							}
							break;
						case UNSUPERVISED:
							if(!supportsUnsupervised) {
								throw new NodeException("This method does not support unsupervised classification.");
							}
							break;
						case REGRESSION:
							if(!supportsRegression) {
								throw new NodeException("This method does not support regression.");
							}
							break;
						}
						
						Class<?> adapter = Class.forName(currentInstance.stringValue(classifierAdapterClassAttribute));
						
						// Set the Interface to supervised (classify(String pathToModel)) or unsupervised (classify()) specific Interface
						if (isSupervised) {
							this.cSinterface = (ClassifierSupervisedInterface)adapter.newInstance();
							commonInterface = this.cSinterface;
						} else {
							this.cUinterface = (ClassifierUnsupervisedInterface)adapter.newInstance();
							commonInterface = this.cUinterface;
						}
						
						Properties classifierProperties = new Properties();
						Integer id = new Double(currentInstance.value(idAttribute)).intValue();
						classifierProperties.setProperty("id",id.toString());
						classifierProperties.setProperty("name",currentInstance.stringValue(nameAttribute));
						classifierProperties.setProperty("classifierFolderName",currentInstance.stringValue(homeFolderAttribute));
						if(directStart) {
							classifierProperties.setProperty("classifierFolder",AmusePreferences.get(KeysStringValue.AMUSE_PATH) + File.separator + "tools" + File.separator + currentInstance.stringValue(homeFolderAttribute));
						} else {
							classifierProperties.setProperty("classifierFolder",nodeHome + File.separator + "tools" + File.separator + currentInstance.stringValue(homeFolderAttribute));
						}
						classifierProperties.setProperty("startScript",currentInstance.stringValue(startScriptAttribute));
						classifierProperties.setProperty("inputBaseBatch",currentInstance.stringValue(inputBaseClassificationBatchAttribute));
						classifierProperties.setProperty("inputBatch",currentInstance.stringValue(inputClassificationBatchAttribute));
						classifierProperties.setProperty("categoryDescription", this.categoryDescription);
						
						((AmuseTask)this.commonInterface).configure(classifierProperties,this,this.requiredParameters);
						
						AmuseLogger.write(this.getClass().getName(), Level.INFO, 
								"Classifier is configured: " + currentInstance.stringValue(classifierAdapterClassAttribute));
					} catch(ClassNotFoundException e) {
						AmuseLogger.write(this.getClass().getName(), Level.ERROR, 
								"Classifier class cannot be located: " + currentInstance.stringValue(classifierAdapterClassAttribute));
						System.exit(1);
					} catch(IllegalAccessException e) {
						AmuseLogger.write(this.getClass().getName(), Level.ERROR, 
								"Classifier class or its nullary constructor is not accessible: " + currentInstance.stringValue(classifierAdapterClassAttribute));
						System.exit(1);
					} catch(InstantiationException e) {
						AmuseLogger.write(this.getClass().getName(), Level.ERROR, 
								"Instantiation failed for classifier class: " + currentInstance.stringValue(classifierAdapterClassAttribute));
						System.exit(1);
					} catch(NodeException e) {
						throw new NodeException("Setting of parameters failed for classifier class: " + e.getMessage());
					}
					
					algorithmFound = true;
					break;
				} 
				currentInstance = classifierTableLoader.getNextInstance(classifierTableLoader.getStructure());
			}
			
			if(!algorithmFound) {
				AmuseLogger.write(this.getClass().getName(), Level.ERROR, 
						"Algorithm with id " + ((ClassificationConfiguration)taskConfiguration).getAlgorithmDescription() + 
						" was not found, task aborted");
				System.exit(1);
			}

	    } catch(IOException e) {
	    	throw new NodeException(e.getMessage());
	    }
	}
	
	/**
	 * Starts the classification method
	 * @throws NodeException
	 */
	private void classify() throws NodeException {
		
		// Check if the used method type fits the settings - regression not included
		if (commonInterface instanceof ClassifierSupervisedInterface) {
			if (isSupervised != true) {
				throw new NodeException("ClassifierNodeScheduler - classify(): "
						+ "The Interface for supervised classification was used, but the loaded method type didn't match that.");
			}
		} else if (commonInterface instanceof ClassifierUnsupervisedInterface) {
			if (isSupervised != false) {
				throw new NodeException("ClassifierNodeScheduler - classify(): "
						+ "The Interface for unsupervised classification was used, but the loaded method type didn't match that.");
			}
		} else {
			throw new NodeException("ClassifierNodeScheduler - classify(): "
					+ "Seems like Paupi couldn't figure out if you wanted supervised or unsupervised classification");
		}
				
		try {
			
	    	// Check the folder for model file if it exists
			if(this.requiredParameters != null) {
				this.requiredParameters = "[" + this.requiredParameters + "]";
			} else {
				this.requiredParameters = "";
			}
			
			/**
			 * if the parameter string contains paths with file separators
			 * we have to modify it so that the model can be loaded correctly
			 */
			String parameterString = requiredParameters;
			if(parameterString.contains(File.separator)) {
				String[] parameters = Algorithm.scanParameters(parameterString);
				parameterString = "[";
				for(int i = 0; i < parameters.length; i++) {
					String parameter = parameters[i];
					if(parameter.contains(File.separator)) {
						parameter = parameter.substring(parameter.lastIndexOf(File.separator) + 1, parameter.lastIndexOf("."));
					}
					parameterString += parameter;
					if(i < parameters.length - 1) {
						parameterString += "_";
					}
				}
				parameterString += "]";
			}
	    	
			if (isSupervised) {
				// Find the classification model in the Amuse model database or set the path to a concrete
				// model given in ClassificationConfiguration.pathToInputModel (for validator or optimizer)
				String pathToModel = new String();
				if(((ClassificationConfiguration)this.taskConfiguration).getPathToInputModel() == null
					|| ((ClassificationConfiguration)this.taskConfiguration).getPathToInputModel().equals(new String("-1"))) {
				
					String inputFeaturesDescription = ((ClassificationConfiguration)taskConfiguration).getInputFeatures();
				
					if(((ClassificationConfiguration)taskConfiguration).getInputFeatureType() == InputFeatureType.RAW_FEATURES) {
						if(inputFeaturesDescription.contains(File.separator) && inputFeaturesDescription.contains(".")) {
							inputFeaturesDescription = inputFeaturesDescription.substring(inputFeaturesDescription.lastIndexOf(File.separator) + 1, 
														inputFeaturesDescription.lastIndexOf('.'));
						}
						inputFeaturesDescription = "RAW_FEATURES_" + inputFeaturesDescription;
					}
					File folderForModels = new File(AmusePreferences.get(KeysStringValue.MODEL_DATABASE)
						+ File.separator
						+ this.categoryDescription
						+ File.separator
						+ ((AmuseTask)this.commonInterface).getProperties().getProperty("id") 
						+ "-" 
						+ ((AmuseTask)this.commonInterface).getProperties().getProperty("name") 
						+ parameterString + "_"
						+ ((ClassificationConfiguration)this.taskConfiguration).getRelationshipType().toString() + "_"
						+ ((ClassificationConfiguration)this.taskConfiguration).getLabelType().toString() + "_"
						+ ((ClassificationConfiguration)this.taskConfiguration).getMethodType().toString()
						+ File.separator
						+ inputFeaturesDescription);
				
					String trainingDescription = ((ClassificationConfiguration)this.taskConfiguration).getTrainingDescription();
					if(trainingDescription.equals("")) {
						pathToModel = new String(folderForModels + File.separator + "model.mod");
					} else {
						pathToModel = new String(folderForModels + File.separator + "model_" + trainingDescription + ".mod");
					}
				
					pathToModel = pathToModel.replaceAll(File.separator + "+", File.separator);
				} else {
					pathToModel = ((ClassificationConfiguration)this.taskConfiguration).getPathToInputModel();
				}
				
				// CLASSIFY SUPERVISED
				AmuseLogger.write(this.getClass().getName(), Level.INFO, "Starting the supervised classification with " + 
						((AmuseTask)this.commonInterface).getProperties().getProperty("name") + "...");
				this.cSinterface.classify(pathToModel);
			} else {
				// CLASSIFY UNSUPERVISED
				AmuseLogger.write(this.getClass().getName(), Level.INFO, "Starting the unsupervised classification with " + 
						((AmuseTask)this.commonInterface).getProperties().getProperty("name") + "...");
				this.cUinterface.classify();
			}
			
			
			AmuseLogger.write(this.getClass().getName(), Level.INFO, "... classification finished!");
			
	    } catch(NodeException e) {
			throw new NodeException("Problem during classification: " + e.getMessage());
	    }
	}
	
	private ArrayList<ClassifiedSongPartitions> createClassifiedSongPartitionDescriptions() {
		ArrayList<ClassifiedSongPartitions> classificationResults = new ArrayList<ClassifiedSongPartitions>();
		
		DataSet d = ((DataSetInput)((ClassificationConfiguration)taskConfiguration).getInputToClassify()).getDataSet();
		// Check if the partitions of each song have already been summarized (currently only the WardAdapter does that)
		boolean partitionsAlreadySummerized = ((ClassificationConfiguration)taskConfiguration).getPartitionsAlreadySummerized();
		
		// If it's unsupervised classification the number of clusters must be counted
		if (!isSupervised) {
			this.updateTheNumberOfClusters(d);
		} 
		
		int positionOfFirstCategory = d.getAttributeCount() - numberOfCategories;
		
		// Basically here is checked if the Ward Agglomeration was used (Unsupervised AND already summarized partitions)
		// Because that means there are no Start and End values to be set
		if (!isSupervised && partitionsAlreadySummerized) {
			
			// Go through all songs
			for(int valueNumber=0; valueNumber < d.getValueCount();valueNumber++) {
				
				// Gather the partition data for this song	
				Double[][] relationships = new Double[1][numberOfCategories];
				String[] labels = new String[numberOfCategories];
				
				for(int cluster=0; cluster < numberOfCategories; cluster++) {
					relationships[0][cluster] = (double)d.getAttribute(positionOfFirstCategory + cluster).getValueAt(valueNumber);
					
					labels[cluster] = d.getAttribute(positionOfFirstCategory + cluster).getName();
				}
				
				// Save the partition data for this song
				classificationResults.add(new ClassifiedSongPartitions(descriptionOfClassifierInput.get(valueNumber).getPathToMusicSong(), 
						descriptionOfClassifierInput.get(valueNumber).getSongId(), labels, relationships));
			}
		} else {
			
			// Go through all songs
			int currentPartition = 0;
			for(int i=0;i<descriptionOfClassifierInput.size();i++) {
				int numberOfCorrespondingPartitions = descriptionOfClassifierInput.get(i).getStartMs().length;
				
				// Gather the partition data for this song	
				Double[][] relationships = new Double[numberOfCorrespondingPartitions][numberOfCategories];
				String[] labels = new String[numberOfCategories];
				
				for(int j=0;j<numberOfCorrespondingPartitions;j++) {
					for(int category=0;category<numberOfCategories;category++) {
						relationships[j][category] = (double)d.getAttribute(positionOfFirstCategory + category).getValueAt(currentPartition);
						if(j==0) {
							String name = d.getAttribute(positionOfFirstCategory + category).getName();
							if (name.length() > 10) { labels[category] = name.substring(10); }
							else { labels[category] = name; }
						}
					}
					currentPartition++;
				}
				
				// Save the partition data for this song
				classificationResults.add(new ClassifiedSongPartitions(descriptionOfClassifierInput.get(i).getPathToMusicSong(), 
						descriptionOfClassifierInput.get(i).getSongId(),
						descriptionOfClassifierInput.get(i).getStartMs(), 
						descriptionOfClassifierInput.get(i).getEndMs(), labels, relationships));
			}
		}
		return classificationResults;
	}

	/**
	 * Saves the results of classification to the given output file
	 */
	private void saveClassifierResultToFile(ArrayList<ClassifiedSongPartitions> classifierResult) throws NodeException {
		try {
			String classificationOutput = ((ClassificationConfiguration)taskConfiguration).getClassificationOutput();
			File classifierResultFile = new File(classificationOutput);
			
			if (classifierResultFile.exists()) 
				if (!classifierResultFile.canWrite()) {
					throw new NodeException("Cannot save classification results");
				}
			if (!classifierResultFile.exists()) {
				classifierResultFile.getParentFile().mkdirs();
				classifierResultFile.createNewFile();
			}
			
			FileOutputStream values_to = new FileOutputStream(classifierResultFile);
			DataOutputStream values_writer = new DataOutputStream(values_to);
			String sep = System.getProperty("line.separator");
			values_writer.writeBytes("% Classifier result");
			values_writer.writeBytes(sep);
			values_writer.writeBytes(sep);
			values_writer.writeBytes("@RELATION 'Classification result'");
			values_writer.writeBytes(sep);
			values_writer.writeBytes(sep);
			values_writer.writeBytes("@ATTRIBUTE Id NUMERIC");
			values_writer.writeBytes(sep);
			values_writer.writeBytes("@ATTRIBUTE Filename STRING");
			values_writer.writeBytes(sep);
			values_writer.writeBytes("@ATTRIBUTE StartMs NUMERIC");
			values_writer.writeBytes(sep);
			values_writer.writeBytes("@ATTRIBUTE EndMs NUMERIC");
			values_writer.writeBytes(sep);
			for(int category = 0; category < numberOfCategories; category ++) {
				values_writer.writeBytes("@ATTRIBUTE " + classifierResult.get(0).getLabels()[category] + " NUMERIC");
				values_writer.writeBytes(sep);
			}
			values_writer.writeBytes(sep);
			values_writer.writeBytes(sep);
			values_writer.writeBytes(sep);
			values_writer.writeBytes("@DATA");
			values_writer.writeBytes(sep);
	        
			// If the partition classifications should be combined
			if(((ClassificationConfiguration)taskConfiguration).getMergeSongResults().equals(new Integer("1"))) {
				List<String> songNames = new ArrayList<String>();
				List<double[]> clusterAffiliations = new ArrayList<double[]>();
				
				// Go through all songs
				for(int songNumber=0; songNumber < classifierResult.size(); songNumber++) {
					String currentSongName = classifierResult.get(songNumber).getPathToMusicSong();
					songNames.add(songNumber, currentSongName);
					double[] currentSongsClusterAffiliations = new double[numberOfCategories];
					
					// Write the results
					values_writer.writeBytes(descriptionOfClassifierInput.get(songNumber).getSongId() + ",'" + currentSongName + "',-1,-1");
					
					// Go through all categories
					for(int category=0; category < numberOfCategories; category++) {
						double meanRelationship = 0d;
						// Go through all partitions of the current song
						for(int partitionNumber=0; partitionNumber < classifierResult.get(songNumber).getRelationships().length; partitionNumber++) {
							meanRelationship += classifierResult.get(songNumber).getRelationships()[partitionNumber][category];
						}
						meanRelationship /= classifierResult.get(songNumber).getRelationships().length;
						currentSongsClusterAffiliations[category] = meanRelationship;
						values_writer.writeBytes("," + meanRelationship);
					}
					
					clusterAffiliations.add(songNumber, currentSongsClusterAffiliations);
					values_writer.writeBytes(sep);
				}
				
				this.createVisual(songNames, clusterAffiliations, classificationOutput + "_visual.tex");
			}
			// If the classification results for each partition should be saved 
			// AND there is only one song
			else if (classifierResult.size() == 1) {
				
				List<String> songNames = new ArrayList<String>();
				List<double[]> clusterAffiliations = new ArrayList<double[]>();
				String songName = classifierResult.get(0).getPathToMusicSong();
				
				// Each partition will be treated as a song
				for (int partitionNumber = 0; partitionNumber < classifierResult.get(0).getRelationships().length; partitionNumber++) {
					double[] currentSongsClusterAffiliations = new double[numberOfCategories];
					
					// Write the results
					values_writer.writeBytes("1,'" + songName + "-partition-" + partitionNumber + "',-1,-1");
					for(int category=0;category<numberOfCategories;category++) {
						currentSongsClusterAffiliations[category] = classifierResult.get(0).getRelationships()[partitionNumber][category];
						values_writer.writeBytes("," + currentSongsClusterAffiliations[category]);
					}
					
					clusterAffiliations.add(partitionNumber, currentSongsClusterAffiliations);
					values_writer.writeBytes(sep);
				}
				
				songNames.add(songName);
				this.createVisual(songNames, clusterAffiliations, classificationOutput + "_visual.tex");
			}
			// If the classification results for each partition should be saved 
			// and there are more songs
			else {
				
				// Go through all songs
				for(int i=0;i<classifierResult.size();i++) {
					String currentSongName = classifierResult.get(i).getPathToMusicSong();
					
					// Go through all partitions of the current song
					for(int j=0;j<classifierResult.get(i).getRelationships().length;j++) {
						
						// Save the results
						values_writer.writeBytes(descriptionOfClassifierInput.get(i).getSongId() + "," + currentSongName + "," + 
								classifierResult.get(i).getStartMs()[j] + "," + 
								classifierResult.get(i).getEndMs()[j]);
						
						for(int category=0;category<numberOfCategories;category++) {
							values_writer.writeBytes("," + classifierResult.get(i).getRelationships()[j][category]);
						}
						
						values_writer.writeBytes(sep);
					}
					
				}
			}
			values_writer.close();
		} catch(Exception e) {
			e.printStackTrace();
		}
		
	}
	
	
	/**
	 * Updates the internal variable "numberOfClusters" to the clusters in the given DataSet
	 * @param input = DataSet of which the cluster amount should be counted
	 */
	private void updateTheNumberOfClusters (DataSet input) {
		
			int clusterNumber = 0;
			
			// Go through all attributes and check if their name is "noise" or "cluster_x"
			for(int attributeNumber = 0; attributeNumber < input.getAttributeCount(); attributeNumber++) {
				// "noise" must be checked first as the substring(0,7) of noise throws an error
				if (input.getAttribute(attributeNumber).getName().contentEquals("noise")) {
					clusterNumber++;
				} else if (input.getAttribute(attributeNumber).getName().substring(0,7).equals("cluster")) {
					clusterNumber++;
				}
			}
			
			// Report the cluster count via the AMUSE logger and set the variable (if the count is plausible)
			if (clusterNumber == 0) {
				AmuseLogger.write(ClassifierNodeScheduler.class.getName(), Level.ERROR, "It's supposed to be unsupervised classification "
						+ "but there wasn't any cluster attribute.");
			} else if (clusterNumber == 1) {
				AmuseLogger.write(ClassifierNodeScheduler.class.getName(), Level.INFO, "There is only one giant cluster!");
				numberOfCategories = clusterNumber;
			} else {
				AmuseLogger.write(ClassifierNodeScheduler.class.getName(), Level.DEBUG, "There were "+ clusterNumber +" clusters detected.");
				numberOfCategories = clusterNumber;
			}
	}
	
	
	/**
	 * Create a LaTeX file containing the classification results as a nice table
	 * @param songNames           = A list containing all names of the classified songs
	 * @param clusterAffiliations = A list containing the songs cluster relationships
	 * @param path                = Path where the file should be saved
	 */
	private void createVisual(List<String> songNames, List<double[]> clusterAffiliations, String path) {
		
		AmuseLogger.write(ClassifierNodeScheduler.class.getClass().getName(), Level.DEBUG, "STARTING to create a visual for output path: " + path);
		File visualOutput = new File(path);
		
		try {
			BufferedWriter fileWriter;
			fileWriter = new BufferedWriter(new FileWriter(visualOutput));
			String sep = System.getProperty("line.separator");
			
			// Write the file head and write it
			String fileHead = "\\documentclass[12pt,border=10pt]{standalone} " + sep 
					+ "\\usepackage{booktabs} " + sep
					+ "\\begin{document} " +sep;
			fileWriter.append( fileHead );
			
			// Create the head of the table
			String tabularStart = "   \\begin{tabular}{";
			String clusterNames = "";
			
			// Go through all clusters and create appropriate names for the columns
			for (int clusterNumber=0; clusterNumber < numberOfCategories-1; clusterNumber++) {
				tabularStart += "c";
				clusterNames += "\\multicolumn{1}{c}{\\textbf{Cluster " + clusterNumber + "}} & ";
			}
			tabularStart += "c} " + sep + "   \\toprule " + sep;
			
			// The last cluster can be noise which will be labeled accordingly
			if (((ClassificationConfiguration)taskConfiguration).getNoise()) {
				clusterNames += "\\multicolumn{1}{c}{\\textbf{Noise}} \\" + "\\ " + sep + "   \\midrule " + sep;
			} else {
				clusterNames += "\\multicolumn{1}{c}{\\textbf{Cluster " + (numberOfCategories-1) + "}} \\" + "\\ " + sep + "   \\midrule " + sep;
			}
			
			// Write the table head
			fileWriter.append( tabularStart );
			fileWriter.append( "      " + clusterNames );
		
			List<List<String>> allClustersAndTheirMembers = new ArrayList<List<String>>();
			
			// GET THE NAMES OF THE SONGS BELONGING TO A CLUSTER
			for (int cluster = 0; cluster < numberOfCategories; cluster++) {
				List<String> thisClustersMembers = new ArrayList<String>();
				
				// Go trough the songs / partitions
				for (int song = 0; song < clusterAffiliations.size(); song++) {
					
					// IS THE CURRENT SONG (No. song) IN THIS CLUSTER (No. cluster)?
					int clusterOfThisSong = 0;
					double clusterCertanty = clusterAffiliations.get(song)[0];
					for (int clusterAgain = 0; clusterAgain < numberOfCategories; clusterAgain++) {
						if (clusterCertanty < clusterAffiliations.get(song)[clusterAgain]) {
							clusterCertanty = clusterAffiliations.get(song)[clusterAgain];
							clusterOfThisSong = clusterAgain;
						}
					}
					
					// ADD THE SONGNAME (IF APPROPRIATE) AS A MEMBER OF THIS CLUSTER (No. cluster)
					//classifierResult.get(song).getLabels()[cluster].substring(classifierResult.get(song).getLabels()[cluster].length()-1).equals(cluster)
					if (clusterOfThisSong == cluster) {
						String nameOfThisSong = "";
						
						if (songNames.size() == 1) {
							nameOfThisSong = "Partition " + song;
						} else {
							char[] name = songNames.get(song).toCharArray();
							// Ignore the last 4 chars as they are something like ".mp3" or ".wav"
							for (int positionInString = name.length -5; positionInString >= 0; positionInString--) {
								// If we have reached the end of the song name there'll be a /
								if (name[positionInString] == '/' || name[positionInString] == '\\') {
									break;
								} else if (name[positionInString] == '_') {
									nameOfThisSong = "-" + nameOfThisSong;
								} else {
									nameOfThisSong = name[positionInString] + nameOfThisSong;
								}
							}
						}
						
						// Add a star at the end of the song name to indicate a good cluster representative
						if (clusterCertanty > 0.98) {
							thisClustersMembers.add(nameOfThisSong + "$^{\\ast}$");
						} else {
							thisClustersMembers.add(nameOfThisSong);
						}
					}
					
				}
				allClustersAndTheirMembers.add(thisClustersMembers);
			}
			
			// Count the maximal cluster size
			int maxMemberCount = 0;
			for (int clusters=0; clusters < allClustersAndTheirMembers.size();  clusters++) {
				if (maxMemberCount < allClustersAndTheirMembers.get(clusters).size()) {
					maxMemberCount = allClustersAndTheirMembers.get(clusters).size();
				}
			}
			AmuseLogger.write(ClassifierNodeScheduler.class.getClass().getName(), Level.DEBUG, "There are max " + maxMemberCount + " members in a cluster.");
		
			// Fill other clusters up until they have the same max size
			for (int clusters=0; clusters < allClustersAndTheirMembers.size();  clusters++) {
				if (allClustersAndTheirMembers.get(clusters).size() < maxMemberCount) {
					int difference = maxMemberCount - allClustersAndTheirMembers.get(clusters).size();
					for (int diff = 0; diff < difference; diff++) {
						allClustersAndTheirMembers.get(clusters).add(null);
					}
				}
			}
			
			// Fill the actual table part
			String table = "";
			for (int members = 0; members < maxMemberCount; members++) {
				
				// Go through all table rows and write them separately
				String tableLine = "      "; //indent
				for (int clusters=0; clusters < allClustersAndTheirMembers.size()-1;  clusters++) {
					// If the clusters still has a member
					if (allClustersAndTheirMembers.get(clusters).get(members) != null) {
						tableLine += allClustersAndTheirMembers.get(clusters).get(members) + " & ";
					} else {
						tableLine += " & ";
					}
				}
				if (allClustersAndTheirMembers.get(allClustersAndTheirMembers.size()-1).get(members) != null) {
					tableLine += allClustersAndTheirMembers.get(allClustersAndTheirMembers.size()-1).get(members) + " \\" + "\\";
				} else {
					tableLine += " \\" + "\\";
				}
				
				table += tableLine + sep;
			}
			
			// Write the table
			fileWriter.append( table );
			
			// Write the table and file ending
			String end = "   \\bottomrule" + sep + "   \\end{tabular}" + sep + "\\end{document}" ;
			fileWriter.append( end );
			fileWriter.close();
			
		} catch (Exception e) {
			AmuseLogger.write(ClassifierNodeScheduler.class.getName(), Level.WARN, "Classifier visual couldn't be created: " +e);
		}
	}
	
	
	public void setNumberOfCategories(int numberOfCategories) {
		this.numberOfCategories = numberOfCategories;
	}

	public ArrayList<SongPartitionsDescription> getDescriptionOfClassifierInput() {
		return descriptionOfClassifierInput;
	}
}

