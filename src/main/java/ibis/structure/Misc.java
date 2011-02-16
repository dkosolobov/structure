package ibis.structure;

import gnu.trove.TIntArrayList;

public final class Misc {
  public static final int XOR = 0;
  public static final int NXOR = 1;
  public static final int OR = 2;

  private static final String TYPES[] = { "XOR", "NXOR", "OR" };
  private static final int TYPE_BITS = 2;
  private static final int TYPE_MASK = (1 << TYPE_BITS) - 1;
  private static final int REMOVED = Integer.MAX_VALUE;

  public static class ClauseIterator {
    private TIntArrayList formula;
    private int index;

    public ClauseIterator(final TIntArrayList formula) {
      this.formula = formula;
      index = 0;
      skipRemoved();
    }

    public boolean hasNext() {
      return index < formula.size();
    }

    public int next() {
      int clause = index;
      index += length(formula, index);
      skipRemoved();
      return clause;
    }

    private void skipRemoved() {
      while (index < formula.size() && formula.get(index) == REMOVED) {
        index++;
      }
      index++;
    }
  }

  /** Encodes the length and the type into a single int. */
  public static int encode(final int length, final int type) {
    assert type == XOR || type == NXOR || type == OR;
    return (length << TYPE_BITS) + type;
  }

  /** Returns the length of the clause. */
  public static int length(final TIntArrayList formula, final int clause) {
    assert !isClauseRemoved(formula, clause);
    return formula.get(clause - 1) >> TYPE_BITS;
  }

  /** Returns the type of the clause. */
  public static int type(final TIntArrayList formula, final int clause) {
    assert !isClauseRemoved(formula, clause);
    return formula.get(clause - 1) & TYPE_MASK;
  }

  public static void switchXOR(final TIntArrayList formula, final int clause) {
    assert !isClauseRemoved(formula, clause);
    formula.set(clause - 1, formula.get(clause - 1) ^ 1);
  }

  /** Returns a string representation of the clause */
  public static String clauseToString(final TIntArrayList formula, final int clause) {
    int type = type(formula, clause);
    int length = length(formula, clause);
    // System.err.println("length = " + length);
    TIntArrayList sub = formula.subList(clause, clause + length);
    return TYPES[type] + " " + sub.toString();
  }

  /** Removes literal at index from clause. */
  public static void removeLiteralAt(final TIntArrayList formula,
                                     final int clause,
                                     final int index) {
    int type = type(formula, clause);
    int length = length(formula, clause);
    for (int i = index + 1; i < clause + length; i++) {
      formula.set(i - 1, formula.get(i));
    }

    formula.set(clause - 1, encode(length - 1, type));
    formula.set(clause + length - 1, REMOVED);
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
    for (int i = clause - 1; i < clause + length; i++) {
      formula.set(i, REMOVED);
    }
  }

  /** Returns true if the clause was removed. */
  public static boolean isClauseRemoved(final TIntArrayList formula,
                                        final int clause) {
    return formula.get(clause - 1) == REMOVED;
  }

  /** Returns formula with REMOVED removed */
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
      // System.err.println(formula.size() + " -> " + p);
      formula.remove(p, removed);
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
}
