package ibis.structure;

import gnu.trove.TIntArrayList;

public final class Misc {
  public static final int NXOR = 0;
  public static final int XOR = 1;
  public static final int OR = 2;

  private static final int TYPE_BITS = 2;
  private static final int TYPE_MASK = (1 << TYPE_BITS) - 1;
  private static final int REMOVED = Integer.MAX_VALUE;

  /**
   * A clause iterator over a given formula.
   *
   * Example:
   * <pre>
   * ClauseIterator it = new ClauseIterator(formula);
   * while (it.hasNext()) {
   *   int clause = it.next();
   *   ... do stuff ...
   * }
   * </pre>
   *
   */
  public static final class ClauseIterator {
    private TIntArrayList formula;
    private int index;

    public ClauseIterator(final TIntArrayList formula) {
      this.formula = formula;
      index = 0;
      skipRemoved();
    }

    /** Returns true if there are more clauses left. */
    public boolean hasNext() {
      return index < formula.size();
    }

    /** Returns the next clause */
    public int next() {
      int clause = index;
      index += length(formula, index);
      skipRemoved();
      return clause;
    }

    private void skipRemoved() {
      while (index < formula.size() && formula.getQuick(index) == REMOVED) {
        index++;
      }
      index++;
    }
  }

  /** Returns the negation of literal */
  public static int neg(final int literal) {
    return -literal;
  }

  /** Returns the variable of literal */
  public static int var(final int literal) {
    return Math.abs(literal);
  }

  /** Encodes the length and the type into a single int. */
  public static int encode(final int length, final int type) {
    assert type == XOR || type == NXOR || type == OR;
    return (length << TYPE_BITS) + type;
  }

  /** Returns the length of the clause. */
  public static int length(final TIntArrayList formula, final int clause) {
    assert !isClauseRemoved(formula, clause) : "Clause " + clause + " was removed";
    return formula.getQuick(clause - 1) >> TYPE_BITS;
  }

  /** Returns the type of the clause. */
  public static int type(final TIntArrayList formula, final int clause) {
    assert !isClauseRemoved(formula, clause) : "Clause " + clause + " was removed";
    return formula.getQuick(clause - 1) & TYPE_MASK;
  }

  /** Switches the value of the XOR instance. */
  public static void switchXOR(final TIntArrayList formula, final int clause) {
    assert !isClauseRemoved(formula, clause) : "Clause " + clause + " was removed";
    assert type(formula, clause) == XOR || type(formula, clause) == NXOR;
    formula.setQuick(clause - 1, formula.getQuick(clause - 1) ^ 1);
  }

  /** Returns a string representation of the clause. */
  public static String clauseToString(final TIntArrayList formula, final int clause) {
    int type = type(formula, clause);
    int length = length(formula, clause);

    StringBuffer result = new StringBuffer();
    if (type != OR) {
      result.append("x ");
    }
    for (int i = clause; i < clause + length; i++) {
      int literal = formula.getQuick(i);
      if (i == clause && type == NXOR) {
        literal = neg(literal);
      }
      result.append(literal + " ");
    }
    result.append("0");

    return result.toString();
  }

  /** Returns a string representation of the formula. */
  public static String formulaToString(final TIntArrayList formula) {
    StringBuffer result = new StringBuffer();
    ClauseIterator it = new ClauseIterator(formula);
    while (it.hasNext()) {
      result.append(clauseToString(formula, it.next()));
      result.append("\n");
    }
    return result.toString();
  }

  /** Removes literal at index from clause. */
  public static void removeLiteralAt(final TIntArrayList formula,
                                     final int clause,
                                     final int index) {
    int type = type(formula, clause);
    int length = length(formula, clause);
    for (int i = index + 1; i < clause + length; i++) {
      formula.setQuick(i - 1, formula.getQuick(i));
    }

    formula.setQuick(clause - 1, encode(length - 1, type));
    formula.setQuick(clause + length - 1, REMOVED);
  }

  /** Removes literal from clause. */
  public static void removeLiteral(final TIntArrayList formula,
                                   final int clause,
                                   final int literal) {
    removeLiteralAt(formula, clause, formula.indexOf(clause, literal));
  }

  /** Removes clause from formula. */
  public static void removeClause(final TIntArrayList formula,
                                  final int clause) {
    int length = length(formula, clause);
    formula.fill(clause - 1, clause + length, REMOVED);
  }

  /** Returns true if the clause was removed. */
  public static boolean isClauseRemoved(final TIntArrayList formula,
                                        final int clause) {
    return formula.getQuick(clause - 1) == REMOVED;
  }

  /**
   * Removes REMOVED elements from formula.
   *
   * formula will represent the same CNF instance,
   * but the clauses will no longer correspond.
   */
  public static void compact(final TIntArrayList formula) {
    int p = 0;
    for (int i = 0; i < formula.size(); i++) {
      int literal = formula.getQuick(i);
      if (literal != REMOVED) {
        formula.setQuick(p, literal);
        p++;
      }
    }

    int removed = formula.size() - p;
    if (removed > 0) {
      formula.remove(p, removed);
    }
  }
}
