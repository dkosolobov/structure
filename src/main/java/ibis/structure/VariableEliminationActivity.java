package ibis.structure;

import gnu.trove.list.array.TDoubleArrayList;
import gnu.trove.list.array.TIntArrayList;
import gnu.trove.set.hash.TIntHashSet;
import ibis.constellation.ActivityIdentifier;
import ibis.constellation.Event;
import org.apache.log4j.Logger;


/**
 * Performs variable elimination.
 */
public final class VariableEliminationActivity extends Activity {
  /** Object to restore a satisfiable solution. */
  private Object ve = null;
  /** Core of instance after variable elimination. */
  private Core core = null;
  /** Set of initial variables used to restore missing. */
  private TIntHashSet initial = null;

  public VariableEliminationActivity(final ActivityIdentifier parent,
                                     final ActivityIdentifier tracer,
                                     final TDoubleArrayList scores,
                                     final Skeleton instance) {
    super(parent, tracer, 0, 0, scores, instance);
    initial = instance.variables();
  }

  @Override
  public void initialize() {
    if (!Configure.ve) {
      executor.submit(new SimplifyActivity(parent, tracer, scores, instance));
      finish();
      return;
    }

    try {
      Solver solver = new Solver(instance);
      ve = VariableElimination.run(solver);

      core = solver.core();
      executor.submit(new SimplifyActivity(
            identifier(), tracer, scores, core.instance()));

      suspend();
    } catch (ContradictionException e) {
      logger.info("Contradiction in VariableElimination");
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

      // Finds missing variables and adds them to solution
      TIntArrayList units = response.units();
      for (int i = 0; i < units.size(); i++) {
        initial.remove(Misc.var(units.getQuick(i)));
      }
      for (int unit : initial.toArray()) {
        units.add(unit);
      }
    }

    reply(response);
    finish();
  }
}
