package joelbits.modules.preprocessing.plugins;

/**
 * Collects project-level data from sources containing unstructured data.
 */
public interface DataCollector {
    String committerName(String commitId);
    String committerEmail(String commitId);
    int commitTime(String commitId);
    String logMessage(String commitId);
}
