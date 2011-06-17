package ibis.structure;

import ibis.constellation.ActivityIdentifier;
import ibis.constellation.Event;
import org.apache.log4j.Logger;


/**
 * Performs some simplification on the instance.
 *
 * This activity is similar to SimplifyActivity, but is
 * part of the solving loop.
 */
public final class SolveActivity extends Activity {
  private static final Logger logger = Logger.getLogger(SolveActivity.class);

  /** branching literal. */
  private int branch;
  /** Core of the instance after simplification. */
  private Core core;

  public SolveActivity(final ActivityIdentifier parent,
                       final int depth,
                       final long generation,
                       final Skeleton instance,
                       final int branch) {
    super(parent, depth, generation, instance);
    this.branch = branch;
  }

  @Override
  public void initialize() {
    Solver solver = null;
    Solution solution = null;

    try {
      solver = new Solver(instance, branch);
      solution = solver.solve(false);
    } catch (ContradictionException e) {
      solution = Solution.unsatisfiable(branch);
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

  @Override
  public void process(final Event e) throws Exception {
    Solution response = (Solution) e.data;
    if (response.isSatisfiable()) {
      response = core.merge(response);
    } else if (response.isUnsatisfiable()) {
      response = Solution.unsatisfiable(branch);
    } else if (response.isUnknown()) {
      response = Solution.unknown(branch, response, core);
    }

    reply(response);
    finish();
  }

  protected void gc() {
    super.gc();
    core.gc();
  }
}
