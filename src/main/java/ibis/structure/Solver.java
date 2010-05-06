package ibis.structure;

import java.io.IOException;
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

import org.apache.log4j.Logger;


/**
 * Solver.
 *
 */
public final class Solver {
    private static final Logger logger = Logger.getLogger(Solver.class);

    private ES.Gene gene;
    private int numVariables;
    private SetInt units;
    private MapInt<SetInt> binaries;
    private MapInt<VecInt[]> ternaries;

    public Solver(Skeleton skeleton, ES.Gene gene) {
        this.gene = gene;
        this.numVariables = skeleton.numVariables;

        this.binaries = new MapInt<SetInt>();
        this.units = binaries.setdefault(0, new SetInt());
        this.ternaries = new MapInt<VecInt[]>();

        /* units */
        for (int i = 0; i < skeleton.clauses[1][0].length; ++i)
            this.units.push(skeleton.clauses[1][0][i]);

        /* binaries */
        for (int i = 0; i < skeleton.clauses[2][0].length; ++i)
            this.addBinary(skeleton.clauses[2][0][i],
                           skeleton.clauses[2][1][i]);

        /* ternaries */
        for (int i = 0; i < skeleton.clauses[3][0].length; ++i) {
            this.addTernary(skeleton.clauses[3][0][i],
                            skeleton.clauses[3][1][i],
                            skeleton.clauses[3][2][i]);
        }
    }

    private void addBinaryHelper(int first, int second) {
        assert first != 0;
        SetInt temp = binaries.get(first);
        if (temp == null) {
            temp = new SetInt();
            binaries.put(first, temp);
        }

        temp.push(second);
    }

    /**
     * Adds a new binary.
     */
    private void addBinary(int first, int second) {
        addBinaryHelper(first, second);
        addBinaryHelper(second, first);
    }

    private void addTernaryHelper(int first, int second, int third) {
        /*
        logger.debug("xxxx " + SAT.toDimacs(first) + " " + SAT.toDimacs(second) + " " +
                SAT.toDimacs(third) + " ");*/

        VecInt[] temp = ternaries.get(first);
        if (temp == null) {
            temp = new VecInt[] {
                    new VecInt(),
                    new VecInt(),
                };
            ternaries.put(first, temp);
        }

        temp[0].push(second);
        temp[1].push(third);
    }

    /**
     * Adds a new ternary.
     */
    private void addTernary(int first, int second, int third) {
        addTernaryHelper(first, second, third);
        addTernaryHelper(second, first, third);
        addTernaryHelper(third, first, second);
    }

    /**
     * Returns true if literal is unknown.
     */
    private boolean isUnknown(int literal) {
        return !units.has(literal) && !units.has(literal ^ 1);
    }

    /**
     * Returns true if the formula is satisfied
     * using the current assignments.
     */
    public boolean isSatisfied() {
        return units.has(1);
    }

    /**
     * Returns true if the formula is a contradiction
     * using the current assignments.
     */
    public boolean isContradiction() {
        return units.has(0);
    }

    public boolean isSolved() {
        return isSatisfied() || isContradiction();
    }

    public int numUnknowns() {
        /* FIXME: units may contain 0 or 1 which are not variables */
        return numVariables - units.size();
    }

    /**
     * Returns the satisfying assignment in DIMACs format.
     */
    public int[] model() {
        assert isSatisfied();
        assert units.peekKey() == 1;

        units.pop();
        int[] model = units.keys();
        units.push(1);
        return model;
    }

    /**
     * Returns the skeleton of this solver.
     *
     * This function doesn't handle bogus clauses like (a | b | b)
     * so it may be a good idea to run a simplify() before this.
     */
    public Skeleton skeleton() {
        VecInt[] clauses;
        int[] keys;
        Skeleton skeleton = new Skeleton();
        skeleton.numVariables = numVariables;

        /* units */
        skeleton.clauses[1][0] = units.keys();

        /* binaries */
        clauses = new VecInt[] {
                new VecInt(),
                new VecInt(),
            };
        keys = binaries.keys();

        for (int first: keys) {
            if (first == 0 || !isUnknown(first))
                continue;

            int[] seconds = binaries.get(first).keys();
            for (int second: seconds)
                if (first < second && isUnknown(second)) {
                    clauses[0].push(first);
                    clauses[1].push(second);
                }
        }
        skeleton.clauses[2][0] = clauses[0].toArray();
        skeleton.clauses[2][1] = clauses[1].toArray();

        /* ternaries */
        clauses = new VecInt[] {
                new VecInt(),
                new VecInt(),
                new VecInt(),
            };
        keys = ternaries.keys();

        for (int first: keys) {
            if (!isUnknown(first))
                continue;

            VecInt[] tern = ternaries.get(first);
            for (int i = 0; i < tern[0].size(); ++i) {
                int second = tern[0].getAt(i);
                int third = tern[1].getAt(i);

                if (first < second && first < third &&
                        isUnknown(second) && isUnknown(third)) {
                    clauses[0].push(first);
                    clauses[1].push(second);
                    clauses[2].push(third);
                }
            }
        }
        skeleton.clauses[3][0] = clauses[0].toArray();
        skeleton.clauses[3][1] = clauses[1].toArray();
        skeleton.clauses[3][2] = clauses[2].toArray();

        return skeleton;
    }

    /**
     * Propagates one literal.
     *
     * @param literal literal to propagate
     */
    public void propagate(int literal) {
        propagate(literal, null, null);
        // check();
    }

    /**
     * Propagates one literal.
     *
     * @param literal literal to propagate
     * @param state stores the state to undo the propragation
     * @param propagated literals propagated
     */
    public void propagate(int literal,
                          MapInt<Integer> state,
                          SetInt propagated) {
        assert !isSolved();
        if (state != null)
            state.setdefault(0, units.size());

        VecInt[] toTry = new VecInt[] {
                new VecInt(), new VecInt()
            };
        toTry[0].push(0);
        toTry[1].push(literal);

        while (!toTry[0].isEmpty()) {
            int l0 = toTry[0].pop();
            int l1 = toTry[1].pop();
            assert l0 != 1 && l1 != 1;

            if (l0 == l1)
                l0 = 0;
            if (l0 > l1) {
                int tmp = l0;
                l0 = l1;
                l1 = tmp;
            }

            if (l0 == 0) {  /* found a unit */
                literal = l1;

                /* checks if literal is already known */
                if (units.has(literal ^ 1)) {
                    units.push(0);
                    return;
                }
                if (units.has(literal)) {
                    continue;
                }

                // logger.debug("propagating literal " + SAT.toDimacs(literal));
                units.push(literal);
                // logger.debug("units " + units);
                if (propagated != null)
                    propagated.push(literal);

                /* !literal is false, solve implications */
                SetInt bin = binaries.get(literal ^ 1);
                if (bin != null) {
                    int[] literals = bin.keys();
                    for (int literal_: literals) {
                        toTry[0].push(0);
                        toTry[1].push(literal_);
                    }
                }

                /* !literal is false, new binaries */
                VecInt[] tern = ternaries.get(literal ^ 1);
                if (tern != null) {
                    for (int t = 0; t < tern[0].size(); ++t) {
                        toTry[0].push(tern[0].getAt(t));
                        toTry[1].push(tern[1].getAt(t));
                    }
                }

            } else {  /* found a binary */
                if (units.has(l0 ^ 1) && units.has(l1 ^ 1)) {
                    /* both literals are false */
                    units.push(0);
                    return;
                }
                if (units.has(l0) || units.has(l1))
                    /* at least one literal is true */
                    continue;
                if (l0 == (l1 ^ 1))
                    /* l0 | !l0 is true */
                    continue;
                if (units.has(l0 ^ 1)) {
                    toTry[0].push(0);
                    toTry[1].push(l1);
                    continue;
                }
                if (units.has(l1 ^ 1)) {
                    toTry[0].push(0);
                    toTry[1].push(l0);
                    continue;
                }

                boolean simplified = false;
                // logger.debug("propagating binary (" + SAT.toDimacs(l0) +
                             // " | " + SAT.toDimacs(l1) + ")");

                SetInt bin0 = binaries.get(l0);
                if (bin0 != null && bin0.has(l1 ^ 1)) {
                    /* (l0 | l1) & (l0 | !l1) <=> l0 */
                    toTry[0].push(0);
                    toTry[1].push(l0);
                    simplified = true;
                }

                SetInt bin1 = binaries.get(l1, SetInt.EMPTY);
                if (bin1 != null && bin1.has(l0 ^ 1)) {
                    /* (l0 | l1) & (!l0 | l1) <=> l1 */
                    toTry[0].push(0);
                    toTry[1].push(l1);
                    simplified = true;
                }

                if (!simplified) {
                    if (state != null) {
                        state.setdefault(l0, bin0 == null ? 0 : bin0.size());
                        state.setdefault(l1, bin1 == null ? 0 : bin1.size());
                    }

                    addBinary(l0, l1);
                }
            }
        }

        if (units.size() == numVariables)
            units.push(1);
    }

    /**
     * Undoes previous propagations.
     *
     * @param state state as filled by propagate()
     */
    public void undo(MapInt<Integer> state) {
        int[] keys = state.keys();
        for (int key: keys) {
            SetInt bin = binaries.get(key);
            bin.pop(bin.size() - state.get(key));
        }
    }

    void check() {
        for (int v = 1; v < numVariables; ++v) {
            SetInt bin = binaries.get(v, new SetInt());
            /* checks for v | v, v | !v */
            assert !bin.has(v) && !bin.has(v ^ 1);

            for (int w = 1; w < numVariables; ++w)
                /* checks for (v | w) & (v | !w) */
                assert !bin.has(w) || !bin.has(w ^ 1);
        }
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
     *         1...numVariables the variable selected for branching
     */
    public int lookahead() {
        if (isSolved())
            return 0;

        /* repeats as long as the instance is simplified */
        int beforeNumUnknowns, afterNumUnknowns;
        do {
            VecInt candidates = select();
            beforeNumUnknowns = numUnknowns();

            for (int c = 0; c < candidates.size() && !isSolved(); ++c) {
                int variable = candidates.getAt(c);
                if (!isUnknown(2 * variable + 0))
                    continue;

                SetInt tUnits = new SetInt();
                SetInt fUnits = new SetInt();
                if (lookahead(variable, tUnits, fUnits))
                    continue;

                constantPropagation(tUnits, fUnits);
            }

            afterNumUnknowns = numUnknowns();
        } while (beforeNumUnknowns > afterNumUnknowns && !isSolved());

        if (isSolved())
            return 0;

        // @todo: selecting variable that appears in most ternaries.
        // this is a simple, not the best, heuristic.
        int best = -1;
        double bestVal = Double.NEGATIVE_INFINITY;
        for (int v = 1; v <= numVariables; ++v) {
            int l = 2 * v + 0;
            if (isUnknown(l)) {
                SetInt bin;
                double num2 = 1;
                VecInt[] tern;
                double num3 = 1;

                bin = binaries.get(l);
                tern = ternaries.get(l);
                num2 *= 1 + (bin == null ? 0 : bin.size());
                num3 *= 1 + (tern == null ? 0 : tern[0].size());

                bin = binaries.get(l ^ 1);
                tern = ternaries.get(l ^ 1);
                num2 *= 1 + (bin == null ? 0 : bin.size());
                num3 *= 1 + (tern == null ? 0 : tern[0].size());

                double val = gene.alpha * Math.log(num3) + Math.log(num2);
                if (val > bestVal) {
                    best = v;
                    bestVal = val;
                }
            }
        }

        assert bestVal != -1;
        return best;

        // assert false: this + " not solved, but all variables assigned!";
        // return 0;
    }

    /**
     * If a literal is true regardless of wheather another
     * literal is false or true then the literal is true.
     * These literals are discovered after a lookahead.
     */
    void constantPropagation(SetInt tUnits, SetInt fUnits) {
        int[] literals = tUnits.keys();
        int discovered = 0;
        for (int literal: literals)
            if (fUnits.has(literal)) {
                // logger.debug("discovered " + SAT.toDimacs(literal));
                ++discovered;
                propagate(literal);
            }

        //if (discovered > 0)
            //logger.debug("discovered " + discovered + " literal(s)");
    }

    /**
     * Does a lookahead on variable storing dicovered literals in fUnits
     * and tUnits.
     */
    boolean lookahead(int variable, SetInt tUnits, SetInt fUnits) {
        // logger.debug("lookup on " + variable);
        MapInt<Integer> state = new MapInt<Integer>();

        propagate(2 * variable + 0, state, tUnits);
        boolean tContradiction = isContradiction();
        if (isSatisfied())
            return true;
        undo(state);

        propagate(2 * variable + 1, state, fUnits);
        boolean fContradiction = isContradiction();
        if (isSatisfied())
            return true;
        undo(state);

        /* analyzes conflicts */
        if (fContradiction && tContradiction) {
            /* contradiction for both true and false */
            units.push(0);
            return true;
        }

        if (tContradiction) {
            /* variable must be false */
            propagate(2 * variable + 1);
            return true;
        }

        if (fContradiction) {
            /* variable must be true */
            propagate(2 * variable + 0);
            return true;
        }

        return false;
    }

    private boolean isMissing(int literal) {
        if (units.has(literal))
            return false;
        if (binaries.has(literal))
            return false;
        if (ternaries.has(literal))
            return false;
        return true;
    }

    /**
     * Selects some possible variables for lookahead.
     *
     * NB: the simplest implementation is to select all
     * unsatisfied variables.
     * @todo: detection of missing variables should be done using counts
     */
    private VecInt select() {
        VecInt candidates = new VecInt();
        int missing = 0;

        for (int v = 1; v <= numVariables; ++v) {
            int l = 2 * v + 0;

            if (isUnknown(l)) {
                if (isMissing(l)) {
                    ++missing;
                    propagate(l ^ 1);
                } else if (isMissing(l ^ 1)) {
                    ++missing;
                    propagate(l);
                } else {
                    candidates.push(v);
                }
            }
        }

        //if (missing > 0)
            //logger.debug(missing + " missing literals");
        return candidates;
    }


    private void removeBogusTernaries() {
        int[] keys = ternaries.keys();
        for (int first: keys) {
            VecInt[] dirty = ternaries.get(first);
            VecInt[] clean = new VecInt[] {
                    new VecInt(),
                    new VecInt(),
                };

            for (int i = 0; i < dirty[0].size(); ++i) {
                int second = dirty[0].getAt(i);
                int third = dirty[1].getAt(i);

                if (first == (second ^ 1) ||
                        first == (third ^ 1) ||
                        second == (third ^ 1)) {
                    /* clause contains literal | !literal */
                } else if (first == second && first == third) {
                    /* first | first | first */
                    units.push(first);
                } else if (first == second) {
                    /* first | first | third */
                    addBinary(first, third);
                } else if (first == third) {
                    /* first | second | first */
                    addBinary(first, second);
                } else if (second == third) {
                    /* first | second | second */
                    addBinary(first, second);
                } else {
                    clean[0].push(second);
                    clean[1].push(third);
                }
            }

            ternaries.put(first, clean);
        }
    }

    private void removeBogusBinaries() {
        int[] keys = binaries.keys();
        for (int first: keys) {
            if (first == 0)
                continue;

            SetInt dirty = binaries.get(first);
            SetInt clean = new SetInt();

            int[] seconds = dirty.keys();
            for (int second: seconds) {
                if (first == second) {
                    /* first | first */
                    units.push(first);
                } else if (first == (second ^ 1)) {
                    /* first | !first */
                } else if (dirty.has(second ^ 1)) {
                    /* (first | second) & (first | !second) */
                    units.push(first);
                } else {
                    clean.push(second);
                }
            }

            binaries.put(first, clean);
        }
    }

    /**
     * Propagates all units such that there is no clause
     * with at least two literals containing the units.
     */
    private void propagateUnits() {
        int[] literals = units.keys();
        units = new SetInt();
        binaries.put(0, units);
        for (int literal: literals)
            propagate(literal);
    }

    /**
     * Simplifies the expression.
     */
    public void simplify() {
        removeBogusTernaries();
        removeBogusBinaries();
        propagateUnits();
    }

    private void extend(StringBuffer result, Vector<String> parts) {
        Collections.sort(parts);
        for (int i = 0; i < parts.size(); ++i) {
            if (result.length() != 0)
                result.append(" & ");
            result.append(parts.elementAt(i));
        }
        parts.clear();
    }

    /**
     * Returns a canonical representation of the instance.
     *
     * This function is heavily used for testing and should
     * return the same format as the one accepted by Skeleton.parse().
     */
    public String toString() {
        StringBuffer result = new StringBuffer();
        Vector<String> parts = new Vector<String>();
        int[] keys;

        /* units */
        keys = units.keys();
        for (int key: keys)
            if (key != 0 && key != 1)
                parts.add("" + SAT.toDimacs(key));
        extend(result, parts);

        /* binaries */
        keys = binaries.keys();
        for (int key: keys) {
            if (key == 0 || !isUnknown(key))
                continue;

            int[] others = binaries.get(key).keys();
            for (int other: others)
                if (key < other && isUnknown(other))
                    parts.add("(" + SAT.toDimacs(key) +
                              " | " + SAT.toDimacs(other) + ")");
        }
        extend(result, parts);

        /* ternaries */
        keys = ternaries.keys();
        for (int key: keys) {
            if (key == 0 || !isUnknown(key))
                continue;

            VecInt[] others = ternaries.get(key);
            for (int i = 0; i < others[0].size(); ++i) {
                int first = others[0].getAt(i);
                int second = others[1].getAt(i);

                if (first > second) {
                    first ^= second;
                    second ^= first;
                    first ^= second;
                }

                if (key < first)
                    if (isUnknown(first) && isUnknown(second))
                        parts.add("(" + SAT.toDimacs(key) + " | " +
                                  SAT.toDimacs(first) + " | " +
                                  SAT.toDimacs(second) + ")");
            }
        }
        extend(result, parts);

        return result.toString();
    }

}

