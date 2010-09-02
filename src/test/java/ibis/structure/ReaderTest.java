package ibis.structure;

import java.io.IOException;
import org.junit.Test;

public class ReaderTest {
  @Test(expected=ParseException.class)
  public void missingHeader() throws Exception {
    Reader.parseText("1 0\n" +
                     "2 0\n" +
                     "3 0\n");
  }

  @Test(expected=ParseException.class)
  public void incompleteProblem() throws Exception {
    Reader.parseText("p cnf 3 3\n" +
                     "1 2 3 0\n");
  }
}
