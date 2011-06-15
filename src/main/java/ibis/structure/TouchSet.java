package ibis.structure;

import gnu.trove.list.array.TIntArrayList;

/**
 * Provides a set storing literals with the following complexities
 * (N is the number of variables):
 *
 * <ul>
 * <li>memory - O(N). Memory consumed remains constant.</li>
 * <li><tt>add</tt> - O(1). Adds a new element.</li>
 * <li><tt>contains</tt> - O(1). Checks membership.</li>
 * <li><tt>remove</tt> - O(1). Removes an element.</li>
 * <li><tt>reset</tt> - O(1). Removes all elements.</li>
 * </ul>
 *
 */
public final class TouchSet {
  private int numVariables = 0;
  private short currentColor = 0;
  private short[] colors = null;

  public TouchSet(final int numVariables) {
    this.numVariables = numVariables;
    this.colors = new short[2 * numVariables + 1];
    reset();
  }

  /** Clears the set. */
  public void reset() {
    if (currentColor == Short.MAX_VALUE) {
      java.util.Arrays.fill(colors, (byte) 0);
      currentColor = 0;
    }
    currentColor++;
  }

  /** Adds a new literal to the set */
  public void add(final int u) {
    colors[u + numVariables] = currentColor;
  }

  /** Adds all literas in array to the set */
  public void add(final int[] array) {
    for (int u : array) {
      colors[u + numVariables] = currentColor;
    }
  }

  /** Adds all literas in array to the set */
  public void add(final TIntArrayList array) {
    for (int i = 0; i < array.size(); i++) {
      int u = array.getQuick(i);
      colors[u + numVariables] = currentColor;
    }
  }

  /** Returns true if set contains u */
  public boolean contains(final int u) {
    return colors[u + numVariables] == currentColor;
  }

  /** Returns true if set contains u, otherwise adds it to the set. */
  public boolean containsOrAdd(final int u) {
    int tmp = colors[u + numVariables];
    colors[u + numVariables] = currentColor;
    return tmp == currentColor;
  }

  /** Returns an array with all elements in the set */
  public int[] toArray() {
    int length = 0;
    for (int u = -numVariables; u <= numVariables; u++) {
      if (colors[u + numVariables] == currentColor) {
        length++;
      }
    }

    int[] array = new int[length];
    length = 0;
    for (int u = -numVariables; u <= numVariables; u++) {
      if (colors[u + numVariables] == currentColor) {
        array[length] = u;
        length++;
      }
    }

    return array;
  }

  /** Returns a string represenatation of this set */
  public String toString() {
    return (new TIntArrayList(toArray())).toString();
  }
}
