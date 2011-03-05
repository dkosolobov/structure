package ibis.structure;

import gnu.trove.list.array.TIntArrayList;
import ibis.constellation.ActivityIdentifier;
import ibis.constellation.Event;
import org.apache.log4j.Logger;

/**
 * Preprocesses the input instance.
 *
 * The proprocessing performed are.
 * <ul>
 * <li>Extracts xor gates.</li>
 * <li>Removes dependent variables</li>
 * <li>Simplifies the formula</li>
 * <li>Removes blocked clauses</li>
 * </ul>
 */
public final class PreprocessActivity extends Activity {
  private static final Logger logger = Logger.getLogger(PreprocessActivity.class);

  private TIntArrayList xorGates;
  private TIntArrayList dve;
  private TIntArrayList bce;
  private Core core;

  public PreprocessActivity(final ActivityIdentifier parent,
                            final int depth,
                            final Skeleton instance) {
    super(parent, depth, instance);
  }

  public void initialize() throws Exception {
    Solver solver = null;
    Solution solution = null;
    
    try {
      if (Configure.xor) {
        xorGates = XOR.extractGates(instance.formula);
        logger.info("After XOR there are " + xorGates.size() + " literals in xorGates");
        if (Configure.dve) {
          dve = DependentVariableElimination.run(
              instance.numVariables, instance.formula, xorGates);
          logger.info("After DVE there are " + xorGates.size() + " literals in xorGates");
        }
        instance.formula.addAll(xorGates);
      }

      solver = new Solver(instance);
      solution = solver.solve(0);
    } catch (ContradictionException e) {
      solution = Solution.unsatisfiable();
    }

    if (!solution.isUnknown()) {
      solution = DependentVariableElimination.restore(dve, solution);
      verify(solution, 0);
      reply(solution);
      finish();
      return;
    }

    if (Configure.bce) {
      bce = BlockedClauseElimination.run(solver);
    }

    core = solver.core();
    executor.submit(new SplitActivity(identifier(), depth, core.instance()));
  }

  public void process(final Event e) throws Exception {
    Solution response = (Solution) e.data;
    response = core.merge(response);
    response = BlockedClauseElimination.restore(bce, response);
    response = DependentVariableElimination.restore(dve, response);
    verify(response, 0);
    reply(response);
    finish();
  }
}
