package ibis.structure;

import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.Vector;
import java.util.Map;

import org.sat4j.reader.InstanceReader;
import org.sat4j.reader.Reader;
import org.sat4j.specs.ContradictionException;
import org.sat4j.specs.TimeoutException;
import org.sat4j.specs.IConstr;
import org.sat4j.specs.ISolver;
import org.sat4j.specs.IVec;
import org.sat4j.specs.IVecInt;
import org.sat4j.specs.SearchListener;
import org.sat4j.core.VecInt;


/**
 * SATInstance.
 *
 */
public final class SATInstance implements ISolver {
    public final static int FALSE = 0;
    public final static int TRUE = 1;
    public final static int UNKNOWN = 2;

    
    /*
     * All clauses read from the problem to be solved
     *
     * A clause is defined as (literal, literal, ..., literal).
     *
     * A literal is in SAT4J format:
     *      * positive: 2*variable + 0
     *      * negative: 2*variable + 1
     * Note that negating a literal is as simple as ^ 1
     *
     * A variable is numbered from 1 to nVars.
     *
     * 0 is false (positive false)
     * 1 is true  (negative false)
     */
    private int[][] clauses;  /* all clauses w/o any variables set */
    private int[][] copycat;  /* clauses with some variables set */
    private int nClauses;     /* number of clauses added */
    private int nVars;        /* number of variables */

    public SATInstance() {
        this.reset();
    }

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

    /**
     * Checks if a literal can be made true
     *
     * @param vars values of the variables (indexed from 1 to nVars)
     * @param l literal to be made true
     * @param true if literal is already true
     * @throws ContradictionException if assigning `l` leads to trivial contradiction
     */
    static boolean check(int[] vars, int l)
            throws ContradictionException {
        if (vars[l >> 1] == FALSE)
            if ((l & 1) == 0)
                throw new ContradictionException();
        if (vars[l >> 1] == TRUE)
            if ((l & 1) == 1)
                throw new ContradictionException();
        return vars[l >> 1] != UNKNOWN;

    }

    /**
     * Builds a copy of clauses considering assumptions and
     * unit clauses propagations
     */
    void copycat(VecInt assumps_)
            throws ContradictionException {
        VecInt assumps = new VecInt();
        assumps_.copyTo(assumps);
                
        /* makes a deep copy of the clauses */
        copycat = new int[nClauses][];
        for (int c = 0; c < nClauses; ++c)
            copycat[c] = clauses[c].clone();

        /* builds watches list */
        Vector<int[]>[] watches = new Vector[nVars + 1];
        for (int v = 1; v <= nVars; ++v)
            watches[v] = new Vector<int[]>();

        for (int c = 0; c < nClauses; ++c) {
            int[] clause = clauses[c];
            for (int l = 0; l < clause.length; ++l)
                watches[clause[l] >> 1].add(clause);
        }

        /* first, all variables value are unknown */
        int[] vars = new int[nVars + 1];
        for (int v = 1; v <= nVars; ++v)
            vars[v] = UNKNOWN;

        for (int a = 0; a < assumps.size(); ++a) {
            int v = fromDimacs(assumps.get(a));

            /* checks if the truth value of this variable is already known */
            if (check(vars, v))
                continue;

            /* variable is unknown, propagate */
            assert vars[v >> 1] == UNKNOWN;
            vars[v >> 1] = (v & 1) == 0 ? TRUE : FALSE;

            for (int[] clause: watches[v >> 1]) {
                boolean tautology = false;
                int unit = -1;  /* -1 not found, -2 at least two literals */

                for (int l = 0; l < clause.length; ++l) {
                    if ((clause[l] >> 1) == (v >> 1))
                        clause[l] = clause[l] == v ? TRUE : FALSE;
                    if (clause[l] == TRUE)
                        /* clause already marked true */
                        tautology = true;
                    else if (clause[l] >= 2)
                        /* literal might be a unit */
                        unit = unit == -1 ? clause[l] : -2;
                }

                if (!tautology) {
                    /* if this clause is not already true */
                    if (unit == -1) {
                        /* i don't have any literal to assign => contradiction */
                        throw new ContradictionException();
                    } else if (unit != -2) {
                        /* only one literal left which must be true */
                        if (!check(vars, unit))
                            assumps.push(unit);
                    }
                }

            }
        }
    }


    public void expand(VecInt assumps) {
        /* makes all assumps and reduces clauses */
        try {
            copycat(assumps);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    

    /*** ISolver methods ***/

    @Deprecated
	public int newVar() {
        throw new UnsupportedOperationException();
    }

    public int newVar(int howMany) {
        assert howMany > 0 && nVars == 0;

        nVars = howMany;
        return nVars;
    }

	public int nextFreeVarId(boolean reserve) {
        throw new UnsupportedOperationException();
    }

    public void setExpectedNumberOfClauses(int numClauses) {
        assert clauses == null;
        clauses = new int[numClauses][];
    }
    
	public IConstr addClause(IVecInt literals)
            throws ContradictionException {
        int[] clause = new int[literals.size()];

        int[] literals_ = literals.toArray();
        for (int l = literals.size(); l-- > 0;) {
            int literal = fromDimacs(literals_[l]);
            clause[l] = literal;
        }

        assert nClauses < clauses.length;
        clauses[nClauses++] = clause;

        return null;  /* intended because removeConstr(...) is not implemented */
    }

    public IConstr addBlockingClause(IVecInt literals)
            throws ContradictionException {
        throw new UnsupportedOperationException();
    }

    public boolean removeConstr(IConstr c) {
        throw new UnsupportedOperationException();
    }
    
	public boolean removeSubsumedConstr(IConstr c) {
        throw new UnsupportedOperationException();
    }

	public void addAllClauses(IVec<IVecInt> clauses)
            throws ContradictionException {
        throw new UnsupportedOperationException();
    }

	public IConstr addAtMost(IVecInt literals, int degree)
			throws ContradictionException {
        throw new UnsupportedOperationException();
    }

	public IConstr addAtLeast(IVecInt literals, int degree)
			throws ContradictionException {
        throw new UnsupportedOperationException();
    }

	public void setTimeout(int t) {
        throw new UnsupportedOperationException();
    }

	public void setTimeoutOnConflicts(int count) {
        throw new UnsupportedOperationException();
    }

	public void setTimeoutMs(long t) {
        throw new UnsupportedOperationException();
    }

	public int getTimeout() {
        throw new UnsupportedOperationException();
    }

	public long getTimeoutMs() {
        throw new UnsupportedOperationException();
    }

	public void expireTimeout() {
        throw new UnsupportedOperationException();
    }

	public void reset() {
        clauses = null;
        nVars = 0;
    }

	@Deprecated
	public void printStat(PrintStream out, String prefix) {
        throw new UnsupportedOperationException();
    }

	public void printStat(PrintWriter out, String prefix) {
        throw new UnsupportedOperationException();
    }

	public Map<String, Number> getStat() {
        throw new UnsupportedOperationException();
    }

	public String toString(String prefix) {
        throw new UnsupportedOperationException();
    }

	public void clearLearntClauses() {
        throw new UnsupportedOperationException();
    }

	public void setDBSimplificationAllowed(boolean status) {
        throw new UnsupportedOperationException();
    }

	public boolean isDBSimplificationAllowed() {
        throw new UnsupportedOperationException();
    }

	public void setSearchListener(SearchListener sl) {
        throw new UnsupportedOperationException();
    }

	public SearchListener getSearchListener() {
        throw new UnsupportedOperationException();
    }

	public boolean isVerbose() {
        throw new UnsupportedOperationException();
    }

	public void setVerbose(boolean value) {
        throw new UnsupportedOperationException();
    }

	public void setLogPrefix(String prefix) {
        throw new UnsupportedOperationException();
    }

	public String getLogPrefix() {
        throw new UnsupportedOperationException();
    }


    /*** IProblem methods ***/

    public int[] model() {
        throw new UnsupportedOperationException();
    }

    public boolean model(int var) {
        throw new UnsupportedOperationException();
    }

    public boolean isSatisfiable()
            throws TimeoutException {
        throw new UnsupportedOperationException();
    }

    public boolean isSatisfiable(IVecInt assumps, boolean globalTimeout)
            throws TimeoutException {
        throw new UnsupportedOperationException();
    }

    public boolean isSatisfiable(boolean globalTimeout)
            throws TimeoutException {
        throw new UnsupportedOperationException();
    }

    public boolean isSatisfiable(IVecInt assumps)
            throws TimeoutException {
        throw new UnsupportedOperationException();
    }

    public int[] findModel()
            throws TimeoutException {
        throw new UnsupportedOperationException();
    }

    public int[] findModel(IVecInt assumps)
            throws TimeoutException {
        throw new UnsupportedOperationException();
    }

    public int nConstraints() {
        return clauses.length;
    }

    public int nVars() {
        return nVars;
    }

    public void printInfos(PrintWriter out, String prefix) {
        throw new UnsupportedOperationException();
    }
}
