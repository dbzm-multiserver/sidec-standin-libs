package ru.sbrf.sidec.config;

import jakarta.annotation.PostConstruct;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AlterConfigOp;
import org.apache.kafka.clients.admin.Config;
import org.apache.kafka.clients.admin.ConfigEntry;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.config.ConfigResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.TestConfiguration;
import ru.sbrf.sidec.exception.SwitchoverException;
import ru.sbrf.sidec.kafka.domain.SignalRequest;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@TestConfiguration
public class TopicRecordsDeleteConfiguration {
    public static final Logger LOGGER = LoggerFactory.getLogger(TopicRecordsDeleteConfiguration.class);

    private static final String RETENTION_MS_CONFIG = "retention.ms";
    private static final String DELETE_RETENTION_MS_CONFIG = "delete.retention.ms";
    private static final String FIXED_RETENTION_MS = "1000";
    private final SwitchoverConfig config;
    private AdminClient adminClient;
    private ConfigResource configResource;

    @PostConstruct
    public void createEmptyTopicWithOffsetsGreaterThenZero() {
        LOGGER.info("Start creating empty topic with offsets greater then zero");
        adminClient = config.getKafkaConfig()
                .getAdminClient();
        configResource = new ConfigResource(ConfigResource.Type.TOPIC, config.getSignalKafkaTopic());

        Producer<String, SignalRequest> producer = config.getKafkaConfig().getProducerFactory().createProducer();
        producer.send(createProducerRecord("test-1"));

        ConfigEntry configEntry1;
        ConfigEntry configEntry2;
        try {
            Map<ConfigResource, Config> configResourceConfigMap = adminClient.describeConfigs(List.of(configResource))
                    .all()
                    .get(5, TimeUnit.SECONDS);
            Config signalTopicConfig = configResourceConfigMap.values().stream()
                    .findFirst()
                    .orElseThrow(
                            () -> new SwitchoverException("Configs for signal topic not found")
                    );

            configEntry1 = signalTopicConfig.get(DELETE_RETENTION_MS_CONFIG);
            configEntry2 = signalTopicConfig.get(RETENTION_MS_CONFIG);

            alterConfig(changedAlterConfigOp(configEntry1));
            alterConfig(changedAlterConfigOp(configEntry2));

            producer.send(createProducerRecord("test-2"));
            producer.send(createProducerRecord("test-3"));

            //Ожидание очистки топика
            TimeUnit.SECONDS.sleep(60);

            alterConfig(originalAlterConfigOp(configEntry1));
            alterConfig(originalAlterConfigOp(configEntry2));
            LOGGER.info("Finish creating empty topic with offsets greater then zero");
        } catch (InterruptedException | ExecutionException | TimeoutException ex) {
            throw new SwitchoverException("Exception during initialization", ex);
        }
    }

    private void alterConfig(AlterConfigOp alterConfigOp) throws ExecutionException, InterruptedException, TimeoutException {
        adminClient.incrementalAlterConfigs(
                        Map.of(configResource, List.of(alterConfigOp))
                )
                .all()
                .get(5, TimeUnit.SECONDS);
    }

    private AlterConfigOp originalAlterConfigOp(ConfigEntry configEntry){
        return new AlterConfigOp(configEntry, AlterConfigOp.OpType.SET);
    }

    private AlterConfigOp changedAlterConfigOp(ConfigEntry configEntry){
        return new AlterConfigOp(changeCleanUpPolicyConfig(configEntry), AlterConfigOp.OpType.SET);
    }

    private ProducerRecord<String, SignalRequest> createProducerRecord(String key) {
        return new ProducerRecord<>(config.getSignalKafkaTopic(), key, new SignalRequest());
    }

    private ConfigEntry changeCleanUpPolicyConfig(ConfigEntry configEntry) {
        return new ConfigEntry(
                configEntry.name(),
                FIXED_RETENTION_MS,
                configEntry.source(),
                configEntry.isSensitive(),
                configEntry.isReadOnly(),
                configEntry.synonyms(),
                configEntry.type(),
                configEntry.documentation()
        );
    }

    public TopicRecordsDeleteConfiguration(SwitchoverConfig config) {
        this.config = config;
    }
}