package ibis.structure;

import java.util.concurrent.Semaphore;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import gnu.trove.TIntArrayList;
import gnu.trove.TIntIntIterator;

/**
 * Performes hyper-binary resolution.
 *
 * If (a1 + ... ak + b) and (l &ge; -a1) ... (l &ge; -ak)
 * then l &ge; b, otherwise if l then clause is contradiction
 */
public class HyperBinaryResolution {
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
    TIntIntIterator iterator = solver.lengths.iterator();

    // Runs numThreads HyperBinaryResolution simultaneously.
    int numThreads = solver.lengths.size() / 10000;
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
  /** Solver on which algorithms is applied */
  private Solver solver;
  /** A semaphore to signal end of computation */
  private Semaphore semaphore;
  /** A vector to store generated units. */
  private TIntArrayList units;
  /** A vector to store generated binaries. */
  private TIntArrayList binaries;
  /** An iterator over clauses. */
  private TIntIntIterator iterator;

  public HyperBinaryResolutionRunnable(final Solver solver,
                                       final TIntArrayList units,
                                       final TIntArrayList binaries,
                                       final TIntIntIterator iterator,
                                       final Semaphore semaphore) {
    this.solver = solver;
    this.units = units;
    this.binaries = binaries;
    this.iterator = iterator;
    this.semaphore = semaphore;
  }

  public void run() {
    int[] counts = new int[2 * solver.numVariables + 1];
    int[] sums = new int[2 * solver.numVariables + 1];
    int[] touched = new int[2 * solver.numVariables + 1];
    int[] twice = new int[2 * solver.numVariables + 1];
    int twiceColor = 0;

    // Cache is used to filter many duplicate binaries.
    final int limit = (int) Math.pow(solver.numVariables, 1.5);
    final int cacheSize = 1 << 10;
    int[] cache = new int[cacheSize * 2];

    while (true) {
      int start;
      synchronized (iterator) {
        if (!iterator.hasNext()) break;
        iterator.advance();
        start = iterator.key();
      }
      
      int numLiterals = 0;
      int clauseSum = 0;
      int numTouched = 0;
      int end = start;

      for (; end < solver.clauses.size(); end++) {
        int literal = solver.clauses.get(end);
        if (literal == solver.REMOVED) {
          continue;
        }
        if (literal == 0) {
          break;
        }

        twiceColor++;
        numLiterals++;
        clauseSum += literal;
        TIntArrayList edges = solver.graph.edges(literal);
        for (int i = 0; i < edges.size(); i++) {
          int node = -edges.get(i) + solver.numVariables;
          if (twice[node] != twiceColor) {
            twice[node] = twiceColor;
            if (counts[node] == 0) touched[numTouched++] = node;
            counts[node] += 1;
            sums[node] += literal;
          }
        }
      }

      start = end + 1;

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
          assert !solver.isLiteralAssigned(missing);

          if (literal != missing) {
            int hash = Hash.hash(missing) ^ literal;
            hash = 2 * (hash & (cacheSize - 1));
            if (cache[hash] != -literal || cache[hash + 1] != missing) {
              cache[hash] = -literal;
              cache[hash + 1] = missing;
              synchronized (binaries) {
                binaries.add(-literal);
                binaries.add(missing);
                if (binaries.size() > limit) {
                  break;
                }
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
}
