package joelbits.collectors;

import com.google.auto.service.AutoService;
import joelbits.collectors.spi.DataCollector;
import joelbits.collectors.types.CollectorType;
import joelbits.collectors.utils.TreeIterator;
import org.apache.commons.lang.StringUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.*;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toMap;

/**
 * Collects data from the Git repository that corresponds to the path that is given as an argument to the collect method.
 * This class is reusable since the current data is cleared every time new data is collected from a repository. This
 * way the GitCollector class could be reused to collect data from e.g., different snapshots of a repository.
 */
@AutoService(DataCollector.class)
public class GitCollector implements DataCollector {
    private static final Logger log = LoggerFactory.getLogger(GitCollector.class);
    private Repository repository;
    private final Map<String, RevCommit> commits = new HashMap<>();
    private final Map<String, List<DiffEntry>> differenceConsecutiveCommits = new HashMap<>();
    private final Map<String, DiffEntry> fileChanges = new HashMap<>();

    @Override
    public void collect(String gitRepositoryPath) {
        try {
            File repository = new File(gitRepositoryPath + Constants.DOT_GIT);
            this.repository = new FileRepositoryBuilder()
                    .setGitDir(repository)
                    .readEnvironment()
                    .findGitDir()
                    .build();

            collect();
            log.info("Finished collecting data from " + repository.getName());
        } catch (IOException e) {
            log.error(e.toString(), e);
        }
    }

    private void collect() {
        clearData();
        try (Git git = new Git(repository)) {
            for (RevCommit commit : git.log().call()) {
                String commitId = commit.getId().name();
                commits.put(commitId, commit);
                storeConsecutiveCommitChanges(git, commit);
            }
        } catch (GitAPIException | IOException e) {
            log.error(e.toString(), e);
        }
    }

    private void clearData() {
        commits.clear();
        fileChanges.clear();
        differenceConsecutiveCommits.clear();
    }

    /**
     * Store the changes made between two consecutive commits (i.e., two commits that have a parent/child relationship).
     * If a commit does not have a parent (the root commit) an empty list is stored.
     *
     * @param git
     * @param commit             the object representing the current commit
     * @throws GitAPIException
     * @throws IOException
     */
    private void storeConsecutiveCommitChanges(Git git, RevCommit commit) throws GitAPIException, IOException {
        String commitId = commit.getId().name();
        if (commit.getParentCount() == 0) {
            differenceConsecutiveCommits.put(commitId, Collections.emptyList());
            return;
        }

        List<DiffEntry> diffs = git.diff()
                .setOldTree(TreeIterator.prepareTreeParser(repository, commit.getParent(0).toObjectId().name()))
                .setNewTree(TreeIterator.prepareTreeParser(repository, commitId))
                .call();

        storeEachFileChange(diffs);
        differenceConsecutiveCommits.put(commitId, new ArrayList<>(diffs));
    }

    /**
     * Store a reference from each file change (sha1 value) to its respective DiffEntry object for future processing.
     * Then you can retrieve that DiffEntry in order to extract information such as the new file path, or
     * the type of the change (e.g., ADD, MODIFY, DELETE).
     *
     * @param diffs    list of files that have been subject to changes between two (in this case) consecutive commits
     */
    private void storeEachFileChange(List<DiffEntry> diffs) {
        for (DiffEntry entry : diffs) {
            fileChanges.put(entry.getNewId().name(), entry);
        }
    }

    /**
     *  Between two commits there are files changed, and each of these file changes has its own object (DiffEntry)
     *  containing information about that specific file's changes. This object can be retrieved by using its unique
     *  sha1 identifier value.
     *
     * @param fileChangeId      the sha1 value of a specific file change within a specific commit
     * @return                  the type of the file change, e.g., ADD, DELETE, MODIFY
     */
    public String fileChangeType(String fileChangeId) {
        return Optional.ofNullable(fileChanges.get(fileChangeId))
                .map(DiffEntry::getChangeType)
                .map(DiffEntry.ChangeType::name)
                .orElse("");
    }

    /**
     * After a file has been changed this method could be invoked to retrieve the current path to the file.
     *
     * @param fileChangeId      the sha1 value of a specific file change within a specific commit
     * @return                  the path to the file after the change
     */
    public String fileChangePath(String fileChangeId) {
        return Optional.ofNullable(fileChanges.get(fileChangeId))
                .map(DiffEntry::getNewPath)
                .orElse("");
    }

    /**
     * The ID of the most recent commit made to the repository. This commit is the last in the commit chain (and
     * therefore has no children).
     *
     * @return      the ID of the most recent commit
     */
    public String mostRecentCommitId() {
        Comparator<RevCommit> comparator = Comparator.comparing(RevCommit::getCommitTime);
        return commits.isEmpty() ? StringUtils.EMPTY : Collections.max(commits.values(), comparator).getId().name();
    }

    /**
     * The ID of the first commit ever made to the repository, i.e., the oldest commit. This is the root in the commit
     * chain (and therefore have no parent).
     *
     * @return      the ID of the repository's root commit
     */
    public String leastRecentCommitId() {
        Comparator<RevCommit> comparator = Comparator.comparing(RevCommit::getCommitTime);
        return commits.isEmpty() ? StringUtils.EMPTY : Collections.min(commits.values(), comparator).getId().name();
    }

    /**
     * Returns the path of the files that have been changed between the two supplied commits, and their respective
     * sha1 values. The sha1 value of a file change is used as key since the sha1 values are unique.
     *
     * @param newCommitId       the ID of the commits that is newest in time
     * @param oldCommitId       the ID of the commits that are oldest in time
     * @return                  a map containing pairs of the sha1 value of a file change and the file path
     */
    public Map<String, String> changedFilesBetweenCommits(String newCommitId, String oldCommitId) {
        if (StringUtils.isEmpty(newCommitId) || StringUtils.isEmpty(oldCommitId)) {
            log.warn("Must provide existing commit IDs");
            return Collections.emptyMap();
        }

        if (hasParentRelationship(newCommitId, oldCommitId)) {
            return differenceConsecutiveCommits.get(newCommitId).stream()
                    .collect(Collectors.toMap(d -> d.getNewId().name(), DiffEntry::getNewPath));
        }

        return retrieveChangedFiles(newCommitId, oldCommitId);
    }

    /**
     * If the commits have a parent/child relationship the changes between these commits are already stored in the
     * differenceConsecutiveCommits map. No need to recompute these.
     *
     * @param newCommitId       commit ID of the newer commit
     * @param oldCommitId       commit ID of the older commit
     * @return                  true if parent/child relationship exists, otherwise false
     */
    private boolean hasParentRelationship(String newCommitId, String oldCommitId) {
        return Optional.ofNullable(commits.get(newCommitId))
                .filter(d -> d.getParentCount() > 0)
                .map(RevCommit::getParents)
                .filter(p -> isParent(p, oldCommitId))
                .isPresent();
    }

    private boolean isParent(RevCommit[] parents, String commitId) {
        return Stream.of(parents).anyMatch(p -> p.getId().name().equals(commitId));
    }

    /**
     * If no parent/child relationship exists between the two commits, the changes between these commits have to be
     * computed.
     *
     * @param newCommitId       commit ID of the newer commit
     * @param oldCommitId       commit ID of the older commit
     * @return                  a map containing pairs of the sha1 value of a file change and the file path
     */
    private Map<String, String> retrieveChangedFiles(String newCommitId, String oldCommitId) {
        try (Git git = new Git(repository)) {
            List<DiffEntry> diffs = git.diff()
                    .setOldTree(TreeIterator.prepareTreeParser(repository, oldCommitId))
                    .setNewTree(TreeIterator.prepareTreeParser(repository, newCommitId))
                    .call();

            return diffs.stream()
                    .collect(toMap(d -> d.getNewId().name(), d -> d.getChangeType().name()));
        } catch (GitAPIException | IOException e) {
            log.error(e.toString(), e);
        }

        return Collections.emptyMap();
    }

    /**
     * The set of commit IDs can be used to checkout each revision (using the ID) for scanning and parsing of
     * relevant files.
     *
     * @return      a set of the repository's all commit IDs in descending order (most recent commit first)
     */
    public Set<String> allCommitIds() {
        return Collections.unmodifiableSet(commits.keySet());
    }

    @Override
    public String type() {
        return CollectorType.GIT.name();
    }

    @Override
    public String toString() {
        return "GitCollector";
    }
}
