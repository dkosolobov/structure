package ibis.structure;

import gnu.trove.list.array.TDoubleArrayList;
import ibis.constellation.ActivityIdentifier;
import org.apache.log4j.Logger;

/**
 * Starts preprocesses the input instance.
 */
public final class PreprocessActivity extends Activity {
  private static final Logger logger = Logger.getLogger(
      PreprocessActivity.class);

  public PreprocessActivity(final ActivityIdentifier parent,
                            final Skeleton instance) {
    super(parent, 0, 0, null, instance);
  }

  @Override
  public void initialize() throws Exception {
    executor.submit(new XORActivity(parent, null, instance));
    finish();
  }
}
