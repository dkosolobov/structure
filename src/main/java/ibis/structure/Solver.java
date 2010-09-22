package ibis.structure;

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
    units = extractUnits(clauses);
    if (branch != 0) {
      if (units.contains(-branch)) {
        throw new ContradictionException();
      }
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
    TIntArrayList newClauses = new TIntArrayList();

    boolean simplified = true;
    while (simplified) {
      simplified = false;
      logger.info("Simplyfing... " + clauses.size() + " literal(s) and "
                  + units.size() + " unit(s)");
      if (clauses.isEmpty()) {
        break;
      }

      while (true) {
        int[] clause = popClause(clauses);
        if (clause == null) {
          break;
        }
        clause = cleanClause(clause);

        if (clause == null) {
          continue;
        } else if (clause.length == 0) {
          // All literals were falsified.
          throw new ContradictionException();
        } else if (clause.length == 1) { // Found a unit.
          simplified = true;
          TIntHashSet neighbours = dag.neighbours(clause[0]);
          if (neighbours == null) {
            units.addAll(clause);
          } else {
            units.addAll(neighbours.toArray());
            dag.delete(neighbours);
          }
        } else if (clause.length == 2) { // Found a binary.
          simplified = true;
          int u = clause[0];
          int v = clause[1];
          // logger.debug("Found binary {" + u + ", " + v + "}");

          TIntHashSet contradictions = dag.findContradictions(-u, v);
          if (contradictions.isEmpty()) {
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
          } else {
            pushClause(clauses, new int[] {u, v});
            for (TIntIterator it = contradictions.iterator(); it.hasNext();) {
              // Adds units as clauses to be processed next.
              pushClause(clauses, new int[] {it.next()});
            }
          }
        } else {
          // Found a clause with at least 3 literals.
          if (hyperBinaryResolution(newClauses, clause)) {
            simplified = true;
          }
          pushClause(newClauses, clause);
        }
      }

      TIntArrayList swap = clauses;
      clauses = newClauses;
      newClauses = swap;
    }
  }

  private static double sigmoid(double x) {
    return (1 / (1 + Math.exp(-x)));
  }

  /**
   * Removes and returns a clause from the list.
   * If clauses is empty returns null.
   */
  private static int[] popClause(
      final TIntArrayList clauses) {
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

  /**
   * Finds all units in clauses.
   *
   * @param clauses the list of clauses
   * @return a set with all units in clauses
   * @throws ContradictionException if a trivial contradiction is found
   */
  private static TIntHashSet extractUnits(final TIntArrayList clauses)
      throws ContradictionException {
    TIntHashSet units = new TIntHashSet();
    int pos = 0;
    while (true) {
      int next = clauses.indexOf(pos, 0);
      if (next == -1) {
        break;
      }
      if (next == pos + 1) {
        int literal = clauses.get(pos);
        if (units.contains(-literal)) {
          throw new ContradictionException();
        }
        units.add(literal);
      }
      pos = next + 1;
    }
    logger.debug("Found " + units.size() + " units");
    return units;
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

  /**
   * Hyper-binary resolution.
   *
   * @return true if an unit or a binary was discovered.
   */
  private boolean hyperBinaryResolution(
      final TIntArrayList clauses, final int[] clause) {
    assert clause.length >= 3;
    boolean simplified = false;

    int[] nonLiterals = new int[clause.length];
    for (int i = 0; i < clause.length; ++i) {
      nonLiterals[i] = -clause[i];
    }

    for (int i = 0; i < 2; ++i) {
      nonLiterals[i ^ 1] = nonLiterals[i];

      TIntHashSet neighbours = dag.neighbours(clause[i]);
      if (neighbours == null) {
        continue;
      }

      for (TIntIterator it = neighbours.iterator(); it.hasNext(); ) {
        int node = it.next();
        TIntHashSet nodeNeighbours = dag.neighbours(-node);
        if (nodeNeighbours.containsAll(nonLiterals)) {
          if (!nodeNeighbours.contains(clause[i ^ 1])) {
            pushClause(clauses, new int [] { node, clause[i ^ 1] });
            simplified = true;
          }
        }
      }

      nonLiterals[i ^ 1] = -clause[i ^ 1];
    }
    return simplified;
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
