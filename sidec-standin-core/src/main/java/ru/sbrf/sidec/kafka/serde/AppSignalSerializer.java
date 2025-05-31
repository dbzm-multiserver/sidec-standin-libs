package ru.sbrf.sidec.kafka.serde;

import org.apache.kafka.common.serialization.Serializer;
import ru.sbrf.sidec.kafka.domain.SignalRequest;
import ru.sbrf.sidec.util.ObjectMapperUtil;

public class AppSignalSerializer implements Serializer<SignalRequest> {

    @Override
    public byte[] serialize(String topic, SignalRequest data) {
        if (data == null) {
            return null;
        }
        return ObjectMapperUtil.writeValueAsBytes(data);
    }
}