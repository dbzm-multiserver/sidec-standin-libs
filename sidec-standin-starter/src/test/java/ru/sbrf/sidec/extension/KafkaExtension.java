package ru.sbrf.sidec.extension;

import ru.sbrf.sidec.containers.SidecKafkaContainer;
import ru.sbrf.sidec.containers.TestcontainersGlobalConfig;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.KafkaAdminClient;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.Network;
import ru.sbrf.sidec.kafka.domain.SignalRequest;
import ru.sbrf.sidec.kafka.serde.AppSignalSerializer;

import java.util.Properties;

import static org.apache.kafka.clients.admin.AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG;
import static org.apache.kafka.clients.producer.ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG;
import static org.apache.kafka.clients.producer.ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG;

public class KafkaExtension implements BeforeAllCallback {
    private final Logger log = LoggerFactory.getLogger(KafkaExtension.class);

    private static KafkaContainer kafkaContainer;

    public static final String KAFKA_BOOTSTRAP_SERVERS = "KAFKA_BOOTSTRAP_SERVERS";


    @Override
    public void beforeAll(ExtensionContext context) throws InterruptedException {
        if (kafkaContainer != null && kafkaContainer.isRunning()) {
            log.info("Kafka container is already running [host: {}, port: {}]", kafkaContainer.getHost(), kafkaContainer.getFirstMappedPort());
            return;
        }
        GenericContainer<?> zookeeperContainer = SidecKafkaContainer.createZookeeperContainer(Network.newNetwork());
        kafkaContainer = SidecKafkaContainer.create(zookeeperContainer);
        kafkaContainer.start();
        if (TestcontainersGlobalConfig.ENABLE_KAFKA_UI) {
            SidecKafkaContainer.createKafkaUiContainer(zookeeperContainer, kafkaContainer).start();
        }
        System.setProperty(KAFKA_BOOTSTRAP_SERVERS, getKafkaBrokers(kafkaContainer, "127.0.0.1"));
    }

    private static String getKafkaBrokers(KafkaContainer kafkaContainer, String host) {
        Integer mappedPort = kafkaContainer.getFirstMappedPort();
        return String.format("%s:%d", host, mappedPort);
    }

    public static AdminClient adminClient() {
        Properties properties = new Properties();
        properties.put(BOOTSTRAP_SERVERS_CONFIG, System.getProperty(KAFKA_BOOTSTRAP_SERVERS));
        return KafkaAdminClient.create(properties);
    }

    public static KafkaProducer<String, SignalRequest> producer() {
        Properties properties = new Properties();
        properties.put(BOOTSTRAP_SERVERS_CONFIG, System.getProperty(KAFKA_BOOTSTRAP_SERVERS));
        properties.put(KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        properties.put(VALUE_SERIALIZER_CLASS_CONFIG, AppSignalSerializer.class.getName());
        return new KafkaProducer<>(properties);
    }

    public static KafkaConsumer<String, SignalRequest> consumer() {
        Properties properties = new Properties();
        properties.put(BOOTSTRAP_SERVERS_CONFIG, System.getProperty(KAFKA_BOOTSTRAP_SERVERS));
        properties.put("group.id", "internal-test-consumer");
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        return new KafkaConsumer<>(properties);
    }
}