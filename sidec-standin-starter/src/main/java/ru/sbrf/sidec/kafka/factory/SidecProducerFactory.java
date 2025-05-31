package ru.sbrf.sidec.kafka.factory;

import org.springframework.kafka.core.DefaultKafkaProducerFactory;

import java.util.Map;

public class SidecProducerFactory<K,V> extends DefaultKafkaProducerFactory<K,V> {
    public SidecProducerFactory(Map<String, Object> producerProperties) {
        super(producerProperties);
    }
}