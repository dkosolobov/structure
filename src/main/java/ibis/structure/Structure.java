package ibis.structure;

import ibis.constellation.Constellation;
import ibis.constellation.ConstellationFactory;
import ibis.constellation.context.UnitWorkerContext;
import ibis.constellation.context.UnitActivityContext;
import ibis.constellation.Executor;
import ibis.constellation.SimpleExecutor;
import ibis.constellation.SingleEventCollector;
import ibis.constellation.StealStrategy;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.Random;
import org.apache.log4j.Logger;

class Structure {
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

  private static Executor[] createExecutors() {
    Executor[] executors = new Executor[Configure.numExecutors + 1];

    Configure.localContext = "Local-" + (new Random()).nextLong();
    Configure.localExecutor = new SimpleExecutor(
        new UnitWorkerContext(Configure.localContext),
        StealStrategy.SMALLEST, StealStrategy.BIGGEST);

    executors[0] = Configure.localExecutor;
    for (int i = 1; i <= Configure.numExecutors; ++i) {
      executors[i] = new SimpleExecutor(
          new UnitWorkerContext("DEFAULT"),
          StealStrategy.SMALLEST, StealStrategy.BIGGEST);
    }

    return executors;
  }

  private static Skeleton readInput() {
    try {
      return Reader.parseURL(Configure.inputFile);
    } catch (Exception e) {
      logger.error("Cannot read input file", e);
      return null;
    }
  }

  public static void main(String[] args) throws Exception {
    if (!Configure.configure(args)) {
      System.exit(1);
    }

    // Activates Constellation.
    Constellation constellation =
        ConstellationFactory.createConstellation(createExecutors());
    constellation.activate();

    // Starts the computation
    if (constellation.isMaster()) {
      displayHeader();
      TracerMaster.create();

      Skeleton instance = readInput();
      if (instance == null) {
        System.exit(1);
      }

      PrintStream output = System.out;
      if (Configure.outputFile != null) {
        try {
          output = new PrintStream(new FileOutputStream(Configure.outputFile));
        } catch (Exception e) {
          logger.error("Cannot open output file " + Configure.outputFile
                       + " for writing", e);
          System.exit(1);
        }
      }

      final long startTime = System.currentTimeMillis();
      Solution solution = solve(constellation, instance);
      final long endTime = System.currentTimeMillis();

      output.println("c Elapsed time " + (endTime - startTime) / 1000.);
      solution.print(output);
      output.flush();

      TracerMaster.stop();
      constellation.done();
    } else {
      constellation.done();
    }
    System.exit(0);
  }

  private static Solution solve(Constellation constellation, Skeleton instance) {
    SingleEventCollector root = new SingleEventCollector(
        new UnitActivityContext(Configure.localContext));
    Configure.localExecutor.submit(root);

    Configure.localExecutor.submit(new PreprocessActivity(
          root.identifier(), TracerMaster.master, instance));
    return (Solution) root.waitForEvent().data;
  }
}
