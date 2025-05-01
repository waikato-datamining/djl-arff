/*
 * LoadIris.java
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
public class LoadIris {

  public static void main(String[] args) throws Exception {
    ArffDataset dataset = ArffDataset.builder()
			    .optArffFile(Path.of("src/main/resources/iris.arff"))
			    .setSampling(32, true)
			    .addNumericFeature("sepallength")
			    .addNumericFeature("sepalwidth")
			    .addNumericFeature("petallength")
			    .addNumericFeature("petalwidth")
			    .addCategoricalLabel("class")
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
