package ibis.structure;

import gnu.trove.TIntArrayList;

/**
 * A bitset that can store negative indexes.
 */
public final class BitSet {
  /** A bitset storing only positive indexes. */
  private java.util.BitSet bs;

  /** Creates an empty bitset. */
  public BitSet() {
    bs = new java.util.BitSet();
  }

  /** Creates a copy of another bitset.  */
  private BitSet(final BitSet copy) {
    bs = (java.util.BitSet) copy.bs.clone();
  }

  /**
   * Sets bit at index.
   *
   * @param index number to add to bitset.
   */
  public void set(final int index) {
    bs.set(mapZtoN(index));
  }

  public void add(int index) {
    set(index);
  }

  /** Removes index from set. */
  public void remove(final int index) {
    bs.clear(mapZtoN(index));
  }

  /**
   * Adds all indexes in array.
   *
   * @param indexes new numbers to add to bitset.
   */
  public void addAll(final int[] indexes) {
    for (int index : indexes) {
      set(index);
    }
  }

  /** Gets the value at index. */
  public boolean get(final int index) {
    return bs.get(mapZtoN(index));
  }

  public boolean contains(final int index) {
    return bs.get(mapZtoN(index));
  }

  /**
   * Returns an array containing all set indexes in the bitset.
   */
  public int[] elements() {
    TIntArrayList elements = new TIntArrayList();
    for (int i = bs.nextSetBit(0); i >= 0; i = bs.nextSetBit(i + 1)) {
      elements.add(mapNtoZ(i));
    }
    return elements.toNativeArray();
  }

  /**
   * Returns a string representation of this bitset.
   */
  public String toString() {
    return (new TIntArrayList(elements())).toString();
  }

  /**
   * Bijective function mapping Z to N.
   */
  public static int mapZtoN(final int a) {
    return a < 0 ? (-1 - a - a) : a + a;
  }

  /**
   * Inverse of mapZtoN.
   */
  public static int mapNtoZ(final int a) {
    return (a & 1) != 0 ? -(a + 1) / 2 : a / 2;
  }
}
