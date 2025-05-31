package ru.sbrf.sidec.metrics;

import org.apache.kafka.common.MetricName;
import org.apache.kafka.common.metrics.Sensor;

/**
 * Инициализатор сенсора с метриками.
 */
@FunctionalInterface
public interface SensorInitializer {

    /**
     * Инициализирует сенсор с метриками.
     *
     * @param sensor  сенсор, который должен быть инициализирован
     * @param factory фабрика для создания экземпляров {@link MetricName}
     */
    void init(Sensor sensor, MetricNameFactory factory);
}