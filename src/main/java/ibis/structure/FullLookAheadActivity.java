package ibis.structure;

import gnu.trove.list.array.TIntArrayList;
import ibis.constellation.ActivityIdentifier;
import ibis.constellation.Event;
import org.apache.log4j.Logger;


public final class FullLookAheadActivity extends Activity {
  private static final Logger logger = Logger.getLogger(FullLookAheadActivity.class);

  private int numLookAheads = 0;

  public FullLookAheadActivity(final ActivityIdentifier parent,
                               final int depth,
                               final Skeleton instance) {
    super(parent, depth, instance, false);
  }

  public void initialize() {
    double[] scores = instance.evaluateLiterals();
    int numVariables = instance.numVariables;

    // Finds higest score
    double bestScore = Double.NEGATIVE_INFINITY;
    for (int branch = 1; branch <= numVariables; branch++) {
      double score = instance.evaluateBranch(scores, branch);
      bestScore = Math.max(score, bestScore);
    }

    for (int branch = 1; branch <= numVariables; branch++) {
      double score = instance.evaluateBranch(scores, branch);
      if (score >= 0.1 * bestScore) {
        numLookAheads++;
        executor.submit(new BranchActivity(
              identifier(), 1, instance, true, branch));
      }
    }

    logger.info("Spawned " + numLookAheads + " branches");

    gc();
    suspend();
  }

  public void process(final Event e) throws Exception {
    Solution response = (Solution) e.data;
    printLearned(response.learned());

    numLookAheads--;
    if (numLookAheads == 0) {
      finish();
    } else {
      suspend();
    }
  }

  private static void printLearned(final TIntArrayList learned) {
    if (learned.isEmpty()) {
      return;
    }
    printLearned(learned, 0, new TIntArrayList());
  }

  private static int printLearned(final TIntArrayList learned,
                                  int pos,
                                  final TIntArrayList path) {
    path.add(learned.get(pos));

    int numChildren = 0;
    pos += 1;
    while (learned.get(pos) != 0) {
      numChildren += 1;
      pos = printLearned(learned, pos, path);
    }

    if (numChildren == 0) {
      logger.info("learned clause = " + path);
    }

    path.remove(path.size() - 1);
    return pos + 1;
  }
}
