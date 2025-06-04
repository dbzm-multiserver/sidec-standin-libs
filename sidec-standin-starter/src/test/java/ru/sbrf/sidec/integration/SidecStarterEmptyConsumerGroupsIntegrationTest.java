package ru.sbrf.sidec.integration;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.ConsumerGroupListing;
import org.apache.kafka.common.ConsumerGroupState;
import org.assertj.core.api.HamcrestCondition;
import org.hamcrest.core.StringStartsWith;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.data.jdbc.DataJdbcTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureJdbc;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ru.sbrf.sidec.autoconfigure.SwitchoverAutoConfiguration;
import ru.sbrf.sidec.config.*;
import ru.sbrf.sidec.helper.SignalBarrierService;
import ru.sbrf.sidec.extension.KafkaExtension;
import ru.sbrf.sidec.extension.PostgresExtension;
import ru.sbrf.sidec.processor.SwitchoverDelegatorConfigurationBeanPostProcessor;
import ru.sbrf.sidec.retry.RetryProperties;
import ru.sbrf.sidec.retry.RetryService;
import ru.sbrf.sidec.utils.KafkaUtil;
import ru.sbrf.sidec.utils.LoggingAppenderService;

import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;


import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.not;
import static ru.sbrf.sidec.config.SidecConfig.*;
import static ru.sbrf.sidec.utils.AwaitilityUtil.defaultAwait;
import static ru.sbrf.sidec.utils.KafkaUtil.clearConsumerGroups;

@DataJdbcTest
@AutoConfigureJdbc
@ContextConfiguration(classes = {
        EmptyConsumerGroupsConfiguration.class,
        SwitchoverConfig.class,
        SwitchoverKafkaConfig.class,
        SwitchoverDataSourceConfiguration.class,
        SwitchoverAutoConfiguration.class,
        MultipleDataSourceConfiguration.class,
        SwitchoverDelegatorConfigurationBeanPostProcessor.class,
        SignalBarrierService.class,
        RetryProperties.class,
        RetryService.class,
        LoggingAppenderService.class
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ExtendWith({PostgresExtension.class, KafkaExtension.class, SpringExtension.class})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@TestPropertySource(properties = {"spring.test.context.cache.maxSize=0"})
public class SidecStarterEmptyConsumerGroupsIntegrationTest {
    private final SwitchoverConfig config;

    public SidecStarterEmptyConsumerGroupsIntegrationTest(@Qualifier("switchoverConfig") SwitchoverConfig config) {
        this.config = config;
    }

    @AfterEach
    public void clean_up() {
        KafkaUtil.deleteKafkaTopics();
    }

    @AfterAll
    public static void afterAll() {
        clearConsumerGroups();
        KafkaUtil.deleteKafkaTopics();
    }

    @Test
    @DisplayName("Нестабильный, тк бывают кейсы когда консьюмер в контексте не успевает отключиться от кафки")
    public void consumer_groups_successfully_cleared_before_start() throws ExecutionException, InterruptedException {
        AdminClient adminClient = config.getKafkaConfig().getAdminClient();
        Collection<ConsumerGroupListing> consumerGroupListings = adminClient.listConsumerGroups().all().get()
                .stream()
                .sorted(Comparator.comparing(x -> x.state().get()))
                .collect(Collectors.toList());
        assertThat(consumerGroupListings).hasSize(2);

        //Проверка, что запушенный консьюмер стартером находится в STABLE состоянии
        assertThat(consumerGroupListings).first()
                .isNotNull()
                .extracting(ConsumerGroupListing::groupId).has(new HamcrestCondition<>(StringStartsWith.startsWith(CONSUMER_GROUP_ID_PREFIX)));
        assertThat(consumerGroupListings).first()
                .extracting(ConsumerGroupListing::state).isEqualTo(Optional.of(ConsumerGroupState.STABLE));

        //Проверка, что были очищенны все консьюмер группы, кроме неподходящей для очистки
        assertThat(consumerGroupListings).last()
                .isNotNull()
                .extracting(ConsumerGroupListing::groupId).has(not(new HamcrestCondition<>(StringStartsWith.startsWith(CONSUMER_GROUP_ID_PREFIX))));
        assertThat(consumerGroupListings).last()
                .extracting(ConsumerGroupListing::state).isEqualTo(Optional.of(ConsumerGroupState.EMPTY));
    }
}