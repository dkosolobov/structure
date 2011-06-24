package ibis.structure;

import gnu.trove.list.array.TDoubleArrayList;
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
   * @param branch branching literal.
   */
  public SolveActivity(final ActivityIdentifier parent,
                       final int depth,
                       final long generation,
                       final TDoubleArrayList scores,
                       final Skeleton instance,
                       final int branch) {
    super(parent, depth, generation, scores, addBranch(instance, branch));
    this.branch = branch;
  }

  /** Adds a branch to instance as an unit clause. */
  private static Skeleton addBranch(final Skeleton instance,
                                    final int branch) {
    instance.formula.add(encode(1, OR));
    instance.formula.add(branch);
    return instance;
  }

  @Override
  public void initialize() {
    Solver solver = null;
    Solution solution = null;

    try {
      normalizer.normalize(instance);
      solver = new Solver(instance);

      solver.propagate();
      HyperBinaryResolution.run(solver);
      HiddenTautologyElimination.run(solver);
      solver.renameEquivalentLiterals();
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
    executor.submit(new SplitActivity(
          identifier(), depth, generation, scores, core.instance()));
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
      response = Solution.unknown(branch, response, core);
    }

    reply(response);
    finish();
  }

  protected void gc() {
    super.gc();
    core.gc();
  }
}
