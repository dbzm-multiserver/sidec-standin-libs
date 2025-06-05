package ru.sbrf.sidec.metrics;

import ru.sbrf.sidec.db.ConnectionMode;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

public class SwitchoverDelegatorMetrics {
    private static final String SWITCHOVER_METRICS_GROUP = "standin-switchover-state";
    private static final String SWITCHOVER_CURRENT_CONNECTION_MODE_NAME = "SwitchoverCurrentConnectionMode";
    private static final String APP_UID_METRIC_TAG = "app.uid";
    private static final String APP_NAME_METRIC_TAG = "app.name";
    private final MetricRegistry metricRegistry;

    public SwitchoverDelegatorMetrics(String appUid, String appName) {
        metricRegistry = new MetricRegistry(new HashMap<String, String>(){{
            put(APP_UID_METRIC_TAG, appUid);
            put(APP_NAME_METRIC_TAG, appName);
        }});
    }

    public void registerSwitchoverMetrics(ConnectionMode connectionMode) {
        Map<String, String> tags = new HashMap<>();
        metricRegistry.addMetric(
                metricRegistry.metricName(
                        SWITCHOVER_CURRENT_CONNECTION_MODE_NAME,
                        SWITCHOVER_METRICS_GROUP,
                        "Show current state of switchover delegator.",
                        tags
                ),
                now -> connectionMode.name()
        );
    }
}