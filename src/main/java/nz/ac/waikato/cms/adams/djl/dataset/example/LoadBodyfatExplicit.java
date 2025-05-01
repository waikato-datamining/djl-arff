/*
 * LoadBodyfatExplicit.java
 * Copyright (C) 2025 University of Waikato, Hamilton, New Zealand
 */

package nz.ac.waikato.cms.adams.djl.dataset.example;

import nz.ac.waikato.cms.adams.djl.dataset.ArffDataset;

import java.nio.file.Path;

/**
 * Just loads an ARFF dataset and outputs the data.
 *
 * @author fracpete (fracpete at waikato dot ac dot nz)
 */
public class LoadBodyfatExplicit {

  public static void main(String[] args) throws Exception {
    ArffDataset dataset = ArffDataset.builder()
			    .optArffFile(Path.of("src/main/resources/bodyfat.arff"))
			    .setSampling(32, true)
			    .addNumericFeature("Density")
			    .addNumericFeature("Age")
			    .addNumericFeature("Weight")
			    .addNumericFeature("Height")
			    .addNumericFeature("Neck")
			    .addNumericFeature("Chest")
			    .addNumericFeature("Abdomen")
			    .addNumericFeature("Hip")
			    .addNumericFeature("Thigh")
			    .addNumericFeature("Knee")
			    .addNumericFeature("Ankle")
			    .addNumericFeature("Biceps")
			    .addNumericFeature("Forearm")
			    .addNumericFeature("Wrist")
			    .addNumericLabel("class")
			    .build();
    dataset.prepare();
    System.out.println(dataset.getRelationName());
    for (int i = 0; i < dataset.size(); i++) {
      for (int n = 0; n < dataset.getFeatureSize(); n++) {
	String cell = dataset.getCell(i, dataset.getFeatures().get(n).getName());
	if (n > 0)
	  System.out.print(",");
	System.out.print(cell);
      }
      String cell = dataset.getCell(i, dataset.getLabels().get(0).getName());
      System.out.println("," + cell);
    }
  }
}
