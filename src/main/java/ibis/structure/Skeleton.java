package ibis.structure;

import java.util.Vector;
import java.io.Serializable;

import org.apache.log4j.Logger;


public final class Skeleton implements Serializable {
    private static final Logger logger = Logger.getLogger(Solver.class);

    public int numVariables;
    public int[][][] clauses;

    public Skeleton() {
        clauses = new int[4][][];
        for (int i = 0; i < 4; ++i)
            clauses[i] = new int[i][];
    }

    /**
     * Parses a string to produce a skeleton instance
     */
    public static Skeleton parse(String nice) {
        Vector<Integer>[][] store = new Vector[4][];
        for (int i = 0; i < 4; ++i) {
            store[i] = new Vector[i];
            for (int j = 0; j < i; ++j)
                store[i][j] = new Vector<Integer>();
        }

        /* splits nice into clauses and clauses into variables */
        String[] clauses = nice.split(" [&] ");
        int numVariables = 0;

        for (String clause: clauses) {
            if (clause.charAt(0) == '(') {
                // multiple literals
                clause = clause.substring(1, clause.length() - 1);
                String[] literalsAsString = clause.split(" [|] ");
                if (literalsAsString.length > 3) {
                    logger.error("Ignore clause (" + clause + ")" +
                                 " with too many literals.");
                    continue;
                }

                int[] literalsAsInt = new int[literalsAsString.length];
                for (int i = 0; i < literalsAsString.length; ++i) {
                    int literal = Integer.parseInt(literalsAsString[i]);
                    numVariables = Math.max(numVariables, Math.abs(literal));
                    literalsAsInt[i] = SAT.fromDimacs(literal);
                }

                Vector<Integer>[] store_ = store[literalsAsInt.length];
                for (int i = 0; i < literalsAsInt.length; ++i)
                    store_[i].add(literalsAsInt[i]);
            } else {
                // unit
                int literal = Integer.parseInt(clause);
                numVariables = Math.max(numVariables, Math.abs(literal));
                store[1][0].add(SAT.fromDimacs(literal));
            }
        }

        /* constructs the skeleton */
        Skeleton skeleton = new Skeleton();
        skeleton.numVariables = numVariables;
        for (int i = 0; i < 4; ++i)
            for (int j = 0; j < i; ++j) {
                skeleton.clauses[i][j] = new int[store[i][j].size()];
                for (int k = 0; k < store[i][j].size(); ++k)
                    skeleton.clauses[i][j][k] = store[i][j].elementAt(k);
            }

        return skeleton;
    }
}
