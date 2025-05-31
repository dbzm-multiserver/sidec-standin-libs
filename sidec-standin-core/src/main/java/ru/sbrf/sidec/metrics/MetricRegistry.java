package ru.sbrf.sidec.metrics;

import org.apache.kafka.common.MetricName;
import org.apache.kafka.common.config.AbstractConfig;
import org.apache.kafka.common.config.ConfigDef;
import org.apache.kafka.common.config.ConfigDef.Importance;
import org.apache.kafka.common.config.ConfigDef.Type;
import org.apache.kafka.common.metrics.Gauge;
import org.apache.kafka.common.metrics.JmxReporter;
import org.apache.kafka.common.metrics.KafkaMetric;
import org.apache.kafka.common.metrics.KafkaMetricsContext;
import org.apache.kafka.common.metrics.MetricConfig;
import org.apache.kafka.common.metrics.Metrics;
import org.apache.kafka.common.metrics.MetricsReporter;
import org.apache.kafka.common.metrics.Sensor;
import org.apache.kafka.common.utils.Time;
import org.apache.kafka.common.utils.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import static org.apache.kafka.clients.CommonClientConfigs.METRICS_CONTEXT_PREFIX;
import static org.apache.kafka.clients.CommonClientConfigs.METRICS_NUM_SAMPLES_CONFIG;
import static org.apache.kafka.clients.CommonClientConfigs.METRICS_NUM_SAMPLES_DOC;
import static org.apache.kafka.clients.CommonClientConfigs.METRICS_RECORDING_LEVEL_CONFIG;
import static org.apache.kafka.clients.CommonClientConfigs.METRICS_RECORDING_LEVEL_DOC;
import static org.apache.kafka.clients.CommonClientConfigs.METRICS_SAMPLE_WINDOW_MS_CONFIG;
import static org.apache.kafka.clients.CommonClientConfigs.METRICS_SAMPLE_WINDOW_MS_DOC;
import static org.apache.kafka.clients.CommonClientConfigs.METRIC_REPORTER_CLASSES_CONFIG;
import static org.apache.kafka.clients.CommonClientConfigs.METRIC_REPORTER_CLASSES_DOC;
import static org.apache.kafka.common.config.ConfigDef.Range.atLeast;
import static org.apache.kafka.common.config.ConfigDef.ValidString.in;

/**
 * Реестр сенсоров с метриками.
 * <p>
 * Метрика - это именованное числовое измерение. Сенсор - это ручка для записи числовых измерений по мере их выполнения.
 * Каждый сенсор имеет ноль или более связанных метрик.
 */
public class MetricRegistry implements AutoCloseable {

    public static final Logger LOGGER = LoggerFactory.getLogger(MetricRegistry.class);

    private static final String METRIC_NAMESPACE = "sidec";
    private static final ConfigDef CONFIG_DEF = new ConfigDef()
            .define(
                    METRICS_NUM_SAMPLES_CONFIG,
                    Type.INT,
                    2,
                    atLeast(1),
                    Importance.LOW,
                    METRICS_NUM_SAMPLES_DOC)
            .define(
                    METRICS_SAMPLE_WINDOW_MS_CONFIG,
                    Type.LONG,
                    30000,
                    atLeast(0),
                    Importance.LOW,
                    METRICS_SAMPLE_WINDOW_MS_DOC)
            .define(
                    METRICS_RECORDING_LEVEL_CONFIG,
                    Type.STRING,
                    Sensor.RecordingLevel.INFO.toString(),
                    in(Sensor.RecordingLevel.INFO.toString(), Sensor.RecordingLevel.DEBUG.toString()),
                    Importance.LOW,
                    METRICS_RECORDING_LEVEL_DOC)
            .define(
                    METRIC_REPORTER_CLASSES_CONFIG,
                    Type.LIST,
                    "",
                    Importance.LOW,
                    METRIC_REPORTER_CLASSES_DOC);

    private final Metrics metrics;
    private final Set<String> sensorNames = Collections.synchronizedSet(new HashSet<>());;
    private final Map<MetricName, MetricValueSupplier<?>> metricValueSuppliers = new ConcurrentHashMap<>();

    public MetricRegistry() {
        this(Map.of(), Map.of());
    }

    public MetricRegistry(Map<String, String> tags) {
        this(Map.of(), tags);
    }

    public MetricRegistry(Map<String, String> metricsConfig, Map<String, String> tags) {
        MetricsConfig config = new MetricsConfig(CONFIG_DEF, metricsConfig);
        Integer numSamples = config.getInt(METRICS_NUM_SAMPLES_CONFIG);
        Long sampleWindowMs = config.getLong(METRICS_SAMPLE_WINDOW_MS_CONFIG);
        String metricsRecordingLevel = config.getString(METRICS_RECORDING_LEVEL_CONFIG);
        List<MetricsReporter> reporters = config.getConfiguredInstances(METRIC_REPORTER_CLASSES_CONFIG, MetricsReporter.class);
        MetricConfig metricConfig = new MetricConfig()
                .samples(numSamples)
                .timeWindow(sampleWindowMs, TimeUnit.MILLISECONDS)
                .recordLevel(Sensor.RecordingLevel.forName(metricsRecordingLevel))
                .tags(tags);
        JmxReporter jmxReporter = new JmxReporter();
        jmxReporter.configure(config.originals());
        reporters.add(jmxReporter);
        Map<String, Object> contextLabels = new HashMap<>(config.originalsWithPrefix(METRICS_CONTEXT_PREFIX));
        KafkaMetricsContext metricsContext = new KafkaMetricsContext(METRIC_NAMESPACE, contextLabels);
        metrics = new Metrics(metricConfig, reporters, Time.SYSTEM, metricsContext);
    }

    /**
     * Измеряет время выполнения указанного действия.
     *
     * @param sensor           сенсор, который должен быть обновлен результатом измерения
     * @param action           измеряемое действие
     * @param measurePredicate нужно ли записывать результат измерения в сенсор
     * @return результат выполнения действия, может быть {@code null}
     */
    public static <R, E extends Throwable> R measureExecutionTime(Sensor sensor,
                                                                  MeasurableAction<R, E> action,
                                                                  Predicate<R> measurePredicate) throws E {
        long startTimeMs = System.currentTimeMillis();
        R result = action.execute();
        if (measurePredicate.test(result)) {
            sensor.record(System.currentTimeMillis() - startTimeMs);
        }
        return result;
    }

    /**
     * Измеряет время выполнения указанного действия.
     *
     * @param sensor сенсор, который должен быть обновлен результатом измерения
     * @param action измеряемое действие
     * @return результат выполнения действия, может быть {@code null}
     */
    public static <R, E extends Throwable> R measureExecutionTime(Sensor sensor, MeasurableAction<R, E> action) throws E {
        return measureExecutionTime(sensor, action, r -> true);
    }

    /**
     * Создает экземпляр {@link MetricName}, используя переданные параметры.
     *
     * @param name        имя метрики
     * @param group       имя группы метрики
     * @param description описание метрики
     * @param tags        метки метрики
     * @return экземпляр {@link MetricName}
     */
    public MetricName metricName(String name, String group, String description, Map<String, String> tags) {
        return metrics.metricName(name, group, description, tags);
    }

    /**
     * Создает экземпляр {@link MetricName}, используя переданные параметры.
     *
     * @param name        имя метрики
     * @param group       имя группы метрики
     * @param description описание метрики
     * @return экземпляр {@link MetricName}
     */
    public MetricName metricName(String name, String group, String description) {
        return metricName(name, group, description, Map.of());
    }

    /**
     * Возвращает или создает сенсор с указанным уникальным именем.
     *
     * @param name        имя сенсора
     * @param initializer функция, что используется для инициализации сенсора
     * @return сенсор
     */
    public synchronized Sensor sensor(String name, SensorInitializer initializer) {
        Sensor sensor = metrics.getSensor(name);
        if (sensor == null) {
            sensor = metrics.sensor(name);
            initializer.init(sensor, this::metricName);
            sensorNames.add(name);
            LOGGER.trace("Sensor \"{}\" is added to the registry", name);
        }
        return sensor;
    }

    /**
     * Добавляет в реестр метрик индикатор, который возвращает текущее значение метрики.
     *
     * @param metricName          экземпляр {@link MetricName}
     * @param metricValueSupplier функция, что используется для получения текущего значения метрики
     */
    public synchronized <T> void addMetric(MetricName metricName, MetricValueSupplier<T> metricValueSupplier) {
        metrics.addMetric(metricName, (Gauge<T>) (config, now) -> metricValueSupplier.metricValue(now));
        metricValueSuppliers.put(metricName, metricValueSupplier);
    }

    /**
     * Удаляет индикатор из реестра метрик.
     *
     * @param metricName экземпляр {@link MetricName}
     */
    public synchronized void removeMetric(MetricName metricName) {
        metrics.removeMetric(metricName);
        MetricValueSupplier<?> metricValueSupplier = metricValueSuppliers.remove(metricName);
        if (metricValueSupplier != null) {
            closeMetricValueSupplier(metricName, metricValueSupplier);
        }
    }

    public synchronized void close() {
        for (String sensorName : sensorNames) {
            metrics.removeSensor(sensorName);
            LOGGER.trace("Sensor \"{}\" is removed from the registry", sensorName);
        }
        sensorNames.clear();
        for (MetricName metricName : new HashSet<>(metrics.metrics().keySet())) {
            metrics.removeMetric(metricName);
            LOGGER.trace("Metric \"{}\" is removed from the registry", metricName);
        }
        metricValueSuppliers.forEach(MetricRegistry::closeMetricValueSupplier);
        Utils.closeQuietly(metrics, "metrics");
    }

    private static void closeMetricValueSupplier(MetricName metricName, MetricValueSupplier<?> metricValueSupplier) {
        Utils.closeQuietly(metricValueSupplier, "value supplier for metric \"" + metricName.name() + "\"");
    }

    public Map<MetricName, KafkaMetric> metrics() {
        return Map.copyOf(metrics.metrics());
    }

    private static class MetricsConfig extends AbstractConfig {

        private MetricsConfig(ConfigDef definition, Map<?, ?> originals) {
            super(definition, originals);
        }
    }
}