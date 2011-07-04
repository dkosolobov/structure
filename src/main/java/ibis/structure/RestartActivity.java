package ibis.structure;

import java.util.Timer;
import java.util.TimerTask;
import java.util.Random;
import gnu.trove.list.array.TDoubleArrayList;
import gnu.trove.list.array.TIntArrayList;
import gnu.trove.set.hash.TIntHashSet;
import ibis.constellation.ActivityIdentifier;
import ibis.constellation.Event;
import org.apache.log4j.Logger;

import static ibis.structure.Misc.*;

/**
 * Implements restarting strategy.
 *
 * Restarting loop contains a few preprocessing activities:
 * BCE, VE, simplification.
 *
 * Each solved instance is part of a generation. When a timer
 * expires is generation killed (see BlackHoleActivity).
 *
 * When a generation is finished RestartActivity
 * adds to formula learned clauses.
 *
 * KNOWN BUGS: learned clauses "enforce" the variable ordering
 * for the future generations.
 */
public final class RestartActivity extends Activity {
  private static final int INITIAL_TTL = 10;
  private static final int EXTRA_TTL = 5;
  private static final int MAX_TTL = 1000000;
  private static final int DEPTH = 0;

  private static final Logger logger = Logger.getLogger(RestartActivity.class);
  private static final Random random = new Random(1);

  /** Current generation time to live. */
  private static int ttl = INITIAL_TTL;  // TODO: should not be static
  /** Starting time. */
  private long startTime;

  public RestartActivity(final ActivityIdentifier parent,
                         final TDoubleArrayList scores,
                         final Skeleton instance) {
    super(parent, 0, guid(), scores, instance);

    sortBinaries();
  }

  /**
   * Returns a unique random long non-zero number.
   *
   * @return an unique id.
   */
  private static long guid() {
    long generation;
    do {
      generation = random.nextLong();
    } while (generation == 0);
    return generation;
  }

  /** Moves binaries (as dominator tree) at the begining of clauses. */
  private void sortBinaries() {
    ClauseIterator it;
    ImplicationsGraph graph = new ImplicationsGraph(instance.numVariables);

    it = new ClauseIterator(instance.formula);
    while (it.hasNext()) {
      int clause = it.next();
      int length = length(instance.formula, clause);

      if (length == 2) {
        int u = instance.formula.getQuick(clause);
        int v = instance.formula.getQuick(clause + 1);
        graph.add(neg(u), v);
      }
    }

    TIntArrayList formula = new TIntArrayList();
    int[] sort = graph.topologicalSort();
    for (int i = 0; i < sort.length; i++) {
      if (sort[i] == 0) {
        continue;
      }

      TIntArrayList edges = graph.edges(sort[i]);
      for (int j = 0; j < edges.size(); j++) {
        int literal = edges.getQuick(j);
        if (neg(sort[i]) > literal) {
          formula.add(encode(2, OR));
          formula.add(neg(sort[i]));
          formula.add(literal);
        }
      }
    }

    it = new ClauseIterator(instance.formula);
    while (it.hasNext()) {
      int clause = it.next();
      int length = length(instance.formula, clause);
      if (length != 2) {
        copy(formula, instance.formula, clause);
      }
    }

    instance.formula = formula;
  }

  @Override
  public void initialize() {
    if (scores == null) {
      double[] tmp = new double[instance.numVariables + 1];
      java.util.Arrays.fill(tmp, 0.5);
      scores = new TDoubleArrayList(tmp);
    }

    startTime = System.currentTimeMillis();

    TIntHashSet tmp = new TIntHashSet();
    ClauseIterator it = new ClauseIterator(instance.formula);
    while (it.hasNext()) {
      int clause = it.next();
      int length = length(instance.formula, clause);
      for (int i = clause; i < clause + length; i++) {
        tmp.add(var(instance.formula.getQuick(i)));
      }
    }

    logger.info("Spawning " + generation + " for " + ttl + " seconds");
    logger.info("Instance has " + tmp.size() + " / " + instance.formula.size());

    SolveActivity.histogram = new TIntArrayList(new int[100]);

    executor.submit(new LookAheadActivity(
          identifier(), generation, scores, instance.clone()));

    if (ttl < MAX_TTL) {
      TimerTask task = new TimerTask() {
        public void run() {
          BlackHoleActivity.moveToGraveyard(generation);
        }
      };

      (new Timer()).schedule(task, ttl * 1000L);
      ttl += EXTRA_TTL;
    }

    suspend();
  }

  @Override
  public void process(final Event e) throws Exception {
    Solution response = (Solution) e.data;
    if (!response.isUnknown()) {
      reply(response);
      finish();
      return;
    }

    long endTime = System.currentTimeMillis();
    logger.info("Ran for " + (endTime - startTime) / 1000. + " seconds");
    logger.info("Histogram is " + SolveActivity.histogram);

    response.addLearnedClauses(instance.formula, 1000);
    executor.submit(new BlockedClauseEliminationActivity(
          identifier(), scores, instance));
    suspend();
  }
}
