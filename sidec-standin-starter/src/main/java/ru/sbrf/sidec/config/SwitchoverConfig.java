package ru.sbrf.sidec.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.Assert;

import java.time.Duration;
import java.util.UUID;

import static ru.sbrf.sidec.autoconfigure.SwitchoverAutoConfiguration.SWITCHOVER_ENABLED_CONFIG_PROPERTY;

@ConfigurationProperties(prefix = "sidec.switchover")
@ConditionalOnProperty(name = SWITCHOVER_ENABLED_CONFIG_PROPERTY, havingValue = "true")
public class SwitchoverConfig {
    public static final Logger LOGGER = LoggerFactory.getLogger(SwitchoverConfig.class);

    // Default values
    public static final String DEFAULT_SIGNAL_TOPIC = "sidec.app_signal";
    public static final String DEFAULT_APP_NAME_PREFIX = "sidec.switchover-";
    public static final Duration DEFAULT_SIGNAL_REQUEST_TIMEOUT = Duration.ofSeconds(1);
    public static final Duration DEFAULT_POLL_TIMEOUT = Duration.ofSeconds(1);
    public static final Duration DEFAULT_WATCHER_DELAY = Duration.ofSeconds(1);
    public static final Duration DEFAULT_CONNECTION_CLOSE_AWAIT = Duration.ofSeconds(1);

    // Configuration fields with duration support
    private Duration signalRequestTimeout;
    private String signalTopic;
    private Duration pollTimeout;
    private String appName;
    private Duration watcherDelay;

    private final SwitchoverDataSourceConfiguration dataSourceConfig;
    private final SwitchoverKafkaConfig kafkaConfig;

    public SwitchoverConfig(
            SwitchoverDataSourceConfiguration dataSourceConfig,
            SwitchoverKafkaConfig kafkaConfig
    ) {
        Assert.notNull(dataSourceConfig, "DataSource configuration must not be null");
        Assert.notNull(kafkaConfig, "Kafka configuration must not be null");

        this.dataSourceConfig = dataSourceConfig;
        this.kafkaConfig = kafkaConfig;
    }

    @PostConstruct
    private void initDefaults() {
        if (signalTopic == null) {
            LOGGER.info("Signal topic not configured, using default: {}", DEFAULT_SIGNAL_TOPIC);
            signalTopic = DEFAULT_SIGNAL_TOPIC;
        }

        if (pollTimeout == null) {
            LOGGER.info("Poll timeout not configured, using default: {}", DEFAULT_POLL_TIMEOUT);
            pollTimeout = DEFAULT_POLL_TIMEOUT;
        }

        if (appName == null) {
            appName = DEFAULT_APP_NAME_PREFIX + UUID.randomUUID();
            LOGGER.info("Application name not configured, generated default: {}", appName);
        }

        if (watcherDelay == null) {
            LOGGER.info("Watcher delay not configured, using default: {}", DEFAULT_WATCHER_DELAY);
            watcherDelay = DEFAULT_WATCHER_DELAY;
        }

        if (signalRequestTimeout == null) {
            LOGGER.info("Signal request timeout not configured, using default: {}", DEFAULT_SIGNAL_REQUEST_TIMEOUT);
            signalRequestTimeout = DEFAULT_SIGNAL_REQUEST_TIMEOUT;
        }
    }

    public String getAppName() {
        return appName;
    }

    public void setAppName(String appName) {
        this.appName = appName;
    }

    public String getSignalTopic() {
        return signalTopic;
    }

    public void setSignalTopic(String signalTopic) {
        this.signalTopic = signalTopic;
    }

    public Duration getPollTimeout() {
        return pollTimeout;
    }

    public void setPollTimeout(Duration pollTimeout) {
        this.pollTimeout = pollTimeout;
    }

    public Duration getWatcherDelay() {
        return watcherDelay;
    }

    public void setWatcherDelay(Duration watcherDelay) {
        this.watcherDelay = watcherDelay;
    }

    public Duration getSignalRequestTimeout() {
        return signalRequestTimeout;
    }

    public void setSignalRequestTimeout(Duration signalRequestTimeout) {
        this.signalRequestTimeout = signalRequestTimeout;
    }

    public SwitchoverDataSourceConfiguration getDataSourceConfig() {
        return dataSourceConfig;
    }

    public SwitchoverKafkaConfig getKafkaConfig() {
        return kafkaConfig;
    }
}