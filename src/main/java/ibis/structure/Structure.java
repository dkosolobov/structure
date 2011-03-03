package ibis.structure;

import gnu.trove.list.array.TIntArrayList;
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
    Constellation constellation =
        ConstellationFactory.createConstellation(createExecutors());
    constellation.activate();

    // Creates the thread pool
    // HyperBinaryResolution.createThreadPool();

    // Starts the computation
    Solution solution = null;
    try {
      if (Configure.verbose) {
        displayHeader();
      }
      if (constellation.isMaster()) {
        solution = solve(constellation, instance);
      }
    } catch (Throwable e) {
      logger.error("Caught unhandled exception", e);
      solution = Solution.unknown();
    } finally {
      final long endTime = System.currentTimeMillis();
      output.println("c Elapsed time " + (endTime - Configure.startTime) / 1000.);
      solution.print(output);

      constellation.done();
      System.exit(solution.exitcode());
    }
  }

  private static Solution solve(Constellation constellation, Skeleton instance) {
    TIntArrayList dve = null, bce = null;
    Solver solver = null;
    
    try {
      if (Configure.xor) {
        XOR.extractXORClauses(instance);
      }

      solver = new Solver(instance);
      if (Configure.dve) {
        dve = DependentVariableElimination.run(solver);
      }
      solver.simplifyAtTopLevel();

      if (Configure.bce) {
        bce = (new BlockedClauseElimination(solver)).run();
      }
    } catch (ContradictionException e) {
      return Solution.unsatisfiable();
    }

    Core core = solver.core();
    InstanceNormalizer normalizer = new InstanceNormalizer();
    normalizer.normalize(core.instance());
    logger.info(core.instance().numVariables
                + " variables remaining out of "
                + instance.numVariables);
    logger.info(core.instance().formula.size()
                + " literals remaining out of "
                + instance.formula.size());

    SingleEventCollector root = new SingleEventCollector();
    constellation.submit(root);
    constellation.submit(new BranchActivity(root.identifier(),
                                            core.instance().numVariables,
                                            core.instance()));

    Solution solution = (Solution) root.waitForEvent().data;
    if (!solution.isSatisfiable()) {
      return solution;
    }

    normalizer.denormalize(solution);
    BitSet units;

    units = new BitSet();
    units.addAll(solution.units());
    if (bce != null) {
      BlockedClauseElimination.addUnits(bce, units);
      solution = Solution.satisfiable(units.elements());
    }

    solution = core.merge(solution);

    units = new BitSet();
    units.addAll(solution.units());
    if (dve != null) {
      DependentVariableElimination.addUnits(dve, units);
      solution = Solution.satisfiable(units.elements());
    }

    return solution;
  }
}
