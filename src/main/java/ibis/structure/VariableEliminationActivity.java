package ibis.structure;

import java.util.Random;
import gnu.trove.list.array.TIntArrayList;
import gnu.trove.set.hash.TIntHashSet;
import ibis.constellation.ActivityIdentifier;
import ibis.constellation.Event;
import org.apache.log4j.Logger;

import static ibis.structure.Misc.*;


public final class VariableEliminationActivity extends Activity {
  private static final Logger logger = Logger.getLogger(VariableEliminationActivity.class);
  private static final Random random = new Random(1);

  private InstanceNormalizer normalizer = new InstanceNormalizer();
  private Object ve = null;
  private Core core = null;

  public VariableEliminationActivity(final ActivityIdentifier parent,
                              final int depth,
                              final Skeleton instance) {
    super(parent, depth, instance);
    assert instance.size() > 0;
  }

  public void initialize() {
    try {
      normalizer.normalize(instance);

      Solver solver = new Solver(instance, 0);
      ve = VariableElimination.run(solver);
      SelfSubsumming.run(solver);
      MissingLiterals.run(solver);
      solver.verifyIntegrity();

      core = solver.core();
      executor.submit(new SplitActivity(identifier(), depth, core.instance()));

      gc();
      suspend();
    } catch (ContradictionException e) {
      reply(Solution.unsatisfiable());
      finish();
    }
  }

  public void process(Event e) throws Exception {
    Solution response = (Solution) e.data;

    if (response.isSatisfiable()) {
      response = core.merge(response);
      response = VariableElimination.restore(ve, response);
      normalizer.denormalize(response);
    }

    reply(response);
    finish();
  }
}
