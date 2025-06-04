package ru.sbrf.sidec.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import ru.sbrf.sidec.autoconfigure.SwitchoverAutoConfiguration;
import ru.sbrf.sidec.config.SwitchoverConfig;
import ru.sbrf.sidec.config.SwitchoverDataSourceConfiguration;
import ru.sbrf.sidec.config.SwitchoverKafkaConfig;
import ru.sbrf.sidec.controller.SwitchoverSignalController;
import ru.sbrf.sidec.kafka.domain.SwitchoverRequest;
import ru.sbrf.sidec.extension.KafkaExtension;
import ru.sbrf.sidec.kafka.domain.SignalMode;
import ru.sbrf.sidec.kafka.domain.SignalResponse;
import ru.sbrf.sidec.kafka.domain.SignalStatus;
import ru.sbrf.sidec.kafka.domain.SwitchType;
import ru.sbrf.sidec.retry.RetryProperties;
import ru.sbrf.sidec.retry.RetryService;
import ru.sbrf.sidec.utils.KafkaUtil;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static ru.sbrf.sidec.controller.SwitchoverSignalController.APP_SIGNAL_ENDPOINT;
import static ru.sbrf.sidec.util.ObjectMapperUtil.OBJECT_MAPPER;
import static ru.sbrf.sidec.utils.AwaitilityUtil.defaultAwait;
import static ru.sbrf.sidec.utils.KafkaUtil.clearConsumerGroups;
import static ru.sbrf.sidec.utils.TestKafkaConsumer.getKafkaData;
import static ru.sbrf.sidec.utils.TestKafkaConsumer.initKafkaConsumer;
import static ru.sbrf.sidec.utils.TestKafkaConsumer.stopConsumers;

@ContextConfiguration(classes = {
        SwitchoverAutoConfiguration.class,
        SwitchoverConfig.class,
        SwitchoverDataSourceConfiguration.class,
        SwitchoverKafkaConfig.class,
        SwitchoverSignalController.class,
        RetryProperties.class,
        RetryService.class
})
@ExtendWith({KafkaExtension.class, SpringExtension.class})
@WebMvcTest(value = SwitchoverSignalController.class, excludeAutoConfiguration = SecurityAutoConfiguration.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@TestPropertySource(properties = {"spring.test.context.cache.maxSize=0"})
public class SidecStarterControllerIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @AfterEach
    public void clean_up() {
        KafkaUtil.deleteKafkaTopics();
        clearConsumerGroups();
        stopConsumers();
    }

    @Autowired
    private SwitchoverConfig config;

    @Test
    public void controller_successfully_process_signal() throws Exception {
        initKafkaConsumer(List.of(config.getSignalTopic()));
        var request = new SwitchoverRequest(
                UUID.randomUUID(),
                SignalMode.STANDIN,
                "grebenyuk sergey",
                "работы на сервере по миграции на новую версию pangolin",
                SwitchType.CONSISTENT
        );
        String jsonRequest = OBJECT_MAPPER.writeValueAsString(request);
        mockMvc.perform(
                        post(APP_SIGNAL_ENDPOINT)
                                .contentType(MediaType.APPLICATION_JSON_VALUE)
                                .content(jsonRequest)
                                .characterEncoding("UTF-8")
                )
                .andExpect(status().isOk());
        defaultAwait().until(() -> !getKafkaData(config.getSignalTopic()).isEmpty());
        SignalResponse signal = OBJECT_MAPPER.readValue(getKafkaData(config.getSignalTopic()).getFirst(), SignalResponse.class);
        assertAll(() -> {
            assertEquals(request.getUid(), signal.getUid());
            assertEquals(request.getAuthor(), signal.getAuthor());
            assertEquals(request.getDescription(), signal.getDescription());
            assertEquals(request.getMode(), signal.getMode());
            assertEquals(request.getSwitchType(), signal.getSwitchType());
            assertEquals(SignalStatus.STARTED, signal.getStatus());
        });
    }
}