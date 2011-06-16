package ibis.structure;

import gnu.trove.list.array.TIntArrayList;
import ibis.constellation.ActivityIdentifier;
import ibis.constellation.Event;
import org.apache.log4j.Logger;

import static ibis.structure.Misc.*;

public final class BlockedClauseEliminationActivity extends Activity {
  private static final Logger logger = Logger.getLogger(
      BlockedClauseEliminationActivity.class);

  private TIntArrayList bce = null;
  private Core core = null;

  public BlockedClauseEliminationActivity(final ActivityIdentifier parent,
                                          final int depth,
                                          final Skeleton instance) {
    super(parent, depth, 0, instance);
  }

  public void initialize() {
    if (!Configure.bce) {
      executor.submit(new VariableEliminationActivity(
            parent, depth, instance));
      finish();
      return;
    }

    try {
      normalize();
      Solver solver = new Solver(instance, 0);
      bce = BlockedClauseElimination.run(solver);
      MissingLiterals.run(solver);
      core = solver.core();
    } catch (ContradictionException e) {
      reply(Solution.unsatisfiable());
      finish();
      return;
    }

    executor.submit(new VariableEliminationActivity(
          identifier(), depth, core.instance()));
    suspend();
  }

  public void process(Event e) throws Exception {
    Solution response = (Solution) e.data;
    response = core.merge(response);
    response = BlockedClauseElimination.restore(bce, response);
    reply(response);
    finish();
  }
}
