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
 *  Creation date: 23.01.2009
 */ 
package amuse.nodes.validator.measures.confusionmatrix;

import java.util.ArrayList;

import amuse.data.annotation.ClassifiedSongPartitions;
import amuse.interfaces.nodes.NodeException;
import amuse.nodes.validator.interfaces.ClassificationQualityDoubleMeasureCalculator;
import amuse.nodes.validator.interfaces.ValidationMeasureDouble;

/**
 * F-Measure measure 
 *  
 * @author Igor Vatolkin
 * @version $Id: FMeasure.java 243 2018-09-07 14:18:30Z frederik-h $
 */
public class FMeasure extends ClassificationQualityDoubleMeasureCalculator {

	/**
	 * @see amuse.nodes.validator.interfaces.ClassificationQualityMeasureCalculatorInterface#setParameters(java.lang.String)
	 */
	public void setParameters(String parameterString) throws NodeException {
		// TODO Currently only F1 is calculated!
	}
	
	/**
	 * @see amuse.nodes.validator.interfaces.ClassificationQualityMeasureCalculatorInterface#calculateOneClassMeasureOnSongLevel(java.util.ArrayList, java.util.ArrayList)
	 */
	public ValidationMeasureDouble[] calculateOneClassMeasureOnSongLevel(ArrayList<Double> groundTruthRelationships, ArrayList<ClassifiedSongPartitions> predictedRelationships) throws NodeException {
		
		// Get precision
		Precision precisionCalculator = new Precision();
		precisionCalculator.setSongLevel(true);
		precisionCalculator.setContinuous(isContinuous());
		ValidationMeasureDouble p = precisionCalculator.calculateOneClassMeasure(groundTruthRelationships, predictedRelationships)[0];
		
		// Get recall
		Recall recallCalculator = new Recall();
		recallCalculator.setSongLevel(true);
		recallCalculator.setContinuous(isContinuous());
		ValidationMeasureDouble r = recallCalculator.calculateOneClassMeasure(groundTruthRelationships, predictedRelationships)[0];
		
		double fMeasure = 2 * p.getValue() * r.getValue() / (p.getValue() + r.getValue());
		
		// Prepare the result
		ValidationMeasureDouble[] fMeasureMeasure = new ValidationMeasureDouble[1];
		fMeasureMeasure[0] = new ValidationMeasureDouble(false);
		fMeasureMeasure[0].setId(108);
		fMeasureMeasure[0].setName("F-measure on song level");
		fMeasureMeasure[0].setValue(new Double(fMeasure));
		return fMeasureMeasure;
	}

	/**
	 * @see amuse.nodes.validator.interfaces.ClassificationQualityMeasureCalculatorInterface#calculateOneClassMeasureOnPartitionLevel(java.util.ArrayList, java.util.ArrayList)
	 */
	public ValidationMeasureDouble[] calculateOneClassMeasureOnPartitionLevel(ArrayList<Double> groundTruthRelationships, ArrayList<ClassifiedSongPartitions> predictedRelationships) throws NodeException {
		
		// Get precision
		Precision precisionCalculator = new Precision();
		precisionCalculator.setPartitionLevel(true);
		precisionCalculator.setContinuous(isContinuous());
		ValidationMeasureDouble p = precisionCalculator.calculateOneClassMeasure(groundTruthRelationships, predictedRelationships)[0];
		
		// Get recall
		Recall recallCalculator = new Recall();
		recallCalculator.setPartitionLevel(true);
		recallCalculator.setContinuous(isContinuous());
		ValidationMeasureDouble r = recallCalculator.calculateOneClassMeasure(groundTruthRelationships, predictedRelationships)[0];
		
		double fMeasure = 2 * p.getValue() * r.getValue() / (p.getValue() + r.getValue());
		
		// Prepare the result
		ValidationMeasureDouble[] fMeasureMeasure = new ValidationMeasureDouble[1];
		fMeasureMeasure[0] = new ValidationMeasureDouble(false);
		fMeasureMeasure[0].setId(108);
		fMeasureMeasure[0].setName("F-measure on partition level");
		fMeasureMeasure[0].setValue(new Double(fMeasure));
		return fMeasureMeasure;
	}

	
	/**
	 * @see amuse.nodes.validator.interfaces.ClassificationQualityMeasureCalculatorInterface#calculateMulticlassMeasureOnSongLevel(java.util.ArrayList, java.util.ArrayList)
	 */
	public ValidationMeasureDouble[] calculateMultiClassMeasureOnSongLevel(ArrayList<ClassifiedSongPartitions> groundTruthRelationships, ArrayList<ClassifiedSongPartitions> predictedRelationships) throws NodeException {
		return calculateMultiLabelMeasureOnSongLevel(groundTruthRelationships, predictedRelationships);
	}


	/**
	 * @see amuse.nodes.validator.interfaces.ClassificationQualityMeasureCalculatorInterface#calculateMulticlassMeasureOnPartitionLevel(java.util.ArrayList, java.util.ArrayList)
	 */
	public ValidationMeasureDouble[] calculateMultiClassMeasureOnPartitionLevel(ArrayList<ClassifiedSongPartitions> groundTruthRelationships, ArrayList<ClassifiedSongPartitions> predictedRelationships) throws NodeException {
		return calculateMultiLabelMeasureOnPartitionLevel(groundTruthRelationships, predictedRelationships);
	}

	/*
	 * (non-Javadoc)
	 * @see amuse.nodes.validator.interfaces.ClassificationQualityMeasureCalculatorInterface#calculateMultiLabelMeasureOnSongLevel(java.util.ArrayList, java.util.ArrayList)
	 */
	public ValidationMeasureDouble[] calculateMultiLabelMeasureOnSongLevel(ArrayList<ClassifiedSongPartitions> groundTruthRelationships, ArrayList<ClassifiedSongPartitions> predictedRelationships) throws NodeException {
		
		// Get precision
		Precision precisionCalculator = new Precision();
		precisionCalculator.setSongLevel(true);
		precisionCalculator.setContinuous(isContinuous());
		ValidationMeasureDouble p = precisionCalculator.calculateMultiLabelMeasure(groundTruthRelationships, predictedRelationships)[0];
		
		// Get recall
		Recall recallCalculator = new Recall();
		recallCalculator.setSongLevel(true);
		recallCalculator.setContinuous(isContinuous());
		ValidationMeasureDouble r = recallCalculator.calculateMultiLabelMeasure(groundTruthRelationships, predictedRelationships)[0];
		
		double fMeasure = 2 * p.getValue() * r.getValue() / (p.getValue() + r.getValue());
		
		// Prepare the result
		ValidationMeasureDouble[] fMeasureMeasure = new ValidationMeasureDouble[1];
		fMeasureMeasure[0] = new ValidationMeasureDouble(false);
		fMeasureMeasure[0].setId(108);
		fMeasureMeasure[0].setName("F-measure on song level");
		fMeasureMeasure[0].setValue(new Double(fMeasure));
		return fMeasureMeasure;
	}


	/*
	 * (non-Javadoc)
	 * @see amuse.nodes.validator.interfaces.ClassificationQualityMeasureCalculatorInterface#calculateMultiLabelMeasureOnPartitionLevel(java.util.ArrayList, java.util.ArrayList)
	 */
	public ValidationMeasureDouble[] calculateMultiLabelMeasureOnPartitionLevel(ArrayList<ClassifiedSongPartitions> groundTruthRelationships, ArrayList<ClassifiedSongPartitions> predictedRelationships) throws NodeException {
		// Get precision
		Precision precisionCalculator = new Precision();
		precisionCalculator.setPartitionLevel(true);
		precisionCalculator.setContinuous(isContinuous());
		ValidationMeasureDouble p = precisionCalculator.calculateMultiLabelMeasure(groundTruthRelationships, predictedRelationships)[0];
		
		// Get recall
		Recall recallCalculator = new Recall();
		recallCalculator.setPartitionLevel(true);
		recallCalculator.setContinuous(isContinuous());
		ValidationMeasureDouble r = recallCalculator.calculateMultiLabelMeasure(groundTruthRelationships, predictedRelationships)[0];
		
		double fMeasure = 2 * p.getValue() * r.getValue() / (p.getValue() + r.getValue());
		
		// Prepare the result
		ValidationMeasureDouble[] fMeasureMeasure = new ValidationMeasureDouble[1];
		fMeasureMeasure[0] = new ValidationMeasureDouble(false);
		fMeasureMeasure[0].setId(108);
		fMeasureMeasure[0].setName("F-measure on partition level");
		fMeasureMeasure[0].setValue(new Double(fMeasure));
		return fMeasureMeasure;
	}
}

