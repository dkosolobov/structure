package ibis.structure;

import ibis.constellation.ActivityIdentifier;
import ibis.constellation.Event;
import org.apache.log4j.Logger;


/**
 * Performs variable elimination.
 */
public final class VariableEliminationActivity extends Activity {
  private static final Logger logger = Logger.getLogger(
      VariableEliminationActivity.class);

  /** Object to restore a satisfiable solution. */
  private Object ve = null;
  /** Core of instance after variable elimination. */
  private Core core = null;

  public VariableEliminationActivity(final ActivityIdentifier parent,
                                     final int depth,
                                     final Skeleton instance) {
    super(parent, depth, 0, instance);
    assert instance.size() > 0;
  }

  @Override
  public void initialize() {
    if (!Configure.ve) {
      executor.submit(new SimplifyActivity(parent, depth, instance));
      finish();
      return;
    }

    try {
      // normalizer.normalize(instance);
      Solver solver = new Solver(instance, 0);

      solver.propagateBinaries();
      ve = VariableElimination.run(solver);
      solver.propagateUnits();
      MissingLiterals.run(solver);
      solver.verifyIntegrity();

      core = solver.core();
      executor.submit(new SimplifyActivity(
            identifier(), depth, core.instance()));

      gc();
      suspend();
    } catch (ContradictionException e) {
      reply(Solution.unsatisfiable());
      finish();
    }
  }

  @Override
  public void process(final Event e) throws Exception {
    Solution response = (Solution) e.data;
    response = core.merge(response);
    response = VariableElimination.restore(ve, response);
    // normalizer.denormalize(response);
    reply(response);
    finish();
  }
}
