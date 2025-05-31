package ru.sbrf.sidec.metrics;

import org.apache.kafka.common.MetricName;

import java.util.Map;

/**
 * Фабрика для создания экземпляров {@link MetricName}.
 */
@FunctionalInterface
public interface MetricNameFactory {

    /**
     * Создает экземпляр {@link MetricName}, используя переданные параметры.
     *
     * @param name        имя метрики
     * @param group       имя группы метрики
     * @param description описание метрики
     * @param tags        метки метрики
     * @return экземпляр {@link MetricName}
     */
    MetricName metricName(String name, String group, String description, Map<String, String> tags);
}