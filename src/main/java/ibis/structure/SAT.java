package ibis.structure;

/**
 * SAT.
 * Contains some useful functions.
 *
 * All clauses read from the problem to be solved
 *
 * A clause is defined as (literal, literal, ..., literal).
 *
 * A literal is in SAT4J format:
 *      * positive: 2*variable + 0
 *      * negative: 2*variable + 1
 * Note that negating a literal is as simple as ^ 1
 *
 * A variable is numbered from 1 to numVariables.
 *
 * 0 is false (positive false)
 * 1 is true  (negative false)
 */
public final class SAT {
    /**
     * Converts a literal from DIMACS to SAT4J format (see above).
     *
     * @param literal a literal in DIMACS format
     * @return literal in SAT4J format
     */
    public static int toDimacs(int literal) {
        if ((literal & 1) == 0)
            return + (literal >> 1);
        else
            return - (literal >> 1);
    }

    /**
     * Converts a literal from DIMACS to SAT4J format (see above).
     *
     * @param literal a literal in SAT4J format
     * @return literal in DIMACS format
     */
    public static int fromDimacs(int literal) {
        if (literal > 0)
            return + literal * 2 + 0;
        else
            return - literal * 2 + 1;
    }
}
