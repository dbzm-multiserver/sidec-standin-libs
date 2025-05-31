package ru.sbrf.sidec.api.kafka.consumer;

import ru.sbrf.sidec.kafka.domain.KafkaSignalListener;
import ru.sbrf.sidec.kafka.domain.SignalResponse;

public interface ForceKafkaSignalListener<E extends SignalResponse> extends KafkaSignalListener<E> {
    /**
     *
     * @param event Входящий ивент
     *              Вызывается при получении сигнала для force переключения
     */
    void doForceSwitch(E event);
}