package ru.sbrf.sidec.integration;

import org.apache.kafka.clients.producer.Producer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.data.jdbc.DataJdbcTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureJdbc;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ru.sbrf.sidec.autoconfigure.SwitchoverAutoConfiguration;
import ru.sbrf.sidec.config.MultipleDataSourceConfiguration;
import ru.sbrf.sidec.config.SwitchoverConfig;
import ru.sbrf.sidec.config.SwitchoverDataSourceConfiguration;
import ru.sbrf.sidec.config.SwitchoverKafkaConfig;
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
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static ru.sbrf.sidec.db.ConnectionMode.SWITCH_TO_STANDIN;
import static ru.sbrf.sidec.utils.AwaitilityUtil.defaultAwait;
import static ru.sbrf.sidec.utils.KafkaUtil.clearConsumerGroups;
import static ru.sbrf.sidec.utils.ProducerUtil.createAppSignalProducerRecord;

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
@Order(40)
public class SidecStarterDatabaseRetriesIntegrationTest {
    private final JdbcTemplate jdbcTemplateMain;
    private final SwitchoverDataSourceDelegator switchoverDataSourceDelegator;
    private SidecProducerFactory<String, SignalRequest> producerFactory;

    private TableQueryExecutor execMain;
    private final LoggingAppenderService appenderService;

    public SidecStarterDatabaseRetriesIntegrationTest(@Qualifier("jdbcTemplateMain") JdbcTemplate jdbcTemplateMain,
                                                      @Qualifier("dataSource") DataSource dataSource,
                                                      @Qualifier("switchoverConfig") SwitchoverConfig config,
                                                      @Qualifier("appenderService") LoggingAppenderService appenderService
    ) {
        this.jdbcTemplateMain = jdbcTemplateMain;
        this.switchoverDataSourceDelegator = (SwitchoverDataSourceDelegator) dataSource;
        this.producerFactory = config.getKafkaConfig().getProducerFactory();
        this.appenderService = appenderService;
    }

    @BeforeEach
    public void init() {
        execMain = new TableQueryExecutor(jdbcTemplateMain);
        execMain.create_sidec_schema();
        execMain.create_app_connection_table();
        execMain.create_meme_table();
    }

    @AfterEach
    public void clean_up() {
        execMain.drop_sidec_schema();
        KafkaUtil.deleteKafkaTopics();
        clearConsumerGroups();
    }

    @Test
    public void starter_successfully_process_signal_during_database_issues() throws InterruptedException {
        Producer<String, SignalRequest> producer = producerFactory.createProducer();
        //Выполняем начало перехода на стендИн
        UUID uuid1 = UUID.randomUUID();
        //Удаляем таблицу с коннектами, чтобы сэмулировать недоступность
        execMain.drop_app_connection_table();
        var standInSwitch = createAppSignalProducerRecord(uuid1, SignalMode.STANDIN, SignalStatus.STARTED);
        producer.send(standInSwitch);
        TimeUnit.SECONDS.sleep(5);
        //Создаем таблицу с коннектами заново
        execMain.create_app_connection_table();
        //Ожидаем смены состояния делегатора на промежуточное
        defaultAwait().until(() -> switchoverDataSourceDelegator.getConnectionMode() == SWITCH_TO_STANDIN);
        //Проверка того, что в базе находится валидный сигнал
        defaultAwait().until(() -> execMain.select_from_app_connection_table().size() == 1);
        //Проверка того, что ретраи были
        appenderService.isMessageExist("Try to save the switching signal to the database, retries");
    }
}