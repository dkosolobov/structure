package ibis.structure;

import gnu.trove.list.array.TDoubleArrayList;
import ibis.constellation.ActivityIdentifier;
import ibis.constellation.Event;
import org.apache.log4j.Logger;

/**
 * Starts preprocesses the input instance.
 */
public final class PreprocessActivity extends Activity {
  /** Core after simplification. */
  private Core core = null;

  public PreprocessActivity(final ActivityIdentifier parent,
                            final ActivityIdentifier tracer,
                            final Skeleton instance) {
    super(parent, tracer, 0, 0, null, instance);
  }

  @Override
  public void initialize() throws Exception {
    Solver solver = null;
    Solution solution = null;
    Normalizer normalizer = new Normalizer();

    try {
      normalizer.normalize(instance);
      solver = new Solver(instance);

      solver.propagate();
      HyperBinaryResolution.run(solver);
      HiddenTautologyElimination.run(solver);
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
    executor.submit(new XORActivity(identifier(), tracer, core.instance()));
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
