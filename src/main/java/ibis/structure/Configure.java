package ibis.structure;

import org.apache.commons.cli.BasicParser;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.log4j.Logger;


public class Configure {
  private static final Logger logger = Logger.getLogger(Structure.class);

  public static long startTime = System.currentTimeMillis();
  public static String inputFile = null;
  public static String outputFile = null;
  public static int numExecutors = Runtime.getRuntime().availableProcessors();
  public static boolean enableExpensiveChecks = false;
  public static int numHyperBinaryResolutions = 4;
  public static boolean pureLiterals = true;
  public static boolean binarySelfSubsumming = true;

  public static boolean configure(String[] args) {
    Options options = new Options();
    options.addOption("help", false, "print this help");
    options.addOption("e", true, "# of executors (0 for number of CPUs)");
    options.addOption("o", true, "output file (defaults to stdout)");
    options.addOption("debug", false, "enable expensive checks");

    options.addOption("nobsss", false, "disable binary self subsumming");
    options.addOption("nopl", false, "disable pure literals");
    options.addOption("hbr", true, "# of hyper binary resolutions (0 to disable)");

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

    if (cl.hasOption("nobsss")) {
      binarySelfSubsumming = false;
    }
    if (cl.hasOption("nopl")) {
      pureLiterals = false;
    }
    if (cl.hasOption("hbr")) {
      numHyperBinaryResolutions = Integer.parseInt(cl.getOptionValue("hbr"));
    }

    return true;
  }
}
