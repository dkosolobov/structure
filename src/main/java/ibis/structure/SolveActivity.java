package ibis.structure;

import gnu.trove.list.array.TDoubleArrayList;
import gnu.trove.list.array.TIntArrayList;
import ibis.constellation.ActivityIdentifier;
import ibis.constellation.Event;
import org.apache.log4j.Logger;

import static ibis.structure.Misc.*;


/**
 * Performs some simplification on the instance.
 *
 * This activity is similar to SimplifyActivity, but is
 * part of the solving loop.
 */
public final class SolveActivity extends Activity {
  private static final Logger logger = Logger.getLogger(SolveActivity.class);

  /** branching literal. */
  private int branch;
  /** Core of the instance after simplification. */
  private Core core;

  /**
   * @param branch last branching literal.
   */
  public SolveActivity(final ActivityIdentifier parent,
                       final ActivityIdentifier tracer,
                       final int depth,
                       final long generation,
                       final TDoubleArrayList scores,
                       final Skeleton instance,
                       final int branch) {
    super(parent, tracer, depth, generation, scores, instance);
    this.branch = branch;
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
      SelfSubsumming.run(solver);
      PureLiterals.run(solver);
      MissingLiterals.run(solver);

      solution = solver.solve();
      solution = normalizer.denormalize(solution);
      assert !solution.isUnsatisfiable();
    } catch (ContradictionException e) {
      solution = Solution.unsatisfiable(branch);
    } catch (Exception e) {
      // Catch unwanted exception.
      e.printStackTrace();
      System.exit(1);
    }

    if (!solution.isUnknown()) {
      reply(solution);
      finish();
      return;
    }

    core = normalizer.denormalize(solver.core());
    assert filter(core.instance().formula, branch).isEmpty();


    executor.submit(new SplitActivity(
          identifier(), tracer, depth, generation, scores, core.instance()));
    suspend();
  }

  @Override
  public void process(final Event e) throws Exception {
    Solution response = (Solution) e.data;

    if (response.isSatisfiable()) {
      response = core.merge(response);
    } else if (response.isUnsatisfiable()) {
      response = Solution.unsatisfiable(branch);
    } else if (response.isUnknown()) {
      response = Solution.unknown(branch, response, core, depth < 3, depth < 2);
    }

    reply(response);
    finish();
  }

  protected void gc() {
    super.gc();
    core.gc();
  }
}
