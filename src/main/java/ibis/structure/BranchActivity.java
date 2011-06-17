package ibis.structure;

import ibis.constellation.ActivityIdentifier;
import ibis.constellation.Event;
import org.apache.log4j.Logger;

import static ibis.structure.Misc.*;

/**
 * An activity to branch on a given literal.
 *
 * It duplicates the instance and generates two new activities:
 *
 * <ol>
 * <li>instance or branch</li>
 * <li>instance or -branch</li>
 * </ol>
 *
 * If any child instance is satisfiable instance is satisfiable,
 * otherwse instance is unsatisfiable.
 */
public final class BranchActivity extends Activity {
  private static final Logger logger = Logger.getLogger(BranchActivity.class);

  /** Branching literal. */
  private int branch;
  /** Responses from branches. */
  private Solution[] responses = new Solution[2];
  /** Number of responses received. */
  private int numReplies = 0;

  /**
   * @param branch branching literal.
   */
  public BranchActivity(final ActivityIdentifier parent,
                        final int depth,
                        final long generation,
                        final Skeleton instance,
                        final int branch) {
    super(parent, depth, generation, instance);
    this.branch = branch;

    assert instance.size() > 0;
  }

  @Override
  public void initialize() {
    Skeleton copy1 = instance;
    Skeleton copy2 = instance.clone();

    executor.submit(new SolveActivity(
          identifier(), depth - 1, generation, copy1, branch));
    executor.submit(new SolveActivity(
          identifier(), depth - 1, generation, copy2, neg(branch)));

    suspend();
  }

  @Override
  public void process(final Event e) throws Exception {
    Solution response = (Solution) e.data;
    if (response.isSatisfiable()) {
      if (numReplies == 0 || !responses[0].isSatisfiable()) {
        // Sends the solution to parent.
        reply(response);
      }
    }

    responses[numReplies] = response;
    numReplies++;

    if (numReplies == 1) {
      // Waits for the other branch to finish.
      suspend();
      return;
    }

    // Both branches finished.
    // A satisfiable solution, if any, was already found and sent to parent.
    assert numReplies == 2;
    if (responses[0].isUnsatisfiable() && responses[1].isUnsatisfiable()) {
      // Both braches returned UNSATISFIABLE so the instance is unsatifiable
      reply(Solution.unsatisfiable());
    } else if (!responses[0].isSatisfiable() && !responses[1].isSatisfiable()) {
      reply(Solution.unknown(responses[0], responses[1]));
    }
    finish();
  }
}
