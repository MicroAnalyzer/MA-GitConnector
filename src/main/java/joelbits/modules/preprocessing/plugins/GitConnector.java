package joelbits.modules.preprocessing.plugins;

import com.google.auto.service.AutoService;
import com.google.common.collect.Iterators;
import com.google.common.collect.PeekingIterator;
import joelbits.modules.preprocessing.plugins.spi.Connector;
import joelbits.modules.preprocessing.plugins.types.SourceCodeFileType;
import joelbits.modules.preprocessing.plugins.utils.PathUtil;
import joelbits.modules.preprocessing.plugins.utils.TreeIterator;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.*;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffEntry.ChangeType;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.*;

import static java.util.stream.Collectors.toMap;

/**
 * Connects to a git repository and allows a client to access its data.
 */
@AutoService(Connector.class)
public final class GitConnector implements Connector {
    private static final Logger log = LoggerFactory.getLogger(GitConnector.class);
    private Repository repository;
    private final Map<String, RevCommit> commits = new HashMap<>();
    private final List<String> allCommitIds = new ArrayList<>();
    private final Map<String, Map<String, String>> differenceConsecutiveCommits = new HashMap<>();

    @Override
    public void connect(String repositoryName) throws Exception {
        String path = PathUtil.clonedRepositoriesFolder() + repositoryName + File.separator;
        File repository = new File(path + Constants.DOT_GIT);
        this.repository = new FileRepositoryBuilder()
                .setGitDir(repository)
                .readEnvironment()
                .findGitDir()
                .build();

        collect();
        log.info("Finished collecting data from " + repository.getName());
    }

    private void collect() throws Exception {
        clearData();
        try (Git git = new Git(repository)) {
            PeekingIterator<RevCommit> commitIterator = Iterators.peekingIterator(git.log().call().iterator());
            while (commitIterator.hasNext()) {
                RevCommit commit = commitIterator.next();
                if (!commitIterator.hasNext()) {
                    continue;
                }

                String commitId = commit.getId().name();
                commits.put(commitId, commit);
                allCommitIds.add(commitId);
                storeConsecutiveCommitChanges(git, commitId, commitIterator.peek().getId().name());
            }
        }
    }

    private void clearData() {
        commits.clear();
        allCommitIds.clear();
        differenceConsecutiveCommits.clear();
    }

    /**
     * Stores the path of the files that have been changed between the two supplied commits, and their respective
     * change types. The path of a file is used as key since it is unique within a revision.
     *
     * @param commitId       the ID of the commits that is newest in time
     * @param parentId       the ID of the commits that are oldest in time
     */
    private void storeConsecutiveCommitChanges(Git git, String commitId, String parentId) throws GitAPIException, IOException {
        List<DiffEntry> diffs = git.diff()
                .setOldTree(TreeIterator.prepareTreeParser(repository, parentId))
                .setNewTree(TreeIterator.prepareTreeParser(repository, commitId))
                .call();

        Map<String, String> files = diffs.stream()
                .filter(file -> hasRelevantFileType(file.getNewPath()))
                .filter(file -> hasRelevantChangeType(file.getChangeType()))
                .collect(toMap(DiffEntry::getNewPath, file -> file.getChangeType().name()));

        differenceConsecutiveCommits.put(commitId, files);
    }

    private boolean hasRelevantChangeType(ChangeType type) {
        return EnumSet.of(ChangeType.ADD, ChangeType.MODIFY, ChangeType.DELETE).contains(type);
    }

    private boolean hasRelevantFileType(String file) {
        return SourceCodeFileType.exist(file);
    }

    /**
     * Retrieve file paths for all files in a project snapshot.
     *
     * @param commitId              revision sha1 for repository snapshot
     * @return                      a set of file paths for all files in repository snapshot
     * @throws IOException
     */
    @Override
    public Set<String> snapshotFiles(String commitId) throws IOException {
        Set<String> filePaths = new HashSet<>();

        try (TreeWalk treeWalk = new TreeWalk(repository)) {
            treeWalk.reset(commits.get(commitId).getTree());
            treeWalk.setRecursive(true);
            while (treeWalk.next()) {
                filePaths.add(treeWalk.getPathString());
            }
            treeWalk.close();
        }

        return filePaths;
    }

    /**
     * Checks out a specific file in a specific revision.
     *
     * @param commitId              the sha1 value of the revision the file is in
     * @param filePath              the file path of the file to check out
     * @throws GitAPIException
     */
    @Override
    public void checkOutFile(String commitId, String filePath) throws GitAPIException {
        try (Git git = new Git(repository)) {
            git.checkout().setStartPoint(commits.get(commitId)).addPath(filePath).call();
        }
    }

    @Override
    public String logMessage(String commitId) {
        return commits.get(commitId).getFullMessage();
    }

    @Override
    public String committerName(String commitId) {
        return commits.get(commitId).getCommitterIdent().getName();
    }

    @Override
    public String committerEmail(String commitId) {
        return commits.get(commitId).getCommitterIdent().getEmailAddress();
    }

    @Override
    public int commitTime(String commitId) {
        return commits.get(commitId).getCommitTime();
    }

    /**
     * The ID of the most recent commit made to the repository. This commit is the last in the commit chain (and
     * therefore has no children).
     *
     * @return      the ID of the most recent commit
     */
    @Override
    public String mostRecentCommitId() {
        Comparator<RevCommit> comparator = Comparator.comparing(RevCommit::getCommitTime);
        return commits.isEmpty() ? "" : Collections.max(commits.values(), comparator).getId().name();
    }

    @Override
    public List<String> allCommitIds() {
        return allCommitIds;
    }

    public Map<String, String> getCommitFileChanges(String commitId) {
        return differenceConsecutiveCommits.get(commitId);
    }

    @Override
    public String toString() {
        return "git";
    }
}
