package ru.sbrf.sidec.api.kafka;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import ru.sbrf.sidec.api.kafka.producer.KafkaSignalBuilder;
import ru.sbrf.sidec.kafka.domain.SignalMode;
import ru.sbrf.sidec.kafka.domain.SignalRequest;
import ru.sbrf.sidec.kafka.domain.SwitchType;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static ru.sbrf.sidec.config.SidecConfig.APP_SIGNAL_RECORD_KEY;
import static ru.sbrf.sidec.config.SidecConfig.SIGNAL_TOPIC_NAME_DEFAULT;
import static ru.sbrf.sidec.kafka.domain.SignalStatus.READY_TO_SWITCH;
import static ru.sbrf.sidec.kafka.domain.SignalStatus.STARTED;

public class KafkaSignalBuilderTest {
    @Test
    public void builder_failed_on_absent_fields() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> KafkaSignalBuilder.builder().build());
        exception.getMessage().contains("Error during build producer request. The following fields should not be null: Uid, Mode, SwitchType");
    }

    @Test
    public void builder_build_with_all_fields_consistent() {
        UUID uuid = UUID.randomUUID();
        ProducerRecord<String, SignalRequest> record = KafkaSignalBuilder.builder()
                .withUid(uuid)
                .withMode(SignalMode.MAIN)
                .withAuthor("author")
                .withDescription("description")
                .withSwitchType(SwitchType.CONSISTENT)
                .build();
        SignalRequest request = record.value();
        assertAll(
                () -> assertThat(request.getUid()).isEqualTo(uuid),
                () -> assertThat(request.getMode()).isEqualTo(SignalMode.MAIN),
                () -> assertThat(request.getAuthor()).isEqualTo("author"),
                () -> assertThat(request.getDescription()).isEqualTo("description"),
                () -> assertThat(request.getSwitchType()).isEqualTo(SwitchType.CONSISTENT),
                () -> assertThat(request.getStatus()).isEqualTo(STARTED),
                () -> assertThat(record.key()).isEqualTo(APP_SIGNAL_RECORD_KEY),
                () -> assertThat(record.topic()).isEqualTo(SIGNAL_TOPIC_NAME_DEFAULT)
        );
    }

    @Test
    public void builder_build_with_all_fields_force() {
        UUID uuid = UUID.randomUUID();
        ProducerRecord<String, SignalRequest> record = KafkaSignalBuilder.builder()
                .withUid(uuid)
                .withMode(SignalMode.MAIN)
                .withAuthor("author")
                .withDescription("description")
                .withSwitchType(SwitchType.FORCE)
                .build();
        SignalRequest request = record.value();
        assertAll(
                () -> assertThat(request.getUid()).isEqualTo(uuid),
                () -> assertThat(request.getMode()).isEqualTo(SignalMode.MAIN),
                () -> assertThat(request.getAuthor()).isEqualTo("author"),
                () -> assertThat(request.getDescription()).isEqualTo("description"),
                () -> assertThat(request.getSwitchType()).isEqualTo(SwitchType.FORCE),
                () -> assertThat(request.getStatus()).isEqualTo(READY_TO_SWITCH),
                () -> assertThat(record.key()).isEqualTo(APP_SIGNAL_RECORD_KEY),
                () -> assertThat(record.topic()).isEqualTo(SIGNAL_TOPIC_NAME_DEFAULT)
        );
    }

    @Test
    public void builder_build_with_with_override_topic() {
        UUID uuid = UUID.randomUUID();
        ProducerRecord<String, SignalRequest> record = KafkaSignalBuilder.builder()
                .withTopic("new_topic")
                .withUid(uuid)
                .withMode(SignalMode.MAIN)
                .withAuthor("author")
                .withDescription("description")
                .withSwitchType(SwitchType.CONSISTENT)
                .build();
        assertThat(record.topic()).isEqualTo("new_topic");
    }
}