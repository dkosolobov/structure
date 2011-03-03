package ibis.structure;

import gnu.trove.TIntArrayList;
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
  private static final int CACHE_SIZE = 256;

  private static final Logger logger = Logger.getLogger(HyperBinaryResolution.class);

  /** The solver. */
  private final Solver solver;
  private final int numVariables;
  /** Formula. */
  private final TIntArrayList formula;
  /** Units discovered. */
  private final TIntArrayList units = new TIntArrayList();
  /** Binaries discovered */
  private final TIntArrayList binaries = new TIntArrayList();
  private final int[] counts;
  private final int[] sums;
  private final int[] touched;
  /** A cache mapping literals to their dfs */
  private final int[] cacheLiterals = new int[CACHE_SIZE];
  private final TIntArrayList[] cacheEdges = new TIntArrayList[CACHE_SIZE];

  public HyperBinaryResolution(final Solver solver) {
    this.solver = solver;

    numVariables = solver.numVariables;
    formula = solver.watchLists.formula();
    counts = new int[2 * numVariables + 1];
    sums = new int[2 * numVariables + 1];
    touched = new int[2 * numVariables + 1];
  }

  public boolean run() {
    long start = System.currentTimeMillis();
    ClauseIterator it = new ClauseIterator(formula);
    while (it.hasNext()) {
      int clause = it.next();
      run(clause);

      long curr = System.currentTimeMillis();
      if (curr - start >= TIME_MILLIS_LIMIT) {
        break;
      }
    }

    // Adds discovered units.
    for (int i = 0; i < units.size(); i++) {
      solver.queueUnit(units.get(i));
    }

    // Adds discovered binary.
    int[] last = new int[2 * numVariables + 1];
    int numBinaries = 0;
    for (int i = 0; i < binaries.size(); i += 2) {
      int u = binaries.get(i);
      int v = binaries.get(i + 1);
      int u_ = u + numVariables;
      int v_ = v + numVariables;

      // last is a small cache to remove some duplicates
      if (last[u_] != v && last[v_] != u) {
        last[u_] = v;
        solver.addBinary(u, v);
        numBinaries++;
      }
    }

    if (Configure.verbose) {
      if (!units.isEmpty()) {
        System.err.print("hu" + units.size() + ".");
      }
      if (!binaries.isEmpty()) {
        System.err.print("hb" + numBinaries + ".");
      }
    }

    return !units.isEmpty() || !binaries.isEmpty();
  }


  private final TIntArrayList cache(int literal) {
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

  public void run(final int clause) {
    if (type(formula, clause) != OR) {
      return;
    }

    int length = length(formula, clause);
    int numLiterals = 0;
    int clauseSum = 0;
    int numTouched = 0;

    boolean bad = false;
    for (int i = clause; i < clause + length; i++) {
      int literal = formula.getQuick(i);
      if (solver.graph.edges(literal).isEmpty()) {
        if (bad) return;
        bad = true;
      }
    }

    for (int i = clause; i < clause + length; i++) {
      int literal = formula.getQuick(i);
      TIntArrayList edges = cache(literal);

      numLiterals++;
      clauseSum += literal;
      for (int j = 0; j < edges.size(); j++) {
        int u = -edges.getQuick(j), u_ = u + numVariables;
        if (counts[u_] == 0) {
          if (numLiterals > 2) {
            continue;
          }
          touched[numTouched++] = u_;
        }
        counts[u_] += 1;
        sums[u_] += literal;
      }
    }

    for (int i = 0; i < numTouched; ++i) {
      int touch = touched[i];
      int literal = touch - numVariables;
      assert !solver.isLiteralAssigned(literal);

      if (counts[touch] == numLiterals) {
        units.add(-literal);
      } else if (counts[touch] + 1 == numLiterals) {
        // There is an edge from literal to all literals in clause except one.
        // New implication: literal -> missing
        int missing = clauseSum - sums[touch];

        if (literal == missing) {
          // Skips tautology
          continue;
        }

        if (-literal == missing) {
          units.add(-literal);
          continue;
        }
        
        if (binaries.size() >= BINARIES_LIMIT) {
          continue;
        }

        binaries.add(-literal);
        binaries.add(missing);
      }

      counts[touch] = 0;
      sums[touch] = 0;
    }
  }
}

