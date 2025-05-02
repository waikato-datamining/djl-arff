/*
 * ArffDataset.java
 * Copyright (C) 2025 University of Waikato, Hamilton, New Zealand
 */

package nz.ac.waikato.cms.adams.djl.dataset;

import ai.djl.basicdataset.tabular.TabularDataset;
import ai.djl.basicdataset.tabular.utils.Feature;
import ai.djl.util.Progress;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
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
 * Ignored columns, explicit or via regexps, should be set first.
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
  protected JsonObject structure;

  protected ArffDataset(ArffBuilder<?> builder) {
    super(builder);
    arffUrl = builder.arffUrl;
    structure = builder.toJson();
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

  /**
   * Returns the feature/labels as json.
   *
   * @return the generated json
   */
  public JsonObject toJson() {
    return structure;
  }

  /**
   * Writes the features/labels JSON representation to the specified path.
   *
   * @param filename		the file to write the representation to
   * @throws IOException	if writing fails
   */
  public void toJson(Path filename) throws IOException {
    try (FileWriter fw = new FileWriter(filename.toFile());
	 BufferedWriter bw = new BufferedWriter(fw)) {
      bw.write(toJson().toString());
    }
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

    protected JsonObject structure;

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
      structure              = new JsonObject();
      structure.add("options", new JsonObject());
      structure.get("options").getAsJsonObject().addProperty("dateColumnsAsNumeric", false);
      structure.get("options").getAsJsonObject().addProperty("stringColumnsAsNominal", false);
      structure.add("features", new JsonArray());
      structure.add("labels", new JsonArray());
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
	structure.addProperty("arffUrl", arffUrl.toString());
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
	structure.addProperty("arffUrl", arffUrl);
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
      structure.get("options").getAsJsonObject().addProperty("dateColumnsAsNumeric", true);
      return self();
    }

    /**
     * Sets whether to treat STRING columns as NOMINAL ones.
     *
     * @return this builder
     */
    public T stringColumnsAsNominal() {
      stringColumnsAsNominal = true;
      structure.get("options").getAsJsonObject().addProperty("stringColumnsAsNominal", true);
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
     * Sets the index/indices of the column(s) to use as class attribute.
     *
     * @param index the 0-based index/indices (based on column in ARFF file)
     * @return this builder
     */
    public T classIndex(int... index) {
      ArffParser	parser;

      for (int i: index) {
	if (i < -1)
	  i = -1;
	if (i == -1)
	  continue;

	parser = getParser();
	addClassColumn(parser.getColNames().get(i));
      }
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
     * @param colName 		the name of the column
     * @param colType 		the type of the column
     * @param isClassColumn 	whether the column is a class attribute
     */
    protected void addColumn(String colName, ArffAttributeType colType, boolean isClassColumn) {
      JsonObject	att;

      if (ignoredColumns.contains(colName))
	return;

      att = new JsonObject();
      att.addProperty("name", colName);
      att.addProperty("type", colType.toString());

      if (isClassColumn) {
	switch (colType) {
	  case NUMERIC:
	    addNumericLabel(colName);
	    structure.get("labels").getAsJsonArray().add(att);
	    break;
	  case DATE:
	    if (dateColumnsAsNumeric) {
	      addNumericLabel(colName);
	      structure.get("labels").getAsJsonArray().add(att);
	    }
	    break;
	  case NOMINAL:
	    addCategoricalLabel(colName);
	    structure.get("labels").getAsJsonArray().add(att);
	    break;
	  case STRING:
	    if (stringColumnsAsNominal) {
	      addCategoricalLabel(colName);
	      structure.get("labels").getAsJsonArray().add(att);
	    }
	    break;
	  default:
	    throw new IllegalStateException("Unhandled class attribute type: " + colType);
	}
      }
      else {
	switch (colType) {
	  case NUMERIC:
	    addNumericFeature(colName);
	    structure.get("features").getAsJsonArray().add(att);
	    break;
	  case DATE:
	    if (dateColumnsAsNumeric) {
	      addNumericFeature(colName);
	      structure.get("features").getAsJsonArray().add(att);
	    }
	    break;
	  case NOMINAL:
	    addCategoricalFeature(colName);
	    structure.get("features").getAsJsonArray().add(att);
	    break;
	  case STRING:
	    if (stringColumnsAsNominal) {
	      addCategoricalFeature(colName);
	      structure.get("features").getAsJsonArray().add(att);
	    }
	    break;
	  default:
	    throw new IllegalStateException("Unhandled class attribute type: " + colType);
	}
      }
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

      addColumn(colName, colType, isClassColumn(parser, index));
    }

    /**
     * Adds the class attribute(s).
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
     * Adds the column name(s) to ignore when adding all features.
     *
     * @param colNames the name(s) of the column(s) to ignore
     * @return this builder
     */
    public T addIgnoredColumn(String... colNames) {
      ignoredColumns.addAll(Arrays.asList(colNames));
      return self();
    }

    /**
     * Ignores all column names that match the regexp(s).
     *
     * @param regexp 	the regular expression(s) to use
     * @return 		this builder
     */
    public T ignoreMatchingColumns(String... regexp) {
      ArffParser	parser;

      parser = getParser();
      for (String r: regexp) {
	for (String colName : parser.getColNames()) {
	  if (colName.matches(r))
	    addIgnoredColumn(colName);
	}
      }

      return self();
    }

    /**
     * Adds all features according to their types.
     * Skips ignored column names and class attribute(s).
     * Only gets executed once.
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
     * Adds all feature columns which names match the regular expression(s).
     * Skips ignored column names and class attribute(s).
     * Only gets executed once per regular expression.
     *
     * @param regexp the regular expression(s) to apply
     * @return this builder
     */
    public T addMatchingFeatures(String... regexp) {
      int	i;

      for (String r: regexp) {
	if (matchingFeaturesAdded.contains(r))
	  continue;

	matchingFeaturesAdded.add(r);
	parser = getParser();

	for (i = 0; i < parser.getColNames().size(); i++) {
	  if (isClassColumn(parser, i))
	    continue;
	  if (parser.getColNames().get(i).matches(r))
	    addColumn(parser, i);
	}
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

    /**
     * Returns the structure of the dataset as JSON.
     *
     * @return		the structure
     */
    public JsonObject toJson() {
      return structure;
    }

    /**
     * Configures the builder based on the structure.
     *
     * @param path the path to the dataset structure JSON file to load and apply
     * @return this builder
     * @see #fromJson(JsonObject)
     */
    public T fromJson(Path path) throws IOException {
      JsonObject 	j;

      try (Reader r = new FileReader(path.toFile()); BufferedReader br = new BufferedReader(r)) {
	j = (JsonObject) JsonParser.parseReader(br);
	return fromJson(j);
      }
    }

    /**
     * Configures the builder based on the structure.
     *
     * @param json the JSON string to load the dataset structure from and apply
     * @return this builder
     * @see #fromJson(JsonObject)
     */
    public T fromJson(String json) throws IOException {
      JsonObject 	j;

      try (Reader r = new StringReader(json); BufferedReader br = new BufferedReader(r)) {
	j = (JsonObject) JsonParser.parseReader(br);
	return fromJson(j);
      }
    }

    /**
     * Configures the builder based on the structure.
     *
     * @param structure	the dataset structure to use
     * @return this builder
     * @see #toJson()
     */
    public T fromJson(JsonObject structure) {
      JsonObject	options;
      JsonArray		features;
      JsonArray		labels;
      JsonObject	feature;
      ArffAttributeType	type;
      int		i;

      // options
      if (structure.has("options")) {
	options = structure.getAsJsonObject("options");
	if (options.has("dateColumnsAsNumeric") && options.get("dateColumnsAsNumeric").getAsBoolean())
	  dateColumnsAsNumeric();
	if (options.has("stringColumnsAsNominal") && options.get("stringColumnsAsNominal").getAsBoolean())
	  stringColumnsAsNominal();
      }

      // features
      if (structure.has("features")) {
	features = structure.getAsJsonArray("features");
	for (i = 0; i < features.size(); i++) {
	  feature = features.get(i).getAsJsonObject();
	  type    = ArffAttributeType.valueOf(feature.get("type").getAsString());
	  addColumn(feature.get("name").getAsString(), type, false);
	}
      }

      // labels
      if (structure.has("labels")) {
	labels = structure.getAsJsonArray("labels");
	for (i = 0; i < labels.size(); i++) {
	  feature = labels.get(i).getAsJsonObject();
	  type    = ArffAttributeType.valueOf(feature.get("type").getAsString());
	  addColumn(feature.get("name").getAsString(), type, true);
	}
      }

      // arffUrl
      if (structure.has("arffUrl"))
	optArffUrl(structure.get("arffUrl").getAsString());

      return self();
    }
  }
}
