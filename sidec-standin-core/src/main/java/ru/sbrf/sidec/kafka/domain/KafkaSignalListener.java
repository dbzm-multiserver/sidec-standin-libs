package ru.sbrf.sidec.kafka.domain;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.sbrf.sidec.api.kafka.consumer.ConsistentKafkaSignalListener;
import ru.sbrf.sidec.api.kafka.consumer.ForceKafkaSignalListener;

import java.util.EventListener;

public interface KafkaSignalListener<E extends SignalResponse> extends EventListener {
    Logger LOGGER = LoggerFactory.getLogger(KafkaSignalListener.class);
    default void doForceSwitch(E event){
        LOGGER.warn("Received force switch signal. " + ForceKafkaSignalListener.class + " not implemented to process it.");
    };
    default void prepareSwitch(E event){
        LOGGER.warn("Received consistent prepare switch signal. " + ConsistentKafkaSignalListener.class + " not implemented to process it.");
    };
    default void doConsistentSwitch(E event){
        LOGGER.warn("Received consistent switch signal. " + ConsistentKafkaSignalListener.class + " not implemented to process it.");
    };
}