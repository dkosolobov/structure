package ibis.structure;

import java.io.PrintStream;
import org.apache.log4j.Logger;
import ibis.cohort.Cohort;
import ibis.cohort.CohortFactory;
import ibis.cohort.MessageEvent;
import ibis.cohort.SingleEventCollector;

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

  public static void main(String[] args) throws Exception {
    ClassLoader.getSystemClassLoader().
      setPackageAssertionStatus("ibis.structure", true);

    Cohort cohort = CohortFactory.createCohort();
    cohort.activate();
    try {
      displayHeader();
      if (cohort.isMaster()) {
        Skeleton instance = configure(args);

        SingleEventCollector root = new SingleEventCollector();
        cohort.submit(root);
        cohort.submit(new Job(root.identifier(), instance, 0));

        int[] model = ((MessageEvent<int[]>)root.waitForEvent()).message;
        if (model == null) {
          printUnsatisfiable(System.out);
        } else {
          printSolution(System.out, model);
        }
      }
    } catch (ContradictionException e) {
      logger.info("Clause unsatisfiable", e);
      printUnsatisfiable(System.out);
    } catch (Exception e) {
      logger.error("Caught unhandled exception", e);
      printUnknown(System.out);
    } finally {
      cohort.done();
    }
  }

  private static void printSolution(PrintStream out, int[] model) {
    out.println("s SATISFIABLE");
    out.print("v");
    for (int i = 0; i < model.length; ++i) {
      out.print(" " + model[i]);
    }
  }

  private static void printUnsatisfiable(PrintStream out) {
    out.println("s UNSATISFIABLE");
  }

  private static void printUnknown(PrintStream out) {
    out.println("s UNKNOWN");
  }
}
