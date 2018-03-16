package joelbits.modules.preprocessing.connectors;

/**
 * Allows snapshots of specific files to be checked out from a version
 * control system to be preprocessed.
 */
public interface SnapshotSwitchable {
    void checkOutFile(String commitId, String filePath) throws Exception;
}
