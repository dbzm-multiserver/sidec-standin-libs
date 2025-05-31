package ru.sbrf.sidec.metrics;

/**
 * Действие, выполнение которого может быть измерено и записано в сенсор.
 */
@FunctionalInterface
public interface MeasurableAction<R, E extends Throwable> {

    /**
     * Выполняет действие с замером времени выполнения.
     *
     * @return результат выполнения действия, может быть {@code null}
     */
    R execute() throws E;
}