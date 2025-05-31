package ru.sbrf.sidec.config;

import jakarta.annotation.PostConstruct;
import org.apache.kafka.clients.consumer.Consumer;
import org.springframework.boot.test.context.TestConfiguration;
import ru.sbrf.sidec.kafka.domain.SignalResponse;
import ru.sbrf.sidec.kafka.factory.SidecConsumerFactory;

import java.util.List;

import static ru.sbrf.sidec.config.SidecConfig.CONSUMER_GROUP_ID_PREFIX;

@TestConfiguration
public class EmptyConsumerGroupsConfiguration {
    private SwitchoverConfig config;
    SidecConsumerFactory<String, SignalResponse> consumerFactory;
    @PostConstruct
    public void createAdditionalConsumerGroups() {
        this.consumerFactory = config.getKafkaConfig().getConsumerFactory();
        createAdditionalConsumerGroup(List.of(config.getSignalKafkaTopic()), CONSUMER_GROUP_ID_PREFIX + "test1");
        createAdditionalConsumerGroup(List.of(config.getSignalKafkaTopic()), CONSUMER_GROUP_ID_PREFIX + "test2");
        createAdditionalConsumerGroup(List.of(config.getSignalKafkaTopic()), "test3");
        createAdditionalConsumerGroup(List.of("other-topic"), CONSUMER_GROUP_ID_PREFIX + "test4");
    }

    //Эмуляция запуска консьюмера с его дальнейшим закрытием, что инициализировать consumer group
    private void createAdditionalConsumerGroup(List<String> topicsList, String groupId) {
        Consumer<String, SignalResponse> consumer = consumerFactory.createConsumer(topicsList, groupId);
        consumer.poll(100);
        consumer.close();
    }

    public EmptyConsumerGroupsConfiguration(SwitchoverConfig config) {
        this.config = config;
    }
}