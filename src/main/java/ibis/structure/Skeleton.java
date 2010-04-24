package ibis.structure;


import java.util.Vector;
import java.io.Serializable;


public final class Skeleton implements Serializable {
    public int numVariables;
    public Vector<int[]> clauses;
    public int[] units;

    public Skeleton(int numVariables) {
        this.numVariables = numVariables;
        this.clauses = new Vector<int[]>();
    }

    public void addValues(Value[] values) {
        assert units == null;

        /* counts number of units */
        int numUnits = 0;
        for (int v = 1; v < values.length; ++v)
            if (values[v] != Value.UNKNOWN)
                ++numUnits;

        /* allocates memory */
        units = new int[numUnits];

        /* builds the clauses */
        numUnits = 0;
        for (int v = 1; v < values.length; ++v)
            if (values[v] != Value.UNKNOWN)
                units[numUnits++] = v * 2 + values[v].intValue();
    }

    public void addClause(int[] clause) {
        if (clause != null)
            clauses.add(clause);
    }
}
