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
import ru.sbrf.sidec.helper.SignalBarrierService;
import ru.sbrf.sidec.kafka.domain.SwitchoverRequest;
import ru.sbrf.sidec.exception.SwitchoverException;
import ru.sbrf.sidec.kafka.domain.SignalRequest;
import ru.sbrf.sidec.kafka.domain.SignalStatus;

import java.time.OffsetDateTime;
import java.util.concurrent.TimeUnit;

import static ru.sbrf.sidec.autoconfigure.SwitchoverAutoConfiguration.SWITCHOVER_ENABLED_CONFIG_PROPERTY;
import static ru.sbrf.sidec.kafka.domain.SwitchType.CONSISTENT;
import static ru.sbrf.sidec.kafka.domain.SwitchType.FORCE;
import static ru.sbrf.sidec.util.ObjectMapperUtil.readValue;

@Controller
@ConditionalOnProperty(name = SWITCHOVER_ENABLED_CONFIG_PROPERTY, havingValue = "true")
public class SwitchoverSignalController {
    public static final String APP_SIGNAL_ENDPOINT = "/appsignal";
    public static final Logger LOGGER = LoggerFactory.getLogger(SwitchoverSignalController.class);
    private final Producer<String, SignalRequest> producer;
    private final SwitchoverConfig config;
    private final SignalBarrierService barrierService;

    public SwitchoverSignalController(SwitchoverConfig config, SignalBarrierService barrierService) {
        this.producer = config.getKafkaConfig().getProducerFactory().createProducer();
        this.config = config;
        this.barrierService = barrierService;
    }

    @PostMapping(
            value = APP_SIGNAL_ENDPOINT,
            produces = MediaType.APPLICATION_JSON_VALUE,
            consumes = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<String> produceAppSignal(@RequestBody byte[] rawRequest) {
        SwitchoverRequest request = readValue(rawRequest, SwitchoverRequest.class);
        LOGGER.info("A request was received to switch the mode via an HTTP request. Request Body: {}", request);
        if (request == null) {
            return ResponseEntity.internalServerError()
                    .body("Request entity is null");
        }
        SignalStatus status = FORCE == request.getSwitchType() ? SignalStatus.READY_TO_SWITCH : SignalStatus.STARTED;
        if (request.getSwitchType() == CONSISTENT && !barrierService.isSignalSentAllowed(request.getMode(), status)) {
            return ResponseEntity.badRequest()
                    .body(String.format(
                            "Cannot transition from %s to %s with status %s",
                            barrierService.getApplicationConnectionMode(),
                            request.getMode(),
                            status
                    ));
        }
        try {
            var partitionInfos = producer.partitionsFor(config.getSignalTopic());
            if (partitionInfos.size() != 1) {
                throw new SwitchoverException("The number of partitions in the topic " + config.getSignalTopic() + " should be equal to 1");
            }
            SignalRequest signal = new SignalRequest(
                    request.getUid(),
                    request.getMode(),
                    request.getAuthor(),
                    request.getDescription(),
                    status,
                    OffsetDateTime.now(),
                    request.getSwitchType()
            );
            var record = new ProducerRecord<>(config.getSignalTopic(), "sidec.app_signal.status", signal);
            producer.send(record).get(config.getSignalRequestTimeout().toMillis(), TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            LOGGER.warn("Error during send signal to kafka with body = '{}'", request, e);
            return ResponseEntity.internalServerError()
                    .body("Error during send signal");
        }
        return ResponseEntity.ok("Signal was sent successfully");
    }
}