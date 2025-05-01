/*
 * ArffDataset.java
 * Copyright (C) 2025 University of Waikato, Hamilton, New Zealand
 */

package nz.ac.waikato.cms.adams.djl.dataset;

import ai.djl.basicdataset.tabular.TabularDataset;
import ai.djl.util.Progress;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/**
 * {@code ArffDataset} represents the dataset that is stored in an .arff[.gz] file.
 * Only supports NUMERIC, NOMINAL, STRING and DATE attributes. DATE attributes get parsed
 * and the epoch time is stored as string, i.e., treated as NUMERIC attribute.
 *
 * @author fracpete (fracpete at waikato dot ac dot nz)
 */
public class ArffDataset extends TabularDataset {

  protected URL arffUrl;
  protected String relationName;
  protected List<String> colNames;
  protected List<ArffAttributeType> colTypes;
  protected List<List<String>> data;
  protected List<Map<String,String>> header;
  protected Map<String,Integer> attLookUp;

  protected ArffDataset(ArffBuilder<?> builder) {
    super(builder);
    arffUrl = builder.arffUrl;
  }

  /** {@inheritDoc} */
  @Override
  public String getCell(long rowIndex, String featureName) {
    List<String> record = data.get(Math.toIntExact(rowIndex));
    return record.get(attLookUp.get(featureName));
  }

  /** {@inheritDoc} */
  @Override
  protected long availableSize() {
    return data.size();
  }

  /**
   * Performs the actual reading.
   *
   * @param r			the reader to read from
   * @throws IOException	if reading fails
   */
  protected void parseDataset(Reader r) throws IOException {
    ArffParser	parser;

    parser = new ArffParser();
    parser.parse(r);

    data      = parser.getData();
    header    = parser.getHeader();
    colNames  = parser.getColNames();
    colTypes  = parser.getColTypes();
    attLookUp = parser.getAttLookUp();
  }

  /** {@inheritDoc} */
  @Override
  public void prepare(Progress progress) throws IOException {
    try (Reader reader = new InputStreamReader(getArffStream(), StandardCharsets.UTF_8)) {
      parseDataset(reader);
    }
    prepareFeaturizers();
  }

  private InputStream getArffStream() throws IOException {
    if (arffUrl.getFile().endsWith(".gz"))
      return new GZIPInputStream(arffUrl.openStream());
    else
      return new BufferedInputStream(arffUrl.openStream());
  }

  /**
   * Creates a builder to build a {@link ArffDataset}.
   *
   * @return a new builder
   */
  public static ArffDataset.ArffBuilder<?> builder() {
    return new ArffDataset.ArffBuilder<>();
  }

  /**
   * Returns the relation name.
   *
   * @return the name of the dataset
   */
  public String getRelationName() {
    return relationName;
  }

  /**
   * Returns the column names of the ARFF file.
   *
   * @return a list of column name
   */
  public List<String> getColumnNames() {
    if ((header == null) || header.isEmpty()) {
      return Collections.emptyList();
    }
    return colNames;
  }

  /**
   * Returns the attribute type for the specified column.
   *
   * @param name the name of the column to get the type for
   * @return the type, null if no dataset info available
   */
  public ArffAttributeType getColumnType(String name) {
    if ((header == null) || header.isEmpty())
      return null;
    return colTypes.get(attLookUp.get(name));
  }

  /**
   * Returns the dataset header information.
   * Information for each attribute in the sequence they appear: name, type, format (only date attributes).
   *
   * @return the header info
   */
  public List<Map<String,String>> getHeader() {
    return header;
  }

  /** Used to build a {@link ArffDataset}. */
  public static class ArffBuilder<T extends ArffDataset.ArffBuilder<T>>
    extends TabularDataset.BaseBuilder<T> {

    protected URL arffUrl;

    /** {@inheritDoc} */
    @Override
    @SuppressWarnings("unchecked")
    protected T self() {
      return (T) this;
    }

    /**
     * Sets the optional ARFF file path.
     *
     * @param arffFile the ARFF file path
     * @return this builder
     */
    public T optArffFile(Path arffFile) {
      try {
	this.arffUrl = arffFile.toAbsolutePath().toUri().toURL();
      } catch (MalformedURLException e) {
	throw new IllegalArgumentException("Invalid file path: " + arffFile, e);
      }
      return self();
    }

    /**
     * Sets the optional ARFF file URL.
     *
     * @param arffUrl the ARFF file URL
     * @return this builder
     */
    public T optArffUrl(String arffUrl) {
      try {
	this.arffUrl = new URL(arffUrl);
      } catch (MalformedURLException e) {
	throw new IllegalArgumentException("Invalid url: " + arffUrl, e);
      }
      return self();
    }

    /**
     * Builds the new {@link ArffDataset}.
     *
     * @return the new {@link ArffDataset}
     */
    public ArffDataset build() {
      return new ArffDataset(this);
    }
  }
}
