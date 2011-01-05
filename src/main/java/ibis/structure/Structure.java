package ibis.structure;

import ibis.constellation.Constellation;
import ibis.constellation.ConstellationFactory;
import ibis.constellation.SimpleExecutor;
import ibis.constellation.Executor;
import ibis.constellation.SingleEventCollector;
import ibis.constellation.StealStrategy;
import ibis.constellation.context.UnitWorkerContext;
import java.io.PrintStream;
import java.io.FileOutputStream;
import org.apache.commons.cli.BasicParser;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.log4j.Logger;

public class Structure {
  private static final Logger logger = Logger.getLogger(Structure.class);

  public static String inputFile = null;
  public static String outputFile = null;
  public static int numExecutors = 0;
  public static long startTime = System.currentTimeMillis();

  private static void displayHeader() {
    logger.info("STRUCTure: a SATisfiability library for Java (c) 2009-2010 Alexandru Moșoi");
    logger.info("This is free software under the MIT licence except where otherwise noted.");

    Runtime runtime = Runtime.getRuntime();
    logger.info("Free memory \t\t" + runtime.freeMemory());
    logger.info("Max memory \t\t" + runtime.maxMemory());
    logger.info("Total memory \t\t" + runtime.totalMemory());
    logger.info("Number of processors \t" + runtime.availableProcessors());
  }

  private static Executor[] createExecutors(int numExecutors) {
    Executor[] executors = new Executor[numExecutors];
    for (int i = 0; i < numExecutors; ++i) {
      executors[i] = new SimpleExecutor(
          new UnitWorkerContext("DEFAULT"),
          StealStrategy.SMALLEST, StealStrategy.BIGGEST);
    }
    return executors;
  }

  private static boolean configure(String[] args) {
    Options options = new Options();
    options.addOption("h", false, "print this help");
    options.addOption("o", true, "output file (defaults to stdout)");
    options.addOption("e", true, "number of executors");

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

    if (wrongArguments || cl.hasOption('h') || args.length != 1) {
      HelpFormatter formatter = new HelpFormatter();
      formatter.printHelp("solver input", options, true);
      return false;
    }

    inputFile = args[0];
    outputFile = cl.hasOption("o") ? cl.getOptionValue("o") : null;
    numExecutors = Integer.parseInt(cl.getOptionValue("e", "0"));
    logger.info("Reading from " + inputFile);
    return true;
  }

  public static void main(String[] args) throws Exception {
    if (!configure(args)) {
      return;
    }

    // Reads input and opens output
    Skeleton instance = null;
    try {
      instance = Reader.parseURL(inputFile);
    } catch (Exception e) {
      logger.error("Cannot read input file", e);
      System.exit(1);
    }

    PrintStream output = System.out;
    if (outputFile != null) {
      try {
        output = new PrintStream(new FileOutputStream(outputFile));
      } catch (Exception e) {
        logger.error("Cannot open output file", e);
        System.exit(1);
      }
    }

    // Activates Constellation.
    if (numExecutors == 0) {
      numExecutors = Runtime.getRuntime().availableProcessors();
    }
    final Constellation constellation =
        ConstellationFactory.createConstellation(
            createExecutors(numExecutors));
    constellation.activate();

    try {
      displayHeader();
      if (constellation.isMaster()) {

        // starts solver
        SingleEventCollector root = new SingleEventCollector();
        constellation.submit(root);
        constellation.submit(new Job(root.identifier(), 0, instance, 0));

        int[] model = (int[])root.waitForEvent().data;
        final long endTime = System.currentTimeMillis();
        output.println("c Elapsed time " + (endTime - startTime) / 1000.);

        if (model == null) {
          printUnsatisfiable(output);
        } else {
          printSolution(output, model);
        }
      }
    } catch (Exception e) {
      logger.error("Caught unhandled exception", e);
      printUnknown(System.out);
    } finally {
      constellation.done();
      System.exit(0);
    }
  }

  private static void printSolution(PrintStream out, int[] model) {
    printSatisfiable(out);
    out.print("v");
    for (int i = 0; i < model.length; ++i) {
       out.print(" " + model[i]);
    }
    out.println(" 0");
  }

  private static void printSatisfiable(PrintStream out) {
    out.println("s SATISFIABLE");
  }

  private static void printUnsatisfiable(PrintStream out) {
    out.println("s UNSATISFIABLE");
  }

  private static void printUnknown(PrintStream out) {
    out.println("s UNKNOWN");
  }
}
