package ibis.structure;

import gnu.trove.list.array.TIntArrayList;
import gnu.trove.list.array.TDoubleArrayList;
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
                     final ActivityIdentifier tracer,
                     final Skeleton instance) {
    super(parent, tracer, 0, 0, null, instance);
  }

  @Override
  public void initialize() {
    if (!Configure.xor) {
      executor.submit(new BlockedClauseEliminationActivity(
            parent, tracer, scores, instance));
      finish();
      return;
    }

    try {
      TIntArrayList xorGates = XOR.extractGates(instance.formula);
      dve = DependentVariableElimination.run(
          instance.numVariables, instance.formula, xorGates);
      instance.formula.addAll(xorGates);
      instance.expandSmallXOR();
    } catch (ContradictionException e) {
      reply(Solution.unsatisfiable());
      finish();
      return;
    }

    executor.submit(new BlockedClauseEliminationActivity(
          identifier(), tracer, scores, instance));
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
