package ru.sbrf.sidec.kafka.serde;

import org.apache.kafka.common.serialization.Deserializer;
import ru.sbrf.sidec.kafka.domain.SignalResponse;
import ru.sbrf.sidec.util.ObjectMapperUtil;

public class AppSignalDeserializer implements Deserializer<SignalResponse> {

    @Override
    public SignalResponse deserialize(String topic, byte[] data) {
        return ObjectMapperUtil.readValue(data, SignalResponse.class);
    }
}