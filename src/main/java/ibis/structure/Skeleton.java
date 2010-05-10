package ibis.structure;

import java.io.Serializable;

import org.apache.log4j.Logger;


/**
 * @todo store initial number of variables
 */
public final class Skeleton implements Serializable {
    private static final Logger logger = Logger.getLogger(Solver.class);

    public int numVariables;
    public VecInt[] clauses;

    public Skeleton() {
        this.clauses = new VecInt[] {
            null,
            new VecInt(),  // units
            new VecInt(),  // binaries
            new VecInt(),  // ternaries
        };
    }

    /**
     * Parses a string to produce a skeleton instance
     */
    public static Skeleton parse(String nice) {
        Skeleton skeleton = new Skeleton();

        /* splits nice into clauses and clauses into variables */
        String[] clauses = nice.split(" [&] ");
        int numVariables = 0;

        /* divides each clause into literals */
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

                VecInt store_ = skeleton.clauses[literalsAsInt.length];
                for (int i = 0; i < literalsAsInt.length; ++i)
                    store_.push(literalsAsInt[i]);
            } else {
                // unit
                int literal = Integer.parseInt(clause);
                numVariables = Math.max(numVariables, Math.abs(literal));
                skeleton.clauses[1].push(SAT.fromDimacs(literal));
            }
        }

        skeleton.numVariables = numVariables;
        return skeleton;
    }

    /**
     * Returns the number of variables.
     */
    public int numVariables() {
        return numVariables;
    }

    /**
     * Returns the number of constraints.
     */
    public int numConstraints() {
        return clauses[2].size() + clauses[3].size();
    }

    /**
     * Returns the number of units
     */
    public int numUnits() {
        return clauses[1].size();
    }

    /**
     * Returns true if model satisfies stored instance.
     */
    public boolean testModel(int[] model) {
        SetInt units = new SetInt();
        for (int unit: model)
            units.push(unit);

        for (int i = 1; i <= 3; ++i) {
            for (int j = 0; j < clauses[i].size(); j += i) {
                boolean invalid = true;
                for (int k = 0; k < i; ++k)
                    if (units.has(clauses[i].getAt(j + k)))
                        invalid = false;
                if (invalid)
                    return false;
            }
        }

        return true;
    }
}
