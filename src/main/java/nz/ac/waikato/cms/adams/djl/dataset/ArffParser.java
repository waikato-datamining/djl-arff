/*
 * ArffParser.java
 * Copyright (C) 2025 University of Waikato, Hamilton, New Zealand
 */

package nz.ac.waikato.cms.adams.djl.dataset;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Parses ARFF files.
 *
 * @author fracpete (fracpete at waikato dot ac dot nz)
 */
public class ArffParser {

  protected boolean onlyHeader;
  protected String relationName;
  protected List<String> colNames;
  protected List<ArffAttributeType> colTypes;
  protected List<List<String>> data;
  protected List<Map<String,String>> header;
  protected Map<String,Integer> attLookUp;

  /**
   * Initializes the parser.
   */
  public ArffParser() {
    relationName = "";
    colNames     = new ArrayList<>();
    colTypes     = new ArrayList<>();
    data         = new ArrayList<>();
    header       = new ArrayList<>();
    attLookUp    = new HashMap<>();
  }

  /**
   * Parses the dataset.
   *
   * @param r			the reader to read from
   * @throws IOException        if reading fails
   */
  protected void doParse(Reader r) throws IOException {
    BufferedReader reader;
    String			line;
    String			lower;
    boolean 			isHeader;
    int 			lineIndex;
    String[]			cells;
    int				i;
    Map<String,String> attInfo;
    List<String> row;
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
	  if (lower.startsWith(ArffKeywords.RELATION)) {
	    relationName = ArffUtils.unquote(line.substring(ArffKeywords.RELATION.length()).trim());
	  }
	  else if (lower.startsWith(ArffKeywords.ATTRIBUTE)) {
	    attInfo = ArffUtils.parseAttribute(line);
	    colNames.add(attInfo.get("name"));
	    colTypes.add(ArffAttributeType.valueOf(attInfo.get("type")));
	    attLookUp.put(attInfo.get("name"), attLookUp.size());
	    if (colTypes.get(colTypes.size() - 1) == ArffAttributeType.DATE)
	      formats.put(colTypes.size() - 1, new SimpleDateFormat(attInfo.get("format")));
	    header.add(attInfo);
	  }
	  else if (lower.startsWith(ArffKeywords.DATA)) {
	    isHeader = false;
	    if (onlyHeader)
	      return;
	  }
	}
	else {
	  row = new ArrayList<>();
	  data.add(row);
	  cells = ArffUtils.split(line, ',', false, '\'', true);
	  for (i = 0; i < cells.length && i < header.size(); i++) {
	    cells[i] = cells[i].trim();
	    if (cells[i].equals("?")) {
	      row.add(null);
	      continue;
	    }
	    cells[i] = ArffUtils.unquote(cells[i]);
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

  /**
   * Parses only the header.
   *
   * @param r			the reader to read from
   * @throws IOException        if reading fails
   */
  public void parseHeader(Reader r) throws IOException {
    onlyHeader = true;
    doParse(r);
  }

  /**
   * Parses the complete dataset.
   *
   * @param r			the reader to read from
   * @throws IOException        if reading fails
   */
  public void parse(Reader r) throws IOException {
    onlyHeader = false;
    doParse(r);
  }

  /**
   * Returns the relation name.
   *
   * @return the name
   */
  public String getRelationName() {
    return relationName;
  }

  /**
   * The complete header information.
   *
   * @return the info
   */
  public List<Map<String, String>> getHeader() {
    return header;
  }

  /**
   * Returns all the column names as they appear.
   *
   * @return the names
   * @see #getColTypes()
   */
  public List<String> getColNames() {
    return colNames;
  }

  /**
   * Returns all the column types as they appear.
   *
   * @return the types
   * @see #getColNames()
   */
  public List<ArffAttributeType> getColTypes() {
    return colTypes;
  }

  /**
   * Returns the actual data of the dataset.
   *
   * @return the data
   */
  public List<List<String>> getData() {
    return data;
  }

  /**
   * Returns the attribute lookup (name/index).
   *
   * @return the lookup
   */
  public Map<String, Integer> getAttLookUp() {
    return attLookUp;
  }
}
