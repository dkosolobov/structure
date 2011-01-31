package ibis.structure;

import java.lang.ref.WeakReference;
import org.apache.log4j.Logger;
import gnu.trove.TIntArrayList;
import ibis.constellation.Activity;
import ibis.constellation.ActivityIdentifier;
import ibis.constellation.Event;
import ibis.constellation.context.UnitActivityContext;

import java.io.FileOutputStream;
import java.io.PrintStream;

public final class Job extends Activity {
  private static final Logger logger = Logger.getLogger(Job.class);

  private ActivityIdentifier parent;
  private int depth;
  private Skeleton instance;
  private int branch;

  private Solution solution = null;
  private int numReplies = 0;
  private boolean solved = false;

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
    // logger.info("At depth " + depth + " on " + Thread.currentThread().getName());

    try {
      Solver solver = new Solver(instance);
      // instance = null;  // helps GC
      solution = solver.solve(branch);
    } catch (AssertionError e) {
      e.printStackTrace();
      // Crashes on error.
      System.exit(1);
    }

    if (solution.isSatisfiable()) {
      sendSolution(solution.solution());
      finish();
    } else if (solution.isUnsatisfiable()) {
      sendSolution(null);
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
    if (!Configure.enableExpensiveChecks)
      return;
      
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
  private void sendSolution(final int[] units) {
    if (units != null) checkSolution(units);
    executor.send(new Event(identifier(), parent, units));
  }

  // TODO: The other branch should be canceled, however
  //       canceling is not implemented in Constellation.
  @Override
  public void process(Event e) throws Exception {
    int[] reply = (int[])e.data;
    if (reply != null && !solved) {
      /* Sends the solution to parent. */
      solved = true;
      // logger.info("Received " + (new TIntArrayList(reply)));
      // logger.info("from " + solution.core());
      sendSolution(solution.solution(reply));
    }

    ++numReplies;
    if (numReplies == 1) {
      /* Waits for the other branch to finish. */
      suspend();
    } else {
      /* Both branches finished */
      assert numReplies == 2;
      if (!solved) sendSolution(null);
      finish();
    }
  }

  @Override
  public void cleanup() throws Exception {
  }

  @Override
  public void cancel() throws Exception {
  }
}
