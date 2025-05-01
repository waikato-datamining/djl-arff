/*
 * ArffDataset.java
 * Copyright (C) 2025 University of Waikato, Hamilton, New Zealand
 */

package nz.ac.waikato.cms.adams.djl.dataset;

import ai.djl.basicdataset.tabular.TabularDataset;
import ai.djl.basicdataset.tabular.utils.Feature;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPInputStream;

/**
 * {@code ArffDataset} represents the dataset that is stored in an .arff[.gz] file.
 * Only supports NUMERIC, NOMINAL, STRING and DATE attributes.
 * By default, DATE and STRING attributes get ignored.
 * DATE attributes can be treated as NUMERIC ones: get parsed
 * and the epoch time is stored as NUMERIC string.
 * STRING attributes can be treated as NOMINAL ones.
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

    relationName = parser.getRelationName();
    data         = parser.getData();
    header       = parser.getHeader();
    colNames     = parser.getColNames();
    colTypes     = parser.getColTypes();
    attLookUp    = parser.getAttLookUp();
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

  /**
   * Generates a string with information about the features/labels.
   *
   * @return the info
   */
  public String toInfo() {
    StringBuilder	result;

    result = new StringBuilder("Relation").append(": ").append(getRelationName());

    result.append("\n").append("Features: ").append(getFeatureSize());
    for (Feature feature: getFeatures()) {
      result.append("\n");
      result.append("- ").append(feature.getName());
      result.append("/");
      result.append(getColumnType(feature.getName()));
      result.append("/");
      result.append(feature.getFeaturizer().getClass().getSimpleName());
    }

    if (getFeatureSize() > 0) {
      result.append("\n").append("Labels: ").append(getLabelSize());
      for (Feature feature : getLabels()) {
	result.append("\n");
	result.append("- ").append(feature.getName());
	result.append("/");
	result.append(getColumnType(feature.getName()));
	result.append("/");
	result.append(feature.getFeaturizer().getClass().getSimpleName());
      }
    }

    return result.toString();
  }

  /** Used to build a {@link ArffDataset}. */
  public static class ArffBuilder<T extends ArffDataset.ArffBuilder<T>>
    extends TabularDataset.BaseBuilder<T> {

    protected URL arffUrl;

    protected Set<String> classColumns;

    protected Set<String> ignoredColumns;

    protected ArffParser parser;

    protected boolean classAdded;

    protected boolean allFeaturesAdded;

    protected Set<String> matchingFeaturesAdded;

    protected boolean stringColumnsAsNominal;

    protected boolean dateColumnsAsNumeric;

    /**
     * Initializes the builder.
     */
    protected ArffBuilder() {
      super();

      classAdded             = false;
      classColumns           = new HashSet<>();
      ignoredColumns         = new HashSet<>();
      allFeaturesAdded       = false;
      matchingFeaturesAdded  = new HashSet<>();
      stringColumnsAsNominal = false;
      dateColumnsAsNumeric   = false;
    }

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
      parser = null;
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
      parser = null;
      try {
	this.arffUrl = new URL(arffUrl);
      } catch (MalformedURLException e) {
	throw new IllegalArgumentException("Invalid url: " + arffUrl, e);
      }
      return self();
    }

    /**
     * Sets whether to treat DATE columns as NUMERIC ones.
     *
     * @return this builder
     */
    public T dateColumnsAsNumeric() {
      dateColumnsAsNumeric = true;
      return self();
    }

    /**
     * Sets whether to treat STRING columns as NOMINAL ones.
     *
     * @return this builder
     */
    public T stringColumnsAsNominal() {
      stringColumnsAsNominal = true;
      return self();
    }

    /**
     * Returns the parser instance. If necessary instantiates and parses the header.
     *
     * @return the parser, null if it can't be instantiated
     */
    protected ArffParser getParser() {
      InputStream	is;
      InputStreamReader	ir;

      if (parser == null) {
	if (arffUrl != null) {
	  is = null;
	  ir = null;
	  try {
	    if (arffUrl.getFile().endsWith(".gz"))
	      is = new GZIPInputStream(arffUrl.openStream());
	    else
	      is = new BufferedInputStream(arffUrl.openStream());
	    ir = new InputStreamReader(is, StandardCharsets.UTF_8);
	    parser = new ArffParser();
	    parser.parseHeader(ir);
	  }
	  catch (Exception e) {
	    // ignored
	  }
	  if (ir != null) {
	    try {
	      ir.close();
	    }
	    catch (Exception e) {
	      // ignored
	    }
	  }
	  if (is != null) {
	    try {
	      is.close();
	    }
	    catch (Exception e) {
	      // ignored
	    }
	  }
	}
      }
      return parser;
    }

    /**
     * Sets the index of the column to use as class attribute.
     *
     * @param index the 0-based index (based on column in ARFF file)
     * @return this builder
     */
    public T classIndex(int index) {
      ArffParser	parser;

      if (index < -1)
	index = -1;
      if (index == -1)
	return self();

      parser = getParser();
      addClassColumn(parser.getColNames().get(index));
      return self();
    }

    /**
     * Sets the flag that the class attribute is the first column.
     *
     * @return this builder
     */
    public T classIsFirst() {
      return classIndex(0);
    }

    /**
     * Sets the flag that the class attribute is the last column.
     *
     * @return this builder
     */
    public T classIsLast() {
      ArffParser	parser;

      parser = getParser();
      addClassColumn(parser.getColNames().get(parser.getColNames().size() - 1));
      return self();
    }

    /**
     * Checks if the specified column is a class attribute.
     *
     * @param parser the parser to use
     * @param index the column index in the dataset
     * @return true if class column
     */
    protected boolean isClassColumn(ArffParser parser, int index) {
      return classColumns.contains(parser.getColNames().get(index));
    }

    /**
     * Adds the column as feature or label.
     * Skips ignored columns.
     *
     * @param parser		the parser to use
     * @param index		the index of the column to add
     */
    protected void addColumn(ArffParser parser, int index) {
      ArffAttributeType	colType;
      String		colName;

      colName = parser.getColNames().get(index);
      colType = parser.getColTypes().get(index);

      if (ignoredColumns.contains(colName))
	return;

      if (isClassColumn(parser, index)) {
	switch (colType) {
	  case NUMERIC:
	    addNumericLabel(colName);
	    break;
	  case DATE:
	    if (dateColumnsAsNumeric)
	      addNumericLabel(colName);
	    break;
	  case NOMINAL:
	    addCategoricalLabel(colName);
	    break;
	  case STRING:
	    if (stringColumnsAsNominal)
	      addCategoricalLabel(colName);
	    break;
	  default:
	    throw new IllegalStateException("Unhandled class attribute type: " + colType);
	}
      }
      else {
	switch (colType) {
	  case NUMERIC:
	    addNumericFeature(colName);
	    break;
	  case DATE:
	    if (dateColumnsAsNumeric)
	      addNumericFeature(colName);
	    break;
	  case NOMINAL:
	    addCategoricalFeature(colName);
	    break;
	  case STRING:
	    if (stringColumnsAsNominal)
	      addCategoricalFeature(colName);
	    break;
	  default:
	    throw new IllegalStateException("Unhandled class attribute type: " + colType);
	}
      }
    }

    /**
     * Adds the class attribute(s).
     * Loads the ARFF file and parses the header.
     *
     * @param colNames the column(s) to use as class attribute(s)
     * @return this builder
     */
    public T addClassColumn(String... colNames) {
      ArffParser	parser;
      int		index;

      parser = getParser();
      for (String colName: colNames) {
	if (classColumns.contains(colName))
	  continue;
	classColumns.add(colName);
	index = parser.getAttLookUp().get(colName);
	addColumn(parser, index);
      }

      return self();
    }

    /**
     * Adds the column name to ignore when adding all features.
     *
     * @param name the name of the column to ignore
     * @return this builder
     */
    public T addIgnoredColumn(String name) {
      ignoredColumns.add(name);
      return self();
    }

    /**
     * Ignores all column names that match the regexp.
     *
     * @param regexp 	the regular expression to use
     * @return 		this builder
     */
    public T ignoreMatchingColumns(String regexp) {
      ArffParser	parser;

      parser = getParser();
      for (String colName: parser.getColNames()) {
	if (colName.matches(regexp))
	  addIgnoredColumn(colName);
      }

      return self();
    }

    /**
     * Adds all features according to their types.
     * Skips ignored column names and class attribute(s).
     * Column specified via class index/classIsLast gets added appropriately.
     * Loads the ARFF file and parses the header.
     *
     * @return this builder
     */
    public T addAllFeatures() {
      ArffParser	parser;
      int		i;

      if (allFeaturesAdded)
	return self();

      allFeaturesAdded = true;
      parser           = getParser();

      // add all columns
      for (i = 0; i < parser.getColNames().size(); i++) {
	if (isClassColumn(parser, i))
	  continue;
	addColumn(parser, i);
      }

      return self();
    }

    /**
     * Adds all feature columns which names match the regular expression.
     * Skips class attributes.
     *
     * @param regexp the regular expression to apply
     * @return this builder
     */
    public T addMatchingFeatures(String regexp) {
      int	i;

      if (matchingFeaturesAdded.contains(regexp))
	return self();

      matchingFeaturesAdded.add(regexp);
      parser = getParser();

      for (i = 0; i < parser.getColNames().size(); i++) {
	if (isClassColumn(parser, i))
	  continue;
	if (parser.getColNames().get(i).matches(regexp))
	  addColumn(parser, i);
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
