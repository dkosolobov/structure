package ibis.structure;

import gnu.trove.TIntArrayList;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.IOException;
import java.util.NoSuchElementException;
import java.util.Scanner;
import org.apache.log4j.Logger;


/**
 * Reader is used to read instances using the SAT4J reader.
 */
public final class Reader {
  private static final Logger logger = Logger.getLogger(Reader.class);

  public static Skeleton parseText(String text)
      throws IOException, ParseException {
    return parseStream(new ByteArrayInputStream(text.getBytes("UTF-8")));
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
