package joelbits.modules.preprocessing.connectors;

import joelbits.modules.preprocessing.connectors.spi.Connectible;

public interface Connector extends Connectible, DataCollector, RevisionProcessible, SnapshotSwitchable {
}
