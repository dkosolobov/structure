package ibis.structure;

import gnu.trove.list.array.TIntArrayList;
import ibis.constellation.ActivityIdentifier;
import ibis.constellation.Event;
import org.apache.log4j.Logger;


public final class XORActivity extends Activity {
  private static final Logger logger = Logger.getLogger(XORActivity.class);

  private TIntArrayList dve;

  public XORActivity(final ActivityIdentifier parent,
                     final int depth,
                     final Skeleton instance) {
    super(parent, depth, 0, instance);
    assert instance.size() > 0;
  }

  public void initialize() {
    if (!Configure.xor) {
      executor.submit(new SimplifyActivity(parent, depth, instance));
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

    executor.submit(new SimplifyActivity(identifier(), depth, instance));
    suspend();
  }

  public void process(Event e) throws Exception {
    Solution response = (Solution) e.data;
    response = DependentVariableElimination.restore(dve, response);
    reply(response);
    finish();
  }
}
