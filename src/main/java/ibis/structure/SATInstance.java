package ibis.structure;

import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.Serializable;
import java.util.Vector;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Iterator;

import org.sat4j.reader.InstanceReader;
import org.sat4j.reader.Reader;
import org.sat4j.specs.ContradictionException;
import org.sat4j.specs.TimeoutException;
import org.sat4j.specs.IConstr;
import org.sat4j.specs.ISolver;
import org.sat4j.specs.IVec;
import org.sat4j.specs.IVecInt;
import org.sat4j.specs.IteratorInt;
import org.sat4j.specs.SearchListener;
import org.sat4j.core.VecInt;


/**
 * SATInstance.
 *
 */
public final class SATInstance implements ISolver, Serializable {
    private static final long serialVersionUID = 10275539472837495L;
    private static final StructureLogger logger = StructureLogger.getLogger(CohortJob.class);

    enum Value {
        FALSE(0), TRUE(1), UNKNOWN(2);

        private final int intValue;

        private Value(int intValue) {
            this.intValue = intValue;        
        }

        public int intValue() {
            return intValue;
        }

        public static Value fromInt(int intValue) {
            if (intValue == 0) return FALSE;
            if (intValue == 1) return TRUE;
            if (intValue == 2) return UNKNOWN;
            throw new IllegalArgumentException();
        }
    }
    
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
     * A variable is numbered from 1 to numVariables.
     *
     * 0 is false (positive false)
     * 1 is true  (negative false)
     */
    private int numVariables;          /* number of variables */
    private int numUnknowns;           /* number of unknown variables */
    private Vector<Clause> watches[];  /* clauses containing each literal */
    private Value[] values;            /* variable of values (1-index based) */
    private int[] counts;              /* count of each literal */


    public SATInstance() {
        reset();
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
     * Sets a literal to true and propagates resulted assignments.
     *
     * @param literal literal to set true
     * @param units a vector to store propagated literals
     * @return true if the propagation returned in a contradiction
     */
    public boolean propagate(int literal, VecInt units) {
        boolean contradiction = false;

        VecInt toTry = new VecInt();
        toTry.push(literal);

        while (!contradiction && !toTry.isEmpty()) {
            literal = toTry.last();
            toTry.pop();
            
            int variable = literal >> 1;
            if (values[variable] != Value.UNKNOWN) {
                /* variable is already assigned */
                if (values[variable] != Value.fromInt(literal & 1))
                    /* assigning variable leads to contradiction */
                    contradiction = true;
                continue;
            }
            
            /* assigns variable */
            if (units != null)
                units.push(literal);
            --numUnknowns;
            values[variable] = Value.fromInt(literal & 1);

            /* literal is true */
            for (Clause clause: watches[literal ^ 0]) {
                if (!clause.isSatisfied()) {
                    for (int l: clause) {
                        --counts[l];
                        if (counts[l] == 0 && values[l >> 1] == Value.UNKNOWN)
                            /*
                             * literal is missing from formula;
                             * therefore non literal must be true
                             */
                            toTry.push(l ^ 1);
                    }
                }

                clause.setLiteral(true);    
            }
            
            /* non literal is false */
            for (Clause clause: watches[literal ^ 1]) {
                clause.setLiteral(false);

                if (clause.isContradiction()) {
                    contradiction = true;
                } else if (clause.isUnit()) {
                    for (int l: clause)
                        if (values[l >> 1] == Value.UNKNOWN)
                            /* left literal must be true */
                            toTry.push(l);
                }
            }
        } 

        check();
        return contradiction;
    }

    /**
     * Searches a new variable to branch on.
     *
     * @return 0 if the formula is a contradiction
     *         1...numVariables the variable to branch on
     */
    public int lookahead() {
        assert numUnknowns > 0;

        boolean isContradiction = false;
        VecInt candidates = select();

        for (int c = 0; c < candidates.size() && !isContradiction; ++c) {
            int variable = candidates.get(c);
            if (values[variable] != Value.UNKNOWN)
                continue;

            /* set variable to *t*rue */
            VecInt tUnits = new VecInt();
            boolean tContradiction = propagate(variable * 2 + 0, tUnits);
            undo(tUnits);

            /* set variable to *f*alse */
            VecInt fUnits = new VecInt();
            boolean fContradiction = propagate(variable * 2 + 1, fUnits);
            undo(fUnits);

            /* analyzes conflicts */
            /* TODO: at most two propagations are needed */

            if (fContradiction && tContradiction) {
                /* contradiction for both true and false */
                isContradiction = true;
                continue;
            }

            if (tContradiction) {
                /* variable must be false */
                propagate(variable, null);
                continue;
            }

            if (fContradiction) {
                /* variable must be true */
                propagate(variable, null);
                continue;
            }

            /*
             * Based on the units propagated there are three cases to analyzes
             *
             * Ia. Constant propagation
             * variable == false => other = false
             * variable == true  => other = false
             *
             * Ib. Constant propagation
             * variable == false => other = true
             * variable == true  => other = true
             *
             * II. Copy propagation (TODO: replace other with variable)
             * variable == false => other = false
             * variable == true  => other = true
             *
             * III. Reverse propagation (TODO: replace other with !variable)
             * variable == false => other = true
             * variable == true  => other = false
             */

            /* constant propagation */
            tUnits.sort();
            fUnits.sort();

            int tIndex = 0, fIndex = 0;
            while (tIndex < tUnits.size() && fIndex < fUnits.size()) {
                if (tUnits.get(tIndex) < fUnits.get(fIndex)) tIndex++;
                else if (tUnits.get(tIndex) > fUnits.get(fIndex)) fIndex++;
                else {
                    /* setting current variable true or false leads
                     * to the same assignment */
                    int literal = tUnits.get(tIndex);
                    boolean contradiction = propagate(literal, null);
                    assert !contradiction;
                }
            }

            /* TODO: euristics */
            return variable;
        }

        assert isContradiction;  /* TODO: remove */
        return 0;
    }

    /**
     * Undoes some previous assignments done using propagate(...)
     *
     * @param literals literals to undo
     */
    void undo(VecInt literals) {
        IteratorInt iterator = literals.iterator();
        while (iterator.hasNext()) {
            int literal = iterator.next();
            for (Clause clause: watches[literal ^ 0]) {
                clause.undoLiteral(true);
                if (!clause.isSatisfied())
                    for (int l: clause)
                        ++counts[l];
            }

            for (Clause clause: watches[literal ^ 1])
                clause.undoLiteral(false);

            values[literal >> 1] = Value.UNKNOWN;
            ++numUnknowns;
        }

        check();
    }

    /**
     * Checks the current object for correctens.
     *
     * TODO: throw a more meaningful exception.
     *
     * @throws RuntimeException if the object is inconsistent
     */
    void check() {
        for (int v = 1; v <= numVariables; ++v) {
            if (values[v] != Value.UNKNOWN) {
                int literal = v * 2 + values[v].intValue();
                for (Clause clause: watches[literal])
                    if (!clause.isSatisfied())
                        throw new RuntimeException("Clause not satisfied");
            }


            for (int i = 0; i < 2; ++i) {
                int literal = v * 2 + i;
                int count = 0;

                for (Clause clause: watches[literal])
                    if (!clause.isSatisfied())
                        ++count;
                if (count != counts[literal])
                    throw new RuntimeException("Invalid count for literal " +
                                               literal + " (variable " + v + ")");
            }
        }
    }


    /**
     * Selects some possible variables for lookahead.
     *
     * NB: simplest implementation is to select all variables
     */
    private VecInt select() {
        VecInt candidates = new VecInt(numVariables);
        for (int v = 1; v <= numVariables; ++v)
            candidates.push(v);
        return candidates;
    }



    /*** ISolver methods ***/

    @Deprecated
	public int newVar() {
        throw new UnsupportedOperationException();
    }

    public int newVar(int howMany) {
        if (numVariables != 0)
            throw new UnsupportedOperationException(
                    "Only one call to newVar(...) allowed");

        numVariables = howMany;
        numUnknowns = howMany;
        watches = new Vector[(numVariables + 1) * 2];
        values = new Value[numVariables + 1];
        counts = new int[(numVariables + 1) * 2];

        for (int v = 1; v <= numVariables; ++v) {
            watches[2 * v + 0] = new Vector<Clause>();
            watches[2 * v + 1] = new Vector<Clause>();
        }

        return numVariables;
    }

	public int nextFreeVarId(boolean reserve) {
        throw new UnsupportedOperationException();
    }

    public void setExpectedNumberOfClauses(int numClauses) {
        /* ignored */
    }
    
	public IConstr addClause(IVecInt literals)
            throws ContradictionException {
        Clause clause = new Clause(literals);
        for (int l = 0; l < clause.size(); ++l)
            watches[clause.get(l)].add(clause);

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
        numVariables = 0;
        numUnknowns = 0;
        watches = null;
        values = null;
        counts = null;
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
        if (numUnknowns != 0)
            return null;

        int[] model = new int[numVariables];
        for (int v = 0; v < numVariables; ++v)
            model[v] = values[v + 1].intValue();
        return model;
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
        throw new UnsupportedOperationException();
    }

    public int nVars() {
        return numVariables;
    }

    public void printInfos(PrintWriter out, String prefix) {
        throw new UnsupportedOperationException();
    }
}


final class Clause implements Iterable<Integer> {
    private final int[] literals;
    private int satisfied, unsatisfied;

    public Clause(IVecInt literals) {
        this.literals = new int[literals.size()];
        for (int l = 0; l < this.literals.length; ++l)
            this.literals[l] = SATInstance.fromDimacs(literals.get(l));
        this.satisfied = this.unsatisfied = 0;
    }

    /**
     * Checks if this clause was satisfied by at least one literal.
     */
    public boolean isSatisfied() {
        return satisfied > 0;
    }

    /**
     * Returns true if all literals are false.
     */
    public boolean isContradiction() {
        return unsatisfied == size();
    }

    /**
     * Returns true if all literals are false except one which is UNKNOWN.
     * To satisfy the clause the left literal must be set to true.
     */
    public boolean isUnit() {
        return satisfied == 0 && unsatisfied == size() - 1;
    }

    /**
     * Marks that one literal was set true or false.
     */
    public void setLiteral(boolean isSatisfied) {
        if (isSatisfied)
            satisfied += 1;
        else
            unsatisfied += 1;
        assert satisfied + unsatisfied < size();
    }

    /**
     * Undoes an assignement.
     */
    public void undoLiteral(boolean isSatisfied) {
        if (isSatisfied)
            satisfied -= 1;
        else
            unsatisfied -= 1;
        assert satisfied + unsatisfied >= 0;
    }

    /**
     * Returns the number of literals in this clause
     */
    public int size() {
        return literals.length;
    }

    public int get(int index) {
        return literals[index];
    }


    public Iterator<Integer> iterator() {
        return new Iterator<Integer>() {
            private int index = 0;

            public boolean hasNext() {
                return index < literals.length;
            }

            public Integer next() {
                if (index == literals.length)
                    throw new NoSuchElementException();
                return literals[index];
            }

            public void remove() {
                throw new UnsupportedOperationException();
            }
        };
    }

}
