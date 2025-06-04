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
import ru.sbrf.sidec.config.*;
import ru.sbrf.sidec.db.AppConnection;
import ru.sbrf.sidec.db.ConnectionMode;
import ru.sbrf.sidec.extension.KafkaExtension;
import ru.sbrf.sidec.extension.PostgresExtension;
import ru.sbrf.sidec.helper.TableQueryExecutor;
import ru.sbrf.sidec.kafka.domain.SignalRequest;
import ru.sbrf.sidec.kafka.domain.SignalStatus;
import ru.sbrf.sidec.kafka.factory.SidecProducerFactory;
import ru.sbrf.sidec.processor.SwitchoverDelegatorConfigurationBeanPostProcessor;
import ru.sbrf.sidec.kafka.domain.SignalMode;
import ru.sbrf.sidec.proxy.SwitchoverDataSourceDelegator;
import ru.sbrf.sidec.retry.RetryProperties;
import ru.sbrf.sidec.retry.RetryService;
import ru.sbrf.sidec.utils.KafkaUtil;

import javax.sql.DataSource;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static ru.sbrf.sidec.config.SidecConfig.CONSUMER_GROUP_ID_PREFIX;
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
        RetryService.class
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ExtendWith({PostgresExtension.class, KafkaExtension.class, SpringExtension.class})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@TestPropertySource(properties = {"spring.test.context.cache.maxSize=0"})
public class SidecStarterIntegrationTest {

    private final JdbcTemplate jdbcTemplateApp;
    private final JdbcTemplate jdbcTemplateMain;
    private final JdbcTemplate jdbcTemplateStandIn;
    private final SwitchoverDataSourceDelegator switchoverDataSourceDelegator;
    private SidecProducerFactory<String, SignalRequest> producerFactory;

    private TableQueryExecutor.ExecutorsTestPool executorsPool;

    public SidecStarterIntegrationTest(@Qualifier("jdbcTemplateMain") JdbcTemplate jdbcTemplateMain,
                                       @Qualifier("jdbcTemplateStandIn") JdbcTemplate jdbcTemplateStandIn,
                                       @Qualifier("dataSource") DataSource dataSource,
                                       @Qualifier("switchoverConfig") SwitchoverConfig config
    ) {
        this.jdbcTemplateMain = jdbcTemplateMain;
        this.jdbcTemplateStandIn = jdbcTemplateStandIn;
        this.switchoverDataSourceDelegator = (SwitchoverDataSourceDelegator) dataSource;
        this.jdbcTemplateApp = new JdbcTemplate(dataSource);
        this.producerFactory = config.getKafkaConfig().getProducerFactory();
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
    public void context_with_delegate_bean_started() {
        assertTrue(true);
    }

    @Test
    public void starter_successfully_process_direct_scenario() {
        Producer<String, SignalRequest> producer = producerFactory.createProducer();

        //После старта приложение переходит в статус main тк в kafka нет записей
        var appExec = new TableQueryExecutor(jdbcTemplateApp);
        //При этом в таблицу сигналов мы ничего не публикуем
        List<AppConnection> appConnections = executorsPool.main().select_from_app_connection_table();
        assertTrue(appConnections.isEmpty());
        //Осуществляем вставку в пользовательскую таблицу
        UUID memeUid_1 = UUID.randomUUID();
        appExec.insert_into_meme_table(memeUid_1, "Status 200 - Fail", "No");
        //Проверяем, то запись успешно добавлена в main таблицу
        assertEquals(1, executorsPool.main().select_from_meme_table(memeUid_1).size());

        //Выполняем начало перехода на стендИн
        UUID uuid1 = UUID.randomUUID();
        var standInSwitch = createConsistentSignal(uuid1, SignalMode.STANDIN, SignalStatus.STARTED);
        producer.send(standInSwitch);
        //Ожидаем смены состояния делегатора на промежуточное
        defaultAwait().until(() -> switchoverDataSourceDelegator.getConnectionMode() == SWITCH_TO_STANDIN);
        //Проверка того, что в базе находится валидный сигнал
        //TODO Больше проверок по полям
        defaultAwait().until(() -> executorsPool.main().select_from_app_connection_table().size() == 1);
        appConnections = executorsPool.main().select_from_app_connection_table();
        assertCorrectSwitchToStandInSignalToConnectionConversion(standInSwitch.value(), appConnections.get(0));
        assertEquals(1, appConnections.size());
        AppConnection appConnection = appConnections.get(0);
        assertEquals(SWITCH_TO_STANDIN, appConnection.getMode());
        //Попытка вставить что-нибудь в базу во время переключения вызовет ошибку Failed to obtain JDBC Connection
        assertThrows(Exception.class, () -> {
            appExec.insert_into_meme_table(UUID.randomUUID(), "Kakoi taakoi sidec?", "No");
        });
        //Проверяем, что состояние таблиц осталось прежним
        assertEquals(1, executorsPool.main().select_from_meme_table().size());
        assertEquals(0, executorsPool.standin().select_from_meme_table().size());

        //Проверка окончания перехода на стендИн
        UUID uuid2 = UUID.randomUUID();
        var standInReady = createConsistentSignal(uuid2, SignalMode.STANDIN, SignalStatus.READY_TO_SWITCH);
        producer.send(standInReady);
        defaultAwait().until(() -> switchoverDataSourceDelegator.getConnectionMode() == ConnectionMode.STANDIN);

        //Вставка в таблицу текущего датаСурса
        appExec.insert_into_meme_table(UUID.randomUUID(), "Kakoi takoi sidec?", "No");
        assertEquals(1, executorsPool.main().select_from_meme_table().size());
        assertEquals(1, executorsPool.standin().select_from_meme_table().size());

        //Проверка, что новый сигнал изменил запись в базе
        defaultAwait().until(() -> !executorsPool.standin().select_from_app_connection_table(uuid2).isEmpty());
        assertEquals(1, executorsPool.standin().select_from_meme_table().size());
    }

    @Test
    public void starter_successfully_process_duplicate_signal_with_same_uuid() throws InterruptedException {
        Producer<String, SignalRequest> producer = producerFactory.createProducer();
        //После старта приложение переходит в статус main тк в kafka нет записей
        //При этом в таблицу сигналов мы ничего не публикуем
        List<AppConnection> appConnections = executorsPool.main().select_from_app_connection_table();
        assertTrue(appConnections.isEmpty());

        UUID uuid = null;
        //Отправка 10ти аналогичных сигналов в кафку
        for (int i = 0; i < 10; i++) {
            uuid = UUID.randomUUID();
            var standInReadyRepeated = createConsistentSignal(uuid, SignalMode.STANDIN, SignalStatus.STARTED);
            producer.send(standInReadyRepeated);
        }

        //Проверка, что последний сигнал изменил запись в базе
        UUID finalUuid = uuid;
        defaultAwait().until(() -> !executorsPool.main().select_from_app_connection_table(finalUuid).isEmpty());
        //Проверка, что в базе только один коннекшн
        assertEquals(1, executorsPool.main().select_from_app_connection_table().size());
    }

    private void assertCorrectSwitchToStandInSignalToConnectionConversion(SignalRequest request, AppConnection appConnection) {
        assertAll("Signal request to kafka correctly convert to database record", () -> {
            assertThat(appConnection.getAppUid()).startsWith(CONSUMER_GROUP_ID_PREFIX);
            assertThat(appConnection.getSignalUid()).isEqualTo(request.getUid());
            assertThat(appConnection.getMode()).isEqualTo(SWITCH_TO_STANDIN);
            assertThat(appConnection.getSignalAuthor()).isEqualTo(request.getAuthor());
            assertThat(appConnection.getAppName()).isEqualTo(switchoverDataSourceDelegator.getAppName());
            assertThat(appConnection.getSignalSwitchType()).isEqualTo(request.getSwitchType());
            assertThat(appConnection.getAdditionalData()).isNull();
        });
    }
}