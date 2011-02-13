package ibis.structure;

import gnu.trove.TIntArrayList;
import gnu.trove.TIntIntIterator;
import gnu.trove.TIntIntHashMap;;


public final class Vitality {
  private static final double DECAY = 0.95;
  private static final double ALPHA = 0.75;

  private int numVariables;
  private double[] vitality = null;
  private double[] before, after;

  public Vitality(int numVariables) {
    this.numVariables = numVariables;
    this.vitality = new double[numVariables + 1];
  }

  public Vitality(Vitality copy) {
    this.numVariables = copy.numVariables;
    vitality = java.util.Arrays.copyOf(copy.vitality, numVariables + 1);
  }

  public double vitality(int variable) {
    return vitality[variable];
  }

  public void normalize(TIntIntHashMap literalMap) {
    numVariables = literalMap.size() / 2;

    double[] temp = new double[numVariables + 1];
    TIntIntIterator it = literalMap.iterator();
    for (int size = literalMap.size(); size > 0; size--) {
      it.advance();
      if (it.value() > 0) {
        temp[it.value()] = vitality[Math.abs(it.key())];
      }
    }

    vitality = temp;
  }

  public void before(Skeleton instance) {
    before = new double[numVariables + 1];
    fill(before, instance.clauses);
  }

  public void after(Skeleton instance) {
    after = new double[numVariables + 1];
    fill(after, instance.clauses);

    for (int u = 1; u <= numVariables; u++) {
      vitality[u] *= DECAY;
      vitality[u] += after[u] - before[u];
      // System.err.print(" " + vitality[u]);
    }
    // System.err.println();

    before = after = null;
  }

  private static void fill(double[] array, TIntArrayList clauses) {
    int start = 0;
    for (int end = 0; end < clauses.size(); end++) {
      int u = clauses.get(end);
      if (u == 0) {
        int length = end - start;
        double delta = Math.pow(ALPHA, length);

        for (int i = start; i < end; i++) {
          int v = clauses.get(i);
          array[Math.abs(v)] += delta;
        }

        start = end + 1;
      }
    }
  }
  
}
