package amuse.nodes.classifier.methods.unsupervised.supportclasses;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Level;

import amuse.data.io.DataSet;
import amuse.data.io.attributes.Attribute;
import amuse.data.io.attributes.NumericAttribute;
import amuse.preferences.AmusePreferences;
import amuse.preferences.KeysStringValue;
import amuse.util.AmuseLogger;

public class Testing {
	
	public static void printMinMax (DataSet data) {
		String minMaxValues = "";
		double minAverage = 0.0;
		double minTrue = (double) data.getAttribute(0).getValueAt(0);
		double maxAverage = 0.0;
		double maxTrue = 0.0;
		
		ArrayList<String> attributes = (ArrayList<String>) data.getAttributeNames();
		for (int attr=0; attr < attributes.size(); attr++) {
			Attribute currentAttribute = data.getAttribute(attr);
			if (currentAttribute.getTypeStr().equals("NUMERIC") && !currentAttribute.getName().substring(0, 7).equals("cluster")) {
				
				minMaxValues += attributes.get(attr) + ": ";
				double min = (double) currentAttribute.getValueAt(0);
				double max = (double) currentAttribute.getValueAt(0);
				
				for (int value=1; value < data.getValueCount(); value++) {
					double currentValue = (double) currentAttribute.getValueAt(value);
					if (currentValue < min) { min = currentValue; }
					if (currentValue > max) { max = currentValue; }
				}
				
				if (min < minTrue) { minTrue = min; }
				if (max > maxTrue) { maxTrue = max; }
				
				minAverage += min;
				maxAverage += max;
				minMaxValues += min + " - " + max + "\n";
			}
		}
		minAverage = minAverage / attributes.size();
		maxAverage = maxAverage / attributes.size();
		//AmuseLogger.write("Testing", Level.DEBUG, "The attributes have the following ranges: \n" + minMaxValues);
		AmuseLogger.write("Testing", Level.DEBUG, "The true min value is " + minTrue + " and the true max value is " + maxTrue);
		AmuseLogger.write("Testing", Level.DEBUG, "On average there is a min value of " + minAverage + " and a max value of " + maxAverage);
	}
	
	public static DataSet createTestDataSet (DataSet originalSet) throws IOException {
		DataSet testSet = new DataSet("testDataSet");
		
		int attributeCount = originalSet.getAttributeCount();
		int valueCount = originalSet.getValueCount();
		
		for (int x=0; x < attributeCount; x++ ) {
			List<Double> featureXList = new ArrayList<Double>();
			double tal = 1;
			
			for (int i=0; i < valueCount; i++) {
				featureXList.add(tal);
				tal += i;
			}
			
			Attribute featureX = new NumericAttribute(originalSet.getAttribute(x).getName() , featureXList);
			testSet.addAttribute(featureX);
			
		}
		
		String path = AmusePreferences.get(KeysStringValue.AMUSE_PATH) + File.separator + "experiments" + File.separator + "DataSetToClassifyTEST.arff";
		testSet.saveToArffFile(new File(path));
		
		return testSet;
	}
	
	
	public static void createTestFile (String path) throws Exception {
		
		FileOutputStream values_to;
		try {
			values_to = new FileOutputStream(new File(path));
			DataOutputStream values_writer = new DataOutputStream(values_to);
			String sep = System.getProperty("line.separator");
			values_writer.writeBytes("@RELATION 'Classifier input'");
			values_writer.writeBytes(sep);
			values_writer.writeBytes(sep);
			values_writer.writeBytes("@ATTRIBUTE 'Mean_1(NaN_eliminated(Zero-crossing rate))' NUMERIC");
			values_writer.writeBytes(sep);
			values_writer.writeBytes("@ATTRIBUTE 'Std_dev_1(NaN_eliminated(Zero-crossing rate))' NUMERIC");
			values_writer.writeBytes(sep);
			values_writer.writeBytes("@ATTRIBUTE 'Mean_1(NaN_eliminated(Root mean square))' NUMERIC");
			values_writer.writeBytes(sep);
			values_writer.writeBytes("@ATTRIBUTE 'Std_dev_1(NaN_eliminated(Root mean square))' NUMERIC");
			values_writer.writeBytes(sep);
			values_writer.writeBytes("@ATTRIBUTE 'Mean_1(NaN_eliminated(Low energy))' NUMERIC");
			values_writer.writeBytes(sep);
			values_writer.writeBytes("@ATTRIBUTE 'Std_dev_1(NaN_eliminated(Low energy))' NUMERIC");
			values_writer.writeBytes(sep);
			values_writer.writeBytes("@ATTRIBUTE 'Mean_1(NaN_eliminated(Normalized energy of harmonic components))' NUMERIC");
			values_writer.writeBytes(sep);
			values_writer.writeBytes("@ATTRIBUTE 'Std_dev_1(NaN_eliminated(Normalized energy of harmonic components))' NUMERIC");
			values_writer.writeBytes(sep);
			values_writer.writeBytes("@ATTRIBUTE 'Mean_1(NaN_eliminated(Chroma vector))' NUMERIC");
			values_writer.writeBytes(sep);
			values_writer.writeBytes("@ATTRIBUTE 'Std_dev_1(NaN_eliminated(Chroma vector))' NUMERIC");
			values_writer.writeBytes(sep);
			values_writer.writeBytes("@ATTRIBUTE 'Mean_2(NaN_eliminated(Chroma vector))' NUMERIC");
			values_writer.writeBytes(sep);
			values_writer.writeBytes("@ATTRIBUTE 'Std_dev_2(NaN_eliminated(Chroma vector))' NUMERIC");
			values_writer.writeBytes(sep);
			values_writer.writeBytes("@ATTRIBUTE 'Mean_3(NaN_eliminated(Chroma vector))' NUMERIC");
			values_writer.writeBytes(sep);
			values_writer.writeBytes("@ATTRIBUTE 'Std_dev_3(NaN_eliminated(Chroma vector))' NUMERIC");
			values_writer.writeBytes(sep);
			values_writer.writeBytes("@ATTRIBUTE 'Mean_4(NaN_eliminated(Chroma vector))' NUMERIC");
			values_writer.writeBytes(sep);
			values_writer.writeBytes("@ATTRIBUTE 'Std_dev_4(NaN_eliminated(Chroma vector))' NUMERIC");
			values_writer.writeBytes(sep);
			values_writer.writeBytes("@ATTRIBUTE 'Mean_5(NaN_eliminated(Chroma vector))' NUMERIC");
			values_writer.writeBytes(sep);
			values_writer.writeBytes("@ATTRIBUTE 'Std_dev_5(NaN_eliminated(Chroma vector))' NUMERIC");
			values_writer.writeBytes(sep);
			values_writer.writeBytes("@ATTRIBUTE 'Mean_6(NaN_eliminated(Chroma vector))' NUMERIC");
			values_writer.writeBytes(sep);
			values_writer.writeBytes("@ATTRIBUTE 'Std_dev_6(NaN_eliminated(Chroma vector))' NUMERIC");
			values_writer.writeBytes(sep);
			values_writer.writeBytes("@ATTRIBUTE 'Mean_7(NaN_eliminated(Chroma vector))' NUMERIC");
			values_writer.writeBytes(sep);
			values_writer.writeBytes("@ATTRIBUTE 'Std_dev_7(NaN_eliminated(Chroma vector))' NUMERIC");
			values_writer.writeBytes(sep);
			values_writer.writeBytes("@ATTRIBUTE 'Mean_8(NaN_eliminated(Chroma vector))' NUMERIC");
			values_writer.writeBytes(sep);
			values_writer.writeBytes("@ATTRIBUTE 'Std_dev_8(NaN_eliminated(Chroma vector))' NUMERIC");
			values_writer.writeBytes(sep);
			values_writer.writeBytes("@ATTRIBUTE 'Mean_9(NaN_eliminated(Chroma vector))' NUMERIC");
			values_writer.writeBytes(sep);
			values_writer.writeBytes("@ATTRIBUTE 'Std_dev_9(NaN_eliminated(Chroma vector))' NUMERIC");
			values_writer.writeBytes(sep);
			values_writer.writeBytes("@ATTRIBUTE 'Mean_10(NaN_eliminated(Chroma vector))' NUMERIC");
			values_writer.writeBytes(sep);
			values_writer.writeBytes("@ATTRIBUTE 'Std_dev_10(NaN_eliminated(Chroma vector))' NUMERIC");
			values_writer.writeBytes(sep);
			values_writer.writeBytes("@ATTRIBUTE 'Mean_11(NaN_eliminated(Chroma vector))' NUMERIC");
			values_writer.writeBytes(sep);
			values_writer.writeBytes("@ATTRIBUTE 'Std_dev_11(NaN_eliminated(Chroma vector))' NUMERIC");
			values_writer.writeBytes(sep);
			values_writer.writeBytes("@ATTRIBUTE 'Mean_12(NaN_eliminated(Chroma vector))' NUMERIC");
			values_writer.writeBytes(sep);
			values_writer.writeBytes("@ATTRIBUTE 'Std_dev_12(NaN_eliminated(Chroma vector))' NUMERIC");
			values_writer.writeBytes(sep);
			values_writer.writeBytes("@ATTRIBUTE 'Mean_13(NaN_eliminated(Chroma vector))' NUMERIC");
			values_writer.writeBytes(sep);
			values_writer.writeBytes("@ATTRIBUTE 'Std_dev_13(NaN_eliminated(Chroma vector))' NUMERIC");
			values_writer.writeBytes(sep);
			values_writer.writeBytes("@ATTRIBUTE 'Mean_14(NaN_eliminated(Chroma vector))' NUMERIC");
			values_writer.writeBytes(sep);
			values_writer.writeBytes("@ATTRIBUTE 'Std_dev_14(NaN_eliminated(Chroma vector))' NUMERIC");
			values_writer.writeBytes(sep);
			values_writer.writeBytes("@ATTRIBUTE 'Mean_15(NaN_eliminated(Chroma vector))' NUMERIC");
			values_writer.writeBytes(sep);
			values_writer.writeBytes("@ATTRIBUTE 'Std_dev_15(NaN_eliminated(Chroma vector))' NUMERIC");
			values_writer.writeBytes(sep);
			values_writer.writeBytes("@ATTRIBUTE 'Mean_16(NaN_eliminated(Chroma vector))' NUMERIC");
			values_writer.writeBytes(sep);
			values_writer.writeBytes("@ATTRIBUTE 'Std_dev_16(NaN_eliminated(Chroma vector))' NUMERIC");
			values_writer.writeBytes(sep);
			values_writer.writeBytes("@ATTRIBUTE 'Mean_17(NaN_eliminated(Chroma vector))' NUMERIC");
			values_writer.writeBytes(sep);
			values_writer.writeBytes("@ATTRIBUTE 'Std_dev_17(NaN_eliminated(Chroma vector))' NUMERIC");
			values_writer.writeBytes(sep);
			values_writer.writeBytes("@ATTRIBUTE 'Mean_18(NaN_eliminated(Chroma vector))' NUMERIC");
			values_writer.writeBytes(sep);
			values_writer.writeBytes("@ATTRIBUTE 'Std_dev_18(NaN_eliminated(Chroma vector))' NUMERIC");
			values_writer.writeBytes(sep);
			values_writer.writeBytes("@ATTRIBUTE 'Mean_19(NaN_eliminated(Chroma vector))' NUMERIC");
			values_writer.writeBytes(sep);
			values_writer.writeBytes("@ATTRIBUTE 'Std_dev_19(NaN_eliminated(Chroma vector))' NUMERIC");
			values_writer.writeBytes(sep);
			values_writer.writeBytes("@ATTRIBUTE 'Mean_20(NaN_eliminated(Chroma vector))' NUMERIC");
			values_writer.writeBytes(sep);
			values_writer.writeBytes("@ATTRIBUTE 'Std_dev_20(NaN_eliminated(Chroma vector))' NUMERIC");
			values_writer.writeBytes(sep);
			values_writer.writeBytes("@ATTRIBUTE 'Mean_21(NaN_eliminated(Chroma vector))' NUMERIC");
			values_writer.writeBytes(sep);
			values_writer.writeBytes("@ATTRIBUTE 'Std_dev_21(NaN_eliminated(Chroma vector))' NUMERIC");
			values_writer.writeBytes(sep);
			values_writer.writeBytes("@ATTRIBUTE 'Mean_22(NaN_eliminated(Chroma vector))' NUMERIC");
			values_writer.writeBytes(sep);
			values_writer.writeBytes("@ATTRIBUTE 'Std_dev_22(NaN_eliminated(Chroma vector))' NUMERIC");
			values_writer.writeBytes(sep);
			values_writer.writeBytes("@ATTRIBUTE 'Mean_23(NaN_eliminated(Chroma vector))' NUMERIC");
			values_writer.writeBytes(sep);
			values_writer.writeBytes("@ATTRIBUTE 'Std_dev_23(NaN_eliminated(Chroma vector))' NUMERIC");
			values_writer.writeBytes(sep);
			values_writer.writeBytes("@ATTRIBUTE 'Mean_24(NaN_eliminated(Chroma vector))' NUMERIC");
			values_writer.writeBytes(sep);
			values_writer.writeBytes("@ATTRIBUTE 'Std_dev_24(NaN_eliminated(Chroma vector))' NUMERIC");
			values_writer.writeBytes(sep);
			values_writer.writeBytes("@ATTRIBUTE 'Mean_1(NaN_eliminated(Tempo))' NUMERIC");
			values_writer.writeBytes(sep);
			values_writer.writeBytes("@ATTRIBUTE 'Std_dev_1(NaN_eliminated(Tempo))' NUMERIC");
			values_writer.writeBytes(sep);
			
			//values_writer.writeBytes("@ATTRIBUTE Unit {milliseconds,samples}");
			//values_writer.writeBytes(sep);
			//values_writer.writeBytes("@ATTRIBUTE Start NUMERIC");
			//values_writer.writeBytes(sep);
			//values_writer.writeBytes("@ATTRIBUTE End NUMERIC");
			//values_writer.writeBytes(sep);
			
			values_writer.writeBytes(sep);
			values_writer.writeBytes("@DATA");
			values_writer.writeBytes(sep);
			
			int partitions = 20;
			int attributes = 58;
			int tal = 1;
			for (int p=0; p < partitions; p++) {
				for (int a=0; a < attributes - 1; a++) {
					values_writer.writeBytes(tal + ",");
				}
				values_writer.writeBytes(tal+"");
				values_writer.writeBytes(sep);
				tal = tal*2;
			}
			
			values_writer.close();
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}
