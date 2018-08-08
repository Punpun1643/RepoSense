package reposense.model;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import reposense.parser.InvalidLocationException;
import reposense.system.LogsManager;
import reposense.util.FileUtil;

public class RepoConfiguration {
    private static final Logger logger = LogsManager.getLogger(RepoConfiguration.class);
    private static final String GIT_LINK_SUFFIX = ".git";
    private static final Pattern GIT_REPOSITORY_LOCATION_PATTERN =
            Pattern.compile("^.*github.com\\/(?<org>.+?)\\/(?<repoName>.+?)\\.git$");
    private static final String DEFAULT_BRANCH = "master";

    private String location;
    private String organization;
    private String repoName;
    private String branch;
    private String displayName;
    private Date sinceDate;
    private Date untilDate;

    private transient boolean needCheckStyle = false;
    private transient List<String> formats;
    private transient int commitNum = 1;
    private transient List<String> ignoreGlobList = new ArrayList<>();
    private transient List<Author> authorList = new ArrayList<>();
    private transient TreeMap<String, Author> authorAliasMap = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    private transient Map<Author, String> authorDisplayNameMap = new HashMap<>();
    private transient boolean annotationOverwrite = true;

    /**
     * @throws InvalidLocationException if {@code location} cannot be represented by a {@code URL} or {@code Path}.
     */
    public RepoConfiguration(String location) throws InvalidLocationException {
        this(location, DEFAULT_BRANCH);
    }

    /**
     * @throws InvalidLocationException if {@code location} cannot be represented by a {@code URL} or {@code Path}.
     */
    public RepoConfiguration(String location, String branch) throws InvalidLocationException {
        this(location, branch, Collections.emptyList());
    }

    /**
     * @throws InvalidLocationException if {@code location} cannot be represented by a {@code URL} or {@code Path}.
     */
    public RepoConfiguration(String location, String branch, List<String> ignoreGlobList)
            throws InvalidLocationException {
        this.location = location;
        this.branch = branch;
        this.ignoreGlobList = ignoreGlobList;

        verifyLocation(location);
        Matcher matcher = GIT_REPOSITORY_LOCATION_PATTERN.matcher(location);

        if (matcher.matches()) {
            organization = matcher.group("org");
            repoName = matcher.group("repoName");
            displayName = organization + "_" + repoName + "_" + branch;
        } else {
            repoName = Paths.get(location).getFileName().toString().replace(GIT_LINK_SUFFIX, "");
            displayName = repoName + "_" + branch;
        }
    }

    public static void setDatesToRepoConfigs(
            List<RepoConfiguration> configs, Optional<Date> sinceDate, Optional<Date> untilDate) {
        for (RepoConfiguration config : configs) {
            config.setSinceDate(sinceDate.orElse(null));
            config.setUntilDate(untilDate.orElse(null));
        }
    }

    /**
     * Merges a {@code RepoConfiguration} from {@code repoConfigs} with another from {@code authorConfigs}
     * if {@code location} and {@code branch} matches.
     */
    public static void merge(List<RepoConfiguration> repoConfigs, List<RepoConfiguration> authorConfigs) {
        for (RepoConfiguration authorConfig : authorConfigs) {
            int index = repoConfigs.indexOf(authorConfig);

            if (index == -1) {
                logger.warning(String.format(
                        "Repository %s is not found in repo-config.csv.", authorConfig.getLocation()));
                continue;
            }

            RepoConfiguration repoConfig = repoConfigs.get(index);

            repoConfig.setAuthorList(authorConfig.getAuthorList());
            repoConfig.setAuthorDisplayNameMap(authorConfig.getAuthorDisplayNameMap());
            repoConfig.setAuthorAliasMap(authorConfig.getAuthorAliasMap());
        }
    }

    /**
     * Sets all {@code RepoConfiguration} in {@code configs} to have {@code formats} set.
     */
    public static void setFormatsToRepoConfigs(List<RepoConfiguration> configs, List<String> formats) {
        configs.forEach(config -> config.setFormats(formats));
    }

    /**
     * Clears authors information and use the information provided from {@code standaloneConfig}.
     */
    public void update(StandaloneConfig standaloneConfig) {
        authorList = new ArrayList<>();
        authorAliasMap.clear();
        authorDisplayNameMap.clear();
        ignoreGlobList = standaloneConfig.getIgnoreGlobList();

        for (StandaloneAuthor sa : standaloneConfig.getAuthors()) {
            Author author = new Author(sa);
            author.appendIgnoreGlobList(ignoreGlobList);

            this.authorList.add(author);
            this.setAuthorDisplayName(author, author.getDisplayName());
            this.addAuthorAliases(author, Arrays.asList(author.getGitId()));
            this.addAuthorAliases(author, author.getAuthorAliases());
        }
    }

    public String getRepoRoot() {
        String path = FileUtil.REPOS_ADDRESS + File.separator + displayName + File.separator;

        if (!repoName.isEmpty()) {
            path += repoName + File.separator;
        }

        return path;
    }

    @Override
    public boolean equals(Object other) {

        if (other == this) {
            return true;
        }

        if (!(other instanceof RepoConfiguration)) {
            return false;
        }

        return hashCode() == ((RepoConfiguration) other).hashCode();
    }

    @Override
    public int hashCode() {
        return Objects.hash(location, branch);
    }

    public Map<Author, String> getAuthorDisplayNameMap() {
        return authorDisplayNameMap;
    }

    public void setAuthorDisplayNameMap(Map<Author, String> authorDisplayNameMap) {
        this.authorDisplayNameMap = authorDisplayNameMap;
    }

    public int getCommitNum() {
        return commitNum;
    }

    public void setCommitNum(int commitNum) {
        this.commitNum = commitNum;
    }

    public boolean isNeedCheckStyle() {
        return needCheckStyle;
    }

    public void setNeedCheckStyle(boolean needCheckStyle) {
        this.needCheckStyle = needCheckStyle;
    }

    public String getBranch() {
        return branch;
    }

    public void setBranch(String branch) {
        this.branch = branch;
    }

    public boolean isAnnotationOverwrite() {
        return annotationOverwrite;
    }

    public void setAnnotationOverwrite(boolean annotationOverwrite) {
        this.annotationOverwrite = annotationOverwrite;
    }

    public List<String> getIgnoreGlobList() {
        return ignoreGlobList;
    }

    public void setIgnoreGlobList(List<String> ignoreGlobList) {
        this.ignoreGlobList = ignoreGlobList;
    }

    public List<Author> getAuthorList() {
        return authorList;
    }

    public void addAuthor(Author author) {
        authorList.add(author);
    }

    public boolean containsAuthor(Author author) {
        return authorList.contains(author);
    }

    public void setAuthorList(List<Author> authorList) {
        this.authorList = authorList;

        authorList.forEach(author -> {
            // Set GitHub Id as default alias
            addAuthorAliases(author, Arrays.asList(author.getGitId()));
            // Propagate RepoConfiguration IgnoreGlobList to Author
            author.appendIgnoreGlobList(this.getIgnoreGlobList());
        });
    }

    public TreeMap<String, Author> getAuthorAliasMap() {
        return authorAliasMap;
    }

    public void setAuthorAliasMap(TreeMap<String, Author> authorAliasMap) {
        this.authorAliasMap = authorAliasMap;
    }

    public Date getSinceDate() {
        return sinceDate;
    }

    public void setSinceDate(Date sinceDate) {
        this.sinceDate = sinceDate;
    }

    public Date getUntilDate() {
        return untilDate;
    }

    public void setUntilDate(Date untilDate) {
        this.untilDate = untilDate;
    }

    public List<String> getFormats() {
        return formats;
    }

    public void setFormats(List<String> formats) {
        this.formats = formats;
    }

    public void setAuthorDisplayName(Author author, String displayName) {
        authorDisplayNameMap.put(author, displayName);
    }

    public void addAuthorAliases(Author author, List<String> aliases) {
        aliases.forEach(alias -> authorAliasMap.put(alias, author));
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getLocation() {
        return location;
    }

    public String getRepoName() {
        return repoName;
    }

    /**
     * Verifies {@code location} can be presented as a {@code URL} or {@code Path}.
     * @throws InvalidLocationException if otherwise.
     */
    private void verifyLocation(String location) throws InvalidLocationException {
        boolean isValidPathLocation = false;
        boolean isValidGitUrl = false;

        try {
            Path pathLocation = Paths.get(location);
            isValidPathLocation = Files.exists(pathLocation);
        } catch (InvalidPathException ipe) {
            // Ignore exception
        }

        try {
            new URL(location);
            isValidGitUrl = location.endsWith(GIT_LINK_SUFFIX);
        } catch (MalformedURLException mue) {
            // Ignore exception
        }

        if (!isValidPathLocation && !isValidGitUrl) {
            throw new InvalidLocationException(location + " is an invalid location.");
        }
    }
}