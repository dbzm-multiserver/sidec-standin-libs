package ru.sbrf.sidec.autoconfigure;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.autoconfigure.jdbc.JdbcConnectionDetails;
import org.springframework.boot.test.autoconfigure.data.jdbc.DataJdbcTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureJdbc;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ru.sbrf.sidec.config.SwitchoverConfig;
import ru.sbrf.sidec.config.SwitchoverKafkaConfig;
import ru.sbrf.sidec.config.MultipleDataSourceConfiguration;
import ru.sbrf.sidec.config.SwitchoverDataSourceConfiguration;
import ru.sbrf.sidec.db.ConnectionMode;
import ru.sbrf.sidec.extension.KafkaExtension;
import ru.sbrf.sidec.extension.PostgresExtension;
import ru.sbrf.sidec.helper.TableQueryExecutor;
import ru.sbrf.sidec.kafka.domain.SignalMode;
import ru.sbrf.sidec.kafka.domain.SignalStatus;
import ru.sbrf.sidec.kafka.factory.SidecConsumerFactory;
import ru.sbrf.sidec.kafka.factory.SidecProducerFactory;
import ru.sbrf.sidec.processor.SwitchoverDelegatorConfigurationBeanPostProcessor;
import ru.sbrf.sidec.proxy.SwitchoverDataSourceDelegator;

import javax.sql.DataSource;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static ru.sbrf.sidec.extension.KafkaExtension.producer;
import static ru.sbrf.sidec.utils.AwaitilityUtil.defaultAwait;
import static ru.sbrf.sidec.utils.ProducerUtil.createConsistentSignal;


@Disabled("Запуск нескольких тестов падает при gradle build")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DataJdbcTest
@ExtendWith({PostgresExtension.class, KafkaExtension.class, SpringExtension.class})
@AutoConfigureJdbc
@ContextConfiguration(classes = {
        MultipleDataSourceConfiguration.class,
        SwitchoverDelegatorConfigurationBeanPostProcessor.class,
        SidecConsumerFactory.class,
        SwitchoverKafkaConfig.class,
        SwitchoverDataSourceConfiguration.class,
        DataSourceProperties.class,
        JdbcConnectionDetails.class,
        SwitchoverConfig.class,
        SidecProducerFactory.class
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public class SidecAutoConfigurationWithKafkaDefaultsTest {

    private final JdbcTemplate jdbcTemplateApp;
    private final JdbcTemplate jdbcTemplateMain;
    private final JdbcTemplate jdbcTemplateStandIn;
    private final SwitchoverDataSourceDelegator delegator;

    public SidecAutoConfigurationWithKafkaDefaultsTest(@Qualifier("jdbcTemplateMain") JdbcTemplate jdbcTemplateMain,
                                                       @Qualifier("jdbcTemplateStandIn") JdbcTemplate jdbcTemplateStandIn,
                                                       @Qualifier("dataSourcePostgres") DataSource dataSource) {

        this.jdbcTemplateMain = jdbcTemplateMain;
        this.jdbcTemplateStandIn = jdbcTemplateStandIn;
        this.delegator = (SwitchoverDataSourceDelegator) dataSource;
        this.jdbcTemplateApp = new JdbcTemplate(dataSource);
    }

    TableQueryExecutor appExecutor;
    TableQueryExecutor mainExecutor;
    TableQueryExecutor standInExecutor;


    @BeforeAll
    public static void initApp() throws ExecutionException, InterruptedException {
        var producer = producer();
        var standInSwitch = createConsistentSignal(UUID.randomUUID(), SignalMode.STANDIN, SignalStatus.STARTED);
        producer.send(standInSwitch).get();
        var standInReady = createConsistentSignal(UUID.randomUUID(), SignalMode.STANDIN, SignalStatus.READY_TO_SWITCH);
        producer.send(standInReady).get();
    }
    @BeforeEach
    public void init() {
        appExecutor = new TableQueryExecutor(jdbcTemplateApp);
        mainExecutor = new TableQueryExecutor(jdbcTemplateMain);
        standInExecutor = new TableQueryExecutor(jdbcTemplateStandIn);
        List.of(mainExecutor, standInExecutor).forEach(
                e -> {
                    e.create_sidec_schema();
                    e.create_app_connection_table();
                    e.create_meme_table();
                }
        );
    }

    @AfterEach
    public void cleanup() {
        mainExecutor.drop_sidec_schema();
        standInExecutor.drop_sidec_schema();
    }

    @Test
    public void delegate_use_main_connection_with_empty_kafka() {
        defaultAwait().until(() -> delegator.getConnectionMode() == ConnectionMode.STANDIN);
    }
}