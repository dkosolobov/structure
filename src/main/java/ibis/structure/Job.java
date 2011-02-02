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
  private Solution[] replies = new Solution[2];
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
      solution.learned.add(-branch);
      solution.learned.add(0);
      sendReply(solution);
      finish();
    } else {
      assert solution.isBranch();
      if (depth > 0) {
        executor.submit(new Job(identifier(), depth - 1,
                        solution.core(), solution.branch()));
        executor.submit(new Job(identifier(), depth - 1,
                        solution.core(), -solution.branch()));
        suspend();
      } else {
        sendReply(Solution.unknown());
        suspend();
      }
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
    // logger.info("Learned = " + reply.learned);
    executor.send(new Event(identifier(), parent, reply));
  }

  @Override
  public void process(Event e) throws Exception {
    Solution reply = (Solution)e.data;
    replies[numReplies++] = reply;

    if (solution.isBranch() && reply.isSatisfiable()) {
      // Sends the solution to parent.
      solution.merge(reply);
      sendReply(solution);
      synchronized (Job.class) {
        stopSearch = true;
      }
    }

    if (numReplies == 1) {
      // Waits for the other branch to finish.
      suspend();
      return;
    }

    /* Both branches finished */
    assert numReplies == 2;
    if (replies[0].isSatisfiable() || replies[1].isSatisfiable()) {
      // A solution was already found and sent to parent.
    } else if (replies[0].isUnsatisfiable() && replies[1].isUnsatisfiable()) {
      // Both braches returned UNSATISFIABLE so the instance is unsatifiable
      reply = Solution.unsatisfiable();
      reply.learned.add(-branch);
      reply.learned.add(0);
      sendReply(reply);
    } else {
      reply = solution.unknown();

      if (!replies[0].learned.isEmpty() || !replies[1].learned.isEmpty()) {
        reply.learned.add(-branch);

        for (int i = 0; i < 2; ++i) {
          int[] tmp = replies[i].learned.toNativeArray();
          solution.denormalize(tmp, 0, tmp.length);

          if (replies[1 - i].isUnsatisfiable()) {
            if (tmp.length > 0) {
              reply.learned.add(tmp, 1, tmp.length - 2);
              // reply.learned.add(tmp);
            }
          } else {
            reply.learned.add(tmp);
          }
        }

        reply.learned.add(0);
      }

      sendReply(reply);
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
