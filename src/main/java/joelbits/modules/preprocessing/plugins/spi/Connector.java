package joelbits.modules.preprocessing.plugins.spi;

import joelbits.modules.preprocessing.plugins.Connectible;
import joelbits.modules.preprocessing.plugins.DataCollector;
import joelbits.modules.preprocessing.plugins.RevisionProcessible;
import joelbits.modules.preprocessing.plugins.SnapshotSwitchable;

public interface Connector extends Connectible, DataCollector, RevisionProcessible, SnapshotSwitchable {
}
