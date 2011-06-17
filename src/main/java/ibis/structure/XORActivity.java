package ibis.structure;

import gnu.trove.list.array.TIntArrayList;
import ibis.constellation.ActivityIdentifier;
import ibis.constellation.Event;
import org.apache.log4j.Logger;

/**
 * Extracts XOR gates and performs Dependent Variable Elimination
 * on the instance.
 */
public final class XORActivity extends Activity {
  private static final Logger logger = Logger.getLogger(XORActivity.class);

  /** Object used to restore solution after Dependent Variable Elimination. */
  private TIntArrayList dve;

  public XORActivity(final ActivityIdentifier parent,
                     final int depth,
                     final Skeleton instance) {
    super(parent, depth, 0, instance);
  }

  @Override
  public void initialize() {
    if (!Configure.xor) {
      executor.submit(new BlockedClauseEliminationActivity(
            parent, depth, instance));
      finish();
      return;
    }

    try {
      TIntArrayList xorGates = XOR.extractGates(instance.formula);
      dve = DependentVariableElimination.run(
          instance.numVariables, instance.formula, xorGates);
      instance.formula.addAll(xorGates);
    } catch (ContradictionException e) {
      reply(Solution.unsatisfiable());
      finish();
      return;
    }

    executor.submit(new BlockedClauseEliminationActivity(
          identifier(), depth, instance));
    suspend();
  }

  @Override
  public void process(final Event e) throws Exception {
    Solution response = (Solution) e.data;
    response = DependentVariableElimination.restore(dve, response);
    reply(response);
    finish();
  }
}
