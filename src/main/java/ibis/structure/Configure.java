package ibis.structure;

import org.apache.commons.cli.BasicParser;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.log4j.Logger;


public class Configure {
  private static final Logger logger = Logger.getLogger(Structure.class);

  /** Start time of program. */
  public static long startTime = System.currentTimeMillis();
  /** Path to input file. */
  public static String inputFile = null;
  /** Path to output file. */
  public static String outputFile = null;
  /** Number of executors to used. */
  public static int numExecutors = Runtime.getRuntime().availableProcessors();
  /** True to enable expensive checks for debugging. */
  public static boolean enableExpensiveChecks = false;
  /** True to print more info. */
  public static boolean verbose = true;
  /** True to perform binary (self) subsumming. */
  public static boolean binarySelfSubsumming = true;
  /** True to perform pure literals. */
  public static boolean pureLiterals = true;
  /** True to perform subsumming. */
  public static boolean subsumming = true;
  /** Number of hyper binary resolutions to perform. 0 to disable. */
  public static int numHyperBinaryResolutions = 2;
  /** Threshold for transitive closure. */
  public static double ttc = 1.10;

  public static boolean configure(String[] args) {
    Options options = new Options();
    options.addOption("help", false, "print this help");
    options.addOption("e", true, "# of executors (defaults to number of CPUs)");
    options.addOption("o", true, "output file (defaults to stdout)");
    options.addOption("debug", false, "enable expensive checks");
    options.addOption("q", false, "be quiet");

    options.addOption("nobsss", false, "disable binary self subsumming");
    options.addOption("nopl", false, "disable pure literals");
    options.addOption("noss", false, "disable subsumming");
    options.addOption("hbr", true, "# of hyper binary resolutions (0 to disable)");
    options.addOption("ttc", true, "threshold for transitive closure");

    BasicParser parser = new BasicParser();
    CommandLine cl = null;
    boolean wrongArguments = false;

    try {
      cl = parser.parse(options, args);
      args = cl.getArgs();
    } catch (ParseException e) {
      e.printStackTrace();
      wrongArguments = true;
    }

    if (wrongArguments || cl.hasOption('?') || args.length != 1) {
      HelpFormatter formatter = new HelpFormatter();
      formatter.printHelp("solve input", options, true);
      return false;
    }

    inputFile = args[0];
    logger.info("Reading from input " + inputFile);

    if (cl.hasOption("e")) {
      numExecutors = Integer.parseInt(cl.getOptionValue("e"));
    }
    if (cl.hasOption("o")) {
      outputFile = cl.getOptionValue("o");
    }
    if (cl.hasOption("debug")) {
      enableExpensiveChecks = true;
    }
    if (cl.hasOption("q")) {
      verbose = false;
    }

    if (cl.hasOption("nobsss")) {
      binarySelfSubsumming = false;
    }
    if (cl.hasOption("nopl")) {
      pureLiterals = false;
    }
    if (cl.hasOption("nopl")) {
      subsumming = false;
    }
    if (cl.hasOption("hbr")) {
      numHyperBinaryResolutions = Integer.parseInt(cl.getOptionValue("hbr"));
    }
    if (cl.hasOption("ttc")) {
      ttc = Double.parseDouble(cl.getOptionValue("ttc"));
    }

    return true;
  }
}
