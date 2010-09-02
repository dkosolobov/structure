package ibis.structure;

public class ParseException extends Exception {
  public ParseException(String message) {
    super("Invalid input file: " + message);
  }
}
