package ru.sbrf.sidec.api.kafka.producer;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.sbrf.sidec.kafka.domain.SignalMode;
import ru.sbrf.sidec.kafka.domain.SignalRequest;
import ru.sbrf.sidec.kafka.domain.SignalStatus;
import ru.sbrf.sidec.kafka.domain.SwitchType;

import java.time.OffsetDateTime;
import java.util.UUID;

import static ru.sbrf.sidec.config.SidecConfig.APP_SIGNAL_RECORD_KEY;
import static ru.sbrf.sidec.config.SidecConfig.SIGNAL_TOPIC_NAME_DEFAULT;
import static ru.sbrf.sidec.kafka.domain.SwitchType.FORCE;

public class KafkaSignalBuilder {
    public static final Logger LOGGER = LoggerFactory.getLogger(KafkaSignalBuilder.class);

    private String topic = SIGNAL_TOPIC_NAME_DEFAULT;

    private UUID uid;
    private SignalMode mode;
    private String author;
    private String description;
    private SwitchType switchType;

    public static KafkaSignalBuilder builder() {
        return new KafkaSignalBuilder();
    }

    public KafkaSignalBuilder withTopic(String topic) {
        this.topic = topic;
        return this;
    }

    public KafkaSignalBuilder withUid(UUID uid) {
        this.uid = uid;
        return this;
    }

    public KafkaSignalBuilder withMode(SignalMode mode) {
        this.mode = mode;
        return this;
    }

    public KafkaSignalBuilder withAuthor(String author) {
        this.author = author;
        return this;
    }

    public KafkaSignalBuilder withDescription(String description) {
        this.description = description;
        return this;
    }

    public KafkaSignalBuilder withSwitchType(SwitchType switchType) {
        this.switchType = switchType;
        return this;
    }

    public ProducerRecord<String, SignalRequest> build() {
        validate();
        SignalStatus status = FORCE == this.switchType ? SignalStatus.READY_TO_SWITCH : SignalStatus.STARTED;
        SignalRequest signal = new SignalRequest(
                this.uid,
                this.mode,
                this.author,
                this.description,
                status,
                OffsetDateTime.now(),
                this.switchType
        );
        return new ProducerRecord<>(this.topic, APP_SIGNAL_RECORD_KEY, signal);
    }

    private void validate() {
        int errorsCount = 0;
        String[] errors = new String[3];
        if (this.uid == null) errors[errorsCount++] = "Uid";
        if (this.mode == null) errors[errorsCount++] = "Mode";
        if (this.switchType == null) errors[errorsCount++] = "SwitchType";
        if (errorsCount > 0)
            throw new IllegalArgumentException("Error during build producer request. The following fields should not be null: " + String.join(", ", errors));
    }
}