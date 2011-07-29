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
    logger.info("Reading from " + url);
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
    TouchSet seen = new TouchSet(numVariables);

    try {
      for (; numClauses > 0; numClauses--) {
        int pos = skeleton.formula.size();
        skeleton.formula.add(0);  // placeholder for header

        String first = scanner.next();
        boolean isXOR = first.equals("x");
        boolean sign = false;
        boolean tautology = false;

        int length = 0;
        int literal;

        if (isXOR) {
          literal = scanner.nextInt();
        } else {
          literal = Integer.parseInt(first);
        }

        seen.reset();
        while (literal != 0) {
          if (isXOR && literal != var(literal)) {
            sign = !sign;
            literal = var(literal);
          }

          if (seen.contains(neg(literal))) {
            assert !isXOR;
            tautology = true;
            logger.warn("Found a tautology");
          }

          if (seen.containsOrAdd(literal)) {
            if (isXOR) {
              throw new ParseException("Duplicate XOR literal " + literal);
            } else {
              logger.warn("Ignored duplicate literal in OR clause");
              literal = scanner.nextInt();
              continue;
            }
          }

          length++;
          skeleton.formula.add(literal);
          literal = scanner.nextInt();
        }

        if (tautology) {
          skeleton.formula.remove(pos, skeleton.formula.size() - pos);
        } else {
          skeleton.formula.set(pos, encode(
                length, isXOR ? sign ? NXOR : XOR : OR));
        }
      }
    } catch (NoSuchElementException e) {
      throw new ParseException(
          "Incomplete problem: " + numClauses + " clauses are missing");
    }

    return skeleton;
  }
}
