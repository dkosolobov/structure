package ibis.structure;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.spi.LoggerFactory;


public class StructureLoggerFactory implements LoggerFactory {
    public void StructureLoggerFactory() {
    }

    public Logger makeNewLoggerInstance(String name) {
        return new StructureLogger(name);
    }
}
