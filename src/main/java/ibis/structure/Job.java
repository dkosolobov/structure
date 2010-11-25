package ibis.structure;

import java.lang.ref.WeakReference;
import org.apache.log4j.Logger;
import gnu.trove.TIntArrayList;
import ibis.constellation.Activity;
import ibis.constellation.ActivityIdentifier;
import ibis.constellation.Event;
import ibis.constellation.context.UnitActivityContext;

public final class Job extends Activity {
  private static final Logger logger = Logger.getLogger(Job.class);

  private static int maxDepth = Integer.MIN_VALUE;
  private static int minDepth = Integer.MAX_VALUE;;

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
    try {
      logger.info("Solving " + instance.clauses.size()
                  + " literals branching on " + branch 
                  + " at depth " + depth
                  + " (" + minDepth + "/" + maxDepth + ")"
                  + " on " + Thread.currentThread().getName());
      synchronized (Job.class) {
        if (maxDepth < depth) {
          maxDepth = depth;
        }
      }

      Solver solver = new Solver(instance, branch);
      instance = null;  // helps GC
      solution = solver.solve();

      if (solution.satisfied()) {
        int[] units = solution.solution(null);
        executor.send(new Event(identifier(), parent, units));
        finish();
      } else {
        executor.submit(new Job(identifier(), depth + 1,
                        solution.core(), solution.branch()));
        executor.submit(new Job(identifier(), depth + 1,
                        solution.core(), -solution.branch()));
        suspend();
      }
    } catch (ContradictionException e) {
      // logger.debug("Instance is a contradiction");
      executor.send(new Event(identifier(), parent, null));
      finish();
    } catch (Exception e) {
      logger.info("Unhandled error", e);
    }
  }

  @Override
  public void process(Event e) throws Exception {
    int[] reply = (int[])e.data;
    if (reply != null && !solved) {
      /* Sends the solution to parent. */
      solved = true;
      int[] units = solution.solution(reply);
      executor.send(new Event(identifier(), parent, units));
      cancel();
    }

    // TODO: The other branch should be canceled, but canceling is not
    // implemented in Constellation.
    ++numReplies;
    if (numReplies == 1) {
      /* Waits for the other branch to finish. */
      suspend();
    } else {
      assert numReplies == 2;
      if (!solved) {
        synchronized (Job.class) {
          if (minDepth > depth) {
            minDepth = depth;
          }
        }
        executor.send(new Event(identifier(), parent, null));
      }
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
