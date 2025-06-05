package ru.sbrf.sidec.utils;

import org.apache.kafka.clients.producer.ProducerRecord;
import ru.sbrf.sidec.kafka.domain.SignalMode;
import ru.sbrf.sidec.kafka.domain.SignalRequest;
import ru.sbrf.sidec.kafka.domain.SignalStatus;
import ru.sbrf.sidec.kafka.domain.SwitchType;

import java.time.OffsetDateTime;
import java.util.UUID;

public class ProducerUtil {
    public static ProducerRecord<String, SignalRequest> createConsistentSignal(UUID signalUid, SignalMode signalMode, SignalStatus signalStatus) {
        return new ProducerRecord<>("sidec.app_signal", "sidec.app_signal.status",
                new SignalRequest(signalUid, signalMode, "sidec-user", "switch mode", signalStatus, OffsetDateTime.now(), SwitchType.CONSISTENT)
        );
    }
}