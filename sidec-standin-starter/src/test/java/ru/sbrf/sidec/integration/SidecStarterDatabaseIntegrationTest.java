package ru.sbrf.sidec.integration;

import org.apache.kafka.clients.producer.Producer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.data.jdbc.DataJdbcTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureJdbc;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ru.sbrf.sidec.autoconfigure.SwitchoverAutoConfiguration;
import ru.sbrf.sidec.config.MultipleDataSourceConfiguration;
import ru.sbrf.sidec.config.SwitchoverConfig;
import ru.sbrf.sidec.config.SwitchoverDataSourceConfiguration;
import ru.sbrf.sidec.config.SwitchoverKafkaConfig;
import ru.sbrf.sidec.db.AppConnection;
import ru.sbrf.sidec.extension.KafkaExtension;
import ru.sbrf.sidec.extension.PostgresExtension;
import ru.sbrf.sidec.helper.TableQueryExecutor;
import ru.sbrf.sidec.kafka.domain.SignalMode;
import ru.sbrf.sidec.kafka.domain.SignalRequest;
import ru.sbrf.sidec.kafka.domain.SignalStatus;
import ru.sbrf.sidec.kafka.factory.SidecProducerFactory;
import ru.sbrf.sidec.processor.SwitchoverDelegatorConfigurationBeanPostProcessor;
import ru.sbrf.sidec.proxy.SwitchoverDataSourceDelegator;
import ru.sbrf.sidec.retry.RetryProperties;
import ru.sbrf.sidec.retry.RetryService;
import ru.sbrf.sidec.utils.KafkaUtil;
import ru.sbrf.sidec.utils.LoggingAppenderService;

import javax.sql.DataSource;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static ru.sbrf.sidec.db.ConnectionMode.STANDIN;
import static ru.sbrf.sidec.db.ConnectionMode.SWITCH_TO_STANDIN;
import static ru.sbrf.sidec.utils.AwaitilityUtil.defaultAwait;
import static ru.sbrf.sidec.utils.KafkaUtil.clearConsumerGroups;
import static ru.sbrf.sidec.utils.ProducerUtil.createConsistentSignal;

@DataJdbcTest
@AutoConfigureJdbc
@ContextConfiguration(classes = {
        SwitchoverConfig.class,
        SwitchoverKafkaConfig.class,
        SwitchoverDataSourceConfiguration.class,
        SwitchoverAutoConfiguration.class,
        MultipleDataSourceConfiguration.class,
        SwitchoverDelegatorConfigurationBeanPostProcessor.class,
        RetryProperties.class,
        RetryService.class,
        LoggingAppenderService.class
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ExtendWith({PostgresExtension.class, KafkaExtension.class, SpringExtension.class})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@TestPropertySource(properties = {"spring.test.context.cache.maxSize=0"})
public class SidecStarterDatabaseIntegrationTest {
    private final JdbcTemplate jdbcTemplateApp;
    private final JdbcTemplate jdbcTemplateMain;
    private final JdbcTemplate jdbcTemplateStandIn;
    private final SwitchoverDataSourceDelegator switchoverDataSourceDelegator;
    private SidecProducerFactory<String, SignalRequest> producerFactory;

    private TableQueryExecutor.ExecutorsTestPool executorsPool;
    private final LoggingAppenderService appenderService;

    public SidecStarterDatabaseIntegrationTest(@Qualifier("jdbcTemplateMain") JdbcTemplate jdbcTemplateMain,
                                               @Qualifier("jdbcTemplateStandIn") JdbcTemplate jdbcTemplateStandIn,
                                               @Qualifier("dataSource") DataSource dataSource,
                                               @Qualifier("switchoverConfig") SwitchoverConfig config,
                                               @Qualifier("appenderService") LoggingAppenderService appenderService
    ) {
        this.jdbcTemplateMain = jdbcTemplateMain;
        this.jdbcTemplateStandIn = jdbcTemplateStandIn;
        this.switchoverDataSourceDelegator = (SwitchoverDataSourceDelegator) dataSource;
        this.jdbcTemplateApp = new JdbcTemplate(dataSource);
        this.producerFactory = config.getKafkaConfig().getProducerFactory();
        this.appenderService = appenderService;
    }

    @BeforeEach
    public void init() {
        executorsPool = new TableQueryExecutor.ExecutorsTestPool(
                new TableQueryExecutor(jdbcTemplateApp),
                new TableQueryExecutor(jdbcTemplateMain),
                new TableQueryExecutor(jdbcTemplateStandIn)
        );
        List.of(executorsPool.main(), executorsPool.standin()).forEach(
                e -> {
                    e.create_sidec_schema();
                    e.create_app_connection_table();
                    e.create_meme_table();
                }
        );
    }

    @AfterEach
    public void clean_up() {
        executorsPool.main().drop_sidec_schema();
        executorsPool.standin().drop_sidec_schema();
        KafkaUtil.deleteKafkaTopics();
        clearConsumerGroups();
    }

    @Test
    public void starter_successfully_process_signal_during_database_issues() throws InterruptedException {
        try (Producer<String, SignalRequest> producer = producerFactory.createProducer()) {
            //Удаляем таблицу с коннектами, чтобы сэмулировать недоступность
            executorsPool.main().drop_app_connection_table();
            var standInSwitch = createConsistentSignal(UUID.randomUUID(), SignalMode.STANDIN, SignalStatus.STARTED);
            producer.send(standInSwitch);
        }
        TimeUnit.SECONDS.sleep(5);
        //Создаем таблицу с коннектами заново
        executorsPool.main().create_app_connection_table();
        //Ожидаем смены состояния делегатора на промежуточное
        defaultAwait().until(() -> {
                return switchoverDataSourceDelegator.getConnectionMode() == SWITCH_TO_STANDIN;
        });
        //Проверка того, что в базе находится валидный сигнал
        defaultAwait().until(() -> executorsPool.main().select_from_app_connection_table().size() == 1);
        //Проверка того, что ретраи были
        appenderService.isMessageExist("Try to save the switching signal to the database, retries");
    }

    //@Test
    public void starter_correctly_process_signal() {
        //MAIN -> SWITCH_TO_STANDIN
        UUID uuid = UUID.randomUUID();
        try (Producer<String, SignalRequest> producer = producerFactory.createProducer()) {
            var standInSwitch = createConsistentSignal(uuid, SignalMode.STANDIN, SignalStatus.STARTED);
            producer.send(standInSwitch);
        }
        defaultAwait().until(() ->  switchoverDataSourceDelegator.getConnectionMode() == SWITCH_TO_STANDIN);
        defaultAwait().until(() -> executorsPool.main().select_from_app_connection_table(uuid).size() == 1);
        AppConnection con1 = executorsPool.main().select_from_app_connection_table(uuid).getFirst();
        assertThat(con1.getMode()).isEqualTo(SWITCH_TO_STANDIN);

        //SWITCH_TO_STANDIN -> STANDIN
        UUID uuid2 = UUID.randomUUID();
        try (Producer<String, SignalRequest> producer = producerFactory.createProducer()) {
            var standInSwitch = createConsistentSignal(uuid2, SignalMode.STANDIN, SignalStatus.READY_TO_SWITCH);
            producer.send(standInSwitch);
        }
        defaultAwait().until(() ->  switchoverDataSourceDelegator.getConnectionMode() == STANDIN);
        defaultAwait().until(() -> executorsPool.standin().select_from_app_connection_table(uuid2).size() == 1);
        AppConnection con2 = executorsPool.standin().select_from_app_connection_table(uuid2).getFirst();
        assertThat(con2.getMode()).isEqualTo(STANDIN);

        //STANDIN -> SWITCH_TO_STANDIN
        UUID uuid3 = UUID.randomUUID();
        try (Producer<String, SignalRequest> producer = producerFactory.createProducer()) {
            var standInSwitch = createConsistentSignal(uuid3, SignalMode.STANDIN, SignalStatus.READY_TO_SWITCH);
            producer.send(standInSwitch);
        }
        defaultAwait().until(() ->  switchoverDataSourceDelegator.getConnectionMode() == SWITCH_TO_STANDIN);
        defaultAwait().until(() -> executorsPool.standin().select_from_app_connection_table(uuid3).size() == 1);
        AppConnection con3 = executorsPool.standin().select_from_app_connection_table(uuid3).getFirst();
        assertThat(con3.getMode()).isEqualTo(SWITCH_TO_STANDIN);

        //SWITCH_TO_STANDIN -> STANDIN
        //STANDIN -> SWITCH_TO_MAIN
        //SWITCH_TO_MAIN -> MAIN
        //MAIN -> SWITCH_TO_MAIN
    }
}