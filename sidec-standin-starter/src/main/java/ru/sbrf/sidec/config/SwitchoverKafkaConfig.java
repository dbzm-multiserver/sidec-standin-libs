package ru.sbrf.sidec.config;

import jakarta.annotation.PostConstruct;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import ru.sbrf.sidec.kafka.domain.SignalRequest;
import ru.sbrf.sidec.kafka.domain.SignalResponse;
import ru.sbrf.sidec.kafka.factory.SidecConsumerFactory;
import ru.sbrf.sidec.kafka.factory.SidecProducerFactory;
import ru.sbrf.sidec.kafka.serde.AppSignalDeserializer;
import ru.sbrf.sidec.kafka.serde.AppSignalSerializer;

import java.util.UUID;

import static ru.sbrf.sidec.autoconfigure.SwitchoverAutoConfiguration.SWITCHOVER_ENABLED_CONFIG_PROPERTY;
import static ru.sbrf.sidec.config.SidecConfig.CONSUMER_GROUP_ID_PREFIX;

@ConfigurationProperties(prefix = "sidec")
@ConditionalOnProperty(name = SWITCHOVER_ENABLED_CONFIG_PROPERTY, havingValue = "true")
public class SwitchoverKafkaConfig {
    @NestedConfigurationProperty
    private KafkaProperties kafkaProperties;
    private SidecConsumerFactory<String, SignalResponse> consumerFactory;
    private SidecProducerFactory<String, SignalRequest> producerFactory;
    private AdminClient adminClient;

    @PostConstruct
    public void initBaseParameters() {
        initKafkaAdminProperties();
        initKafkaConsumerProperties();
        initKafkaProducerProperties();
        this.consumerFactory = new SidecConsumerFactory<>(kafkaProperties.buildConsumerProperties(null));
        this.producerFactory = new SidecProducerFactory<>(kafkaProperties.buildProducerProperties(null));
    }

    private void initKafkaAdminProperties() {
        var admin = kafkaProperties.getAdmin();
        admin.setAutoCreate(false);
        adminClient = AdminClient.create(kafkaProperties.buildAdminProperties(null));
    }

    private void initKafkaConsumerProperties() {
        var consumer = kafkaProperties.getConsumer();
        consumer.setGroupId(CONSUMER_GROUP_ID_PREFIX + UUID.randomUUID());
        consumer.setEnableAutoCommit(false);
        consumer.setAutoOffsetReset("earliest");
        consumer.setKeyDeserializer(StringDeserializer.class);
        consumer.setValueDeserializer(AppSignalDeserializer.class);
    }

    private void initKafkaProducerProperties() {
        var producer = kafkaProperties.getProducer();
        producer.setKeySerializer(StringSerializer.class);
        producer.setValueSerializer(AppSignalSerializer.class);
    }

    public void setKafka(KafkaProperties kafkaProperties) {
        this.kafkaProperties = kafkaProperties;
    }

    public SidecConsumerFactory<String, SignalResponse> getConsumerFactory() {
        return consumerFactory;
    }

    public SidecProducerFactory<String, SignalRequest> getProducerFactory() {
        return producerFactory;
    }

    public AdminClient getAdminClient() {
        return adminClient;
    }
}