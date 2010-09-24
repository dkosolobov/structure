package ibis.structure;

import java.util.BitSet;
import gnu.trove.TIntArrayList;
import gnu.trove.TIntHashSet;
import gnu.trove.TIntIntHashMap;
import gnu.trove.TIntIterator;
import org.apache.log4j.Logger;

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
      logger.info("Simplyfing: " + clauses.size() + " literal(s), "
                  + dag.size() + " binary(ies) and "
                  + units.size() + " unit(s)");
      simplified = false;

      TIntArrayList newClauses = hyperBinaryResolution();
      if (!newClauses.isEmpty()) {
        simplified = true;
        clauses.add(newClauses.toNativeArray());
        propagate();
      }

      TIntArrayList literals = pureLiterals();
      if (!literals.isEmpty()) {
        simplified = true;
        for (int i = 0; i < literals.size(); ++i) {
          addUnit(literals.get(i));
        }
        propagate();
      }
    }
  }

  public boolean propagateAll() throws ContradictionException {
    boolean simplified = false;
    while (propagate()) {
      simplified = true;
    }
    return simplified;
  }

  public boolean propagate() throws ContradictionException {
    if (clauses.isEmpty()) {
      return false;
    }

    boolean simplified = false;
    TIntArrayList oldClauses = clauses;
    clauses = new TIntArrayList();
    while (!oldClauses.isEmpty()) {
      int[] clause = popClause(oldClauses);
      clause = cleanClause(clause);

      if (clause == null) {
        // Clause was satisfied.
        continue;
      } else if (clause.length == 0) {
        // All literals were falsified.
        throw new ContradictionException();
      } else if (clause.length == 1) {
        simplified = true;
        addUnit(clause[0]);
      } else if (clause.length == 2) {
        simplified = true;
        addBinary(clause[0], clause[1]);
      } else {
        // Found a clause with at least 3 literals.
        pushClause(clauses, clause);
      }
    }

    return simplified;
  }

  private static double sigmoid(double x) {
    return (1 / (1 + Math.exp(-x)));
  }

  /**
   * Removes and returns a clause from the list.
   * If clauses is empty returns null.
   */
  private static int[] popClause(final TIntArrayList clauses) {
    int size = clauses.size();
    if (size == 0) {
      return null;
    }

    int offset = clauses.lastIndexOf(size - 2, 0) + 1;
    int[] clause = clauses.toNativeArray(offset, size - offset - 1);
    clauses.remove(offset, size - offset);
    return clause;
  }

  /**
   * Adds clause to clauses.
   */
  private static void pushClause(
      final TIntArrayList clauses, final int[] clause) {
    clauses.add(clause);
    clauses.add(0);
  }

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
   * @param clause clause to clean
   * @return cleaned clause or null if clause is satisfied
   */
  private int[] cleanClause(final int[] clause) {
    // Renames literals to component.
    for (int j = 0; j < clause.length; ++j) {
      clause[j] = getProxy(clause[j]);
    }

    // Checks if the clause is satisfied, removes unsatisfied
    // literals and does binary resolution.
    for (int j = 0; j < clause.length; ++j) {
      if (clause[j] == REMOVED) {
        continue;
      }
      if (units.contains(clause[j])) {
        return null;
      }
      if (units.contains(-clause[j])) {
        clause[j] = REMOVED;
        continue;
      }
      for (int k = 0; k < clause.length; ++k) {
        if (j == k || clause[k] == REMOVED) {
          continue;
        }
        if (clause[j] == clause[k]) {
          // j + j = j
          clause[k] = REMOVED;
          continue;
        }
        if (clause[j] == -clause[k]) {
          // j + -j = true
          return null;
        }
        TIntHashSet neighbours = dag.neighbours(-clause[j]);
        if (neighbours == null) {
          continue;
        }
        if (neighbours.contains(clause[k])) {
          // if j + k ... and -j => k
          // then true
          return null;
        }
        if (neighbours.contains(-clause[k])) {
          // if j + k + ... and -j => -k
          // then j + ...
          clause[k] = REMOVED;
          continue;
        }
      }
    }

    // Removes REMOVED from clause.
    int length = 0;
    for (int j = 0; j < clause.length; ++j) {
      if (clause[j] != REMOVED) {
        length += 1;
      }
    }
    if (length != clause.length) {
      int[] tmp = new int[length];
      length = 0;
      for (int j = 0; j < clause.length; ++j) {
        if (clause[j] != REMOVED) {
          tmp[length++] = clause[j];
        }
      }
      return tmp;
    }
    return clause;
  }

  private final int convert(int a) {
    return a < 0 ? (1 - a - a) : a + a;
  }

  private final int deconvert(int a) {
    return (a & 1) != 0 ? - (a - 1) / 2 : a / 2;
  }

  /**
   * Hyper-binary resolution.
   *
   * @return true if an unit or a binary was discovered.
   */
  private TIntArrayList hyperBinaryResolution() {
    int[] unit = new int[1], binary = new int[2];
    int numUnits = 0, numBinaries = 0;

    TIntArrayList newClauses = new TIntArrayList();
    for (int node: dag.nodes()) {
      // Usign bitset insted of hashset operations is somehow faster.
      TIntHashSet neighbours = dag.neighbours(-node);
      BitSet bs = new BitSet();
      for (TIntIterator it = neighbours.iterator(); it.hasNext(); ) {
        bs.set(convert(it.next()));
      }

      int numMissing = 0;
      int missingLiteral = 0;
      for (int i = 0; i < clauses.size(); ++i) {
        int literal = clauses.get(i);
        if (literal == 0) {
          if (numMissing == 0) {
            unit[0] = node;
            pushClause(newClauses, unit);
            ++numUnits;
            break;
          } else if (numMissing == 1 && !bs.get(convert(missingLiteral))) {
            binary[0] = node;
            binary[1] = missingLiteral;
            pushClause(newClauses, binary);
            ++numBinaries;
          }
          numMissing = 0;
        } else if (numMissing < 2 && !bs.get(convert(-literal))) {
          missingLiteral = literal;
          ++numMissing;
        }
      }
    }
    logger.debug("Hyper binary resolution found " + numUnits + " unit(s) and "
                 + numBinaries + " binary(ies)");
    return newClauses;
  }

  /**
   * Pure literal assignment.
   */
  public TIntArrayList pureLiterals() {
    BitSet bs = new BitSet();
    for (int i = 0; i < clauses.size(); ++i) {
      bs.set(convert(clauses.get(i)));
    }
    for (int u: dag.nodes()) {
      if (dag.neighbours(u).size() > 1) {
        bs.set(convert(-u));
      }
    }
    for (int u: units.toArray()) {
      bs.set(convert(u));
    }

    bs.clear(0);
    TIntArrayList pureLiterals = new TIntArrayList();
    for (int pos = bs.nextSetBit(0); pos >= 0; pos = bs.nextSetBit(pos + 1)) {
      if (!bs.get(pos ^ 1)) {
        int literal = deconvert(pos);
        if (!units.contains(literal)) {
          pureLiterals.add(literal);
        }
      }
    }

    logger.debug("Discovered " + pureLiterals.size() + " pure literal(s)");
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
