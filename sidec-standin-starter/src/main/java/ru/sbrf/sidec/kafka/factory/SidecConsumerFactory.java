package ru.sbrf.sidec.kafka.factory;

import org.apache.kafka.clients.consumer.Consumer;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import ru.sbrf.sidec.exception.SwitchoverException;

import java.util.List;
import java.util.Map;

import static ru.sbrf.sidec.config.SidecConfig.CONSUMER_CLIENT_ID;
import static ru.sbrf.sidec.config.SidecConfig.CONSUMER_GROUP_ID;

public class SidecConsumerFactory<K, V> extends DefaultKafkaConsumerFactory<K, V> {
    private final Map<String, Object> consumerProperties;

    public SidecConsumerFactory(Map<String, Object> consumerProperties) {
        super(consumerProperties);
        this.consumerProperties = consumerProperties;
    }

    public Consumer<K, V> createConsumer(List<String> topicsList) {
        return createConsumer(topicsList, getGroupId());
    }

    public Consumer<K, V> createConsumer(List<String> topicsList, String groupId) {
        Consumer<K, V> kafkaConsumer = this.createConsumer(groupId, getClientId());
        kafkaConsumer.subscribe(topicsList);
        var subscription = kafkaConsumer.subscription();
        if (subscription.isEmpty()) {
            throw new SwitchoverException("Topic is absent. Please create new one.");
        }
        return kafkaConsumer;
    }

    private String getGroupId() {
        return String.valueOf(consumerProperties.get(CONSUMER_GROUP_ID));
    }

    private String getClientId() {
        return String.valueOf(consumerProperties.get(CONSUMER_CLIENT_ID));
    }
}