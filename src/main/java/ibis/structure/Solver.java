package ibis.structure;

import org.apache.commons.lang.ArrayUtils;
import org.apache.log4j.Logger;
import gnu.trove.TIntArrayList;
import gnu.trove.TIntHashSet;
import gnu.trove.TIntIntHashMap;
import gnu.trove.TIntIterator;

public final class Solver {
  private static final Logger logger = Logger.getLogger(Solver.class);
  private static final int REMOVED = Integer.MAX_VALUE;

  private TIntHashSet units = new TIntHashSet();
  private DAG dag = new DAG();
  private TIntIntHashMap proxies = new TIntIntHashMap();
  private TIntArrayList clauses;

  public Solver(Skeleton instance, int branch) {
    clauses = (TIntArrayList)instance.clauses.clone();
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
    for (int literal: proxies.keys()) {
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

  public String toString() {
    Skeleton skeleton = skeleton(true);
    skeleton.canonicalize();
    return skeleton.toString();
  }

  /**
   * Returns a list with all units including units from proxies.
   */
  public TIntArrayList getAllUnits() {
    TIntArrayList units = new TIntArrayList();
    units.add(this.units.toArray());
    for (int literal: proxies.keys()) {
      if (this.units.contains(getProxy(literal))) {
        units.add(literal);
      }
    }
    return units;
  }

  public int lookahead() throws ContradictionException {
    simplify();
    TIntIntHashMap counts = new TIntIntHashMap();
    for (int i = 0; i < clauses.size(); ++i) {
      counts.put(clauses.get(i), counts.get(clauses.get(i)));
    }

    int bestNode = 0;
    double bestValue = Double.NEGATIVE_INFINITY;
    for (int node: dag.nodes()) {
      if (node > 0) {
        double value =
            sigmoid((1 + dag.neighbours(node).size()) * 
                    (1 + dag.neighbours(-node).size())) +
            sigmoid((1 + counts.get(node)) * 
                    (1 + counts.get(-node)));
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
    TIntArrayList tmp = new TIntArrayList();
    TIntArrayList newClauses = new TIntArrayList();

    boolean simplified = true;
    while (simplified) {
      simplified = false;
      logger.info("Simplyfing... " + clauses.size() + " literals and " +
                  units.size() + " units");

      clauses.remove(clauses.size() - 1);
      while (true) {
        // Pops one literal.
        int literal;
        int size = clauses.size();
        if (size == 0) {
          if (tmp.isEmpty()) {
            break;
          }
          literal = 0;
        } else {
          literal = clauses.get(size - 1);
          clauses.remove(size - 1);
        }
        if (literal != 0) {
          tmp.add(literal);
          continue;
        }

        int[] clause = cleanClause(tmp.toNativeArray());
        tmp.clear();

        if (clause == null) {
          continue;
        } else if (clause.length == 0) {
          // All literals were falsified.
          throw new ContradictionException();
        } else if (clause.length == 1) {  // Found a unit.
          simplified = true;
          TIntHashSet neighbours = dag.neighbours(clause[0]);
          if (neighbours == null) {
            neighbours = new TIntHashSet();
            neighbours.add(clause[0]);
          }
          units.addAll(neighbours.toArray());
          dag.delete(neighbours);
          // logger.debug("Found unit(s) " + (new TIntArrayList(set.toArray())) +
                       // ", " + units.size() + " unit(s) discovered");
        } else if (clause.length == 2) {  // Found a binary.
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
            TIntIterator it;
            for (it = contradictions.iterator(); it.hasNext();) {
              // Adds units as clauses to be processed next.
              clauses.add(new int[] { 0, it.next() });
            }
            newClauses.add(new int[] { u, v, 0 });
          }
        } else {
          // Found a clause with at least 3 literals.
          newClauses.add(clause);
          newClauses.add(0);
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
   * @return cleand clause or null if clause is satisfied
   */
  private int[] cleanClause(int[] clause) {
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
        if (dag.containsEdge(-clause[j], -clause[k])) {
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
      length += clause[j] != REMOVED ? 1 : 0;
    }
    if (length != clause.length) {
      int[] tmp = new int[length];
      length = 0;
      for (int j = 0; j < clause.length; ++j) {
        if (clause[j] != REMOVED) {
          tmp[length++] = clause[j];
        }
      }
      clause = tmp;
    }
    return clause;
  }

  /**
   * Returns the proxy of u.
   * The returned value doesn't have any proxy.
   */
  private final int getProxy(int u) {
    if (proxies.contains(u)) {
      int v = getProxy(proxies.get(u));
      proxies.put(u, v);
      return v;
    }
    return u;
  }

}
