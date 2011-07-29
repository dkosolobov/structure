package ibis.structure;

import ibis.constellation.ActivityIdentifier;
import ibis.constellation.ActivityIdentifier;
import ibis.constellation.context.UnitActivityContext;
import ibis.constellation.Event;
import ibis.constellation.Executor;
import org.apache.log4j.Logger;

/**
 * Starts preprocesses the input instance.
 */
public final class TracerSlave extends ibis.constellation.Activity {
  protected static final Logger logger = Logger.getLogger(TracerSlave.class);

  public static ActivityIdentifier master = null;
  public static ActivityIdentifier slave = null;
  public static boolean created = false;

  public TracerSlave() {
    super(new UnitActivityContext(Configure.localContext), true, true);
  }

  public static void registerSlave(final ActivityIdentifier master_) {
    if (slave == null) {
      synchronized (TracerSlave.class) {
        if (slave == null && !created) {
          created = true;
          master = master_;
          Configure.localExecutor.submit(new TracerSlave());
        }
      }
    }
  }

  @Override
  public void initialize() throws Exception {
    synchronized (TracerSlave.class) {
      slave = this.identifier();
      TracerMaster.registerSlave(master, slave);
    }

    suspend();
  }

  @Override
  public void process(final Event e) throws Exception {
    long generation = (Long) e.data;

    if (generation == 0) {
      logger.info(slave + " received stop call");
      executor.send(new Event(
            slave, master, new TracerMaster.TracerStop(slave)));
      finish();
    } else {
      logger.info(identifier() + " received dead call for " + generation);
      BlackHoleActivity.killGeneration(generation);
      suspend();
    }
  }

  @Override
  public void cleanup() throws Exception {
  }

  @Override
  public void cancel() throws Exception {
  }
}
