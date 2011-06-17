package ibis.structure;

import ibis.constellation.ActivityIdentifier;
import ibis.constellation.Event;
import org.apache.log4j.Logger;

import static ibis.structure.Misc.*;

/**
 * Performs some simplification on the instance.
 *
 * This activity is similar to SolveActivity, but is
 * part of the restart loop.
 */
public final class SimplifyActivity extends Activity {
  private static final Logger logger = Logger.getLogger(SimplifyActivity.class);

  /** Core after simplification. */
  private Core core = null;

  public SimplifyActivity(final ActivityIdentifier parent,
                          final int depth,
                          final Skeleton instance) {
    super(parent, depth, 0, instance.clone());
  }

  @Override
  public void initialize() {
    try {
      normalize();
      Solver solver = new Solver(instance, 0);

      solver.propagate();
      HyperBinaryResolution.run(solver);
      HiddenTautologyElimination.run(solver);
      SelfSubsumming.run(solver);
      solver.propagate();
      solver.renameEquivalentLiterals();
      PureLiterals.run(solver);
      MissingLiterals.run(solver);

      Solution solution = solver.solve2();
      if (!solution.isUnknown()) {
        reply(solution);
        finish();
        return;
      }

      core = solver.core();
    } catch (ContradictionException e) {
      reply(Solution.unsatisfiable());
      finish();
      return;
    }

    executor.submit(new RestartActivity(
          identifier(), depth, core.instance()));
    suspend();
  }

  @Override
  public void process(final Event e) throws Exception {
    reply(core.merge((Solution) e.data));
    finish();
  }
}
