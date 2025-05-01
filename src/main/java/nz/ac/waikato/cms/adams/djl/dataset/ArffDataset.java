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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

  /** Used to build a {@link ArffDataset}. */
  public static class ArffBuilder<T extends ArffDataset.ArffBuilder<T>>
    extends TabularDataset.BaseBuilder<T> {

    protected URL arffUrl;

    protected int classIndex;

    protected Boolean classIsLast;

    protected Set<String> ignoredColumns;

    protected ArffParser parser;

    /**
     * Initializes the builder.
     */
    protected ArffBuilder() {
      super();
      classIndex     = -1;
      ignoredColumns = new HashSet<>();
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
      if (index < -1)
	index = -1;
      classIndex = index;
      return self();
    }

    /**
     * Sets the flag that the class attribute is the last column.
     *
     * @return this builder
     */
    public T classIsLast() {
      classIsLast = true;
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
     * Adds all features/labels according to their types.
     * Skips ignored column names.
     * Column specified via class index/classIsLast gets added appropriately.
     * Loads the ARFF file and parses the header.
     *
     * @return this builder
     */
    public T addAllColumns() {
      ArffParser	parser;
      int 		actClassIndex;
      int		i;

      parser = getParser();

      // actual class index
      actClassIndex = classIndex;
      if ((classIsLast != null) && classIsLast)
	actClassIndex = parser.getColNames().size() - 1;
      if (actClassIndex >= parser.getColNames().size())
	throw new IllegalArgumentException("0-based class index out of range (#atts=" + parser.getColNames() + "): " + actClassIndex);

      // add features
      for (i = 0; i < parser.getColNames().size(); i++) {
	if (ignoredColumns.contains(parser.getColNames().get(i)))
	  continue;
	if (i == actClassIndex) {
	  switch (parser.getColTypes().get(i)) {
	    case NUMERIC:
	    case DATE:
	      addNumericLabel(parser.getColNames().get(i));
	      break;
	    case NOMINAL:
	    case STRING:
	      addCategoricalLabel(parser.getColNames().get(i));
	      break;
	    default:
	      throw new IllegalStateException("Unhandled class attribute type: " + parser.getColTypes().get(i));
	  }
	}
	else {
	  switch (parser.getColTypes().get(i)) {
	    case NUMERIC:
	    case DATE:
	      addNumericFeature(parser.getColNames().get(i));
	      break;
	    case NOMINAL:
	    case STRING:
	      addCategoricalFeature(parser.getColNames().get(i));
	      break;
	    default:
	      throw new IllegalStateException("Unhandled class attribute type: " + parser.getColTypes().get(i));
	  }
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
  }
}
