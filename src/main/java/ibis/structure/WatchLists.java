package ibis.structure;

import gnu.trove.TIntHashSet;
import gnu.trove.TIntArrayList;
import gnu.trove.TIntIterator;
import org.apache.log4j.Logger;

import static ibis.structure.Misc.*;


public final class WatchLists {
  private static Logger logger = Logger.getLogger(WatchLists.class);

  /** Marks a removed literal */
  /** An EMPTY HashSet to improve performance */
  protected static final TIntHashSet EMPTY = new TIntHashSet();

  /** Number of variables. */
  protected int numVariables;
  /** List of clauses separated by 0. */
  protected TIntArrayList formula;
  /** Watch lists */
  protected TIntHashSet[] watchLists;
  /** Short clauses */
  protected TIntArrayList shortClauses;

  /** Constructor */
  public WatchLists(final int numVariables, final TIntArrayList formula) {
    this.numVariables = numVariables;
    this.formula = (TIntArrayList) formula.clone();
  }

  /** Returns formula */
  public TIntArrayList formula() {
    return formula;
  }

  /** Returns list of short clauses */
  public TIntArrayList shortClauses() {
    return shortClauses;
  }

  /** Builds the watch lists */
  public void build() throws ContradictionException {
    watchLists = new TIntHashSet[2 * numVariables + 1];
    for (int u = -numVariables; u <= numVariables; ++u) {
      watchLists[u + numVariables] = new TIntHashSet();
    }
    shortClauses = new TIntArrayList();

    ClauseIterator it = new ClauseIterator(formula);
    while (it.hasNext()) {
      int clause = it.next();
      int length = length(formula, clause);
      for (int i = clause; i < clause + length; i++) {
        int u = formula.get(i);
        get(u).add(clause);
      }
      clauseLengthChanged(clause);
    }
  }

  /** Returns the watch list for literal. */
  public TIntHashSet get(final int literal) {
    return watchLists[literal + numVariables];
  }

  public void merge(final int from, final int to) {
    TIntArrayList tautologies = new TIntArrayList();

    TIntIterator it = get(from).iterator();
    for (int size = get(from).size(); size > 0; size--) {
      int clause = it.next();

      if (type(formula, clause) == OR) {
        if (mergeOR(clause, from, to)) {
          tautologies.add(clause);
        }
      } else {
        mergeXOR(clause, from, to);
      }
    }

    for (int i = 0; i < tautologies.size(); i++) {
      removeClause(tautologies.get(i));
    }
    watchLists[from + numVariables] = EMPTY;
  }

  /** Renames from to to in an or clause. */
  private boolean mergeOR(final int clause, final int from, final int to) {
    if (get(to).contains(clause)) {
      // to or to = to
      removeLiteral(formula, clause, from);
      clauseLengthChanged(clause);
      return false;
    } else if (get(-to).contains(clause)) {
      // to or -to = true
      return true;
    } else {
      formula.set(formula.indexOf(clause, from), to);
      get(to).add(clause);
      return false;
    }
  }

  /** Renames from to to in a xor/nxor clause. */
  private void mergeXOR(final int clause, final int from, final int to) {
    assert type(formula, clause) != OR;
    assert from > 0;

    if (get(to).contains(clause)) {
      // to xor to = 0
      removeLiteral(formula, clause, from);
      removeLiteral(formula, clause, to);
      get(to).remove(clause);
      clauseLengthChanged(clause);
    } else if (get(-to).contains(clause)) {
      // to xor -to = 1
      removeLiteral(formula, clause, from);
      removeLiteral(formula, clause, -to);
      get(-to).remove(clause);
      switchXOR(formula, clause);
      clauseLengthChanged(clause);
    } else {
      formula.set(formula.indexOf(clause, from), var(to));
      get(var(to)).add(clause);
      if (to < 0) {
        switchXOR(formula, clause);
      }
    }
  }

  /** Removes literal at index from clause. */
  public void removeLiteralAt(final int clause,
                              final int index) {
    get(formula.get(index)).remove(clause);
    Misc.removeLiteralAt(formula, clause, index);
  }

  /** Removes clause and updates the watch lists */
  public void removeClause(final int clause) {
    int length = length(formula, clause);
    for (int i = clause; i < clause + length; i++) {
      get(formula.get(i)).remove(clause);
    }
    Misc.removeClause(formula, clause);
  }

  public void assign(final int u) {
    // logger.info("assiging " + u);
    // logger.info("to " + compact(formula) + " " + clauseToString(formula, 1));

    for (int clause : get(u).toArray()) {
      if (type(formula, clause) == OR) {
        removeClause(clause);
      } else {
        removeLiteral(formula, clause, u);
        get(u).remove(clause);
        switchXOR(formula, clause);
        clauseLengthChanged(clause);
      }
    }

    TIntIterator it = get(-u).iterator();
    for (int size = get(-u).size(); size > 0; size--) {
      int clause = it.next();
      if (type(formula, clause) == OR) {
        removeLiteral(formula, clause, -u);
        clauseLengthChanged(clause);
      } else {
        removeLiteral(formula, clause, -u);
        clauseLengthChanged(clause);
      }
    }

    // logger.info("after " + compact(formula) + " " + clauseToString(formula, 1));
    watchLists[u + numVariables] = EMPTY;
    watchLists[-u + numVariables] = EMPTY;
  }

  /** Enqueues short clauses. */
  private void clauseLengthChanged(final int clause) {
    int length = length(formula, clause);
    if (length <= 2) {
      shortClauses.add(clause);
    }
  }

  /** Verifies integrity of watch lists. */
  public void verifyIntegrity() {
    for (int u = -numVariables; u <= numVariables; ++u) {
      if (u == 0) {
        continue;
      }

      TIntIterator it = get(u).iterator();
      for (int size = get(u).size(); size > 0; size--) {
        int clause = it.next();
        assert formula.subList(clause, clause + length(formula, clause)).contains(u)
            : "Clause " + clauseToString(formula, clause) + " does not contains literal " + u;
      }
    }

    ClauseIterator it = new ClauseIterator(formula);
    while (it.hasNext()) {
      int clause = it.next();
      int length = length(formula, clause);
      int type = type(formula, clause);
      for (int i = clause; i < clause + length; i++) {
        int u = formula.get(i);
        assert get(u).contains(clause)
            : "Missing clause " + clause + " from literal " + u;
        assert type == OR || u > 0
            : clauseToString(formula, clause) + " contains negative literal " + u + " xxx " + clause;
      }
    }
  }
}
