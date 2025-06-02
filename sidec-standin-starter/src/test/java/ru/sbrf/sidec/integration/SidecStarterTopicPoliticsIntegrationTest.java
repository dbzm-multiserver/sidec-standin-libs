package ru.sbrf.sidec.integration;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.ConfigEntry;
import org.apache.kafka.common.config.ConfigResource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.data.jdbc.DataJdbcTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureJdbc;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ru.sbrf.sidec.autoconfigure.SwitchoverAutoConfiguration;
import ru.sbrf.sidec.config.*;
import ru.sbrf.sidec.extension.KafkaExtension;
import ru.sbrf.sidec.extension.PostgresExtension;
import ru.sbrf.sidec.processor.SwitchoverDelegatorConfigurationBeanPostProcessor;
import ru.sbrf.sidec.retry.RetryProperties;
import ru.sbrf.sidec.retry.RetryService;
import ru.sbrf.sidec.utils.KafkaUtil;
import ru.sbrf.sidec.utils.LoggingAppenderService;

import java.util.List;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static ru.sbrf.sidec.config.SidecConfig.*;
import static ru.sbrf.sidec.utils.AwaitilityUtil.defaultAwait;
import static ru.sbrf.sidec.utils.KafkaUtil.clearConsumerGroups;

@DataJdbcTest
@AutoConfigureJdbc
@ContextConfiguration(classes = {
        LoggingAppenderService.class,
        TopicRecordsDeleteConfiguration.class,
        SwitchoverConfig.class,
        SwitchoverKafkaConfig.class,
        SwitchoverDataSourceConfiguration.class,
        SwitchoverAutoConfiguration.class,
        MultipleDataSourceConfiguration.class,
        SwitchoverDelegatorConfigurationBeanPostProcessor.class,
        RetryProperties.class,
        RetryService.class
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ExtendWith({PostgresExtension.class, KafkaExtension.class, SpringExtension.class})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@Order(5)
public class SidecStarterTopicPoliticsIntegrationTest {
    private final SwitchoverConfig config;
    private final LoggingAppenderService appenderService;

    public SidecStarterTopicPoliticsIntegrationTest(@Qualifier("switchoverConfig") SwitchoverConfig config,
                                                    @Qualifier("appenderService") LoggingAppenderService appenderService) {
        this.config = config;
        this.appenderService = appenderService;
    }

    @AfterEach
    public void clean_up() {
        KafkaUtil.deleteKafkaTopics();
    }

    @AfterAll
    public static void afterAll() {
        clearConsumerGroups();
    }

    @Test
    public void starter_change_topic_cleanup_policy_and_start_with_exception_when_signal_topic_is_empty() throws ExecutionException, InterruptedException {
        AdminClient adminClient = config.getKafkaConfig().getAdminClient();
        ConfigEntry configEntry = adminClient.describeConfigs(
                        List.of(new ConfigResource(ConfigResource.Type.TOPIC, SIGNAL_TOPIC_NAME_DEFAULT))
                )
                .all().get()
                .values().stream().findFirst().get()
                .get(CLEAN_UP_POLICY_CONFIG_NAME);
        assertThat(configEntry.value()).isEqualTo(CLEAN_UP_POLICY_COMPACT);
        defaultAwait("Wait until delegator failed to start").untilAsserted(
                () -> Assertions.assertTrue(appenderService.isMessageExist("There is no messages in topic. Starter couldn't start in correct position."))
        );
    }
}