package ibis.structure;

import org.apache.commons.lang.ArrayUtils;
import org.apache.log4j.Logger;
import gnu.trove.TIntArrayList;
import gnu.trove.TIntHashSet;
import gnu.trove.TIntIntHashMap;
import gnu.trove.TIntIterator;

public class Solver {
  private static final Logger logger = Logger.getLogger(Solver.class);
  private static final int REMOVED = Integer.MAX_VALUE;

  private TIntHashSet units = new TIntHashSet();
  private DAG dag = new DAG();
  private TIntIntHashMap proxies = new TIntIntHashMap();
  private TIntArrayList clauses;

  public Solver(Skeleton instance) throws ContradictionException {
    clauses = (TIntArrayList)instance.clauses.clone();
  }

  public Skeleton skeleton() {
    Skeleton skeleton = new Skeleton();
    for (TIntIterator it = units.iterator(); it.hasNext();) {
      skeleton.addArgs(it.next());
    }
    for (int literal: proxies.keys()) {
      int proxy = getProxy(literal);
      if (units.contains(proxy)) {
        skeleton.addArgs(literal);
      } else if (!units.contains(-proxy)) {
        skeleton.addArgs(-literal, proxy);
        skeleton.addArgs(literal, -proxy);
      }
    }
    skeleton.append(dag.skeleton());
    skeleton.append(clauses);
    return skeleton;
  }

  public String toString() {
    Skeleton skeleton = skeleton();
    skeleton.canonicalize();
    return skeleton.toString();
  }

  /**
   * Simplifies the instances.
   *
   * Does all unit propagations. Renames all units.
   */
  public void simplify()
      throws ContradictionException {
    TIntArrayList tmp = new TIntArrayList();
    TIntArrayList newClauses = new TIntArrayList();

    boolean simplified = true;
    while (simplified) {
      simplified = false;
      logger.info("**************************************************");
      logger.info("Simplyfing... " + clauses.size() + " literals and " +
                  units.size() + " units");

      for (int i = 0; i < clauses.size(); ++i) {
        int literal = clauses.get(i);
        if (literal != 0) {
          tmp.add(literal);
          continue;
        }

        // logger.debug("Current clause " + tmp);
        boolean satisfied = false;
        int[] clause = tmp.toNativeArray();
        tmp.clear();

        // Renames literals to component.
        for (int j = 0; j < clause.length; ++j) {
          clause[j] = getProxy(clause[j]);
        }
        // logger.debug("Renamed to " + (new TIntArrayList(clause)));

        // Checks if the clause is satisfied, removes unsatisfied
        // literals and does binary resolution.
        for (int j = 0; j < clause.length && !satisfied; ++j) {
          if (clause[j] == REMOVED) {
            continue;
          }
          if (units.contains(clause[j])) {
            satisfied = true;
            break;
          }
          if (units.contains(-clause[j])) {
            // logger.debug("Removed " + clause[j]);
            clause[j] = REMOVED;
            simplified = true;
            continue;
          }
          for (int k = 0; k < clause.length; ++k) {
            if (j == k || clause[k] == REMOVED) {
              continue;
            }
            if (clause[j] == clause[k]) {
              // j + j = j
              clause[k] = REMOVED;
              simplified = true;
              continue;
            }
            if (clause[j] == -clause[k]) {
              // j + -j = true
              satisfied = true;
              break;
            }
            if (dag.containsEdge(-clause[j], -clause[k])) {
              // if j + k + ... and -j => -k
              // then j + ...
              clause[k] = REMOVED;
              simplified = true;
              continue;
            }
          }
        }
        if (satisfied) {
          simplified = true;
          continue;
        }

        // Removes REMOVED from clause.
        int length = 0;
        for (int j = 0; j < clause.length; ++j) {
          length += clause[j] != REMOVED ? 1 : 0;
        }
        int[] cleanClause = new int[length];
        length = 0;
        for (int j = 0; j < clause.length; ++j) {
          if (clause[j] != REMOVED) {
            cleanClause[length++] = clause[j];
          }
        }
        clause = cleanClause;
        // logger.debug("Cleaned clause " + (new TIntArrayList(clause)));
      
        if (clause.length == 0) {
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
            logger.debug("Contradictions " +
                         (new TIntArrayList(contradictions.toArray())));
            TIntIterator it;
            for (it = contradictions.iterator(); it.hasNext();) {
              newClauses.add(new int[] { it.next(), 0 });
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
      newClauses.clear();
    }
  }

  private int getProxy(int u) {
    if (proxies.contains(u)) {
      int v = getProxy(proxies.get(u));
      proxies.put(u, v);
      return v;
    }
    return u;
  }

}
