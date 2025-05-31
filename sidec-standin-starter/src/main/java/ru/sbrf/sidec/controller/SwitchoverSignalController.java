package ru.sbrf.sidec.controller;

import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import ru.sbrf.sidec.config.SwitchoverConfig;
import ru.sbrf.sidec.kafka.domain.SwitchoverRequest;
import ru.sbrf.sidec.exception.SwitchoverException;
import ru.sbrf.sidec.kafka.domain.SignalRequest;
import ru.sbrf.sidec.kafka.domain.SignalStatus;

import java.time.OffsetDateTime;
import java.util.concurrent.TimeUnit;

import static ru.sbrf.sidec.autoconfigure.SwitchoverAutoConfiguration.SWITCHOVER_ENABLED_CONFIG_PROPERTY;
import static ru.sbrf.sidec.kafka.domain.SwitchType.FORCE;
import static ru.sbrf.sidec.util.ObjectMapperUtil.readValue;

@Controller
@ConditionalOnProperty(name = SWITCHOVER_ENABLED_CONFIG_PROPERTY, havingValue = "true")
public class SwitchoverSignalController {
    public static final String APP_SIGNAL_ENDPOINT = "/appsignal";
    public static final Logger LOGGER = LoggerFactory.getLogger(SwitchoverSignalController.class);
    private final Producer<String, SignalRequest> producer;
    private final SwitchoverConfig config;

    public SwitchoverSignalController(SwitchoverConfig config) {
        this.producer = config.getKafkaConfig().getProducerFactory().createProducer();
        this.config = config;
    }

    @PostMapping(
            value = APP_SIGNAL_ENDPOINT,
            produces = MediaType.APPLICATION_JSON_VALUE,
            consumes = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<String> produceAppSignal(@RequestBody byte[] rawRequest) {
        SwitchoverRequest request = readValue(rawRequest, SwitchoverRequest.class);
        LOGGER.info("A request was received to switch the mode via an HTTP request. Request Body: {}", request);
        try {
            var partitionInfos = producer.partitionsFor(config.getSignalKafkaTopic());
            if (partitionInfos.size() != 1) {
                throw new SwitchoverException("The number of partitions in the topic " + config.getSignalKafkaTopic() + " should be equal to 1");
            }
            SignalStatus status = FORCE == request.getSwitchType() ? SignalStatus.READY_TO_SWITCH : SignalStatus.STARTED;
            SignalRequest signal = new SignalRequest(
                    request.getUid(),
                    request.getMode(),
                    request.getAuthor(),
                    request.getDescription(),
                    status,
                    OffsetDateTime.now(),
                    request.getSwitchType()
            );
            var record = new ProducerRecord<>(config.getSignalKafkaTopic(), "sidec.app_signal.status", signal);
            producer.send(record).get(config.getSignalRequestTimeoutMs(), TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            LOGGER.warn("Error during send signal to kafka with body = '{}'", request, e);
            return ResponseEntity.internalServerError()
                    .body("Error during send signal");
        }
        return ResponseEntity.ok("Signal was sent successfully");
    }
}