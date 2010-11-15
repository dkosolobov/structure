package ibis.structure;

import org.apache.log4j.Logger;
import gnu.trove.TIntArrayList;
import ibis.constellation.Activity;
import ibis.constellation.ActivityIdentifier;
import ibis.constellation.Event;
import ibis.constellation.context.UnitActivityContext;

public class Job extends Activity {
  private static final Logger logger = Logger.getLogger(Job.class);

  private final ActivityIdentifier parent;
  private final Skeleton instance;
  private final int branch;
  private int numReplies = 0;
  private boolean solved = false;
  private TIntArrayList units;

  public Job(ActivityIdentifier parent, Skeleton instance, int branch) {
    super(UnitActivityContext.DEFAULT, true);
    this.parent = parent;
    this.instance = instance;
    this.branch = branch;
  }
    
  @Override
  public void initialize() {
    logger.debug("Branching on " + branch);
    logger.debug("Instance difficulty " + instance.difficulty());

    try {
      Solver solver = new Solver(instance, branch);
      int literal = solver.lookahead();
      units = solver.getAllUnits();

      if (literal == 0) {
        executor.send(new Event(identifier(), parent, units.toNativeArray()));
        finish();
      } else {
        Skeleton skeleton = solver.skeleton(false);
        executor.submit(new Job(identifier(), skeleton, literal));
        executor.submit(new Job(identifier(), skeleton, -literal));
        suspend();
      }
    } catch (ContradictionException e) {
      executor.send(new Event(identifier(), parent, null));
      finish();
    }
  }

  @Override
  public void process(Event e) throws Exception {
    int[] reply = (int[])e.data;
    if (reply != null && !solved) {
      // Sends to parent the solution.
      solved = true;
      units.add(reply);
      executor.send(new Event(identifier(), parent, units.toNativeArray()));
    }

    // TODO: The other branch should be canceled, but canceling is not
    // implemented in Constellation.
    ++numReplies;
    if (numReplies == 1) {
      // Waits for the other branch to finish.
      suspend();
    } else {
      assert numReplies == 2;
      if (!solved) {
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
