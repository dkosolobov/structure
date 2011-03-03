package ibis.structure;

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
      solver = new Solver(instance);
      solution = solver.solve(branch);
      if (!Configure.enableExpensiveChecks) {
        instance = null;  // Helps GC
      }
    } catch (Throwable e) {
      logger.error("Failed to solve instance", e);
      // logger.error("Branch is " + branch);
      // logger.error("Formula is " + instance);
      // System.exit(1);
      reply(Solution.unknown());
      finish();
      return;
    }

    if (solution.isUnknown() && depth > 0) {
      core = solver.core();
      executor.submit(new SplitActivity(identifier(), depth, core.instance()));
      core.gc();
      suspend();
    } else {
      verify(solution, branch);
      reply(solution);
      finish();
    }
  }

  public void process(final Event e) throws Exception {
    Solution response = (Solution)e.data;
    if (response.isSatisfiable()) {
      Solution solution = core.merge(response);
      verify(solution, branch);
      reply(solution);
    } else {
      reply(response);
    }
    finish();
  }
}
