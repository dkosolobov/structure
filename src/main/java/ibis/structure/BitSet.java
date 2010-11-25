package ibis.structure;

import gnu.trove.TIntArrayList;

public final class BitSet {
  java.util.BitSet bs;

  public BitSet() {
    bs = new java.util.BitSet();
  }

  private BitSet(BitSet copy) {
    bs = (java.util.BitSet) copy.bs.clone();
  }

  public Object clone() {
    return new BitSet(this);
  }

  /**
   * Sets bit at index.
   */
  public void set(int index) {
    bs.set(mapZtoN(index));
  }

  public void add(int index) {
    set(index);
  }

  public void addAll(int[] indexes) {
    for (int index : indexes) {
      set(index);
    }
  }

  /**
   * Gets the value at index.
   */
  public boolean get(int index) {
    return bs.get(mapZtoN(index));
  }

  public boolean contains(int index) {
    return get(index);
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
   * Bijective function mapping Z to N.
   */
  public static final int mapZtoN(int a) {
    return a < 0 ? (- 1 - a - a) : a + a;
  }

  /**
   * Inverse of mapZtoN.
   */
  public static final int mapNtoZ(int a) {
    return (a & 1) != 0 ? - (a + 1) / 2 : a / 2;
  }
}
