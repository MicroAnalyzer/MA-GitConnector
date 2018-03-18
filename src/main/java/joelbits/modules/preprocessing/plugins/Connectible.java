package joelbits.modules.preprocessing.plugins;

/**
 * Connect to a repository of specific type (e.g., SVN, GIT, CVS) and access its data.
 */
public interface Connectible {
    void connect(String repositoryName) throws Exception;
}
