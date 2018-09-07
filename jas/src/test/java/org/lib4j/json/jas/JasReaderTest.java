/* Copyright (c) 2018 lib4j
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * You should have received a copy of The MIT License (MIT) along with this
 * program. If not, see <http://opensource.org/licenses/MIT/>.
 */

package org.lib4j.json.jas;

import static org.junit.Assert.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;

import org.junit.Test;
import org.lib4j.io.DecodingReader;
import org.lib4j.io.Readers;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;

public class JasReaderTest {
  private static boolean testIterator = true;
  private static boolean testReadBack = true;
  private static boolean testReadChar = true;
  private static boolean testReadBuff = true;

  private static Double random = null;

  private static double random() {
    return random != null ? random : Math.random();
  }

  private static String compact(final String json) throws IOException {
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    final JsonFactory factory = new JsonFactory();
    final JsonParser parser = factory.createParser(json);
    try (final JsonGenerator generator = factory.createGenerator(out)) {
      while (parser.nextToken() != null)
        generator.copyCurrentEvent(parser);
    }

    return new String(out.toByteArray());
  }

  private static void readRemainder(final JasReader reader, final StringBuilder builder) throws IOException {
    final String remainder = reader.readToken();
    if (remainder != null)
      builder.append(remainder);
  }

  private static void readBuff(final int length, final JasReader reader, final StringBuilder builder) throws IOException {
    final char[] cbuf = new char[length];
    final int start = (int)(random() * cbuf.length * .5);
    final int end = start + (int)(random() * cbuf.length * .5);
    final int read = reader.read(cbuf, start, end - start);
    if (read > 0)
      builder.append(cbuf, start, read);
  }

  private static void testString(final String json, final boolean testSetIndex) throws IOException {
    testString(json, testSetIndex, false);
    testString(json, testSetIndex, true);
  }

  private static void testString(final String json, final boolean testSetIndex, final boolean ignoreWhitespace) throws IOException {
    try (final JasReader reader = new JasReader(new StringReader(json), ignoreWhitespace)) {
      final StringBuilder builder = new StringBuilder();
      final int gap = (int)(json.length() / 100d);
      int cut = 0;
      int index = 0;
      // Test that reader.readToken() and iterator.next() are properly synced
      if (testReadChar) {
        for (int i = 0, ch; i < random() * 10 && (ch = reader.read()) != -1; ++i)
          builder.append((char)ch);

        if (!testReadBuff)
          readRemainder(reader, builder);
      }

      if (testReadBuff) {
        for (int i = 0; i < random() * 10; ++i)
          readBuff((int)(random() * 20), reader, builder);

        readRemainder(reader, builder);
      }

      index = reader.getIndex() + 1;
      for (String token; (token = !testIterator || random() < .5 ? reader.readToken() : reader.iterator().hasNext() ? reader.iterator().next() : null) != null;) {
        if (token.length() == 0)
          fail("Index: " + reader.getIndex() + ", Position: " + reader.getPosition() + ": token.length() == 0");

        if (ignoreWhitespace || !token.matches("\\s+"))
          assertEquals("ignoreWhitespace: " + ignoreWhitespace + ", Index: " + reader.getIndex() + ", Position: " + reader.getPosition(), token.trim(), token);

        if (testIterator)
          for (int i = 0; i < (int)(Math.random() * 10); ++i)
            reader.iterator().hasNext();

        if (index == reader.getIndex()) {
          builder.append(token);
          if (testReadChar && random() < .5) {
            for (int i = 0, ch; i < random() * 20 && (ch = reader.read()) != -1; ++i)
              builder.append((char)ch);

            if (!testReadBuff)
              readRemainder(reader, builder);
          }

          if (testReadBuff) {
            for (int i = 0; i < random() * 10; ++i)
              readBuff((int)(random() * 20), reader, builder);

            readRemainder(reader, builder);
          }

          index = reader.getIndex() + 1;
          if (testReadBack && testSetIndex && reader.getPosition() > gap * cut)
            reader.setIndex((int)(reader.getIndex() - random() * reader.getIndex() / ++cut));
        }
        else {
          // If the content is being re-read, ensure that the token is equal to what was read previously
          final String expected = json.substring(reader.getPosition() - token.length(), reader.getPosition());
          assertEquals("ignoreWhitespace: " + ignoreWhitespace + ", Index: " + reader.getIndex() + ", Position: " + reader.getPosition(), expected, token);
        }
      }

      final String expected = ignoreWhitespace ? compact(json.trim()).toString() : json.trim();
      assertEquals("ignoreWhitespace: " + ignoreWhitespace, expected, builder.toString());
    }
  }

  protected static String readFile(final String jsonFileName) throws IOException {
    return Readers.readFully(new DecodingReader(Thread.currentThread().getContextClassLoader().getResourceAsStream(jsonFileName), StandardCharsets.UTF_8));
  }

  protected void passFile(final String jsonFileName) throws IOException {
    testString(readFile(jsonFileName), true);
  }

  protected static void passString(final String json) throws IOException {
    testString(json, false, false);
  }

  private static void failString(final String json, final Class<? extends Exception> cls, final String message) throws IOException {
    try {
      testString(json, false, false);
      fail("Expected SAJParseException");
    }
    catch (final IOException e) {
      throw e;
    }
    catch (final Exception e) {
      assertEquals(cls, e.getClass());
      assertEquals(message, e.getMessage());
    }
  }

  @Test
  public void testScopeEnd() throws IOException {
    failString("  {", JasParseException.class, "Missing closing scope character: '}' [errorOffset: 3]");
    failString("[[]", JasParseException.class, "Missing closing scope character: ']' [errorOffset: 3]");
  }

  @Test
  public void testScopeMiddle() throws IOException {
    failString("{[", JasParseException.class, "Missing closing scope character: ']' [errorOffset: 2]");
    failString("[}", JasParseException.class, "Expected character ']', but encountered '}' [errorOffset: 1]");
    failString("{]", JasParseException.class, "Expected character '}', but encountered ']' [errorOffset: 1]");
    failString("{\"foo\":[[[]]}", JasParseException.class, "Expected character ']', but encountered '}' [errorOffset: 12]");
    failString("[[[{]]", JasParseException.class, "Expected character '}', but encountered ']' [errorOffset: 4]");
  }

  @Test
  public void testExpectedColon() throws IOException {
    failString("  {foo, bar}  ", JasParseException.class, "Expected character '\"', but encountered 'f' [errorOffset: 3]");
    failString("{\"foo\", bar}", JasParseException.class, "Expected character ':', but encountered ',' [errorOffset: 6]");
    failString("{\"foo\"{ bar}", JasParseException.class, "Expected character ':', but encountered '{' [errorOffset: 6]");
    failString("{\"foo\"[ bar}", JasParseException.class, "Expected character ':', but encountered '[' [errorOffset: 6]");
    failString("{\"foo\"} bar}", JasParseException.class, "Expected character ':', but encountered '}' [errorOffset: 6]");
    failString("{\"foo\"] bar}", JasParseException.class, "Expected character ':', but encountered ']' [errorOffset: 6]");
    failString("{\"foo\" bar }", JasParseException.class, "Expected character ':', but encountered 'b' [errorOffset: 7]");
  }

  @Test
  public void testNoContentExpected() throws IOException {
    failString("  { foo : [   ]  }  ", JasParseException.class, "Expected character '\"', but encountered 'f' [errorOffset: 4]");
    failString("{ \"foo\"  \"bar\" }", JasParseException.class, "Expected character ':', but encountered '\"' [errorOffset: 9]");
    failString("{\"foo\": \"bar\"} f", JasParseException.class, "No content is expected at this point: f [errorOffset: 15]");
  }

  @Test
  public void testTerm() throws IOException {
    passString("{\"foo\": null}");

    failString("{\"foo\": nulll}", JasParseException.class, "Illegal character: 'l' [errorOffset: 12]");
    failString(".", JasParseException.class, "Expected character '{' or '[', but encountered '.' [errorOffset: 0]");
    failString("x", JasParseException.class, "Expected character '{' or '[', but encountered 'x' [errorOffset: 0]");
    failString("[x]", JasParseException.class, "Illegal character: 'x' [errorOffset: 1]");
    failString("[null: x]", JasParseException.class, "Illegal character: ':' [errorOffset: 5]");
    failString("[null, x]", JasParseException.class, "Illegal character: 'x' [errorOffset: 7]");
    failString("{\"foo\": xyz}", JasParseException.class, "Illegal character: 'x' [errorOffset: 8]");
  }

  @Test
  public void testString() throws IOException {
    passString("{\"foo\": \"\"}");
    passString("{\"foo\": \"\"}");
    passString("{\"foo\": \"b\\\"ar\"}");
    passString("{\"foo\": \"\"}");
    passString("{\"foo\": \"ba\\\"r\"}");

    failString("{\"foo\": \"bar}", JasParseException.class, "Unterminated string [errorOffset: 8]");
    failString("{\"foo\": 'bar'}", JasParseException.class, "Illegal character: ''' [errorOffset: 8]");
  }

  @Test
  public void testBoolean() throws IOException {
    passString("{\"foo\":  true}");
    passString("{\"foo\": false}");
    failString("{\"foo\": truee}", JasParseException.class, "Illegal character: 'e' [errorOffset: 12]");
    failString("{\"foo\": falss}", JasParseException.class, "Illegal character: 's' [errorOffset: 12]");
  }

  @Test
  public void testNumber() throws IOException {
    passString("{\"foo\": -0}");
    passString("{\"foo\": 0}");
    passString("{\"foo\": 2931}");
    passString("{\"foo\": 2931.32}");
    passString("{\"foo\": 10e0}");
    passString("{\"foo\": 10E1}");
    passString("{\"foo\": 10e+12}");
    passString("{\"foo\": 10E-12}");

    failString("{\"foo\": -.5}", JasParseException.class, "Integer component required before fraction part [errorOffset: 9]");
    failString("{\"foo\": 001}", JasParseException.class, "Leading zeros are not allowed [errorOffset: 8]");
    failString("{\"foo\": 0.}", JasParseException.class, "Decimal point must be followed by one or more digits [errorOffset: 10]");
    failString("{\"foo\": 0.0.}", JasParseException.class, "Illegal character: '.' [errorOffset: 11]");
    failString("{\"foo\": --0}", JasParseException.class, "Expected digit, but encountered '-' [errorOffset: 9]");
    failString("{\"foo\": 10E-}", JasParseException.class, "Expected digit, but encountered '}' [errorOffset: 12]");
    failString("{\"foo\": 10E+}", JasParseException.class, "Expected digit, but encountered '}' [errorOffset: 12]");
    failString("{\"foo\": 10E--}", JasParseException.class, "Illegal character: '-' [errorOffset: 12]");
    failString("{\"foo\": 10E++}", JasParseException.class, "Illegal character: '+' [errorOffset: 12]");
    failString("{\"foo\": 10E+1.}", JasParseException.class, "Illegal character: '.' [errorOffset: 13]");
    failString("{\"foo\": 10E01}", JasParseException.class, "Leading zeros are not allowed [errorOffset: 11]");
    failString("{\"foo\": 10E+01}", JasParseException.class, "Leading zeros are not allowed [errorOffset: 12]");
  }

  @Test
  public void testBlank() throws IOException {
    passString("");
  }

  @Test
  public void testEmpty() throws IOException {
    passString("{}");
    passString("[]");
  }

  @Test
  public void testPaypal() throws IOException {
    passFile("paypal.json");
  }

  @Test
  public void testGiphy() throws IOException {
    passFile("giphy.json");
  }

  @Test
  public void testEmployees() throws IOException {
    passFile("employees.json");
  }

  @Test
  public void testWebapp() throws IOException {
    passFile("webapp.json");
  }

  @Test
  public void testTweets() throws IOException {
    passFile("tweets.json");
  }

  @Test
  public void testVatRates() throws IOException {
    passFile("vatrates.json");
  }

  @Test
  public void testGitHub() throws IOException {
    passFile("github.json");
  }

  @Test
  public void testAstronauts() throws IOException {
    passFile("astronauts.json");
  }

  @Test
  public void testEarthquakes() throws IOException {
    passFile("earthquakes.json");
  }

  @Test
  public void testNobel() throws IOException {
    passFile("nobel.json");
  }

  @Test
  public void testReddit() throws IOException {
    passFile("reddit.json");
  }

  @Test
  public void testShowtimes() throws IOException {
    passFile("showtimes.json");
  }

  @Test
  public void testMovies() throws IOException {
    passFile("movies.json");
  }

  @Test
  public void testDcat() throws IOException {
    passFile("dcat.json");
  }

  @Test
  public void testDemographics() throws IOException {
    passFile("demographics.json");
  }

  @Test
  public void testComplaints() throws IOException {
    passFile("complaints.json");
  }

  @Test
  public void testBusinesses() throws IOException {
    passFile("businesses.json");
  }

  @Test
  public void testNames() throws IOException {
    passFile("names.json");
  }

  @Test
  public void testPowerball() throws IOException {
    passFile("powerball.json");
  }

  @Test
  public void testNutrition() throws IOException {
    passFile("nutrition.json");
  }

  @Test
  public void testNhanes() throws IOException {
    passFile("nhanes.json");
  }

  @Test
  public void testMega() throws IOException {
    passFile("mega.json");
  }

  @Test
  public void testZipcodes() throws IOException {
    passFile("zipcodes.json");
  }

  @Test
  public void testDoe() throws IOException {
    passFile("doe.json");
  }

  @Test
  public void testJobs() throws IOException {
    passFile("jobs.json");
  }

  @Test
  public void testPets() throws IOException {
    passFile("pets.json");
  }
}