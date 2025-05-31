package ru.sbrf.sidec.api.kafka.consumer;

import ru.sbrf.sidec.kafka.domain.KafkaSignalListener;
import ru.sbrf.sidec.kafka.domain.SignalResponse;

public interface ConsistentKafkaSignalListener<E extends SignalResponse> extends KafkaSignalListener<E> {
    /**
     *
     * @param event Входящий ивент
     *              Вызывается при получении сигнала для подготовки консистентного переключения
     */
    void prepareSwitch(E event);

    /**
     *
     * @param event Входящий ивент
     *              Вызывается при получении сигнала для консистентного переключения
     */
    void doConsistentSwitch(E event);
}