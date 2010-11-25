package ibis.structure;

import ibis.constellation.Constellation;
import ibis.constellation.ConstellationFactory;
import ibis.constellation.SimpleExecutor;;
import ibis.constellation.Executor;;
import ibis.constellation.SingleEventCollector;
import ibis.constellation.StealStrategy;;
import ibis.constellation.context.UnitWorkerContext;
import java.io.PrintStream;
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

  private static Skeleton configure(String[] args) throws Exception {
    String url = args[0];
    logger.info("Reading from " + url);
    return Reader.parseURL(url);
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
    final long startTime = System.currentTimeMillis();
    final PrintStream output = System.out;

    final Constellation constellation =
        ConstellationFactory.createConstellation(createExecutors(4));
    constellation.activate();

    try {
      displayHeader();
      if (constellation.isMaster()) {
        Skeleton instance = configure(args);

        SingleEventCollector root = new SingleEventCollector();
        constellation.submit(root);
        constellation.submit(new Job(root.identifier(), 0, instance, 0));

        int[] model = (int[])root.waitForEvent().data;
        final long endTime = System.currentTimeMillis();
        output.println("c Elapsed time " + (endTime - startTime) / 1000.);

        if (model == null) {
          printUnsatisfiable(output);
        } else {
          printSatisfiable(output);
          printSolution(output, model);
        }
      }
    } catch (ContradictionException e) {
      logger.info("Clause unsatisfiable", e);
      printUnsatisfiable(System.out);
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
    out.println();
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
