package ru.sbrf.sidec.helper;

import org.apache.kafka.clients.producer.ProducerRecord;
import ru.sbrf.sidec.kafka.domain.SignalMode;
import ru.sbrf.sidec.kafka.domain.SignalRequest;
import ru.sbrf.sidec.kafka.domain.SignalStatus;
import ru.sbrf.sidec.kafka.domain.SwitchType;

import java.time.OffsetDateTime;
import java.util.UUID;

public class Signals {
    public static String SIDEC_APP_SIGNAL_TOPIC = "sidec.app_signal";

    public static ProducerRecord<String, SignalRequest> consistentPrepareSwitch(UUID signalUid, SignalMode signalMode) {
        return kafkaSignalRecord(signalUid, signalMode, SignalStatus.STARTED, SwitchType.CONSISTENT);
    }

    public static ProducerRecord<String, SignalRequest> consistentDoSwitch(UUID signalUid, SignalMode signalMode) {
        return kafkaSignalRecord(signalUid, signalMode, SignalStatus.READY_TO_SWITCH, SwitchType.CONSISTENT);
    }

    public static ProducerRecord<String, SignalRequest> doSwitch(UUID signalUid, SignalMode signalMode) {
        return kafkaSignalRecord(signalUid, signalMode, SignalStatus.READY_TO_SWITCH, SwitchType.FORCE);
    }

    private static ProducerRecord<String, SignalRequest> kafkaSignalRecord(UUID signalUid, SignalMode signalMode, SignalStatus signalStatus, SwitchType switchType) {
        return new ProducerRecord<>("sidec.app_signal", "sidec.app_signal.status",
                new SignalRequest(signalUid, signalMode, "sidec-user", "switch mode", signalStatus, OffsetDateTime.now(), switchType)
        );
    }
}