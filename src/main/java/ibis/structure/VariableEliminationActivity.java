package ibis.structure;

import gnu.trove.list.array.TDoubleArrayList;
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
                                     final TDoubleArrayList scores,
                                     final Skeleton instance) {
    super(parent, 0, 0, scores, instance);
  }

  @Override
  public void initialize() {
    if (!Configure.ve) {
      executor.submit(new SimplifyActivity(parent, scores, instance));
      finish();
      return;
    }

    try {
      Solver solver = new Solver(instance, 0);
      ve = VariableElimination.run(solver);
      solver.propagateUnits();
      MissingLiterals.run(solver);
      solver.verifyIntegrity();

      core = solver.core();
      executor.submit(new SimplifyActivity(
            identifier(), scores, core.instance()));

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
    reply(response);
    finish();
  }
}
