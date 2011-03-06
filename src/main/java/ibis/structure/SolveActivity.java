package ibis.structure;

import ibis.constellation.ActivityIdentifier;
import ibis.constellation.Event;
import org.apache.log4j.Logger;


public final class SolveActivity extends Activity {
  private static final Logger logger = Logger.getLogger(SolveActivity.class);

  private final int branch;
  private Core core;

  public SolveActivity(final ActivityIdentifier parent,
                       final int depth,
                       final Skeleton instance,
                       final boolean learn,
                       final int branch) {
    super(parent, depth, instance, learn);
    this.branch = branch;
  }

  public void initialize() {
    Solver solver = null;
    Solution solution = null;

    try {
      solver = new Solver(instance);
      solution = solver.solve(branch);
    } catch (Throwable e) {
      logger.error("Failed to solve instance", e);
      // logger.error("Branch is " + branch);
      // logger.error("Formula is " + instance);
      System.exit(1);
      reply(Solution.unknown());
      finish();
      return;
    }

    if (solution.isUnknown() && depth > 0) {
      // solver.printHist();
      core = solver.core();
      executor.submit(new SplitActivity(
            identifier(), depth, core.instance(), learn));
      gc();
      suspend();
    } else {
      verify(solution, branch);
      reply(solution);
      finish();
    }
  }

  public void process(final Event e) throws Exception {
    Solution response = (Solution) e.data;
    response = core.merge(response);
    verify(response, branch);
    reply(response);
    finish();
  }

  public void gc() {
    super.gc();
    core.gc();
  }
}
