/*
 * ArffDataset.java
 * Copyright (C) 2025 University of Waikato, Hamilton, New Zealand
 */

package nz.ac.waikato.cms.adams.djl.dataset;

import ai.djl.basicdataset.tabular.TabularDataset;
import ai.djl.util.Progress;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
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

  public static final String KEYWORD_RELATION = "@relation";

  public static final String KEYWORD_ATTRIBUTE = "@attribute";

  public static final String KEYWORD_DATA = "@data";

  /**
   * Attribute types.
   */
  public enum AttributeType {
    NUMERIC,
    NOMINAL,
    STRING,
    DATE,
  }

  protected URL arffUrl;
  protected String relationName;
  protected List<String> colNames;
  protected List<AttributeType> colTypes;
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
   * Extracts the attribute name, type and date format from the line.
   *
   * @param line	the line to parse
   * @return		the extracted data
   * @throws IOException	if parsing fails, e.g., invalid date format
   */
  protected HashMap<String,String> parseAttribute(String line) throws IOException {
    HashMap<String,String>	result;
    boolean			quoted;
    String 			current;
    String			lower;
    String			format;

    result  = new HashMap<>();
    current = line.replace("\t", " ");
    current = current.substring(KEYWORD_ATTRIBUTE.length() + 1).trim();

    // name
    if (current.startsWith("'")) {
      quoted = true;
      result.put("name", current.substring(1, current.indexOf('\'', 1)).trim());
    }
    else if (current.startsWith("\"")) {
      quoted = true;
      result.put("name", current.substring(1, current.indexOf('"', 1)).trim());
    }
    else {
      quoted = false;
      result.put("name", current.substring(0, current.indexOf(' ', 1)).trim());
    }
    current = current.substring(result.get("name").length() + (quoted ? 2 : 0)).trim();

    // type
    lower = current.toLowerCase();
    if (lower.startsWith("numeric") || lower.startsWith("real") || lower.startsWith("integer"))
      result.put("type", AttributeType.NUMERIC.toString());
    else if (lower.startsWith("string"))
      result.put("type", AttributeType.STRING.toString());
    else if (lower.startsWith("date"))
      result.put("type", AttributeType.DATE.toString());
    else if (lower.startsWith("{"))
      result.put("type", AttributeType.NOMINAL.toString());
    else
      throw new IllegalStateException("Unsupported attribute: " + current);

    // date format
    if (result.get("type").equals(AttributeType.DATE.toString())) {
      current = current.substring(5).trim();   // remove "date "
      if (current.startsWith("'"))
	format = Utils.unquote(current);
      else if (current.startsWith("\""))
	format = Utils.unDoubleQuote(current);
      else
	format = current;
      try {
	new SimpleDateFormat(format);
	result.put("format", format);
      }
      catch (Exception e) {
	throw new IllegalStateException("Invalid date format: " + format);
      }
    }

    return result;
  }

  /**
   * Performs the actual reading.
   *
   * @param r			the reader to read from
   * @throws IOException	if reading fails
   */
  protected void parseDataset(Reader r) throws IOException {
    BufferedReader 		reader;
    String			line;
    String			lower;
    boolean 			isHeader;
    int 			lineIndex;
    String[]			cells;
    int				i;
    Map<String,String>		attInfo;
    List<String>		row;
    Map<Integer, DateFormat>	formats;

    data      = new ArrayList<>();
    header    = new ArrayList<>();
    colNames  = new ArrayList<>();
    colTypes  = new ArrayList<>();
    attLookUp = new HashMap<>();
    formats   = new HashMap<>();

    if (r instanceof BufferedReader)
      reader = (BufferedReader) r;
    else
      reader = new BufferedReader(r);

    lineIndex = 0;
    isHeader  = true;
    try {
      while ((line = reader.readLine()) != null) {
	lineIndex++;

	line = line.trim();
	if (line.isEmpty())
	  continue;
	if (line.startsWith("%"))
	  continue;

	if (isHeader) {
	  lower = line.toLowerCase();
	  if (lower.startsWith(KEYWORD_RELATION)) {
	    relationName = Utils.unquote(line.substring(KEYWORD_RELATION.length()).trim());
	  }
	  else if (lower.startsWith(KEYWORD_ATTRIBUTE)) {
	    attInfo = parseAttribute(line);
	    colNames.add(attInfo.get("name"));
	    colTypes.add(AttributeType.valueOf(attInfo.get("type")));
	    attLookUp.put(attInfo.get("name"), attLookUp.size());
	    if (colTypes.get(colTypes.size() - 1) == AttributeType.DATE)
	      formats.put(colTypes.size() - 1, new SimpleDateFormat(attInfo.get("format")));
	    header.add(attInfo);
	  }
	  else if (lower.startsWith(KEYWORD_DATA)) {
	    isHeader = false;
	  }
	}
	else {
	  row = new ArrayList<>();
	  data.add(row);
	  cells = Utils.split(line, ',', false, '\'', true);
	  for (i = 0; i < cells.length && i < header.size(); i++) {
	    cells[i] = cells[i].trim();
	    if (cells[i].equals("?")) {
	      row.add(null);
	      continue;
	    }
	    cells[i] = Utils.unquote(cells[i]);
	    switch (colTypes.get(i)) {
	      case NUMERIC:
		Double.parseDouble(cells[i]);
		row.add(cells[i]);
		break;
	      case NOMINAL:
	      case STRING:
		row.add(cells[i]);
		break;
	      case DATE:
		row.add("" + formats.get(i).parse(cells[i]).getTime());
		break;
	      default:
		throw new IOException("Unhandled attribute type: " + colTypes.get(i));
	    }
	  }
	}
      }
    }
    catch (IOException ioe) {
      throw ioe;
    }
    catch (Exception e) {
      throw new IOException("Failed to read ARFF data from reader (line #" + (lineIndex +1) + ")!", e);
    }
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
    if (arffUrl.getFile().endsWith(".gz")) {
      return new GZIPInputStream(arffUrl.openStream());
    }
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
    if (header.isEmpty()) {
      return Collections.emptyList();
    }
    return colNames;
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
