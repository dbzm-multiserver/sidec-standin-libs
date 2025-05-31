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
KafkaSignalTranslator-4
        package ru.sbrf.sidec.api.kafka.consumer;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.sbrf.sidec.exception.SwitchoverException;
import ru.sbrf.sidec.kafka.domain.KafkaSignalListener;
import ru.sbrf.sidec.kafka.domain.SignalResponse;
import ru.sbrf.sidec.kafka.domain.SignalStatus;
import ru.sbrf.sidec.kafka.domain.SwitchType;

import java.time.Duration;

import java.util.Map;
import java.util.Properties;

import static ru.sbrf.sidec.config.SidecConfig.CONSUMER_GROUP_ID;
import static ru.sbrf.sidec.config.SidecConfig.CONSUMER_GROUP_ID_PREFIX;

public class KafkaSignalTranslator extends KafkaConsumer<String, SignalResponse> {
    public static final Logger LOGGER = LoggerFactory.getLogger(KafkaSignalTranslator.class);
    private final KafkaSignalListener<SignalResponse> listener;

    public KafkaSignalTranslator(KafkaSignalListener<SignalResponse> listener, Properties properties) {
        super(validate(properties));
        this.listener = listener;
    }

    public KafkaSignalTranslator(KafkaSignalListener<SignalResponse> listener, Map<String, Object> configs) {
        super(validate(configs));
        this.listener = listener;
    }

    public void translate(Duration timeout) {
        ConsumerRecords<String, SignalResponse> records = poll(timeout);
        for (ConsumerRecord<String, SignalResponse> record : records) {
            SignalResponse value = record.value();
            if (value.getSwitchType() == null || value.getStatus() == null) {
                LOGGER.warn("switchType or status is null. Message will be ignored. Message [{}]", value);
            }
            if (value.getSwitchType() == SwitchType.FORCE) {
                listener.doForceSwitch(value);
                continue;
            }
            if (value.getSwitchType() == SwitchType.CONSISTENT) {
                if (value.getStatus() == SignalStatus.STARTED) {
                    listener.prepareSwitch(value);
                    continue;
                }
                if (value.getStatus() == SignalStatus.READY_TO_SWITCH) {
                    listener.doConsistentSwitch(value);
                    continue;
                }
            }
            LOGGER.warn("The message is unidentified. Message will be ignored. Message [{}]", value);
        }
    }

    private static Properties validate(Map<String, Object> configs) {
        Properties properties = new Properties();
        properties.putAll(configs);
        return validate(properties);
    }

    private static Properties validate(Properties properties) {
        Object groupIdObj = properties.get(CONSUMER_GROUP_ID);
        if (groupIdObj == null) {
            throw new SwitchoverException("groupId is null. Please, set groupId in config");
        }
        String groupId = String.valueOf(groupIdObj);
        if (groupId.isEmpty()) {
            throw new SwitchoverException("groupId is empty. Please, set groupId in config");
        }
        if (groupId.startsWith(CONSUMER_GROUP_ID_PREFIX)) {
            LOGGER.info("Consumer group.id start starts with " + CONSUMER_GROUP_ID_PREFIX + ". No action needed");
        } else {
            LOGGER.info("Consumer group.id  not start starts with " + CONSUMER_GROUP_ID_PREFIX + ". Prefix will be added");
            properties.put(CONSUMER_GROUP_ID, CONSUMER_GROUP_ID_PREFIX + groupId);
        }
        return properties;
    }
}