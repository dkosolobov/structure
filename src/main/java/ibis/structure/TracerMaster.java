package ibis.structure;

import java.util.Vector;
import gnu.trove.list.array.TLongArrayList;
import ibis.constellation.ActivityIdentifier;
import ibis.constellation.SingleEventCollector;
import ibis.constellation.Constellation;
import ibis.constellation.context.UnitActivityContext;
import ibis.constellation.Event;
import ibis.constellation.Executor;
import org.apache.log4j.Logger;

/**
 * Starts preprocesses the input instance.
 */
public final class TracerMaster extends ibis.constellation.Activity {
  protected static final Logger logger = Logger.getLogger(TracerMaster.class);

  private static class TracerRegisterSlave implements java.io.Serializable {
    public final ActivityIdentifier slave;

    public TracerRegisterSlave(final ActivityIdentifier slave) {
      this.slave = slave;
    }
  }

  private static class TracerKillGeneration implements java.io.Serializable {
    public final long generation;

    public TracerKillGeneration(final long generation) {
      this.generation = generation;
    }
  }

  public static class TracerStop implements java.io.Serializable {
    public final ActivityIdentifier slave;

    public TracerStop(final ActivityIdentifier slave) {
      this.slave = slave;
    }

    public TracerStop() {
      this.slave = null;
    }
  }

  public static ActivityIdentifier master = null;
  private static boolean initialized = false;

  private TLongArrayList killed = null;
  private Vector<ActivityIdentifier> slaves = null;

  private int deadReceived = 0;
  private ActivityIdentifier deadSource = null;


  /** Constructor. */
  public TracerMaster() {
    super(new UnitActivityContext(Configure.localContext), true, true);

    killed = new TLongArrayList();
    slaves = new Vector<ActivityIdentifier>();
  }

  public static void killGeneration(final Executor executor,
                                    final ActivityIdentifier master,
                                    final long generation) {
    BlackHoleActivity.killGeneration(generation);
    executor.send(new Event(
          null, master, new TracerKillGeneration(generation)));
  }

  public static void registerSlave(final ActivityIdentifier master,
                                   final ActivityIdentifier slave) {
    Configure.localExecutor.send(new Event(
          slave, master, new TracerRegisterSlave(slave)));
  }

  public static void create() {
    assert master == null;
    TracerMaster activity = new TracerMaster();
    Configure.localExecutor.submit(activity);

    synchronized (activity) {
      while (!initialized) {
        try {
          activity.wait();
        } catch (InterruptedException e) {
          /* Ignored */
        }
      }
    }
  }

  public static void stop() {
    SingleEventCollector root = new SingleEventCollector(
        new UnitActivityContext(Configure.localContext));
    Configure.localExecutor.submit(root);

    assert master != null;
    Configure.localExecutor.send(new Event(
          root.identifier(), master, new TracerStop()));
    root.waitForEvent();

    logger.info(master + " stopped process");
  }

  @Override
  public synchronized void initialize() throws Exception {
    master = this.identifier();
    initialized = true;
    notifyAll();
    suspend();
  }

  @Override
  public void process(final Event e) throws Exception {
    if (e.data instanceof TracerRegisterSlave) {
      ActivityIdentifier slave = ((TracerRegisterSlave) e.data).slave;
      synchronized (this) {
        slaves.add(slave);
        logger.info(identifier() + " has slaves " + slaves);
        for (int i = 0; i < killed.size(); i++) {
          executor.send(new Event(master, slave, killed.get(i)));
        }
      }

      logger.info(identifier() + " registerd slave " + slave);
      suspend();
    } else if (e.data instanceof TracerKillGeneration) {
      long generation = ((TracerKillGeneration) e.data).generation;

      synchronized (this) {
        if (!killed.contains(generation)) {
          logger.info(master + " ending generation " + generation
                      + " for " + slaves.size() + " slaves.");
          killed.add(generation);
          for (int i = 0; i < slaves.size(); i++) {
            executor.send(new Event(master, slaves.get(i), generation));
          }
        }
      }
      suspend();
    } else if (e.data instanceof TracerStop) {
      TracerStop stop = (TracerStop) e.data;

      if (stop.slave == null) {
        logger.info(master + " stopping process for " + slaves);
        synchronized (this) {
          killed.add(0);
          deadSource = e.source;
          for (int i = 0; i < slaves.size(); i++) {
            logger.info("Sending 0 to " + slaves.get(i));
            executor.send(new Event(master, slaves.get(i), 0L));
          }
        }
      } else {
        deadReceived++;
      }

      logger.info("Received " + deadReceived
                  + " confirmations out of " + slaves.size());
      if (deadReceived == slaves.size()) {
        executor.send(new Event(master, deadSource, new Object()));
        finish();
      } else {
        suspend();
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
