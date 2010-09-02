package ibis.structure;

import gnu.trove.TIntArrayList;
import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.IOException;
import java.net.URL;
import java.util.zip.GZIPInputStream;
import java.util.NoSuchElementException;
import java.util.Scanner;
import org.apache.log4j.Logger;


/**
 * Reads SAT instancs in DIMACS CNF format.
 */
public final class Reader {
  private static final Logger logger = Logger.getLogger(Reader.class);

  public static Skeleton parseText(String text)
      throws IOException, ParseException {
    return parseStream(new ByteArrayInputStream(text.getBytes("UTF-8")));
  }

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

  private static Skeleton parseStream(InputStream source)
      throws IOException, ParseException {
    Scanner scanner = new Scanner(source);

    // Skip comments
    try {
      String token = scanner.next();
      while (token.equals("c")) {
        logger.debug("c" + scanner.nextLine());
        token = scanner.next();
      }
      if (!token.equals("p")) {
        throw new ParseException("Excepted 'p', but '" + token +
                                 "' was found");
      }
    } catch (NoSuchElementException e) {
      throw new ParseException("Header not found");
    }

    // Reads header
    int numVariables, numClauses;
    try {
      String cnf = scanner.next();
      if (!cnf.equals("cnf")) {
        throw new ParseException("Expected 'cnf', but '" + cnf +
                                 "' was found");
      }
      numVariables = scanner.nextInt();
      numClauses = scanner.nextInt();
    } catch (NoSuchElementException e) {
      throw new ParseException("Incomplete header");
    }

    // Reads clauses
    Skeleton skeleton = new Skeleton();
    try {
      TIntArrayList clause = new TIntArrayList();
      while (numClauses > 0) {
        int literal = scanner.nextInt();
        if (literal == 0) {
          skeleton.add(clause.toNativeArray());
          clause.clear();
          --numClauses;
        } else {
          clause.add(literal);
        }
      }
    } catch (NoSuchElementException e) {
      throw new ParseException("Incomplete problem: " + numClauses +
                               " clauses are missing");
    }

    return skeleton;
  }
}
