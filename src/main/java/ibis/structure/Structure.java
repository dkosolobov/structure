package ibis.structure;

import ibis.constellation.Constellation;
import ibis.constellation.ConstellationFactory;
import ibis.constellation.context.UnitWorkerContext;
import ibis.constellation.Executor;
import ibis.constellation.SimpleExecutor;
import ibis.constellation.SingleEventCollector;
import ibis.constellation.StealStrategy;
import java.io.FileOutputStream;
import java.io.PrintStream;
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
    Executor[] executors = new Executor[Configure.numExecutors];
    for (int i = 0; i < Configure.numExecutors; ++i) {
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
      return;
    }

    // Activates Constellation.
    Constellation constellation =
        ConstellationFactory.createConstellation(createExecutors());
    constellation.activate();

    // Starts the computation
    Solution solution = null;
    if (constellation.isMaster()) {
      displayHeader();

      PrintStream output = System.out;
      if (Configure.outputFile != null) {
        try {
          output = new PrintStream(new FileOutputStream(Configure.outputFile));
        } catch (Exception e) {
          logger.error("Cannot open output file", e);
          System.exit(1);
        }
      }

      Skeleton instance = readInput();
      if (instance == null) {
        return;
      }
      solution = solve(constellation, instance);

      final long endTime = System.currentTimeMillis();
      output.println("c Elapsed time " + (endTime - Configure.startTime) / 1000.);
      solution.print(output);
    }

    constellation.done();
  }

  private static Solution solve(Constellation constellation, Skeleton instance) {
    SingleEventCollector root = new SingleEventCollector();
    constellation.submit(root);
    constellation.submit(new PreprocessActivity(root.identifier(), instance));
    return (Solution) root.waitForEvent().data;
  }
}
