package ibis.structure;

import java.util.concurrent.Semaphore;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import gnu.trove.TIntArrayList;
import gnu.trove.TIntIntIterator;

import static ibis.structure.Misc.*;


/**
 * Performes hyper-binary resolution.
 *
 * If (a1 + ... ak + b) and (l &ge; -a1) ... (l &ge; -ak)
 * then l &ge; b, otherwise if l then clause is contradiction
 */
public final class HyperBinaryResolution {
  /** Pool of executors for parallelization. */
  private static  ExecutorService pool = null;

  /** Creates the pool of executors */
  public static void createThreadPool() {
    pool = Executors.newFixedThreadPool(Configure.numExecutors);
  }

  /** Performs hyper-binary resolution */
  public static boolean run(final Solver solver) throws ContradictionException {
    Semaphore semaphore = new Semaphore(0);
    TIntArrayList units = new TIntArrayList();
    TIntArrayList binaries = new TIntArrayList();
    ClauseIterator iterator = new ClauseIterator(solver.watchLists.formula);

    // Runs numThreads HyperBinaryResolution simultaneously.
    int numThreads = solver.watchLists.formula().size() / 50000;
    numThreads = Math.max(numThreads, 1);
    numThreads = Math.min(numThreads, Configure.numExecutors);
    for (int i = 1; i < numThreads; i++) {
      pool.execute(new HyperBinaryResolutionRunnable(
            solver, units, binaries, iterator, semaphore));
    }
    HyperBinaryResolutionRunnable local = new HyperBinaryResolutionRunnable(
        solver, units, binaries, iterator, semaphore);
    local.run();
    try {
      semaphore.acquire(numThreads);
    } catch (InterruptedException e) {
      // ignored
    }

    // Adds discovered units.
    for (int i = 0; i < units.size(); i++) {
      solver.queueUnit(units.get(i));
    }

    // Filters some duplicate binaries and adds remaining to solver
    int[] last = new int[2 * solver.numVariables + 1];
    for (int i = 0; i < binaries.size(); i += 2) {
      int u = binaries.get(i), u_ = u + solver.numVariables;
      int v = binaries.get(i + 1), v_ = v + solver.numVariables;
      if (last[u_] != v && last[v_] != u) {
        // Binary was not found in cached
        last[u_] = v;
        solver.addBinary(u, v);
      }
    }

    int numUnits = units.size();
    int numBinaries = binaries.size() / 2;
    if (Configure.verbose) {
      if (numUnits > 0) {
        System.err.print("hu" + numUnits + ".");
      }
      if (numBinaries > 0) {
        System.err.print("hb" + numBinaries + ".");
      }
    }
    return numUnits > 0 || numBinaries > 0;
  }
}

/**
 * Does hyper binary resolution.
 */
class HyperBinaryResolutionRunnable implements Runnable {
  private static final int CACHE_SIZE = 1 << 10;

  /** Solver on which algorithms is applied */
  private Solver solver;
  /** A semaphore to signal end of computation */
  private Semaphore semaphore;
  /** A vector to store generated units. */
  private TIntArrayList units;
  /** A vector to store generated binaries. */
  private TIntArrayList binaries;
  /** An iterator over clauses. */
  private ClauseIterator iterator;
  /** Space for cache */
  private int[] cache;

  public HyperBinaryResolutionRunnable(final Solver solver,
                                       final TIntArrayList units,
                                       final TIntArrayList binaries,
                                       final ClauseIterator iterator,
                                       final Semaphore semaphore) {
    this.solver = solver;
    this.units = units;
    this.binaries = binaries;
    this.iterator = iterator;
    this.semaphore = semaphore;
    this.cache = new int[CACHE_SIZE * 2];
  }

  public void run() {
    int[] counts = new int[2 * solver.numVariables + 1];
    int[] sums = new int[2 * solver.numVariables + 1];
    int[] touched = new int[2 * solver.numVariables + 1];
    TouchSet twice = new TouchSet(solver.numVariables);
    TIntArrayList formula = solver.watchLists.formula();

    while (true) {
      int clause;
      synchronized (iterator) {
        if (!iterator.hasNext()) {
          break;
        }
        clause = iterator.next();
      }
      if (type(formula, clause) != OR) {
        continue;
      }
      
      int length = length(formula, clause);
      int numLiterals = 0;
      int clauseSum = 0;
      int numTouched = 0;

      for (int i = clause; i < clause + length; i++) {
        int literal = formula.get(i);

        twice.reset();
        numLiterals++;
        clauseSum += literal;
        TIntArrayList edges = solver.graph.edges(literal);
        for (int j = 0; j < edges.size(); j++) {
          int u = -edges.get(j), u_ = u + solver.numVariables;
          if (!twice.containsOrAdd(u)) {
            if (counts[u_] == 0) touched[numTouched++] = u_;
            counts[u_] += 1;
            sums[u_] += literal;
          }
        }
      }

      for (int i = 0; i < numTouched; ++i) {
        int touch = touched[i];
        int literal = touch - solver.numVariables;
        assert !solver.isLiteralAssigned(literal);

        if (counts[touch] == numLiterals) {
          synchronized (units) {
            units.add(-literal);
          }
        } else if (counts[touch] + 1 == numLiterals) {
          // There is an edge from literal to all literals in clause except one.
          // New implication: literal -> missing
          int missing = clauseSum - sums[touch];
          assert !solver.isLiteralAssigned(missing)
              : "Missing literal " + missing + " is assigned";

          if (literal != missing && !cacheTest(-literal, missing)) {
            if (-literal == missing) {
              synchronized (units) {
                units.add(-literal);
              }
            } else {
              synchronized (binaries) {
                binaries.add(-literal);
                binaries.add(missing);
              }
            }
          }
        }

        counts[touch] = 0;
        sums[touch] = 0;
      }
    }

    semaphore.release(1);
  }

  private boolean cacheTest(final int u, final int v) {
    int hash = Hash.hash(v) ^ u;
    hash = 2 * (hash & (CACHE_SIZE - 1));
    if (cache[hash] != u || cache[hash + 1] != v) {
      cache[hash] = u;
      cache[hash + 1] = v;
      return false;
    }
    return true;
  }
}
