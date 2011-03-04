package ibis.structure;

import gnu.trove.set.hash.TIntHashSet;
import gnu.trove.list.array.TIntArrayList;
import gnu.trove.iterator.TIntIterator;
import org.apache.log4j.Logger;

import static ibis.structure.Misc.*;


public final class WatchLists {
  private static Logger logger = Logger.getLogger(WatchLists.class);

  /** Marks a removed literal */
  /** An EMPTY HashSet to improve performance */
  protected static final TIntHashSet EMPTY = new TIntHashSet();

  /** Number of variables. */
  private final int numVariables;
  /** List of clauses separated by 0. */
  private final TIntArrayList formula;

  /** Watch lists */
  private final TIntHashSet[] watchLists;
  /** Short clauses discovered */
  public final TIntArrayList units, binaries;

  /** Constructor */
  public WatchLists(final int numVariables, final TIntArrayList formula) {
    this.numVariables = numVariables;
    this.formula = new TIntArrayList(formula);

    watchLists = new TIntHashSet[2 * numVariables + 1];
    units = new TIntArrayList();
    binaries = new TIntArrayList();
  }

  /** Returns formula */
  public TIntArrayList formula() {
    return formula;
  }

  /** Builds the watch lists */
  public void build() throws ContradictionException {
    for (int u = -numVariables; u <= numVariables; ++u) {
      watchLists[u + numVariables] = new TIntHashSet(3);
    }

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

  public void merge(final int from, final int to)
      throws ContradictionException {
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
  private boolean mergeOR(final int clause, final int from, final int to)
      throws ContradictionException {
    if (get(to).contains(clause)) {
      // to or to = to
      Misc.removeLiteral(formula, clause, from);
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
  private void mergeXOR(final int clause, final int from, final int to)
      throws ContradictionException {
    assert type(formula, clause) != OR;
    assert from > 0;

    if (get(to).contains(clause)) {
      // to xor to = 0
      Misc.removeLiteral(formula, clause, from);
      Misc.removeLiteral(formula, clause, to);
      get(to).remove(clause);
      clauseLengthChanged(clause);
    } else if (get(-to).contains(clause)) {
      // to xor -to = 1
      Misc.removeLiteral(formula, clause, from);
      Misc.removeLiteral(formula, clause, -to);
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
  public void removeLiteralAt(final int clause, final int index)
      throws ContradictionException {
    get(formula.get(index)).remove(clause);
    Misc.removeLiteralAt(formula, clause, index);
    clauseLengthChanged(clause);
  }

  /** Removes literal at index from clause. */
  public void removeLiteral(final int clause, final int literal)
      throws ContradictionException {
    int index = formula.indexOf(clause, literal);
    removeLiteralAt(clause, index);
  }

  /** Removes clause and updates the watch lists */
  public void removeClause(final int clause) {
    int length = length(formula, clause);
    for (int i = clause; i < clause + length; i++) {
      get(formula.get(i)).remove(clause);
    }
    Misc.removeClause(formula, clause);
  }

  /** Assigns u to true, -u to false and removes the literals from clauses. */
  public void assign(final int u) throws ContradictionException {
    // logger.info("assigning unit " + u);
    // logger.info("formula is " + formulaToString(formula));

    for (int clause : get(u).toArray()) {
      if (type(formula, clause) == OR) {
        removeClause(clause);
      } else {
        Misc.removeLiteral(formula, clause, u);
        get(u).remove(clause);
        switchXOR(formula, clause);
        clauseLengthChanged(clause);
      }
    }

    TIntIterator it = get(neg(u)).iterator();
    for (int size = get(neg(u)).size(); size > 0; size--) {
      int clause = it.next();
      if (type(formula, clause) == OR) {
        Misc.removeLiteral(formula, clause, -u);
        clauseLengthChanged(clause);
      } else {
        Misc.removeLiteral(formula, clause, -u);
        clauseLengthChanged(clause);
      }
    }

    watchLists[u + numVariables] = EMPTY;
    watchLists[-u + numVariables] = EMPTY;
  }

  /** Enqueues short clauses. */
  private void clauseLengthChanged(final int clause) throws ContradictionException {
    int length = length(formula, clause);
    if (length == 0) {
      if (type(formula, clause) != NXOR) {
        throw new ContradictionException();
      } else {
        Misc.removeClause(formula, clause);
      }
    } else if (length == 1) {
      units.add(clause);
    } else if (length == 2) {
      binaries.add(clause);
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

    ClauseIterator it;
    
    it = new ClauseIterator(formula);
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

    TouchSet visited = new TouchSet(numVariables);
    it = new ClauseIterator(formula);
    while (it.hasNext()) {
      int clause = it.next();
      int length = length(formula, clause);
      visited.reset();
      for (int i = clause; i < clause + length; i++) {
        int literal = formula.get(i);
        assert !visited.contains(literal)
            : "Duplicate literal in " + clauseToString(formula, clause);
        assert !visited.contains(-literal)
            : "Duplicate variable in " + clauseToString(formula, clause);
        visited.add(literal);
      }
    }
  }
}
