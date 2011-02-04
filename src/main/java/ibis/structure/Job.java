package ibis.structure;

import gnu.trove.TIntArrayList;
import ibis.constellation.Activity;
import ibis.constellation.ActivityIdentifier;
import ibis.constellation.context.UnitActivityContext;
import ibis.constellation.Event;
import org.apache.log4j.Logger;


public final class Job extends Activity {
  private static final Logger logger = Logger.getLogger(Job.class);

  private static boolean stopSearch = false;

  private ActivityIdentifier parent;
  private int depth;
  private Skeleton instance;
  private int branch;

  private Core core = null;
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

    Solver solver = null;
    Solution solution = null;
    try {
      solver = new Solver(instance);
      solution = solver.solve(branch);
      if (!Configure.enableExpensiveChecks) {
        instance = null;  // helps GC
      }
    } catch (AssertionError e) {
      e.printStackTrace();
      System.exit(1);
    }

    if (solution.isSatisfiable()) {
      // logger.info("found solution");
      sendReply(solution);
      finish();
    } else if (solution.isUnsatisfiable()) {
      sendReply(solution.unsatisfiable());
      finish();
    } else {
      assert solution.isUnknown();

      if (depth > 0) {
        core = solver.core();
        int branchingLiteral = solver.chooseBranchingLiteral();
        branchingLiteral = core.normalize(branchingLiteral);

        executor.submit(new Job(identifier(), depth - 1,
                        core.instance(), branchingLiteral));
        executor.submit(new Job(identifier(), depth - 1,
                        core.instance(), -branchingLiteral));
        suspend();
      } else {
        finish();
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

    assert units.length == instance.numVariables:
        "Not enough units " + units.length + " given versus "
        + instance.numVariables + " required";
    
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
          assert false: "Clause not satisfied";
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
    if (reply.isSatisfiable()) {
      checkSolution(reply.units());
    }
    executor.send(new Event(identifier(), parent, reply));
  }

  @Override
  public void process(Event e) throws Exception {
    if (branch == 0) logger.info("processing");

    Solution reply = (Solution)e.data;
    if (reply.isSatisfiable()) {
      if (numReplies == 0 || !replies[0].isSatisfiable()) {
        // Sends the solution to parent.
        synchronized (Job.class) {
          stopSearch = true;
        }

        sendReply(core.merge(reply));
      }
    }

    replies[numReplies] = reply;
    numReplies++;

    if (numReplies == 1) {
      // Waits for the other branch to finish.
      suspend();
      return;
    }

    // Both branches finished
    assert numReplies == 2;
    if (replies[0].isSatisfiable() || replies[1].isSatisfiable()) {
      // A solution was already found and sent to parent.
    } else if (replies[0].isUnsatisfiable() && replies[1].isUnsatisfiable()) {
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
