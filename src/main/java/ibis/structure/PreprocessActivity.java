package ibis.structure;

import ibis.constellation.ActivityIdentifier;
import org.apache.log4j.Logger;

/**
 * Starts preprocesses the input instance.
 */
public final class PreprocessActivity extends Activity {
  private static final Logger logger = Logger.getLogger(
      PreprocessActivity.class);

  public PreprocessActivity(final ActivityIdentifier parent,
                            final int depth,
                            final Skeleton instance) {
    super(parent, depth, 0, instance);
  }

  @Override
  public void initialize() throws Exception {
    executor.submit(new XORActivity(parent, depth, instance));
    finish();
  }
}
