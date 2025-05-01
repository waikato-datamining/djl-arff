/*
 * Utils.java
 * Copyright (C) 2025 University of Waikato, Hamilton, New Zealand
 */

package nz.ac.waikato.cms.adams.djl.dataset;

import java.util.ArrayList;
import java.util.List;

/**
 * Helper methods for ARFF parsing.
 *
 * @author fracpete (fracpete at waikato dot ac dot nz)
 */
public class Utils {

  /**
   * unquotes are previously quoted string (but only if necessary), i.e., it
   * removes the double quotes around it. Inverse to doubleQuote(String).
   *
   * @param string	the string to process
   * @return		the unquoted string
   */
  public static String unDoubleQuote(String string) {
    return unquote(string, "\"");
  }

  /**
   * unquotes are previously quoted string (but only if necessary), i.e., it
   * removes the single quotes around it. Inverse to quote(String).
   *
   * @param string	the string to process
   * @return		the unquoted string
   */
  public static String unquote(String string) {
    return unquote(string, "'");
  }

  /**
   * unquotes are previously quoted string (but only if necessary), i.e., it
   * removes the quote characters around it. Inverse to quote(String,String).
   *
   * @param string	the string to process
   * @param quoteChar	the quote character to use
   * @return		the unquoted string
   */
  public static String unquote(String string, String quoteChar) {
    if ((string == null) || (string.length() < 2))
      return string;

    if (string.startsWith(quoteChar) && string.endsWith(quoteChar)) {
      string = string.substring(1, string.length() - 1);

      if (string.contains("\\n")  || string.contains("\\r") ||
	    string.contains("\\'")  || string.contains("\\\"") ||
	    string.contains("\\\\") || string.contains("\\t")) {
	string = unbackQuoteChars(string);
      }
    }

    return string;
  }

  /**
   * The inverse operation of backQuoteChars().
   * Converts back-quoted carriage returns and new lines in a string
   * to the corresponding character ('\r' and '\n').
   * Also "un"-back-quotes the following characters: ` " \ \t and %
   *
   * @param string 	the string
   * @return 		the converted string
   */
  public static String unbackQuoteChars(String string) {
    return unbackQuoteChars(
      string,
      new String[]{"\\\\", "\\'", "\\t", "\\n", "\\r", "\\\""},
      new char[]{'\\', '\'', '\t', '\n', '\r', '"'});
  }

  /**
   * The inverse operation of backQuoteChars().
   * Converts the specified strings into their character representations.
   *
   * @param string 	the string
   * @param find	the string to find
   * @param replace	the character equivalents of the strings
   * @return 		the converted string
   */
  public static String unbackQuoteChars(String string, String[] find, char[] replace) {
    int 		index;
    StringBuilder 	newStr;
    int[] 		pos;
    int			curPos;
    String 		str;
    int			i;

    if (string == null)
      return null;

    pos = new int[find.length];

    str = string;
    newStr = new StringBuilder();
    while (!str.isEmpty()) {
      // get positions and closest character to replace
      curPos = str.length();
      index  = -1;
      for (i = 0; i < pos.length; i++) {
	pos[i] = str.indexOf(find[i]);
	if ( (pos[i] > -1) && (pos[i] < curPos) ) {
	  index  = i;
	  curPos = pos[i];
	}
      }

      // replace character if found, otherwise finished
      if (index == -1) {
	newStr.append(str);
	str = "";
      }
      else {
	newStr.append(str, 0, pos[index]);
	newStr.append(replace[index]);
	str = str.substring(pos[index] + find[index].length());
      }
    }

    return newStr.toString();
  }

  /**
   * Attempts to split a string, using the specified delimiter character.
   * A delimiter gets ignored if inside double quotes.
   *
   * @param s		the string to split
   * @param delimiter	the delimiting character
   * @param unquote	whether to remove single/double quotes
   * @param quoteChar	the quote character to use
   * @param escaped	if true then quotes preceded by backslash ('escaped') get ignored
   * @return		the parts (single array element if no range)
   */
  public static String[] split(String s, char delimiter, boolean unquote, char quoteChar, boolean escaped) {
    List<String> result;
    int			i;
    StringBuilder	current;
    boolean 		quoted;
    char		c;
    boolean		backslash;

    result = new ArrayList<>();

    current   = new StringBuilder();
    quoted    = false;
    backslash = false;
    for (i = 0; i < s.length(); i++) {
      c = s.charAt(i);
      if (c == quoteChar) {
	if (!backslash)
	  quoted = !quoted;
	current.append(c);
      }
      else if (c == delimiter) {
	if (quoted) {
	  current.append(c);
	}
	else {
	  if (unquote) {
	    if (quoteChar == '"')
	      result.add(unDoubleQuote(current.toString()));
	    else if (quoteChar == '\'')
	      result.add(unquote(current.toString()));
	    else
	      result.add(current.toString());
	  }
	  else {
	    result.add(current.toString());
	  }
	  current.delete(0, current.length());
	}
      }
      else {
	current.append(c);
      }

      if (escaped)
	backslash = (c == '\\');
    }

    // add last string
    if (current.length() > 0) {
      if (unquote) {
	if (quoteChar == '"')
	  result.add(unDoubleQuote(current.toString()));
	else if (quoteChar == '\'')
	  result.add(unquote(current.toString()));
	else
	  result.add(current.toString());
      }
      else {
	result.add(current.toString());
      }
    }

    return result.toArray(new String[0]);
  }
}
