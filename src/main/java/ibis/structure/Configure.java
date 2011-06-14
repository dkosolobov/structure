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
  public static int numExecutors = 1; // Runtime.getRuntime().availableProcessors();
  /** True to enable expensive checks for debugging. */
  public static boolean enableExpensiveChecks = false;
  /** True to print more info. */
  public static boolean verbose = true;
  /** True to perform hidden tautology elimination. */
  public static boolean hiddenTautologyElimination = true;
  /** True to perform pure literals. */
  public static boolean pureLiterals = true;
  /** True to perform binary (self) subsumming. */
  public static boolean selfSubsumming = true;
  /** True to split instances when possible. */
  public static boolean split = true;
  /** True to extract xor gates and enable dependent variable elimination. */
  public static boolean xor = true;
  /** True to run blocked clause elimination. */
  public static boolean bce = true;
  /** True to enable variable elimination. */
  public static boolean ve = true;
  /** Number of hyper binary resolutions to perform. 0 to disable. */
  public static boolean hyperBinaryResolution = true;
  /** Threshold for transitive closure. */
  public static double[] ttc = {0.29, 0.19};

  public static boolean configure(String[] args) {
    Options options = new Options();
    options.addOption("help", false, "print this help");
    options.addOption("e", true, "# of executors (defaults to number of CPUs)");
    options.addOption("o", true, "output file (defaults to stdout)");
    options.addOption("debug", false, "enable expensive checks");
    options.addOption("q", false, "be quiet");

    options.addOption("nohte", false, "disable hidden tautology elimination");
    options.addOption("nopl", false, "disable pure literals");
    options.addOption("nosss", false, "disable self-subsumming");
    options.addOption("nosplit", false, "disable splitting");
    options.addOption("noxor", false, "disable xor gates extraction");
    options.addOption("nobce", false, "disable blocked clause elimination");
    options.addOption("nohbr", false, "disable hyper binary resolutions");
    options.addOption("ttc", true, "some coefficients");

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

    if (cl.hasOption("hte")) {
      hiddenTautologyElimination = false;
    }
    if (cl.hasOption("nopl")) {
      pureLiterals = false;
    }
    if (cl.hasOption("nosss")) {
      selfSubsumming = false;
    }
    if (cl.hasOption("nosplit")) {
      split = false;
    }
    if (cl.hasOption("noxor")) {
      xor = false;
    }
    if (cl.hasOption("nobce")) {
      bce = false;
    }
    if (cl.hasOption("nohbr")) {
      hyperBinaryResolution = false;
    }
    if (cl.hasOption("ttc")) {
      String[] ttc_ = cl.getOptionValue("ttc").split(",");
      ttc = new double[ttc_.length];
      for (int i = 0; i < ttc_.length; i++) {
        ttc[i] = Double.parseDouble(ttc_[i]);
      }
    }

    return true;
  }
}
