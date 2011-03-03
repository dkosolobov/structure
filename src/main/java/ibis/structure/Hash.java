package ibis.structure;

public final class Hash {
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
}
