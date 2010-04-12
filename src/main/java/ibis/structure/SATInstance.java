package ibis.structure;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.Serializable;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Vector;
import java.util.Arrays;
import java.util.Collections;

import org.sat4j.core.VecInt;
import org.sat4j.reader.InstanceReader;
import org.sat4j.reader.Reader;
import org.sat4j.specs.ContradictionException;
import org.sat4j.specs.IConstr;
import org.sat4j.specs.ISolver;
import org.sat4j.specs.IteratorInt;
import org.sat4j.specs.IVec;
import org.sat4j.specs.IVecInt;
import org.sat4j.specs.SearchListener;
import org.sat4j.specs.TimeoutException;


/**
 * SATInstance.
 *
 */
public final class SATInstance implements ISolver, Serializable, Cloneable {
    private static final long serialVersionUID = 10275539472837495L;
    private static final StructureLogger logger = StructureLogger.getLogger(CohortJob.class);

    enum Value {
        TRUE(0), FALSE(1), UNKNOWN(2);

        private final int intValue;

        private Value(int intValue) {
            this.intValue = intValue;        
        }

        public int intValue() {
            return intValue;
        }

        public static Value fromInt(int intValue) {
            if (intValue == 0) return TRUE;
            if (intValue == 1) return FALSE;
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
    private int numClauses;            /* number of clauses */
    private int numUnknowns;           /* number of unknown variables */
    private Vector<Clause> watches[];  /* non-satisfied clauses containing each literal */
    private Value[] values;            /* variable of values (1-index based) */
    private int[] counts;              /* count of each literal */


    public SATInstance() {
        reset();
    }

    /**
     * Returns a deep copy of this instance.
     *
     * Code adapted from: http://javatechniques.com/blog/faster-deep-copies-of-java-objects/
     */
    public SATInstance deepCopy() {
        try {
            /*
             * write the object out to a byte array
             */
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ObjectOutputStream out = new ObjectOutputStream(bos);
            out.writeObject(this);
            out.flush();
            out.close();

            /*
             * Make an input stream from the byte array and read
             * a copy of the object back in.
             */
            ObjectInputStream in = new ObjectInputStream(
                    new ByteArrayInputStream(bos.toByteArray()));
            return (SATInstance)in.readObject();
        } catch (Exception e) {
            logger.error("Cannot create a deep copy of the sat instance", e);
            return null;
        }
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
     * Returns true if the formula is satisfied
     * using the current assignments.
     */
    public boolean isSatisfied() {
        return values[0] == Value.FALSE;
    }

    /**
     * Returns true if the formula is a contradiction
     * using the current assignments.
     */
    public boolean isContradiction() {
        return values[0] == Value.TRUE;
    }

    public void assume(int literal) {
        int variable = literal >> 1;
        assert values[variable] == Value.UNKNOWN;
        --numUnknowns;
        values[variable] = Value.fromInt(literal & 1);
    }

    public boolean checkLiteral(int literal) {
        int variable = literal >> 1;
        return values[variable] == Value.fromInt(literal & 1);
    }

    /**
     * Sets a literal to true and propagates resulted assignments.
     *
     * @param literal literal to set true
     * @param units a vector to store propagated literals
     * @return true if the propagation returned in a contradiction
     */
    public boolean propagate(int literal, VecInt units) {
        // logger.debug("*** propagation starting at " + toDimacs(literal));
        // logger.debug("*** propagation on instance " + this);
        boolean contradiction = false;

        // TODO: use static buffers
        VecInt toTry = new VecInt();
        toTry.push(literal);

        while (!contradiction && !toTry.isEmpty()) {
            literal = toTry.last();
            toTry.pop();

            int variable = literal >> 1;
            if (values[variable] != Value.UNKNOWN) {
                /* variable is already assigned */
                if (!checkLiteral(literal)) {
                    /* assigning variable leads to contradiction */
                    logger.error("variable " + variable + " = " +
                            values[variable] + " versus just found " + Value.fromInt(literal & 1));
                    contradiction = true;
                }
                continue;
            }

            // logger.debug("propagating literal " + toDimacs(literal));
            // logger.debug("current instance " + this);
            
            /* assigns variable */
            if (units != null)
                units.push(literal);
            assume(literal);

            /* literal is true */
            for (Clause clause: watches[literal ^ 0]) {
                if (!clause.isSatisfied())
                    for (int l: clause)
                        --counts[l];
                clause.setLiteral(true);    
            }
            
            /* non literal is false */
            for (Clause clause: watches[literal ^ 1]) {
                clause.setLiteral(false);

                if (clause.isContradiction()) {
                    contradiction = true;
                } else if (clause.isUnit()) {
                    for (int l: clause)
                        if (values[l >> 1] == Value.UNKNOWN) {
                            /* left literal must be true */
                            // logger.debug("found unit " + toDimacs(l));
                            toTry.push(l);
                        }
                }
            }
        } 

        // logger.debug("propagation ended: " + contradiction + " --- " + units);
        // check();
        return contradiction;
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

        // check();
    }

    boolean lookahead(int variable, VecInt tUnits, VecInt fUnits) {
        /* set variable to *t*rue */
        boolean tContradiction = propagate(variable * 2 + 0, tUnits);
        if (!tContradiction && numUnknowns == 0) {
            /* solution discovered */
            values[0] = Value.FALSE;
            // logger.debug("maybe 1");
            return true;
        }
        undo(tUnits);

        /* set variable to *f*alse */
        boolean fContradiction = propagate(variable * 2 + 1, fUnits);
        if (!fContradiction && numUnknowns == 0) {
            /* solution discovered */
            values[0] = Value.FALSE;
            // logger.debug("maybe 2");
            return true;
        }
        undo(fUnits);

        /* analyzes conflicts */
        if (fContradiction && tContradiction) {
            /* contradiction for both true and false */
            values[0] = Value.TRUE;
            // logger.debug("maybe 3");
            return true;
        }

        if (tContradiction) {
            /* variable must be false */
            propagate(2 * variable + 1, null);
            // logger.debug("maybe 4");
            return true;
        }

        if (fContradiction) {
            /* variable must be true */
            propagate(2 * variable + 0, null);
            // logger.debug("maybe 5");
            return true;
        }

        return false;
    }

    /**
     * Propagates a variable which appears in a single form.
     *
     * If variable appears only as a positive literal
     * or only as a negative literal then if the formula
     * is satisfiable then there exists an assignment
     * of variables satisfying the formula such that
     * the literal is true.
     *
     * @param variable variable to propagate
     * @return true if variable was propagated
     */
    boolean propagateIfMissing(int variable) {
        if (counts[2 * variable + 1] == 0) {
            // logger.debug("missing -" + variable);
            propagate(variable * 2 + 0, null);
            return true;
        } else if (counts[2 * variable + 0] == 0) {
            // logger.debug("missing +" + variable);
            propagate(variable * 2 + 1, null);
            return true;
        }

        return false;
    }

    /**
     * Searches a new variable to branch on.
     *
     * Looking ahead may results in free assignments or replacement
     * making the instances easier to solve.
     * Based on the literal units resulted from assigment a variable
     * true and false there are three cases to analyze:
     *
     * TODO: heuristics
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
     *
     * variable == true  => other = false
     *
     * @return 0 if the formula is a contradiction or has been satisfied
     *         1...numVariables the variable to branch on
     */
    public int lookahead() {
        assert !isSatisfied() && !isContradiction();

        int currentNumUnknowns;
        int decision;

        /* repeats as long as new units are discovered */
        do {
            currentNumUnknowns = numUnknowns;
            decision = 0;

            VecInt candidates = select();
            //logger.debug("Selected variables are " + candidates);

            for (int c = 0; c < candidates.size() &&
                    values[0] == Value.UNKNOWN; ++c) {

                int variable = candidates.get(c);

                if (values[variable] != Value.UNKNOWN)
                    continue;
                if (propagateIfMissing(variable))
                    continue;

                VecInt tUnits = new VecInt();
                VecInt fUnits = new VecInt();
                if (lookahead(variable, tUnits, fUnits))
                    continue;

                tUnits.sort();
                fUnits.sort();

                constantPropagation(tUnits, fUnits);
                propagateIfMissing(variable);

                if (values[variable] == Value.UNKNOWN)
                    decision = variable;
            }

            if (numUnknowns == 0)
                values[0] = Value.FALSE;

            // logger.debug(currentNumUnknowns + " versus now " + numUnknowns);
        } while (currentNumUnknowns != numUnknowns
                && values[0] == Value.UNKNOWN);

        if (values[0] != Value.UNKNOWN)
            return 0;

        assert values[decision] == Value.UNKNOWN;
        return decision;
    }

    public String toString() {
        HashSet<Clause> clauses = new HashSet<Clause>();
        Vector<String> text = new Vector<String>();

        for (int v = 1; v < numVariables; ++v) {
            for (int i = 0; i < 2; ++i) 
                for (Clause clause: watches[v * 2 + i]) {
                    if (clauses.contains(clause))
                        continue;
                    clauses.add(clause);

                    String tmp = clause.toString(values);
                    if (tmp.length() != 0)
                        text.add(tmp);
                }
        }

        Collections.sort(text);
        StringBuffer result = new StringBuffer();
        for (String t: text) {
            if (result.length() != 0)
                result.append(" & ");
            result.append(t);
        }

        result.append(" [numUnknowns = " + numUnknowns + ", hashCode = " +
                      result.toString().hashCode() + "]");
        return result.toString();
    }

    VecInt convert(VecInt spam) {
        VecInt egg = new VecInt();
        for (int i = 0; i < spam.size(); ++i)
            egg.push(toDimacs(spam.get(i)));
        return egg;
    }

    /**
     * Propagates all common literals resulted from looking
     * ahead on a variable.
     *
     * @param tUnits sorted unit literals from looking ahead on true
     * @param fUnits sorted unit literals from looking ahead on false
     */
    void constantPropagation(VecInt tUnits, VecInt fUnits) {
        int tIndex = 0, fIndex = 0;
        while (tIndex < tUnits.size() && fIndex < fUnits.size()) {
            if (tUnits.get(tIndex) < fUnits.get(fIndex)) tIndex++;
            else if (tUnits.get(tIndex) > fUnits.get(fIndex)) fIndex++;
            else {
                /* setting current variable true or false leads
                 * to the same assignment */
                int literal = tUnits.get(tIndex);
                boolean contradiction = propagate(literal, null);
                assert !contradiction: "Propagating common literal " +
                        toDimacs(literal) + " resulted in contradiction";

                ++tIndex;
                ++fIndex;
            }
        }
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
                        throw new RuntimeException(
                                "Literal " + toDimacs(literal) + " is true, " +
                                "but clause " + clause + " is not satisfied");
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

        values[0] = Value.UNKNOWN;
        for (int v = 1; v <= numVariables; ++v) {
            values[v] = Value.UNKNOWN;
            watches[2 * v + 0] = new Vector<Clause>();
            watches[2 * v + 1] = new Vector<Clause>();
        }

        return numVariables;
    }

	public int nextFreeVarId(boolean reserve) {
        throw new UnsupportedOperationException();
    }

    public void setExpectedNumberOfClauses(int numClauses) {
        this.numClauses = numClauses;
    }
    
	public IConstr addClause(IVecInt literals)
            throws ContradictionException {
        Clause clause = new Clause(literals);
        for (int l: clause) {
            watches[l].add(clause);
            ++counts[l];
        }

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
        if (!isSatisfied())
            return null;

        int[] model = new int[numVariables];
        for (int v = 1; v <= numVariables; ++v)
            model[v - 1] = v * (2 * values[v].intValue() - 1);
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
        return numClauses;
    }

    public int nVars() {
        return numVariables;
    }

    public void printInfos(PrintWriter out, String prefix) {
        throw new UnsupportedOperationException();
    }
}


final class Clause implements Iterable<Integer>, Serializable {
    private static final long serialVersionUID = -3868323690628118329L;

    private final int[] literals;
    private int satisfied, unsatisfied;

    public Clause(IVecInt literals) {
        this.literals = new int[literals.size()];
        for (int l = 0; l < this.literals.length; ++l)
            this.literals[l] = SATInstance.fromDimacs(literals.get(l));
        Arrays.sort(this.literals);
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
        assert satisfied + unsatisfied <= size();
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

    public String toString(SATInstance.Value[] values) {
        if (values != null && isSatisfied())
            return "";
        if (values != null && isContradiction())
            return "0";

        StringBuffer result = new StringBuffer();

        boolean first = true;
        for (int l: literals) {
            if (values != null)
                if (values[l >> 1] != SATInstance.Value.UNKNOWN)
                    continue;

            if (first)
                result.append('(');
            else
                result.append(" | ");
            first = false;

            if ((l & 1) != 0)
                result.append('-');
            result.append("" + (l >> 1));
        }

        if (!first)
            result.append(')');
        return result.toString();
    }

    public String toString() {
        return toString(null);
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
                return literals[index++];
            }

            public void remove() {
                throw new UnsupportedOperationException();
            }
        };
    }

}
