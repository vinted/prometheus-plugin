package org.jenkinsci.plugins.prometheus.config;

import hudson.Extension;
import hudson.util.FormValidation;
import jenkins.YesNoMaybe;
import jenkins.model.GlobalConfiguration;
import jenkins.model.Jenkins;
import net.sf.json.JSONException;
import net.sf.json.JSONObject;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * @author Robin Müller
 */
@Extension(dynamicLoadable = YesNoMaybe.NO)
public class PrometheusConfiguration extends GlobalConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(PrometheusConfiguration.class);

    private static final String PROMETHEUS_ENDPOINT = "PROMETHEUS_ENDPOINT";
    private static final String DEFAULT_ENDPOINT = "prometheus";
    static final String COLLECTING_METRICS_PERIOD_IN_SECONDS = "COLLECTING_METRICS_PERIOD_IN_SECONDS";
    static final long DEFAULT_COLLECTING_METRICS_PERIOD_IN_SECONDS = TimeUnit.MINUTES.toSeconds(2);
    static final String COLLECT_DISK_USAGE = "COLLECT_DISK_USAGE";

    private String urlName = null;
    private String additionalPath;
    private String defaultNamespace = "default";
    private String jobAttributeName = "jenkins_job";
    private boolean useAuthenticatedEndpoint;
    private Long collectingMetricsPeriodInSeconds = null;

    private boolean countSuccessfulBuilds = true;
    private boolean countUnstableBuilds = true;
    private boolean countFailedBuilds = true;
    private boolean countNotBuiltBuilds = true;
    private boolean countAbortedBuilds = true;
    private boolean fetchTestResults = true;

    private boolean processingDisabledBuilds = false;

    private boolean appendParamLabel = false;
    private boolean appendStatusLabel = false;
    private boolean perBuildMetrics = false;

    private boolean environmentVariableSet;

    private String labeledBuildParameterNames = "";

    private boolean collectDiskUsage = true;
    private boolean collectNodeStatus = true;
    private boolean collectStageMetrics = true;


    public PrometheusConfiguration() {
        load();
        setPath(getPath());
        setCollectingMetricsPeriodInSeconds(collectingMetricsPeriodInSeconds);
        setCollectDiskUsageBasedOnEnvironmentVariableIfDefined();
        environmentVariableSet = isValidBooleanEnv(COLLECT_DISK_USAGE);
    }

    public static PrometheusConfiguration get() {
        return (PrometheusConfiguration) Jenkins.get().getDescriptor(PrometheusConfiguration.class);
    }

    @Override
    public boolean configure(StaplerRequest req, JSONObject json) throws FormException {
        setPath(json.getString("path"));
        setCollectDiskUsage(json.getBoolean("collectDiskUsage"));
        useAuthenticatedEndpoint = json.getBoolean("useAuthenticatedEndpoint");
        defaultNamespace = json.getString("defaultNamespace");
        jobAttributeName = json.getString("jobAttributeName");
        countSuccessfulBuilds = json.getBoolean("countSuccessfulBuilds");
        countUnstableBuilds = json.getBoolean("countUnstableBuilds");
        countFailedBuilds = json.getBoolean("countFailedBuilds");
        countNotBuiltBuilds = json.getBoolean("countNotBuiltBuilds");
        countAbortedBuilds = json.getBoolean("countAbortedBuilds");
        fetchTestResults = json.getBoolean("fetchTestResults");
        collectingMetricsPeriodInSeconds = validateProcessingMetricsPeriodInSeconds(json);
        processingDisabledBuilds = json.getBoolean("processingDisabledBuilds");
        appendParamLabel = json.getBoolean("appendParamLabel");
        appendStatusLabel = json.getBoolean("appendStatusLabel");
        perBuildMetrics = json.getBoolean("perBuildMetrics");
        collectNodeStatus = json.getBoolean("collectNodeStatus");
        collectStageMetrics = json.getBoolean("collectStageMetrics");

        labeledBuildParameterNames = json.getString("labeledBuildParameterNames");

        save();
        return super.configure(req, json);
    }

    public String getPath() {
        return StringUtils.isEmpty(additionalPath) ? urlName : urlName + additionalPath;
    }

    public void setPath(String path) {
        if (path == null) {
            Map<String, String> env = System.getenv();
            path = env.getOrDefault(PROMETHEUS_ENDPOINT, DEFAULT_ENDPOINT);
        }
        urlName = path.split("/")[0];
        List<String> pathParts = Arrays.asList(path.split("/"));
        additionalPath = (pathParts.size() > 1 ? "/" : "") + StringUtils.join(pathParts.subList(1, pathParts.size()), "/");
    }

    public String getJobAttributeName() {
        return jobAttributeName;
    }

    public void setJobAttributeName(String jobAttributeName) {
        this.jobAttributeName = jobAttributeName;
    }

    public String getDefaultNamespace() {
        return defaultNamespace;
    }

    public void setDefaultNamespace(String path) {
        this.defaultNamespace = path;
    }

    public void setCollectDiskUsage(Boolean collectDiskUsage) {
        this.collectDiskUsage = collectDiskUsage;
    }

    public void setCollectDiskUsageBasedOnEnvironmentVariableIfDefined() {
        try {
            this.collectDiskUsage = getBooleanEnvironmentVariableOrThrowException(COLLECT_DISK_USAGE);
        } catch (IllegalArgumentException e) {
            logger.warn("Unable to parse environment variable '{}'. Must either be 'true' or 'false'. Ignoring...", COLLECT_DISK_USAGE);
        }
    }

    private boolean getBooleanEnvironmentVariableOrThrowException(String key) throws IllegalArgumentException {
        if (isValidBooleanEnv(key)) {
            return Boolean.parseBoolean(System.getenv(key));
        }
        throw new IllegalArgumentException();
    }

    private boolean isValidBooleanEnv(String key) {
        String value = System.getenv(key);
        if (value != null) {
            return ("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value));
        }
        return false;
    }

    public boolean getCollectDiskUsage() {
        return collectDiskUsage;
    }

    public long getCollectingMetricsPeriodInSeconds() {
        return collectingMetricsPeriodInSeconds;
    }

    public void setCollectingMetricsPeriodInSeconds(Long collectingMetricsPeriodInSeconds) {
        if (collectingMetricsPeriodInSeconds == null) {
            this.collectingMetricsPeriodInSeconds = parseLongFromEnv();
        } else {
            this.collectingMetricsPeriodInSeconds = collectingMetricsPeriodInSeconds;
        }
    }

    public boolean isUseAuthenticatedEndpoint() {
        return useAuthenticatedEndpoint;
    }

    public void setUseAuthenticatedEndpoint(boolean useAuthenticatedEndpoint) {
        this.useAuthenticatedEndpoint = useAuthenticatedEndpoint;
    }

    public boolean isCountSuccessfulBuilds() {
        return countSuccessfulBuilds;
    }

    public void setCountSuccessfulBuilds(boolean countSuccessfulBuilds) {
        this.countSuccessfulBuilds = countSuccessfulBuilds;
    }

    public boolean isCountUnstableBuilds() {
        return countUnstableBuilds;
    }

    public void setCountUnstableBuilds(boolean countUnstableBuilds) {
        this.countUnstableBuilds = countUnstableBuilds;
    }

    public boolean isCountFailedBuilds() {
        return countFailedBuilds;
    }

    public void setCountFailedBuilds(boolean countFailedBuilds) {
        this.countFailedBuilds = countFailedBuilds;
    }

    public boolean isCountNotBuiltBuilds() {
        return countNotBuiltBuilds;
    }

    public void setCountNotBuiltBuilds(boolean countNotBuiltBuilds) {
        this.countNotBuiltBuilds = countNotBuiltBuilds;
    }

    public boolean isCountAbortedBuilds() {
        return countAbortedBuilds;
    }

    public void setCountAbortedBuilds(boolean countAbortedBuilds) {
        this.countAbortedBuilds = countAbortedBuilds;
    }

    public boolean isFetchTestResults() {
        return fetchTestResults;
    }

    public void setFetchTestResults(boolean fetchTestResults) {
        this.fetchTestResults = fetchTestResults;
    }

    public boolean isProcessingDisabledBuilds() {
        return processingDisabledBuilds;
    }

    public void setProcessingDisabledBuilds(boolean processingDisabledBuilds) {
        this.processingDisabledBuilds = processingDisabledBuilds;
    }

    public boolean isAppendParamLabel() {
        return appendParamLabel;
    }

    public void setAppendParamLabel(boolean appendParamLabel) {
        this.appendParamLabel = appendParamLabel;
    }

    public boolean isAppendStatusLabel() {
        return appendStatusLabel;
    }

    public void setAppendStatusLabel(boolean appendStatusLabel) {
        this.appendStatusLabel = appendStatusLabel;
    }

    public boolean isPerBuildMetrics() {
        return perBuildMetrics;
    }

    public boolean isCollectNodeStatus() {
        return collectNodeStatus;
    }

    public boolean isCollectStageMetrics() {
        return collectStageMetrics;
    }

    public void setCollectStageMetrics(boolean collectStageMetrics) {
        this.collectStageMetrics = collectStageMetrics;
        save();
    }

    public void setPerBuildMetrics(boolean perBuildMetrics) {
        this.perBuildMetrics = perBuildMetrics;
    }

    public String getUrlName() {
        return urlName;
    }

    public String getAdditionalPath() {
        return additionalPath;
    }

    public String getLabeledBuildParameterNames() {
        return labeledBuildParameterNames;
    }

    public void setLabeledBuildParameterNames(String labeledBuildParameterNames) {
        this.labeledBuildParameterNames = labeledBuildParameterNames;
    }

    public String[] getLabeledBuildParameterNamesAsArray() {
        return parseParameterNamesFromStringSeparatedByComma(labeledBuildParameterNames);
    }


    public FormValidation doCheckPath(@QueryParameter String value) {
        if (StringUtils.isEmpty(value)) {
            return FormValidation.error(Messages.path_required());
        } else if (System.getenv().containsKey(PROMETHEUS_ENDPOINT)) {
            return FormValidation.warning(Messages.path_environment_override(PROMETHEUS_ENDPOINT, System.getenv(PROMETHEUS_ENDPOINT)));
        } else {
            return FormValidation.ok();
        }
    }

    private Long validateProcessingMetricsPeriodInSeconds(JSONObject json) throws FormException {
        try {
            long value = json.getLong("collectingMetricsPeriodInSeconds");
            if (value > 0) {
                return value;
            }
        } catch (JSONException ignored) {
        }
        throw new FormException("CollectingMetricsPeriodInSeconds must be a positive integer", "collectingMetricsPeriodInSeconds");
    }

    private long parseLongFromEnv() {
        Map<String, String> env = System.getenv();
        String message = String.format("COLLECTING_METRICS_PERIOD_IN_SECONDS must be a positive integer. The default value: '%d' will be used instead of provided.", DEFAULT_COLLECTING_METRICS_PERIOD_IN_SECONDS);
        try {
            return Optional.ofNullable(env.get(COLLECTING_METRICS_PERIOD_IN_SECONDS))
                    .map(Long::parseLong)
                    .filter(v -> v > 0)
                    .orElseGet(() -> {
                        logger.warn(message);
                        return DEFAULT_COLLECTING_METRICS_PERIOD_IN_SECONDS;
                    });
        } catch (NumberFormatException e) {
            logger.warn(message);
            return DEFAULT_COLLECTING_METRICS_PERIOD_IN_SECONDS;
        }
    }

    private String[] parseParameterNamesFromStringSeparatedByComma(String stringValue) {
        if (stringValue == null || stringValue.trim().length() < 1) {
            return new String[]{};
        }
        return stringValue.split("\\s*,\\s*");
    }

    public boolean isEnvironmentVariableSet() {
        return environmentVariableSet;
    }
}
