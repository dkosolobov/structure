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
    executor.submit(new XORActivity(
          parent, depth, instance));
    finish();
  }

  public void process(final Event e) throws Exception {
  }
}
