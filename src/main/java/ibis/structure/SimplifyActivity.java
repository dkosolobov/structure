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
  /** Core after simplification. */
  private Core core = null;

  public SimplifyActivity(final ActivityIdentifier parent,
                          final ActivityIdentifier tracer,
                          final TDoubleArrayList scores,
                          final Skeleton instance) {
    super(parent, tracer, 0, 0, scores, instance.clone());
  }

  @Override
  public void initialize() {
    Solver solver = null;
    Solution solution = null;
    Normalizer normalizer = new Normalizer();

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
    }

    if (!solution.isUnknown()) {
      reply(solution);
      finish();
      return;
    }

    core = solver.core();
    normalizer.denormalize(core);
    executor.submit(new LookAheadActivity(
          identifier(), tracer, scores, core.instance()));
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
