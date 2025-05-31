package ru.sbrf.sidec.retry;

import com.fasterxml.jackson.annotation.JsonValue;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "sidec.switchover.retry")
public class RetryProperties {
    @JsonValue
    private int maxAttempts = 120;
    private int backOffIntervalMs = 1000;

    public RetryProperties() {
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public int getBackOffIntervalMs() {
        return backOffIntervalMs;
    }

    public void setBackOffIntervalMs(int backOffIntervalMs) {
        this.backOffIntervalMs = backOffIntervalMs;
    }
}