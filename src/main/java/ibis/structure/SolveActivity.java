package ibis.structure;

import gnu.trove.TIntArrayList;
import ibis.constellation.ActivityIdentifier;
import ibis.constellation.Event;
import org.apache.log4j.Logger;


public final class SolveActivity extends Activity {
  private static final Logger logger = Logger.getLogger(SolveActivity.class);

  private int branch;
  private Core core = null;

  public SolveActivity(final ActivityIdentifier parent,
                       final int depth,
                       final Skeleton instance,
                       final int branch) {
    super(parent, depth, instance);
    this.branch = branch;
  }

  public void initialize() {
    Solver solver = null;
    Solution solution = null;

    try {
      // logger.info("before = " + instance);
      solver = new Solver(instance);
      solution = solver.solve(branch);
      if (!Configure.enableExpensiveChecks) {
        instance = null;  // Helps GC
      }
    } catch (Throwable e) {
      logger.error("Failed to solve instance", e);
      logger.error("Branch is " + branch);
      logger.error("Formula is " + instance);
      System.exit(1);
      reply(Solution.unknown());
      finish();
      return;
    }

    if (solution.isUnknown() && depth > 0) {
      core = solver.core();
      /*
      logger.info("units are " + new TIntArrayList(core.units.elements()));
      logger.info("instance + " + core.instance());
      System.exit(1);
      */

      // logger.info("after = " + core.instance());
      executor.submit(new SplitActivity(identifier(), depth, core.instance()));
      suspend();
    } else {
      if (solution.isSatisfiable() && solver.bceClauses != null) {
        BitSet units = new BitSet();
        units.addAll(solution.units());
        BlockedClauseElimination.addUnits(solver.bceClauses, units);
        solution = Solution.satisfiable(units.elements());
      }

      verify(solution, branch);
      reply(solution);
      finish();
    }
  }

  public void process(final Event e) throws Exception {
    Solution response = (Solution)e.data;
    if (response.isSatisfiable()) {
      /*
      // logger.info("*************** MERGING *******************************");
      // logger.info("before " + new TIntArrayList(response.units()));
      // logger.info("instance " + instance);
      // logger.info("core " + new TIntArrayList(core.units.elements()));
      for (int u = 1; u <= instance.numVariables; ++u) {
        if (core.proxies[u] != u) {
          logger.info(u + " -> " + core.proxies[u]);
        }
      }
      // logger.info("instance + " + core.instance());
      */
      Solution solution = core.merge(response);
      // logger.info("after " + new TIntArrayList(solution.units()));
      verify(solution, branch);
      reply(solution);
    } else {
      // logger.info("***************************** UNSAT *********************");
      reply(response);
    }
    finish();
  }
}
