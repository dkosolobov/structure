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
import org.apache.log4j.Logger;

public class Structure {
  private static final Logger logger = Logger.getLogger(Structure.class);


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


  public static void main(String[] args) throws Exception {
    if (!Configure.configure(args)) {
      return;
    }

    // Reads input and opens output
    Skeleton instance = null;
    try {
      instance = Reader.parseURL(Configure.inputFile);
    } catch (Exception e) {
      logger.error("Cannot read input file", e);
      System.exit(1);
    }

    PrintStream output = System.out;
    if (Configure.outputFile != null) {
      try {
        output = new PrintStream(new FileOutputStream(Configure.outputFile));
      } catch (Exception e) {
        logger.error("Cannot open output file", e);
        System.exit(1);
      }
    }

    // Activates Constellation.
    final Constellation constellation =
        ConstellationFactory.createConstellation(
            createExecutors(Configure.numExecutors));
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
        output.println("c Elapsed time " + (endTime - Configure.startTime) / 1000.);

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
