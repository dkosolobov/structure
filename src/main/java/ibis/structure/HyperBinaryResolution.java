package ibis.structure;

import gnu.trove.list.array.TIntArrayList;
import gnu.trove.set.hash.TIntHashSet;
import org.apache.log4j.Logger;

import static ibis.structure.Misc.*;


/**
 * Performes hyper-binary resolution.
 *
 * If (a1 + ... ak + b) and (l &ge; -a1) ... (l &ge; -ak)
 * then l &ge; b, otherwise if l then clause is contradiction
 */
public final class HyperBinaryResolution {
  private static final int BINARIES_LIMIT = 1 << 24;
  private static final int TIME_MILLIS_LIMIT = 20000;
  private static final int CACHE_SIZE = 512;

  private static final Logger logger = Logger.getLogger(HyperBinaryResolution.class);

  /** The solver. */
  private final Solver solver;
  /** Units discovered. */
  private final TIntHashSet units = new TIntHashSet();
  /** Binaries discovered */
  private final TIntArrayList binaries = new TIntArrayList();
  /** How many times each variables was seen */
  private final int[] counts;
  private final int[] sums;
  private final int[] touched;
  /** A cache mapping literals to their dfs */
  private final int[] cacheLiterals = new int[CACHE_SIZE];
  private final TIntArrayList[] cacheEdges = new TIntArrayList[CACHE_SIZE];

  private HyperBinaryResolution(final Solver solver) {
    this.solver = solver;

    counts = new int[2 * solver.numVariables + 1];
    sums = new int[2 * solver.numVariables + 1];
    touched = new int[2 * solver.numVariables + 1];
  }

  public static boolean run(final Solver solver) throws ContradictionException {
    boolean simplified = (new HyperBinaryResolution(solver)).run();
    solver.propagate();
    solver.renameEquivalentLiterals();
    return simplified;
  }

  public boolean run() throws ContradictionException {
    long start = System.currentTimeMillis();
    ClauseIterator it = new ClauseIterator(solver.formula);
    while (it.hasNext()) {
      int clause = it.next();
      run(clause);

      long curr = System.currentTimeMillis();
      if (curr - start >= TIME_MILLIS_LIMIT) {
        break;
      }
    }

    // Adds discovered units.
    int numUnits = units.size();
    solver.unitsQueue.addAll(units);

    int numBinaries = binaries.size() / 3;
    solver.watchLists.append(binaries);

    if (Configure.verbose) {
      if (!units.isEmpty()) {
        System.err.print("hu" + numUnits + ".");
      }
      if (!binaries.isEmpty()) {
        System.err.print("hb" + numBinaries + ".");
      }
    }

    return !units.isEmpty() || !binaries.isEmpty();
  }

  /** Runs hyper binary resolution on clause. */
  private void run(final int clause) {
    if (type(solver.formula, clause) != OR) {
      return;
    }

    int length = length(solver.formula, clause);
    int numLiterals = 0;
    int clauseSum = 0;
    int numTouched = 0;

    // If clause contains two literals with no
    // binaries then hbr is effective on it.
    int numEmpty = 0;
    for (int i = clause; i < clause + length; i++) {
      int literal = solver.formula.getQuick(i);
      if (units.contains(literal)) {
        return;
      }
      if (solver.graph.edges(literal).isEmpty()) {
        numEmpty++;
        if (numEmpty >= 2) {
          return;
        }
      }
    }

    for (int i = clause; i < clause + length; i++) {
      int literal = solver.formula.getQuick(i);
      TIntArrayList edges = cache(literal);

      numLiterals++;
      clauseSum += literal;
      for (int j = 0; j < edges.size(); j++) {
        int u = -edges.getQuick(j), u_ = u + solver.numVariables;
        if (counts[u_] == 0) {
          touched[numTouched++] = u_;
        }
        counts[u_] += 1;
        sums[u_] += literal;
      }
    }

    for (int i = 0; i < numTouched; ++i) {
      int touch = touched[i];
      int literal = touch - solver.numVariables;
      assert !solver.isLiteralAssigned(literal);

      if (counts[touch] == numLiterals) {
        units.add(neg(literal));
      } else if (counts[touch] + 1 == numLiterals) {
        // There is an edge from literal to all literals in clause except one.
        // New implication: literal -> missing
        int missing = clauseSum - sums[touch];

        if (literal == missing) {
          // Skips tautology
          continue;
        }

        if (neg(literal) == missing) {
          units.add(neg(literal));
          continue;
        }
        
        if (binaries.size() >= BINARIES_LIMIT) {
          continue;
        }

        binaries.add(encode(2, OR));
        binaries.add(neg(literal));
        binaries.add(missing);
      }

      counts[touch] = 0;
      sums[touch] = 0;
    }
  }

  /**
   * Returns dfs for a given literal caching the result.
   */
  private final TIntArrayList cache(final int literal) {
    int hash = Hash.hash(literal) & (CACHE_SIZE - 1);

    if (cacheLiterals[hash] == literal) {
      return cacheEdges[hash];
    }

    if (cacheEdges[hash] == null) {
      cacheEdges[hash] = new TIntArrayList();
    } else {
      cacheEdges[hash].reset();
    }

    cacheLiterals[hash] = literal;
    solver.graph.dfs(literal, cacheEdges[hash]);
    return cacheEdges[hash];
  }
}

