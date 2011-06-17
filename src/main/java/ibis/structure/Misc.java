package ibis.structure;

import gnu.trove.list.array.TIntArrayList;
import gnu.trove.set.hash.TIntHashSet;


public final class Misc {
  public static final int NXOR = 0;
  public static final int XOR = 1;
  public static final int OR = 2;
  public static final int DELETED = 3;

  private static final int TYPE_BITS = 2;
  private static final int TYPE_MASK = (1 << TYPE_BITS) - 1;
  private static final int REMOVED = Integer.MAX_VALUE;

  /**
   * A clause iterator over a given formula. <br/>
   *
   * Example:
   * <pre>
   * ClauseIterator it = new ClauseIterator(formula);
   * while (it.hasNext()) {
   *   int clause = it.next();
   *   ... do stuff ...
   * }
   * </pre> <br/>
   * Example:
   * <pre>
   * int start = formula.size();
   * formula.addAll(other);
   * ClauseIterator it = new ClauseIterator(formula, start)
   * while (it.hasNext()) {
   *   int clause = it.next();
   *   // ...
   * }
   * </pre> <br/>
   *
   */
  public static final class ClauseIterator {
    private final TIntArrayList formula;
    private int index;
    private boolean hasNext;

    /**
     * Creates an interator over formula starting from begining.
     */
    public ClauseIterator(final TIntArrayList formula) {
      this(formula, 0);
    }

    /**
     * Creates an interator over formula starting at start.
     *
     * This constructor is useful when iterating over some appending clases.
     * start should point to a clause header.
     *
     */
    public ClauseIterator(final TIntArrayList formula, final int start) {
      this.formula = formula;

      index = start;

      if (index == formula.size()) {
        hasNext = false;
      } else if (type(formula, index + 1) == DELETED) {
        next();
      } else {
        hasNext = true;
      }
    }

    /** Returns true if there are more clauses left. */
    public boolean hasNext() {
      return hasNext;
    }

    /** Returns the next clause */
    public int next() {
      final int clause = index + 1;

      skipClause();
      findNext();
      return clause;
    }

    private void findNext() {
      hasNext = false;

      while (true) {
        while (index < formula.size()
            && formula.getQuick(index) == REMOVED) {
          index++;
        }

        if (index == formula.size()) {
          return;
        }

        if (type(formula, index + 1) != DELETED) {
          hasNext = true;
          return;
        }

        skipClause();
      }
    }

    private void skipClause() {
      index += 1 + length(formula, index + 1);
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

  /**
   * Returns a hash of a.
   * This function is better than the one from
   * gnu.trove.HashUtils which is the same as the one
   * provided by the Java library.
   *
   * Robert Jenkins' 32 bit integer hash function
   * http://burtleburtle.net/bob/hash/integer.html
   *
   * @param b integer to hash
   * @return a hash code for the given integer
   */
  public static int hash(final int b) {
    int a = b;
    a = (a + 0x7ed55d16) + (a << 12);
    a = (a ^ 0xc761c23c) ^ (a >>> 19);
    a = (a + 0x165667b1) + (a << 5);
    a = (a + 0xd3a2646c) ^ (a << 9);
    a = (a + 0xfd7046c5) + (a << 3);
    a = (a ^ 0xb55a4f09) ^ (a >>> 16);
    return a;
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
    int length = length(formula, clause);
    int type = type(formula, clause);
    for (int i = index + 1; i < clause + length; i++) {
      formula.setQuick(i - 1, formula.getQuick(i));
    }

    formula.setQuick(clause + length - 1, REMOVED);
    formula.setQuick(clause - 1, encode(length - 1, type));
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

  public static boolean isEmptyFormula(final TIntArrayList formula) {
    return !(new ClauseIterator(formula)).hasNext();
  }

  /** Returns true if the clause was removed. */
  public static boolean isClauseRemoved(final TIntArrayList formula,
                                        final int clause) {
    return type(formula, clause) == DELETED;
  }

  /** Returns true if the clause is satisfied by the given assingment. */
  public static boolean isClauseSatisfied(final TIntArrayList formula,
                                          final int clause,
                                          final TIntHashSet units) {
    int length = length(formula, clause);
    for (int i = clause; i < clause + length; i++) {
      if (units.contains(formula.getQuick(i))) {
        return true;
      }
    }
    return false;
  }

  /** Appends clause from src to dst. */
  public static void copy(final TIntArrayList dst,
                          final TIntArrayList src,
                          final int clause) {
    int length = length(src, clause);
    dst.ensureCapacity(dst.size() + length + 1);
    for (int i = clause - 1; i < clause + length; i++) {
      dst.add(src.getQuick(i));
    }
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

      if (type != DELETED && (type != NXOR || length != 0)) {
        formula.setQuick(p++, encode(length, type));
        for (int i = clause; i < clause + length; i++) {
          formula.setQuick(p++, formula.getQuick(i));
        }
      }
    }

    formula.remove(p, formula.size() - p);
  }

  /** Returns all clauses containing given literals. */
  public static TIntArrayList filter(final TIntArrayList formula,
                                     final int... literals) {
    TIntArrayList filtered = new TIntArrayList();
    TIntHashSet set = new TIntHashSet();

    for (int literal : literals) {
      set.add(var(literal));
    }

    ClauseIterator it = new ClauseIterator(formula);
    while (it.hasNext()) {
      int clause = it.next();
      int length = length(formula, clause);

      for (int i = clause; i < clause + length; i++) {
        int literal = formula.getQuick(clause);
        if (set.contains(var(literal))) {
          copy(filtered, formula, clause);
          break;
        }
      }
    }

    return filtered;
  }
}
