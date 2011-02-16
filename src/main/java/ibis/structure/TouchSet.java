package ibis.structure;

public final class TouchSet {
  private int numVariables = 0;
  private byte currentColor = 0;
  private byte[] colors = null;

  public TouchSet(int numVariables) {
    this.numVariables = numVariables;
    this.colors = new byte[2 * numVariables + 1];
    reset();
  }

  public void reset() {
    if (currentColor == Byte.MAX_VALUE) {
      java.util.Arrays.fill(colors, (byte) 0);
      currentColor = 0;
    }
    currentColor++;
  }

  /** Adds a new literal to the set */
  public void add(int u) {
    colors[u + numVariables] = currentColor;
  }

  /** Adds all literas in array to the set */
  public void add(int[] array) {
    for (int u : array) {
      colors[u + numVariables] = currentColor;
    }
  }

  /** Returns true if set contains u */
  public boolean contains(final int u) {
    return colors[u + numVariables] == currentColor;
  }

  public boolean containsOrAdd(final int u) {
    if (contains(u)) {
      return true;
    }
    add(u);
    return false;
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
}
