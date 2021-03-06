package com.jin.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.zip.GZIPInputStream;

/**
 * Clean attempt at reading a json string using the state pattern
 * 
 * @author Pablo Westphalen (gotjin@gmail.com)
 */
public class JsonReader {
	private int currentChar = 0;
	private boolean done;
	private final String json;
	private final JsonObject obj = new JsonObject();
	private final boolean debug = false;
	
	private JsonReader(String json) {
		this.json = json;
	}
	
	public static String fetch(String url) {
		try {
			URLConnection con = new URL(url).openConnection();
			con.setConnectTimeout(100 * 1000);
			con.setReadTimeout(100 * 1000);
			StringBuilder out = new StringBuilder();
			String line;
			BufferedReader in = null;
			if (con.getContentEncoding() != null
					&& con.getContentEncoding().equals("gzip")) {
				InputStreamReader is = new InputStreamReader(
						new GZIPInputStream(con.getInputStream()));
				in = new BufferedReader(is);
			} else {
				try (BufferedReader reader = new BufferedReader(
						new InputStreamReader(con.getInputStream()))) {
					in = reader;
				}
			}
			while ((line = in.readLine()) != null) {
				out.append(line);
			}
			in.close();
			return out.toString();
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		}
	}
	
	public static JsonObject getJson(String url) {
		return JsonReader.toJava(fetch(url));
	}
	
	/**
	 * Gets back to java
	 * 
	 * @param json
	 * @return
	 */
	public static JsonObject toJava(String json) throws RuntimeException {
		return new JsonReader(json).parseJson();
	}
	
	/**
	 * @param json
	 */
	private JsonObject parseJson() {
		Character c;
		while (!done) {
			c = readSkipWhitespace();
			if (c != null && c == '{') {
				if (nextNonWhitespaceChar() == '}') {
					return null;
				}
				do {
					String field = "";
					Object value = null;
					if (readSkipWhitespace() == '"') { // temos um field
						field = readString();
						if (debug) {
							System.out.println("Read field " + field);
						}
					} else {
						System.out.println("problema em ler o fieldName");
					}
					if (readSkipWhitespace() == ':') {
						value = getValue();
						if (debug) {
							System.out.println("Read value " + value);
						}
					} else {
						System.out.println("problema em ler o fieldValue");
					}
					obj.put(field, value);
				} while ((c = readSkipWhitespace()) != null && c == ',');
			}
		}
		return obj;
	}
	
	/**
	 * Gets the value associated with the field, using the first non whitespace
	 * character to infer its type.
	 * <p>
	 * The value can be:
	 * <ul>
	 * <li>A String, '"'</li>
	 * <li>An array, '['</li>
	 * <li>A number, if it's a digit</li>
	 * <li>Another Object, '{'</li>
	 * <li>A null value, 'n'</li>
	 * </ul>
	 * 
	 * @return The property value.
	 */
	private Object getValue() {
		if (nextNonWhitespaceChar() == '"') {
			readSkipWhitespace();
			return readString();
		}
		if (nextNonWhitespaceChar() == '[') {
			readSkipWhitespace();
			return readArray();
		}
		if (Character.isDigit(nextNonWhitespaceChar()) || nextNonWhitespaceChar() == '.') {
			return readNumber();
		}
		if (nextNonWhitespaceChar() == '{') {
			readSkipWhitespace();
			return readObject();
		}
		if (nextNonWhitespaceChar() == 'n') {
			return readNull();
		}
		return null;
	}
	
	/**
	 * @return
	 */
	private Object readNull() {
		readSkipWhitespace();
		int pos = currentChar;
		if (json.substring(pos - 1, pos + 3).equals("null")) {
			currentChar += 3;
			return "null";
		}
		return "porra";
	}
	
	/**
	 * @return
	 */
	private Character nextNonWhitespaceChar() {
		int charsRead = 0;
		if (currentChar + 1 <= json.length()) {
			Character c;
			while (currentChar + 1 <= json.length()) {
				charsRead++;
				if (!Character.isWhitespace(c = json.charAt(currentChar++))) {
					currentChar -= charsRead;
					return c;
				}
			}
		}
		done = true;
		return null;
	}
	
	/**
	 * @return
	 */
	private JsonObject readObject() {
		String objectString = "{";
		boolean bracesBalanced = false;
		int openedBraces = 1, closedBraces = 0;
		Character c = null;
		while (!bracesBalanced) {
			c = read();
			if (c == '{') {
				openedBraces++;
			}
			if (c == '}') {
				closedBraces++;
				if (openedBraces == closedBraces) {
					bracesBalanced = true;
				}
			}
			objectString += c;
		}
		/*
		 * if (objectString.equals("{}")) { return null; } else { return
		 * JsonReader.toJava(objectString); }
		 */
		return JsonReader.toJava(objectString);
	}
	
	/**
	 * @return
	 */
	private String readNumber() {
		/*TODO: see if it's possible (and relevant) to change the return type to Number and parse
		 the appropriate type.*/
		String value = "";
		Character c;
		while (nextNonWhitespaceChar() != ',' && nextNonWhitespaceChar() != '}') {
			c = readSkipWhitespace();
			value += c;
		}
		return value;
	}
	
	/**
	 * @return
	 */
	private Object readArray() {
		Character c = nextNonWhitespaceChar();
		if (c == ']') {
			return null;
		}
		if (c == '"') { // array of strings
			ArrayList<String> elements = new ArrayList<String>();
			do {
				readSkipWhitespace();
				String element = readString();
				elements.add(element);
			} while ((c = readSkipWhitespace()) != null && c == ',');
			return elements;
		}
		if (c == '{') { // array of objects
			ArrayList<JsonObject> objs = new ArrayList<JsonObject>();
			Character d = null;
			boolean withinString = false;
			do {
				String objectString = "";
				boolean bracesBalanced = false;
				int openedBraces = 0, closedBraces = 0;
				while (!bracesBalanced) {
					d = read();
					if (d != null) {
						if (d == '"' && withinString) {
							withinString = false;
						} else if (d == '"' && !withinString) {
							withinString = true;
						}
						if (d == '{' && !withinString) {
							openedBraces++;
						}
						if (d == '}' && !withinString) {
							closedBraces++;
							if (openedBraces == closedBraces) {
								bracesBalanced = true;
							}
						}
						objectString += d;
					} else {
						return objs;
					}
				}
				objs.add(JsonReader.toJava(objectString));
			} while (readSkipWhitespace() == ',');
			return objs;
		}
		if (Character.isDigit(c)) { // array of numbers
			ArrayList<Number> elements = new ArrayList<Number>();
			do {
				String element = "";
				do {
					element += readSkipWhitespace();
					c = nextNonWhitespaceChar();
				} while (c == '.' || Character.isDigit(c));
				if (element.contains(".")) {
					elements.add(Double.parseDouble(element));
				} else {
					elements.add(Integer.parseInt(element));
				}
			} while ((c = readSkipWhitespace()) != null && c == ',');
			return elements;
		}
		return "INVALID ARRAY";
	}
	
	private String readString() {
		String value = "";
		Character c;
		c = read();
		boolean string_end = false;
		while (!string_end) {
			if ((c != '"')) {
				if (c == '\\' && nextNonWhitespaceChar() == '"') {
					// escaped quote, don't add it now
				} else {
					value += c; // normal char, add it
				}
				c = read();
			} else {
				// dealing with a "
				char previous = json.charAt(currentChar - 2);
				if (previous == '\\') { // if it is an escaped ", add it
					value += String.valueOf('"'); // will add the previously
					// skipped \ and the actual
					// "
					c = read();
				} else {
					string_end = true; // is a regular " meaning the end of this
					// string
				}
			}
		}
		return value;
	}
	
	/**
	 * @return
	 */
	private Character read() {
		if (currentChar + 1 <= json.length()) {
			return json.charAt(currentChar++);
		} else {
			return null;
		}
	}
	
	/**
	 * @param json
	 * @return
	 */
	private Character readSkipWhitespace() {
		if (currentChar + 1 <= json.length()) {
			Character c;
			while (currentChar + 1 <= json.length()) {
				if (!Character.isWhitespace(c = json.charAt(currentChar++))) {
					return c;
				}
			}
		}
		done = true;
		return null;
	}
}
