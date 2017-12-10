package joelbits.collectors;

import com.google.auto.service.AutoService;
import joelbits.collectors.spi.DataCollector;
import joelbits.collectors.types.CollectorType;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

/**
 * Connects to a (remote) GitHub repository and collects its metadata as desired.
 * The collect method's argument should be the full name of the GitHub repository, e.g., "JCTools/JCTools"
 */
@AutoService(DataCollector.class)
public class GitHubCollector implements DataCollector {
    private static final Logger log = LoggerFactory.getLogger(GitHubCollector.class);
    private GHRepository remoteRepository;

    @Override
    public void collect(String repository) {
        try {
            this.remoteRepository = GitHub.connectAnonymously().getRepository(repository);
            log.info("Connected to " + remoteRepository.getGitTransportUrl());
        } catch (IOException e) {
            log.error(e.toString(), e);
        }
    }

    public int forks() {
        return remoteRepository.getForks();
    }

    public int stargazersCount() {
        return remoteRepository.getStargazersCount();
    }

    public String login() throws IOException {
        return remoteRepository.getOwner().getLogin();
    }

    public long createdAt() throws IOException {
        return remoteRepository.getCreatedAt().toInstant().getEpochSecond();
    }

    public String language() {
        return remoteRepository.getLanguage();
    }

    public String fullName() {
        return remoteRepository.getFullName();
    }

    public String homepage() {
        return remoteRepository.getHomepage();
    }

    public String name() {
        return remoteRepository.getName();
    }

    public String description() {
        return remoteRepository.getDescription();
    }

    public String ownerName() {
        return remoteRepository.getOwnerName();
    }

    public String htmlUrl() {
        return remoteRepository.getHtmlUrl().toString();
    }

    public int size() {
        return remoteRepository.getSize();
    }

    public String url() {
        return remoteRepository.getUrl().toString();
    }

    public boolean isFork() {
        return remoteRepository.isFork();
    }

    public long repositoryId() {
        return remoteRepository.getId();
    }

    public long updatedAt() throws IOException {
        return remoteRepository.getUpdatedAt().toInstant().getEpochSecond();
    }

    public Set<String> collaboratorNames() throws IOException {
        return remoteRepository.getCollaboratorNames();
    }

    public int openIssueCount() {
        return remoteRepository.getOpenIssueCount();
    }

    public int subscribersCount() {
        return remoteRepository.getSubscribersCount();
    }

    public int watchersCount() {
        return remoteRepository.getWatchers();
    }

    public Map<String, Long> languages() throws IOException {
        return remoteRepository.listLanguages();
    }

    public long parentRepositoryId() throws IOException {
        return remoteRepository.getParent().getId();
    }

    @Override
    public String type() {
        return CollectorType.GITHUB.name();
    }

    @Override
    public String toString() {
        return "GitHubCollector";
    }
}
