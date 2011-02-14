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

  public void add(int u) {
    colors[u + numVariables] = currentColor;
  }

  public boolean contains(int u) {
    return colors[u + numVariables] == currentColor;
  }
}
