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

  private static int maxDepth = Integer.MIN_VALUE;
  private static int minDepth = Integer.MAX_VALUE;;
  private static int numSolves = 0;

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

  static {
    Runtime.getRuntime().addShutdownHook(new Thread() {
      public void run() {
        try {
          FileOutputStream fos = new FileOutputStream("numSolves");
          PrintStream dos = new PrintStream(fos);

          dos.println(numSolves);
          dos.close();
        } catch (Exception e) {
        }
      }
    });
  }

  @Override
  public void initialize() {
    synchronized (Job.class) {
      ++numSolves;
      if (maxDepth < depth) {
        maxDepth = depth;
      }
    }
    logger.info("At depth " + depth + " (" + minDepth + "/" + maxDepth + ")"
                + " on " + Thread.currentThread().getName());

    Solver solver = new Solver(instance, branch);
    instance = null;  // helps GC
    solution = solver.solve();

    if (solution.isSatisfiable()) {
      int[] units = solution.solution();
      executor.send(new Event(identifier(), parent, units));
      finish();
    } else if (solution.isUnsatisfiable()) {
      executor.send(new Event(identifier(), parent, null));
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
