package ibis.structure;

import org.apache.log4j.Logger;
import gnu.trove.TIntArrayList;
import ibis.constellation.Activity;
import ibis.constellation.ActivityIdentifier;
import ibis.constellation.Event;
import ibis.constellation.context.UnitActivityContext;


public final class Job extends Activity {
  private static final Logger logger = Logger.getLogger(Job.class);

  private static boolean stopSearch = false;

  private ActivityIdentifier parent;
  private int depth;
  private Skeleton instance;
  private int branch;

  private Solution solution = null;
  private int[] replies = new int[2];
  private int numReplies = 0;

  public Job(final ActivityIdentifier parent, final int depth,
             final Skeleton instance, final int branch) {
    super(UnitActivityContext.DEFAULT, true);
    this.parent = parent;
    this.depth = depth;
    this.instance = instance;
    this.branch = branch;
  }

  @Override
  public void initialize() {
    synchronized (Job.class) {
      if (stopSearch) {
        // If search has stopped ignores all jobs.
        sendReply(Solution.unknown());
        finish();
        return;
      }
    }

    try {
      Solver solver = new Solver(instance);
      if (!Configure.enableExpensiveChecks) {
        instance = null;  // helps GC
      }
      solution = solver.solve(branch);
    } catch (AssertionError e) {
      e.printStackTrace();
      // Crashes on error.
      System.exit(1);
    }

    if (solution.isSatisfiable()) {
      sendReply(solution);
      finish();
    } else if (solution.isUnsatisfiable()) {
      sendReply(solution.unsatisfiable());
      finish();
    } else {
      assert solution.isUnknown();
      executor.submit(new Job(identifier(), depth + 1,
                      solution.core(), solution.branch()));
      executor.submit(new Job(identifier(), depth + 1,
                      solution.core(), -solution.branch()));
      suspend();
    }
  }

  /**
   * Checks solution.
   */
  private void checkSolution(final int[] units) {
    if (!Configure.enableExpensiveChecks) {
      return;
    }
      
    BitSet unitsSet = new BitSet();
    for (int unit : units) {
      unitsSet.set(unit);
      if (unitsSet.get(-unit)) {
        logger.error("Contradictory units for");
        logger.error("by " + (new TIntArrayList(units)));
        logger.error("in branch " + branch);
        logger.error("and instance " + instance.toString());
        assert false;
      }
    }

    boolean satisfied = false;
    int lastClauseStart = 0;
    for (int i = 0; i < instance.clauses.size(); ++i) {
      int literal = instance.clauses.get(i);
      if (literal == 0) {
        if (!satisfied) {
          TIntArrayList clause = instance.clauses.subList(lastClauseStart, i);
          logger.error("Clause " + clause + " not satisfied");
          logger.error("by " + (new TIntArrayList(units)));
          logger.error("in branch " + branch);
          logger.error("and instance " + instance.toString());
          assert false;
        }
        lastClauseStart = i + 1;
        satisfied = false;
      }
      if (unitsSet.get(literal)) {
        satisfied = true;
      }
    }
  }

  /**
   * Sends solution to parent.
   */
  private void sendReply(Solution reply) {
    executor.send(new Event(identifier(), parent, reply));
  }

  @Override
  public void process(Event e) throws Exception {
    Solution reply = (Solution)e.data;
    if (solution.isUnknown() && reply.isSatisfiable()) {
      // Sends the solution to parent.
      solution.merge(reply);
      sendReply(solution);
      synchronized (Job.class) {
        stopSearch = true;
      }
    }

    replies[numReplies] = reply.solved();
    numReplies++;

    if (numReplies == 1) {
      // Waits for the other branch to finish.
      suspend();
      return;
    }

    /* Both branches finished */
    assert numReplies == 2;
    if (replies[0] == Solution.SATISFIABLE
        || replies[1] == Solution.SATISFIABLE) {
      // A solution was already found and sent to parent.
    } else if (replies[0] == Solution.UNSATISFIABLE
        && replies[1] == Solution.UNSATISFIABLE) {
      // Both braches returned UNSATISFIABLE so the instance is unsatifiable
      sendReply(Solution.unsatisfiable());
    } else {
      sendReply(Solution.unknown());
    }
    finish();
  }

  @Override
  public void cleanup() throws Exception {
  }

  @Override
  public void cancel() throws Exception {
  }
}
