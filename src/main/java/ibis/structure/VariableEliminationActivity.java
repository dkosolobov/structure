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
                                     final ActivityIdentifier tracer,
                                     final TDoubleArrayList scores,
                                     final Skeleton instance) {
    super(parent, tracer, 0, 0, scores, instance);
  }

  @Override
  public void initialize() {
    if (!Configure.ve) {
      executor.submit(new SimplifyActivity(
            parent, tracer, scores, instance));
      finish();
      return;
    }

    try {
      Solver solver = new Solver(instance);
      ve = VariableElimination.run(solver);
      solver.verifyIntegrity();

      core = solver.core();
      executor.submit(new SimplifyActivity(
            identifier(), tracer, scores, core.instance()));

      gc();
      suspend();
    } catch (ContradictionException e) {
      logger.info("Contradiction in VE");
      e.printStackTrace();
      reply(Solution.unsatisfiable());
      finish();
    }
  }

  @Override
  public void process(final Event e) throws Exception {
    Solution response = (Solution) e.data;
    if (response.isSatisfiable()) {
      response = core.merge(response);
      response = VariableElimination.restore(ve, response);
    }

    reply(response);
    finish();
  }
}
