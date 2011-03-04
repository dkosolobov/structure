package ibis.structure;

import gnu.trove.list.array.TIntArrayList;


public final class Misc {
  public static final int NXOR = 0;
  public static final int XOR = 1;
  public static final int OR = 2;
  public static final int DELETED = 3;

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
    private final TIntArrayList formula;
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
      while (true) {
        while (index < formula.size() && formula.getQuick(index) == REMOVED) {
          index++;
        }
        index++;

        if (index < formula.size() && type(formula, index) == DELETED) {
          index += length(formula, index);
        } else {
          break;
        }
      }
    }
  }

  /** Sorts clauses by length */
  public static final class ClauseLengthComparator
      implements java.util.Comparator<Integer> {
    private final TIntArrayList formula;

    public ClauseLengthComparator(final TIntArrayList formula) {
      this.formula = formula;
    }

    public int compare(final Integer c1, final Integer c2) {
      int l1 = length(formula, c1);
      int l2 = length(formula, c2);
      return l1 - l2;
    }
  }

  /** Sorts clauses by variables */
  public static final class ClauseVariablesComparator
      implements java.util.Comparator<Integer> {
    private final TIntArrayList formula;

    public ClauseVariablesComparator(final TIntArrayList formula) {
      this.formula = formula;
    }

    public int compare(final Integer o1, final Integer o2) {
      int c1 = o1, c2 = o2;
      int l1 = length(formula, c1);
      int l2 = length(formula, c2);
      for (int i = 0; i < l1 && i < l2; i++) {
        int u1 = var(formula.getQuick(c1 + i));
        int u2 = var(formula.getQuick(c2 + i));
        if (u1 != u2) {
          return u1 - u2;
        }
      }
      return l1 - l2;
    }
  }

  /** Sorts clauses by literals */
  public static final class ClauseLiteralsComparator
      implements java.util.Comparator<Integer> {
    private final TIntArrayList formula;

    public ClauseLiteralsComparator(final TIntArrayList formula) {
      this.formula = formula;
    }

    public int compare(final Integer o1, final Integer o2) {
      int c1 = o1, c2 = o2;
      int l1 = length(formula, c1);
      int l2 = length(formula, c2);
      for (int i = 0; i < l1 && i < l2; i++) {
        int u1 = formula.getQuick(c1 + i);
        int u2 = formula.getQuick(c2 + i);
        if (u1 != u2) {
          return u1 - u2;
        }
      }
      return l1 - l2;
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
    assert type == XOR || type == NXOR || type == OR || type == DELETED;
    return (length << TYPE_BITS) + type;
  }

  /** Returns the length of the clause. */
  public static int length(final TIntArrayList formula, final int clause) {
    return formula.getQuick(clause - 1) >> TYPE_BITS;
  }

  /** Returns the type of the clause. */
  public static int type(final TIntArrayList formula, final int clause) {
    return formula.getQuick(clause - 1) & TYPE_MASK;
  }

  /** Switches the value of the XOR instance. */
  public static void switchXOR(final TIntArrayList formula, final int clause) {
    assert type(formula, clause) == XOR || type(formula, clause) == NXOR;
    formula.setQuick(clause - 1, formula.getQuick(clause - 1) ^ 1);
  }

  /** Returns a string representation of the clause. */
  public static String clauseToString(final TIntArrayList formula, final int clause) {
    int type = type(formula, clause);
    int length = length(formula, clause);

    StringBuffer result = new StringBuffer();
    if (type == XOR || type == NXOR) {
      result.append("x ");
    }
    if (type == DELETED) {
      result.append("r ");
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
    formula.setQuick(clause - 1, encode(length, DELETED));
  }

  /** Returns true if the clause was removed. */
  public static boolean isClauseRemoved(final TIntArrayList formula,
                                        final int clause) {
    return type(formula, clause) == DELETED;
  }

  /**
   * Removes REMOVED elements from formula.
   *
   * formula will represent the same CNF instance,
   * but the clauses will no longer correspond.
   */
  public static void compact(final TIntArrayList formula) {
    int p = 0;

    ClauseIterator it = new ClauseIterator(formula);
    while (it.hasNext()) {
      int clause = it.next();
      int length = length(formula, clause);
      int type = type(formula, clause);

      if (type != DELETED) {
        formula.setQuick(p++, encode(length, type));
        for (int i = clause; i < clause + length; i++) {
          formula.setQuick(p++, formula.getQuick(i));
        }
      }
    }

    formula.remove(p, formula.size() - p);
  }
}
