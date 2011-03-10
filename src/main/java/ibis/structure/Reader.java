package ibis.structure;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.IOException;
import java.net.URL;
import java.util.zip.GZIPInputStream;
import java.util.NoSuchElementException;
import java.util.Scanner;
import org.apache.log4j.Logger;

import static ibis.structure.Misc.*;


/**
 * Reads SAT instancs in DIMACS CNF format.
 */
public final class Reader {
  private static final Logger logger = Logger.getLogger(Reader.class);

  public static Skeleton parseText(String text)
      throws IOException, ParseException {
    return parseStream(new ByteArrayInputStream(text.getBytes("UTF-8")));
  }

  /**
   * Parses an url.
   *
   * If url starts with http:// the instance is downloaded.
   * If url ends with .gz it is assumed to be a gziped stream.
   *
   * @param url location of instance
   * @return read skeleton
   * @throws ParseException if url contains an invalid instance
   */
  public static Skeleton parseURL(String url)
      throws IOException, ParseException {
    InputStream source = null;
    try {
      if (url.startsWith("http://")) {
        source = (new URL(url)).openStream();
      } else {
        source = new FileInputStream(url);
      }
      if (url.endsWith(".gz")) {
        source = new GZIPInputStream(source);
      }
      return parseStream(source);
    } finally {
      if (source != null) {
        source.close();
      }
    }
  }

  /**
   * Parses a stream for a CNF instance.
   *
   * @param source input stream
   * @return read skeleton
   * @throws ParseException if stream contains an invalid instance
   */
  private static Skeleton parseStream(final InputStream source)
      throws IOException, ParseException {
    Scanner scanner = new Scanner(source);

    // Skip comments
    try {
      String token = scanner.next();
      while (token.equals("c")) {
        scanner.nextLine();
        token = scanner.next();
      }
      if (!token.equals("p")) {
        throw new ParseException(
            "Excepted 'p', but '" + token + "' was found");
      }
    } catch (NoSuchElementException e) {
      throw new ParseException("Header not found");
    }

    // Reads header
    int numVariables, numClauses;
    try {
      String cnf = scanner.next();
      if (!cnf.equals("cnf")) {
        throw new ParseException(
            "Expected 'cnf', but '" + cnf + "' was found");
      }
      numVariables = scanner.nextInt();
      numClauses = scanner.nextInt();
    } catch (NoSuchElementException e) {
      throw new ParseException("Incomplete header");
    }
    logger.debug("p cnf " + numVariables + " " + numClauses);

    // Reads clauses
    Skeleton skeleton = new Skeleton(numVariables);
    int pos = skeleton.formula.size();
    TouchSet seen = new TouchSet(numVariables);
    int length = 0;
    skeleton.formula.add(0);

    try {
      while (numClauses > 0) {
        int literal = scanner.nextInt();
        if (literal == 0) {
          numClauses--;

          skeleton.formula.set(pos, encode(length, OR));
          pos = skeleton.formula.size();
          seen.reset();
          length = 0;
          skeleton.formula.add(0);
        } else {
          if (!seen.containsOrAdd(literal)) {
            skeleton.formula.add(literal);
            length++;
          }
        }
      }
    } catch (NoSuchElementException e) {
      throw new ParseException(
          "Incomplete problem: " + numClauses + " clauses are missing");
    }

    skeleton.formula.removeAt(skeleton.formula.size() - 1);
    return skeleton;
  }
}
