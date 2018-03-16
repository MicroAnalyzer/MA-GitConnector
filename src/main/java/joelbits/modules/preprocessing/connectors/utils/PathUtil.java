package joelbits.modules.preprocessing.connectors.utils;

import java.io.File;

public final class PathUtil {
    private static final String PATH = System.getProperty("user.dir") + File.separator;
    private static final String REPOSITORIES = PATH + "repositories" + File.separator;

    public static String clonedRepositoriesFolder() { return REPOSITORIES; }
}
