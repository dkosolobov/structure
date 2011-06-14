package ibis.structure;

import gnu.trove.set.hash.TIntHashSet;
import gnu.trove.list.array.TIntArrayList;
import ibis.constellation.ActivityIdentifier;
import ibis.constellation.Event;
import org.apache.log4j.Logger;

import static ibis.structure.Misc.*;


public final class SimplifyActivity extends Activity {
  private static final Logger logger = Logger.getLogger(SimplifyActivity.class);

  private InstanceNormalizer normalizer = new InstanceNormalizer();
  private Core core = null;

  public SimplifyActivity(final ActivityIdentifier parent,
                          final int depth,
                          final Skeleton instance) {
    super(parent, depth, 0, instance.clone());
    assert instance.size() > 0;
  }

  public void initialize() {
    try {
      normalizer.normalize(instance);
      Solver solver = new Solver(instance, 0);

      Configure.verbose = true;
      solver.propagate();
      HyperBinaryResolution.run(solver);
      HiddenTautologyElimination.run(solver);
      solver.renameEquivalentLiterals();
      SelfSubsumming.run(solver);
      PureLiterals.run(solver);
      MissingLiterals.run(solver);
      Configure.verbose = false;

      core = solver.core();
    } catch (ContradictionException e) {
      reply(Solution.unsatisfiable());
      finish();
      return;
    }

    executor.submit(new BlockedClauseEliminationActivity(
          identifier(), depth, core.instance()));
    suspend();
  }

  public void process(Event e) throws Exception {
    Solution response = (Solution) e.data;
    response = core.merge(response);
    normalizer.denormalize(response);
    reply(response);
    finish();
  }
}
