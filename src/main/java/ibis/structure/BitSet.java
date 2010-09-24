package ibis.structure;

import gnu.trove.TIntArrayList;

public final class BitSet {
  java.util.BitSet bs = new java.util.BitSet();

  public BitSet() {
  }

  /**
   * Sets bit at index.
   */
  public void set(int index) {
    bs.set(mapZtoN(index));
  }

  /**
   * Gets the value at index.
   */
  public boolean get(int index) {
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
   * Bijective function mapping Z to N.
   */
  private int mapZtoN(int a) {
    return a < 0 ? (- 1 - a - a) : a + a;
  }

  /**
   * Inverse of mapZtoN.
   */
  private int mapNtoZ(int a) {
    return (a & 1) != 0 ? - (a + 1) / 2 : a / 2;
  }
}
