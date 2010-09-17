package ibis.structure;

import java.io.FileOutputStream;
import java.io.PrintStream;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.BasicParser;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.HelpFormatter;
import org.apache.log4j.Logger;

public class Simplify {
  private static final Logger logger = Logger.getLogger(Simplify.class);

  private static String inputFile = null;
  private static String outputFile = null;

  public static void main(final String[] args)
      throws Exception {
    if (!configure(args)) {
      return;
    }

    long startTime = System.currentTimeMillis();

    Skeleton input = Reader.parseURL(inputFile);
    Skeleton output;
    try {
      Solver solver = new Solver(input, 0);
      solver.simplify();
      output = solver.skeleton(true);
    } catch (ContradictionException e) {
      logger.info("Found trivial contradiction", e);
      output = new Skeleton();
      output.addArgs(1);
      output.addArgs(-1);
    }
    output.canonicalize();

    long endTime = System.currentTimeMillis();
    logger.info("Reduced from " + input.difficulty() + " literal(s) to "
                + output.difficulty() + " literal(s) in "
                + ((endTime - startTime) / 1000.) + "s");

    PrintStream outputStream;
    if (outputFile == null) {
      outputStream = System.out;
    } else {
      outputStream = new PrintStream(new FileOutputStream(outputFile));
    }
    outputStream.print(output.toString());
  }

  private static boolean configure(String[] args)
      throws Exception {
    Options options = new Options();
    options.addOption("h", false, "print this help");
    options.addOption("o", true, "output file (defaults to stdout)");

    BasicParser parser = new BasicParser();
    CommandLine cl = parser.parse(options, args);
    args = cl.getArgs();

    if (cl.hasOption('h') || args.length != 1) {
      HelpFormatter formatter = new HelpFormatter();
      formatter.printHelp("simplify input.cnf", options, true);
      return false;
    }

    if (cl.hasOption("o")) {
      outputFile = cl.getOptionValue("o");
    }
    inputFile = args[0];
    return true;
  }

  private Simplify() {
  }
}
