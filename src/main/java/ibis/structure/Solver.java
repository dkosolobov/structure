package ibis.structure;

import gnu.trove.TIntArrayList;
import gnu.trove.TIntHashSet;
import gnu.trove.TIntIntHashMap;
import gnu.trove.TIntIntIterator;
import gnu.trove.TIntIterator;
import org.apache.log4j.Logger;
import java.text.DecimalFormat;

public final class Solver {
  private static final Logger logger = Logger.getLogger(Solver.class);
  private static final int REMOVED = Integer.MAX_VALUE;

  // The set of true literals discovered.
  private TIntHashSet units;
  // The implication graph.
  private DAG dag = new DAG();
  // Stores equalities between literals.
  private TIntIntHashMap proxies = new TIntIntHashMap();
  // List of clauses separated by 0.
  private TIntArrayList clauses;

  public Solver(Skeleton instance)
      throws ContradictionException {
    this(instance, 0);
  }

  public Solver(final Skeleton instance, final int branch)
      throws ContradictionException {
    clauses = (TIntArrayList) instance.clauses.clone();
    units = new TIntHashSet();
    if (branch != 0) {
      units.add(branch);
    }
  }

  /**
   * Returns the current instance.
   */
  public Skeleton skeleton(boolean includeUnits) {
    Skeleton skeleton = new Skeleton();
    if (includeUnits) {
      for (TIntIterator it = units.iterator(); it.hasNext();) {
        skeleton.addArgs(it.next());
      }
    }
    for (int literal : proxies.keys()) {
      int proxy = getProxy(literal);
      if (units.contains(proxy)) {
        if (includeUnits) {
          skeleton.addArgs(literal);
        }
      } else if (!units.contains(-proxy)) {
        skeleton.addArgs(literal, -proxy);
      }
    }
    skeleton.append(dag.skeleton());
    skeleton.append(clauses);
    return skeleton;
  }

  /**
   * Returns string representation of stored instance.
   */
  public String toString() {
    Skeleton skeleton = skeleton(true);
    skeleton.canonicalize();
    return skeleton.toString();
  }

  /**
   * Returns a list with all units including units from proxies.
   */
  public TIntArrayList getAllUnits() {
    TIntArrayList allUnits = new TIntArrayList();
    allUnits.add(units.toArray());
    for (int literal : proxies.keys()) {
      if (units.contains(getProxy(literal))) {
        allUnits.add(literal);
      }
    }
    return allUnits;
  }

  public int lookahead() throws ContradictionException {
    simplify();
    TIntIntHashMap counts = new TIntIntHashMap();
    for (int i = 0; i < clauses.size(); ++i) {
      counts.put(clauses.get(i), counts.get(clauses.get(i)));
    }

    int bestNode = 0;
    double bestValue = Double.NEGATIVE_INFINITY;
    for (int node : dag.nodes()) {
      if (node > 0) {
        double value =
            sigmoid((1 + dag.neighbours(node).size())
                    * (1 + dag.neighbours(-node).size()))
            + sigmoid((1 + counts.get(node)) * (1 + counts.get(-node)));
        if (value > bestValue) {
          bestNode = node;
          bestValue = value;
        }
      }
    }
    return bestNode;
  }

  /**
   * Simplifies the instance.
   */
  public void simplify() throws ContradictionException {
    logger.info("Simplyfing: " + clauses.size() + " literal(s)");
    propagateAll();

    boolean simplified = true;
    while (simplified) {
      DecimalFormat formatter = new DecimalFormat(".###");
      int numNodes = dag.numNodes();
      int numEdges = dag.numEdges();
      logger.debug("DAG has " + numNodes + " nodes and " + numEdges +
                   " edges, " + formatter.format(1. * numEdges / numNodes)
                   + " edges/node on average");
      logger.debug("Simplifying: " + clauses.size() + " literal(s), "
                  + numEdges + " binary(ies) and "
                  + getAllUnits().size() + " unit(s)");

      simplified = propagate(hyperBinaryResolution());
    }

    subSumming();
    propagate(pureLiterals());
    logger.info(clauses.size() + " literals left (excluding binaries "
                + "and units)");
  }

  /**
   * Tests if clause starting at startFirst is contained
   * in clause at startSecond.
   */
  private boolean contained(int startFirst, int startSecond) {
    for (int indexFirst = 0; ; ++indexFirst) {
      int literalFirst = clauses.get(startFirst + indexFirst);
      if (literalFirst == 0) {
        return true;
      }

      for (int indexSecond = 0; ; ++indexSecond) {
        int literalSecond = clauses.get(startSecond + indexSecond);
        if (literalSecond == 0) {
          return false;
        }
        if (literalFirst == literalSecond) {
          break;
        }
      }
    }
  }

  /**
   * Returns a hash of a.
   * This function is better than the one provided by
   * gnu.trove.HashUtils which is the same as the one
   * provided by the Java library.
   *
   * @param a integer to hash
   * @return a hash code for the given integer
   */
  private static int hash(int a) {
    a = (a + 0x7ed55d16) + (a << 12);
    a = (a ^ 0xc761c23c) ^ (a >>> 19);
    a = (a + 0x165667b1) + (a << 5);
    a = (a + 0xd3a2646c) ^ (a << 9);
    a = (a + 0xfd7046c5) + (a << 3);
    a = (a ^ 0xb55a4f09) ^ (a >>> 16);
    return a;
  }

  /**
   * Removes all clauses for which there exist
   * another clause included.
   *
   * @return true if any clause was removed.
   */
  public boolean subSumming() {
    final int numIndexBits = 8;  // must be a POT
    final int indexMask = numIndexBits - 1;

    TIntArrayList[] sets = new TIntArrayList[1 << numIndexBits];
    for (int i = 0; i < (1 << numIndexBits); ++i) {
      sets[i] = new TIntArrayList();
    }
    TIntArrayList starts = new TIntArrayList();
    TIntArrayList hashes = new TIntArrayList();
    int numClauses = 0;
    int start = 0;
    int clauseHash = 0;
    int clauseIndex = 0;

    // Puts every clause in a set to reduce the number of
    // pairs to be checked.
    for (int i = 0; i < clauses.size(); ++i) {
      int literal = clauses.get(i);
      if (literal == 0) {
        sets[clauseIndex].add(numClauses);
        starts.add(start);
        hashes.add(clauseHash);
        ++numClauses;
        start = i + 1;
        clauseHash = 0;
        clauseIndex = 0;
      } else {
        final int hash = hash(literal);
        clauseIndex |= 1 << (hash >> (32 - numIndexBits) & indexMask);
        clauseHash |= 1 << (hash >> (27 - numIndexBits) & 0x1f);
      }
    }
    starts.add(start);  // Add a sentinel.

    long numTests = 0;
    boolean simplified = false;
    for (int first = 0; first < (1 << numIndexBits); ++first) {
      for (int second = first; second < (1 << numIndexBits); ++second) {
        if ((first & second) != first) {
          continue;
        }
        numTests += (long) sets[first].size() * sets[second].size();

        for (int i = 0; i < sets[first].size(); ++i) {
          final int indexFirst = sets[first].get(i);
          final int startFirst = starts.get(indexFirst);
          final int hashFirst = hashes.get(indexFirst);

          for (int j = 0; j < sets[second].size(); ++j) {
            final int indexSecond = sets[second].get(j);
            final int startSecond = starts.get(indexSecond);
            final int hashSecond = hashes.get(indexSecond);

            if (indexFirst == indexSecond) {
              continue;
            }
            if ((hashFirst & hashSecond) != hashFirst) {
              continue;
            }
            if (contained(startFirst, startSecond)) {
              simplified = true;
              // Removes sets[second][j] by replacing it
              // with the last element.
              starts.set(indexSecond, -1);
              int last = sets[second].size() - 1;
              sets[second].set(j, sets[second].get(last));
              sets[second].remove(last);
              --j;
            }
          }
        }
      }
    }
    logger.debug("Tested " + numTests + " pairs out of "
                 + ((long) numClauses * numClauses) + " ("
                 + ((double) numTests / numClauses / numClauses) + ")");

    // Removes sub-summed clauses.
    int pos = 0, startsPos = 0;
    boolean removed = starts.get(startsPos) == -1;
    for (int i = 0; i < clauses.size(); ++i) {
      int literal = clauses.get(i);
      if (!removed) {
        clauses.set(pos++, literal);
      }
      if (literal == 0) {
        removed = starts.get(++startsPos) == -1;
      }
    }
    logger.debug("Sub-summing removed " + (clauses.size() - pos)
                 + " literals");
    if (pos < clauses.size()) {
      // BUG: TIntArrayList.remove() raises ArrayIndexOutOfBoundsException
      // if pos == clauses.size()
      clauses.remove(pos, clauses.size() - pos);
    }
    return simplified;
  }


  /**
   * Propagates every unit and every binary.
   */
  public boolean propagateAll() throws ContradictionException {
    boolean simplified = false;
    while (propagate()) {
      simplified = true;
    }
    return simplified;
  }

  /**
   * Appends and propagates new clauses.
   *
   * @return true if instance was simplified.
   */
  public boolean propagate(final TIntArrayList extraClauses)
      throws ContradictionException {
    clauses.add(extraClauses.toNativeArray());
    return propagate();
  }

  /**
   * Propagates units and binary clauses (one pass only).
   *
   * @return true if instances was simplified
   */
  public boolean propagate() throws ContradictionException {
    int start, end = clauses.size() - 1, pos = end;
    for (int i = end - 1; i >= 0; --i) {
      int literal = clauses.get(i);
      if (literal == 0 || i == 0) {
        start = i + (i != 0 ? 1 : 0);
        if (cleanClause(start, end)) {
          end = i;
          continue;
        }

        int length = 0;
        clauses.set(pos, 0);
        for (int j = end - 1; j >= start; --j) {
          int tmp = clauses.get(j);
          if (tmp != REMOVED) {
            clauses.set(pos - (++length), tmp);
          }
        }

        if (length == 0) {
          throw new ContradictionException();
        } else if (length == 1) {
          addUnit(clauses.get(pos - 1));
        } else if (length == 2) {
          addBinary(clauses.get(pos - 1), clauses.get(pos - 2));
        } else {
          pos -= length + 1;
        }

        end = i;
      }
    }

    if (pos != -1) {
      clauses.remove(0, pos + 1);
      return true;
    }
    return false;
  }

  private static double sigmoid(double x) {
    return (1 / (1 + Math.exp(-x)));
  }

  /**
   * Adds clause to clauses.
   */
  private static void pushClause(
      final TIntArrayList clauses, final int[] clause) {
    clauses.add(clause);
    clauses.add(0);
  }

  /**
   * Adds unit to clauses.
   */
  private static void pushClause(
      final TIntArrayList clauses, final int u0) {
    clauses.add(u0);
    clauses.add(0);
  }

  /**
   * Adds binary to clauses.
   */
  private static void pushClause(
      final TIntArrayList clauses, final int u0, final int u1) {
    clauses.add(u0);
    clauses.add(u1);
    clauses.add(0);
  }

  /**
   * Adds a new unit.
   *
   * @param u unit to add.
   */
  private void addUnit(int u) {
    // logger.debug("Found unit " + u);
    TIntHashSet neighbours = dag.neighbours(u);
    if (neighbours == null) {
      units.add(u);
    } else {
      int[] neighbours_ = neighbours.toArray();
      units.addAll(neighbours_);
      dag.delete(neighbours_);
    }
  }

  private void addBinary(int u, int v) {
    // logger.debug("Found binary " + u + " or " + v);
    TIntHashSet contradictions = dag.findContradictions(-u, v);
    if (!contradictions.isEmpty()) {
      int[] contradictions_ = contradictions.toArray();
      units.addAll(contradictions_);
      dag.delete(contradictions_);
      if (contradictions.contains(u) || contradictions.contains(v)) {
        return;
      }
    }

    DAG.Join join = dag.addEdge(-u, v);
    if (join != null) {
      TIntIterator it;
      for (it = join.children.iterator(); it.hasNext();) {
        int node = it.next();
        assert !proxies.contains(node) && !proxies.contains(-node);
        proxies.put(node, join.parent);
        proxies.put(-node, -join.parent);
      }
    }
  }

  /**
   * Cleans a clause.
   *
   * Checks if clause is trivial satisfied.
   * Removes falsified literals or those proved to be extraneous.
   *
   * @param start position of the first literal
   * @param end one after the position of the last literal
   * @return true if the clause was satisfied
   */
  private boolean cleanClause(int start, int end) {
    // Renames literals to component.
    for (int i = start; i < end; ++i) {
      assert clauses.get(i) != REMOVED;
      clauses.set(i, getProxy(clauses.get(i)));
    }

    // Checks if the clause is satisfied, removes unsatisfied
    // literals and does binary resolution.
    for (int i = start; i < end; ++i) {
      final int literal = clauses.get(i);
      if (literal == REMOVED) {
        continue;
      }
      if (units.contains(literal)) {
        return true;
      }
      if (units.contains(-literal)) {
        clauses.set(i, REMOVED);
        continue;
      }
      for (int k = start; k < end; ++k) {
        if (i == k) {
          continue;
        }
        final int other = clauses.get(k);
        if (other == REMOVED) {
          continue;
        }
        TIntHashSet neighbours = dag.neighbours(-literal);
        if (neighbours == null) {
          if (literal == -other) {
            // literal + -literal = true
            return true;
          }
          if (literal == other) {
            // literal + literal = literal
            clauses.set(k, REMOVED);
            continue;
          }
        } else {
          if (neighbours.contains(other)) {
            // if literal + other ... and -literal => other
            // then true
            return true;
          }
          if (neighbours.contains(-other)) {
            // if literal + other + ... and -literal => -other
            // then literal + ...
            clauses.set(k, REMOVED);
            continue;
          }
        }
      }
    }
    return false;
  }


  /**
   * Hyper-binary resolution.
   *
   * @return clauses representing binaries and units discovered.
   */
  public TIntArrayList hyperBinaryResolution() {
    int numUnits = 0, numBinaries = 0;
    int numLiterals = 0, clauseSum = 0;
    TIntIntHashMap counts = new TIntIntHashMap();
    TIntIntHashMap sums = new TIntIntHashMap();

    TIntArrayList newClauses = new TIntArrayList();
    for (int i = 0; i < clauses.size(); ++i) {
      int literal = clauses.getQuick(i);
      if (literal == 0) {
        for (TIntIntIterator it = counts.iterator(); it.hasNext(); ) {
          it.advance();
          if (it.value() == numLiterals && !units.contains(-it.key())) {
            pushClause(newClauses, -it.key());
            ++numUnits;
          } else if (it.value() == numLiterals - 1) {
            int missingLiteral = clauseSum - sums.get(it.key());
            if (!dag.containsEdge(it.key(), missingLiteral)) {
              pushClause(newClauses, -it.key(), missingLiteral);
              ++numBinaries;
            }
          }
        }
        numLiterals = 0;
        clauseSum = 0;
        counts.clear();
        sums.clear();
      } else {
        ++numLiterals;
        clauseSum += literal;
        TIntHashSet neighbours = dag.neighbours(literal);
        if (neighbours != null) {
          for (TIntIterator it = neighbours.iterator(); it.hasNext(); ) {
            int node = -it.next();
            counts.adjustOrPutValue(node, 1, 1);
            sums.adjustOrPutValue(node, literal, literal);
          }
        }
      }
    }

    logger.debug("Hyper binary resolution found " + numUnits + " unit(s) and "
                 + numBinaries + " binary(ies)");
    return newClauses;
  }

  /**
   * Pure literal assignment.
   *
   * @return clauses representing units discovered.
   */
  public TIntArrayList pureLiterals() {
    BitSet bs = new BitSet();
    for (int i = 0; i < clauses.size(); ++i) {
      bs.set(getProxy(clauses.get(i)));
    }
    for (int u : dag.nodes()) {
      if (dag.neighbours(u).size() > 1) {
        bs.set(-u);
      }
    }
    for (int u : units.toArray()) {
      bs.set(u);
    }

    // 0 is in bs, but -0 is also so
    // 0 will not be considered a pure literal.
    TIntArrayList pureLiterals = new TIntArrayList();
    int numUnits = 0;
    for (int literal : bs.elements()) {
      if (!bs.get(-literal) && !units.contains(literal)) {
        pushClause(pureLiterals, literal);
        ++numUnits;
      }
    }

    logger.debug("Discovered " + numUnits + " pure literal(s)");
    return pureLiterals;
  }

  /**
   * Recursively finds the proxy of u.
   * The returned value doesn't have any proxy.
   */
  private int getProxy(final int u) {
    if (proxies.contains(u)) {
      int v = getProxy(proxies.get(u));
      proxies.put(u, v);
      return v;
    }
    return u;
  }
}
