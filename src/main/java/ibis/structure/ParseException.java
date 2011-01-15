package ibis.structure;

/**
 * ParseException is thrown when input files contains an invalid instance.
 */
public class ParseException extends Exception {
  /**
   * @param message message to include with exception.
   */
  public ParseException(final String message) {
    super("Input file " + Configure.inputFile + " is invalid: " + message);
  }
}
