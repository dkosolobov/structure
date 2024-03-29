package ibis.structure;

import org.apache.commons.cli.BasicParser;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.log4j.Logger;
import ibis.constellation.Executor;


public class Configure {
  private static final Logger logger = Logger.getLogger(Structure.class);

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
  public static boolean hur = true;
  /** True to perform binary (self) subsumming. */
  public static boolean sss = true;
  /** True to split instances when possible. */
  public static boolean split = true;
  /** True to extract xor gates and enable dependent variable elimination. */
  public static boolean xor = true;
  /** True to run blocked clause elimination. */
  public static boolean bce = true;
  /** True to enable variable elimination. */
  public static boolean ve = true;
  /** True to enable learning. */
  public static boolean learn = true;
  /** True to enable sorting binaries in RestartActivity. */
  public static boolean sb = false;

  /** Root look-ahead size. */
  public static int lookAheadSize = 4;
  /** Generation initial timeToLive. */
  public static int initialTTL = 10;
  /** Generation extra timeToLive. */
  public static int extralTTL = 10;
  /** ttc is used to set some coefficients. */
  public static double[] ttc = { 3, 2 };

  /** Context for local jobs. */
  public static String localContext;
  /** Executor for local jobs. */
  public static Executor localExecutor;

  public static boolean configure(String[] args) {
    Options options = new Options();
    options.addOption("help", false, "print this help");
    options.addOption("e", true, "# of executors (defaults to number of CPUs)");
    options.addOption("o", true, "output file (defaults to stdout)");
    options.addOption("debug", false, "enable expensive checks");

    options.addOption("q", false, "be quiet");
    options.addOption("nohur", false, "disable hyper unit resolution");
    options.addOption("nosss", false, "disable self-subsumming");
    options.addOption("nosplit", false, "disable splitting");
    options.addOption("noxor", false, "disable xor gates extraction");
    options.addOption("nobce", false, "disable blocked clause elimination");
    options.addOption("nove", false, "disable variable elimination");
    options.addOption("nolearn", false, "disable learning");
    options.addOption("nosb", false, "disable binaries sorting");

    options.addOption("la", true, "root look-ahead size");
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

    enableExpensiveChecks = cl.hasOption("debug");

    verbose = verbose && !cl.hasOption("q");
    hur = hur && !cl.hasOption("nohur");
    sss = sss && !cl.hasOption("nosss");
    split = split && !cl.hasOption("nosplit");
    xor = xor && !cl.hasOption("noxor");
    bce = bce && !cl.hasOption("nobce");
    ve = ve && !cl.hasOption("nove");
    learn = learn && !cl.hasOption("nolearn");
    sb = sb && !cl.hasOption("nosb");

    if (cl.hasOption("la")) {
      lookAheadSize = Integer.parseInt(cl.getOptionValue("la"));
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
