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
                       final long generation,
                       final Skeleton instance,
                       final int branch) {
    super(parent, depth, generation, instance);
    this.branch = branch;
  }

  public void initialize() {
    Solver solver = null;
    Solution solution = null;

    try {
      solver = new Solver(instance, branch);
      solution = solver.solve(false);
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
      executor.submit(new BlackHoleActivity(
            identifier(), depth, generation, core.instance()));
      suspend();
    } else {
      reply(solution);
      finish();
    }
  }

  public void process(final Event e) throws Exception {
    Solution response = (Solution) e.data;
    if (response.isSatisfiable()) {
      response = core.merge(response);
    }
    reply(response);
    finish();
  }

  protected void gc() {
    super.gc();
    core.gc();
  }
}
