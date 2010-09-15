package ibis.structure;

import org.apache.log4j.Logger;
import gnu.trove.TIntArrayList;
import ibis.cohort.Activity;
import ibis.cohort.ActivityIdentifier;
import ibis.cohort.Event;
import ibis.cohort.MessageEvent;
import ibis.cohort.context.UnitActivityContext;

public class Job extends Activity {
  private static final Logger logger = Logger.getLogger(Job.class);

  private final ActivityIdentifier parent;
  private final Skeleton instance;
  private final int branch;
  private boolean firstReply = false;
  private boolean solved = false;
  private TIntArrayList units;

  public Job(ActivityIdentifier parent, Skeleton instance, int branch) {
    super(UnitActivityContext.DEFAULT);
    this.parent = parent;
    this.instance = instance;
    this.branch = branch;
  }
    
  @Override
  public void initialize() {
    logger.info("Branching on " + branch);
    logger.info("Instance difficulty " + instance.difficulty());

    try {
      Solver solver = new Solver(instance, branch);
      int literal = solver.lookahead();
      units = solver.getAllUnits();

      if (literal == 0) {
        cohort.send(identifier(), parent, units.toNativeArray());
        finish();
      } else {
        Skeleton skeleton = solver.skeleton(false);
        cohort.submit(new Job(identifier(), skeleton, literal));
        cohort.submit(new Job(identifier(), skeleton, -literal));
        suspend();
      }
    } catch (ContradictionException e) {
      cohort.send(identifier(), parent, null);
      finish();
    }
  }

  @Override
  public void process(Event e) throws Exception {
    int[] reply = ((MessageEvent<int[]>)e).message;
    firstReply = !firstReply;

    if (reply == null) {
      if (firstReply) {
        // Waits for the other branch to finish.
        suspend();
      } else {
        cohort.send(identifier(), parent, null);
        finish();
      }
    } else {
      // TODO: The other branch should be canceled.
      if (!solved) {
        solved = true;
        units.add(reply);
        cohort.send(identifier(), parent, units.toNativeArray());
      }
      if (!firstReply) {
        finish();
      }
    }
  }

  @Override
  public void cleanup() throws Exception {
  }

  @Override
  public void cancel() throws Exception {
  }
}
