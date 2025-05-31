package ru.sbrf.sidec.retry;

import org.springframework.retry.support.RetryTemplate;
import org.springframework.retry.support.RetryTemplateBuilder;
import org.springframework.stereotype.Service;

@Service
public class RetryService {
    private final RetryProperties properties;

    public RetryService(RetryProperties properties) {
        this.properties = properties;
    }

    public RetryTemplate retryTemplate() {
        return new RetryTemplateBuilder()
                .maxAttempts(properties.getMaxAttempts())
                .fixedBackoff(properties.getBackOffIntervalMs())
                .build();
    }
}