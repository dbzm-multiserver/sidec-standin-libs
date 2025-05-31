package ru.sbrf.sidec.utils;

import org.awaitility.core.ConditionFactory;

import org.awaitility.Duration;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;

public class AwaitilityUtil {
    public static final Duration POLL_TIMEOUT = new Duration(60, TimeUnit.SECONDS);
    public static final Duration POLL_INTERVAL = new Duration(10, TimeUnit.MILLISECONDS);

    public static ConditionFactory defaultAwait(String alias) {
        return await(alias)
                .atMost(POLL_TIMEOUT)
                .with()
                .pollInterval(POLL_INTERVAL);
    }

    public static ConditionFactory defaultAwait() {
        return defaultAwait(null);
    }
}