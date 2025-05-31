package ru.sbrf.sidec.metrics;

/**
 * Поставщик для получения текущего значения метрики.
 */
@FunctionalInterface
public interface MetricValueSupplier<T> extends AutoCloseable {

    /**
     * Возвращает текущее значение метрики.
     *
     * @param now текущее время в миллисекундах
     * @return текущее значение метрики
     */
    T metricValue(long now);

    default void close() throws Exception {
    }
}