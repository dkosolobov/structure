package ibis.structure;

import gnu.trove.list.array.TDoubleArrayList;
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
                          final TDoubleArrayList scores,
                          final Skeleton instance) {
    super(parent, 0, 0, scores, instance.clone());
  }

  @Override
  public void initialize() {
    Solver solver = null;
    Solution solution = null;

    try {
      normalizer.normalize(instance);
      solver = new Solver(instance);

      solver.propagate();
      HyperBinaryResolution.run(solver);
      HiddenTautologyElimination.run(solver);
      solver.renameEquivalentLiterals();
      SelfSubsumming.run(solver);
      PureLiterals.run(solver);
      MissingLiterals.run(solver);

      solution = solver.solve();
      solution = normalizer.denormalize(solution);
      assert !solution.isUnsatisfiable();
    } catch (ContradictionException e) {
      solution = Solution.unsatisfiable();
    } catch (Exception e) {
      // Catch unwanted exception.
      e.printStackTrace();
      System.exit(1);
    }

    if (!solution.isUnknown()) {
      reply(solution);
      finish();
      return;
    }

    core = solver.core();
    normalizer.denormalize(core);
    executor.submit(new RestartActivity(identifier(), scores, core.instance()));
    suspend();
  }

  @Override
  public void process(final Event e) throws Exception {
    Solution solution = (Solution) e.data;
    if (solution.isSatisfiable()) {
      solution = core.merge(solution);
    }

    reply(solution);
    finish();
  }
}
