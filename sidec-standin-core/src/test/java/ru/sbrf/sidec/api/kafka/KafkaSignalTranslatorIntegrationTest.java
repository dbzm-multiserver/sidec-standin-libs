package ru.sbrf.sidec.api.kafka;

import org.apache.log4j.Logger;
import org.apache.log4j.spi.LoggingEvent;
import ru.sbrf.sidec.kafka.domain.KafkaSignalListener;
import ru.sbrf.sidec.api.kafka.consumer.KafkaSignalTranslator;
import ru.sbrf.sidec.exception.SwitchoverException;
import ru.sbrf.sidec.extension.KafkaExtension;
import ru.sbrf.sidec.helper.Signals;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.sbrf.sidec.kafka.domain.SignalMode;
import ru.sbrf.sidec.kafka.domain.SignalRequest;
import ru.sbrf.sidec.kafka.domain.SignalResponse;
import ru.sbrf.sidec.kafka.serde.AppSignalDeserializer;
import ru.sbrf.sidec.util.TestAppender;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static ru.sbrf.sidec.config.SidecConfig.CONSUMER_GROUP_ID;
import static ru.sbrf.sidec.config.SidecConfig.CONSUMER_GROUP_ID_PREFIX;
import static ru.sbrf.sidec.extension.KafkaExtension.*;
import static ru.sbrf.sidec.helper.Signals.SIDEC_APP_SIGNAL_TOPIC;
import static ru.sbrf.sidec.helper.TestKafkaConsumer.*;
import static org.apache.kafka.clients.CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static ru.sbrf.sidec.util.AwaitilityUtil.defaultAwait;
import static ru.sbrf.sidec.util.KafkaUtil.deleteKafkaTopics;

@ExtendWith({MockitoExtension.class, KafkaExtension.class})
public class KafkaSignalTranslatorIntegrationTest {
    public static final String WAIT_ALL_SIGNALS_MESSAGE = "wait for all signals come to kafka";
    public static final String WAIT_ALL_INVOCATIONS_MESSAGE = "wait for all invocations of listener";
    public static final String NOT_IMPLEMENTED_PATTERN = "not implemented to process it";
    private static final UUID uuid = UUID.randomUUID();

    @Mock
    private KafkaSignalListener<SignalResponse> listener;
    private KafkaSignalTranslator kafkaSignalTranslator;
    private KafkaProducer<String, SignalRequest> producer = producer();

    private final Logger logger = Logger.getRootLogger();
    private final TestAppender appender = new TestAppender();

    @BeforeEach
    public void init() {
        mockListener();
        kafkaSignalTranslator = new KafkaSignalTranslator(listener, consumerProperties(UUID.randomUUID().toString()));
        initKafkaConsumer(new ArrayList<String>() {{
            add(SIDEC_APP_SIGNAL_TOPIC);
        }});
        subscribe();
        logger.addAppender(appender);
    }

    private void mockListener() {
        lenient().doNothing().when(listener).prepareSwitch(any());
        lenient().doNothing().when(listener).doConsistentSwitch(any());
        lenient().doNothing().when(listener).doForceSwitch(any());
    }

    @AfterEach
    public void cleanUp() {
        clearKafkaData();
        deleteKafkaTopics();
        kafkaSignalTranslator.close();
    }

    @Test
    public void translator_process_prepare_switch_signal() throws ExecutionException, InterruptedException {
        producer.send(Signals.consistentPrepareSwitch(uuid, SignalMode.MAIN)).get();
        producer.send(Signals.consistentPrepareSwitch(uuid, SignalMode.STANDIN)).get();
        defaultAwait(WAIT_ALL_SIGNALS_MESSAGE).until(() -> getKafkaData(SIDEC_APP_SIGNAL_TOPIC).size() == 2);
        defaultAwait(WAIT_ALL_INVOCATIONS_MESSAGE).until(() -> {
            kafkaSignalTranslator.translate(Duration.ofMillis(100));
            return Mockito.mockingDetails(listener).getInvocations().size() == 2;
        });
    }

    @Test
    public void translator_process_consistent_switch_signal() throws ExecutionException, InterruptedException {
        producer.send(Signals.consistentDoSwitch(uuid, SignalMode.MAIN)).get();
        producer.send(Signals.consistentDoSwitch(uuid, SignalMode.STANDIN)).get();
        defaultAwait(WAIT_ALL_SIGNALS_MESSAGE).until(() -> getKafkaData(SIDEC_APP_SIGNAL_TOPIC).size() == 2);
        defaultAwait(WAIT_ALL_INVOCATIONS_MESSAGE).until(() -> {
            kafkaSignalTranslator.translate(Duration.ofMillis(100));
            return Mockito.mockingDetails(listener).getInvocations().size() == 2;
        });
    }

    @Test
    public void translator_process_force_switch_signal() throws ExecutionException, InterruptedException {
        producer.send(Signals.doSwitch(uuid, SignalMode.MAIN)).get();
        producer.send(Signals.doSwitch(uuid, SignalMode.STANDIN)).get();
        defaultAwait(WAIT_ALL_SIGNALS_MESSAGE).until(() -> getKafkaData(SIDEC_APP_SIGNAL_TOPIC).size() == 2);
        defaultAwait(WAIT_ALL_INVOCATIONS_MESSAGE).until(() -> {
            kafkaSignalTranslator.translate(Duration.ofMillis(100));
            return Mockito.mockingDetails(listener).getInvocations().size() == 2;
        });
    }

    @Test
    public void listener_inform_about_not_releaseble_behaviour() throws ExecutionException, InterruptedException {
        listener = new NotReleasableListener();
        kafkaSignalTranslator = new KafkaSignalTranslator(listener, consumerProperties(UUID.randomUUID().toString()));
        subscribe();
        producer.send(Signals.doSwitch(uuid, SignalMode.MAIN)).get();
        producer.send(Signals.consistentDoSwitch(uuid, SignalMode.MAIN)).get();
        producer.send(Signals.consistentPrepareSwitch(uuid, SignalMode.STANDIN)).get();
        defaultAwait(WAIT_ALL_SIGNALS_MESSAGE).until(() -> getKafkaData(SIDEC_APP_SIGNAL_TOPIC).size() == 3);
        for (int i = 0; i < 5; i++) {
            kafkaSignalTranslator.translate(Duration.ofMillis(100));
            TimeUnit.SECONDS.sleep(1);
        }
        assertThat(extractLogs()).hasSize(3);
    }

    @Test
    public void translator_creation_without_consumer_group_id() {
        Properties properties = consumerProperties("test");
        properties.remove(ConsumerConfig.GROUP_ID_CONFIG);
        assertThatThrownBy(() -> {
            new KafkaSignalTranslator(new NotReleasableListener(), properties);
        }).isInstanceOf(SwitchoverException.class)
                .hasMessageContaining("groupId is null. Please, set groupId in config");
    }

    @Test
    public void translator_creation_with_empty_consumer_group_id() {
        Properties properties = consumerProperties("");
        assertThatThrownBy(() -> {
            new KafkaSignalTranslator(new NotReleasableListener(), properties);
        }).isInstanceOf(SwitchoverException.class)
                .hasMessageContaining("groupId is empty. Please, set groupId in config");
    }

    @Test
    public void translator_creation_with_not_contract_consumer_group_id() {
        Properties properties = consumerProperties("not_contract");
        KafkaSignalTranslator translator = new KafkaSignalTranslator(new NotReleasableListener(), properties);
        assertThat(translator.groupMetadata().groupId()).isEqualTo(CONSUMER_GROUP_ID_PREFIX + "not_contract");
    }

    private List<String> extractLogs() {
        return appender.getLog().stream()
                .map(LoggingEvent::getRenderedMessage)
                .filter(x -> x.contains(NOT_IMPLEMENTED_PATTERN))
                .collect(Collectors.toList());
    }

    private void subscribe() {
        kafkaSignalTranslator.subscribe(new ArrayList<String>() {{
            add(SIDEC_APP_SIGNAL_TOPIC);
        }});
    }

    private Properties consumerProperties(String groupId) {
        Properties properties = new Properties();
        properties.put(BOOTSTRAP_SERVERS_CONFIG, System.getProperty(KAFKA_BOOTSTRAP_SERVERS));
        properties.put(CONSUMER_GROUP_ID, groupId);
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, AppSignalDeserializer.class.getName());
        return properties;
    }
}