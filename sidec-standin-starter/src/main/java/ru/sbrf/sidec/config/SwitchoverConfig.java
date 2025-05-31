package ru.sbrf.sidec.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.UUID;

import static ru.sbrf.sidec.autoconfigure.SwitchoverAutoConfiguration.SWITCHOVER_ENABLED_CONFIG_PROPERTY;

@ConfigurationProperties(prefix = "sidec.switchover")
@ConditionalOnProperty(name = SWITCHOVER_ENABLED_CONFIG_PROPERTY, havingValue = "true")
public class SwitchoverConfig {
    public static final Logger LOGGER = LoggerFactory.getLogger(SwitchoverConfig.class);

    public final static String SWITCHOVER_SIGNAL_TOPIC_NAME_DEFAULT = "sidec.app_signal";
    public final static String SWITCHOVER_APP_NAME_DEFAULT = "sidec.switchover-" + UUID.randomUUID();
    public final static Integer SWITCHOVER_SIGNAL_REQUEST_TIMEOUT_MS_DEFAULT = 1000;
    public final static Integer SWITCHOVER_POLL_TIMEOUT_MS_DEFAULT = 1000;
    public final static Integer SWITCHOVER_WATCHER_DELAY_MS_DEFAULT = 1000;
    public final static Integer SWITCHOVER_WATCHER_CONNECTION_CLOSE_AWAIT_MS_DEFAULT = 1000;

    private Integer signalRequestTimeoutMs;
    private String signalTopic;
    private Integer pollTimeoutMs;
    private String appName;
    private Integer watcherDelayMs;

    private final SwitchoverDataSourceConfiguration dataSourceConfig;
    private final SwitchoverKafkaConfig kafkaConfig;

    public SwitchoverConfig(
            SwitchoverDataSourceConfiguration dataSourceConfig,
            SwitchoverKafkaConfig kafkaConfig
    ) {
        this.dataSourceConfig = dataSourceConfig;
        this.kafkaConfig = kafkaConfig;
    }

    @PostConstruct
    private void setDefaultParameters() {
        if (signalTopic == null) {
            LOGGER.info("The signal.topic value is not set. The default value: " + SWITCHOVER_SIGNAL_TOPIC_NAME_DEFAULT);
            this.signalTopic = SWITCHOVER_SIGNAL_TOPIC_NAME_DEFAULT;
        }
        if (pollTimeoutMs == null) {
            LOGGER.info("The poll.timeout.ms value is not set. The default value: " + SWITCHOVER_POLL_TIMEOUT_MS_DEFAULT);
            this.pollTimeoutMs = SWITCHOVER_POLL_TIMEOUT_MS_DEFAULT;
        }
        if (appName == null) {
            LOGGER.info("The app.name value is not set. The default value: " + SWITCHOVER_APP_NAME_DEFAULT);
            this.appName = SWITCHOVER_APP_NAME_DEFAULT;
        }
        if (watcherDelayMs == null) {
            LOGGER.info("The watcher.delay.ms value is not set. The default value: " + SWITCHOVER_WATCHER_DELAY_MS_DEFAULT);
            this.watcherDelayMs = SWITCHOVER_WATCHER_DELAY_MS_DEFAULT;
        }
        if (signalRequestTimeoutMs == null) {
            LOGGER.info("The signal.request.timeout.ms value is not set. The default value: " + SWITCHOVER_SIGNAL_REQUEST_TIMEOUT_MS_DEFAULT);
            this.signalRequestTimeoutMs = SWITCHOVER_SIGNAL_REQUEST_TIMEOUT_MS_DEFAULT;
        }
    }

    public String getAppName() {
        return appName;
    }

    public void setAppName(String appName) {
        this.appName = appName;
    }

    public String getSignalKafkaTopic() {
        return signalTopic;
    }

    public void setSignalTopic(String signalTopic) {
        this.signalTopic = signalTopic;
    }

    public SwitchoverDataSourceConfiguration getDataSourceConfig() {
        return dataSourceConfig;
    }

    public SwitchoverKafkaConfig getKafkaConfig() {
        return kafkaConfig;
    }

    public Integer getPollTimeoutMs() {
        return pollTimeoutMs;
    }

    public void setPollTimeoutMs(Integer pollTimeoutMs) {
        this.pollTimeoutMs = pollTimeoutMs;
    }

    public Integer getWatcherDelayMs() {
        return watcherDelayMs;
    }

    public void setWatcherDelayMs(Integer watcherDelayMs) {
        this.watcherDelayMs = watcherDelayMs;
    }

    public Integer getSignalRequestTimeoutMs() {
        return signalRequestTimeoutMs;
    }

    public void setSignalRequestTimeoutMs(Integer signalRequestTimeoutMs) {
        this.signalRequestTimeoutMs = signalRequestTimeoutMs;
    }
}