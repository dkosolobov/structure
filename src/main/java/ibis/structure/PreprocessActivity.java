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
    super(parent, depth, 0, instance);
  }

  public void initialize() throws Exception {
    Solver solver = null;
    Solution solution = null;
    
    try {
      if (Configure.xor) {
        TIntArrayList xorGates = XOR.extractGates(instance.formula);
        dve = DependentVariableElimination.run(
            instance.numVariables, instance.formula, xorGates);
        logger.info("After DVE: " + xorGates.size() + " literals in xorGates");
        instance.formula.addAll(xorGates);
      }

      solver = new Solver(instance, 0);
      solution = solver.solve(true);
    } catch (ContradictionException e) {
      solution = Solution.unsatisfiable();
    } catch (Throwable e) {
      logger.error("Failed to solve instance", e);
    }

    if (!solution.isUnknown()) {
      solution = DependentVariableElimination.restore(dve, solution);
      reply(solution);
      finish();
      return;
    }

    if (Configure.bce) {
      bce = BlockedClauseElimination.run(solver);
    }

    core = solver.core();
    executor.submit(new VariableEliminationActivity(
          identifier(), depth, core.instance()));

    gc();
    suspend();
  }

  public void process(final Event e) throws Exception {
    Solution response = (Solution) e.data;
    response = BlockedClauseElimination.restore(bce, response);
    response = core.merge(response);
    response = DependentVariableElimination.restore(dve, response);
    reply(response);
    finish();
  }
}
