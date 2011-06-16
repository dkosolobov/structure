package ibis.structure;

import gnu.trove.list.array.TIntArrayList;
import ibis.constellation.ActivityIdentifier;
import ibis.constellation.Event;
import org.apache.log4j.Logger;

import static ibis.structure.Misc.*;


/**
 * Performs variable elimination.
 *
 * TODO: Handle case when all variables are eliminated.
 * TODO: Add disable option.
 */
public final class VariableEliminationActivity extends Activity {
  private static final Logger logger = Logger.getLogger(
      VariableEliminationActivity.class);

  private Object ve = null;
  private Core core = null;

  public VariableEliminationActivity(final ActivityIdentifier parent,
                                     final int depth,
                                     final Skeleton instance) {
    super(parent, depth, 0, instance);
    assert instance.size() > 0;
  }

  public void initialize() {
    if (!Configure.ve) {
      executor.submit(new SimplifyActivity(
            parent, depth, instance));
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

  public void process(Event e) throws Exception {
    Solution response = (Solution) e.data;
    response = core.merge(response);
    response = VariableElimination.restore(ve, response);
    // normalizer.denormalize(response);
    reply(response);
    finish();
  }
}
