package ru.sbrf.sidec.proxy;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AlterConfigOp;
import org.apache.kafka.clients.admin.Config;
import org.apache.kafka.clients.admin.ConfigEntry;
import org.apache.kafka.clients.admin.ConsumerGroupListing;
import org.apache.kafka.clients.admin.ListConsumerGroupsOptions;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.ConsumerGroupState;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.config.ConfigResource;
import org.apache.kafka.common.errors.InterruptException;
import org.slf4j.LoggerFactory;
import org.springframework.retry.support.RetryTemplate;
import ru.sbrf.sidec.config.SwitchoverConfig;
import ru.sbrf.sidec.db.ConnectionMode;
import ru.sbrf.sidec.db.ConnectionTableQueryExecutor;
import ru.sbrf.sidec.exception.SwitchoverException;
import ru.sbrf.sidec.helper.DataSourceConnectionWatcher;
import ru.sbrf.sidec.helper.SwitchoverLocker;
import ru.sbrf.sidec.api.db.TransitionManager;
import ru.sbrf.sidec.kafka.domain.SignalResponse;
import ru.sbrf.sidec.kafka.factory.SidecConsumerFactory;
import ru.sbrf.sidec.retry.RetryService;
import ru.sbrf.sidec.metrics.SwitchoverDelegatorMetrics;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.ConnectionBuilder;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.ShardingKeyBuilder;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;
import java.util.stream.Collectors;


import static ru.sbrf.sidec.api.db.TransitionManager.isSameMode;
import static ru.sbrf.sidec.api.db.TransitionManager.isTransitionAllowed;
import static ru.sbrf.sidec.config.SidecConfig.CLEAN_UP_POLICY_COMPACT;
import static ru.sbrf.sidec.config.SidecConfig.CLEAN_UP_POLICY_CONFIG_NAME;
import static ru.sbrf.sidec.config.SidecConfig.CONSUMER_GROUP_ID_PREFIX;

public class SwitchoverDataSourceDelegator implements DataSource {

    public static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(SwitchoverDataSourceDelegator.class);
    private static final String EMPTY_TOPIC_MESSAGE = "There is no messages in topic. Starter couldn't start in correct position.";

    private static final Map<ConnectionMode, DataSource> dispatcher = new HashMap<>();
    private final ConnectionTableQueryExecutor dbExecutor;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final SwitchoverLocker locker = new SwitchoverLocker();

    private volatile ConnectionMode connectionMode = null;
    private final AtomicReference<DataSource> dataSource = new AtomicReference<>();
    private String appUid;
    private final String appName;
    private final SwitchoverConfig config;
    private Consumer<String, SignalResponse> consumer;
    private final SidecConsumerFactory<String, SignalResponse> consumerFactory;

    private final DataSourceConnectionWatcher watcher;
    private final TopicPartition signalPartition;
    private final List<TopicPartition> signalPartitionList;
    private final AdminClient adminClient;

    //TODO метрика
    private final RetryTemplate retryTemplate;

    public SwitchoverDataSourceDelegator(Object bean, SwitchoverConfig config, RetryService retryService) {
        this.retryTemplate = retryService.retryTemplate();
        this.config = config;
        adminClient = config.getKafkaConfig().getAdminClient();
        createSignalTopicIfNeed();
        clearEmptyConsumerGroups();
        changeTopicCleanUpPolicy();
        watcher = new DataSourceConnectionWatcher(config);
        watcher.watch();
        signalPartition = new TopicPartition(config.getSignalTopic(), 0);
        signalPartitionList = List.of(signalPartition);
        initializeDatasource(bean);
        this.appName = config.getAppName();
        dbExecutor = new ConnectionTableQueryExecutor(appName);
        this.consumerFactory = config.getKafkaConfig().getConsumerFactory();
        checkStateOnStartUp();
        executor.execute(this::startStateChecker);
        registerMetrics();
        LOGGER.info("Switchover data source delegator started in [{}] mode", connectionMode);
    }

    private void registerMetrics() {
        SwitchoverDelegatorMetrics metrics = new SwitchoverDelegatorMetrics(appUid, appName);
        metrics.registerSwitchoverMetrics(connectionMode);
    }

    private void createSignalTopicIfNeed() {
        try {
            Set<String> existingTopics = adminClient.listTopics().names().get();
            if (!existingTopics.contains(config.getSignalTopic())) {
                adminClient.createTopics(List.of(new NewTopic(config.getSignalTopic(), 1, (short) 1)));
            }
        } catch (Exception ex) {
            throw new SwitchoverException("Exception during topic creation", ex);
        }
    }

    //TODO на флаг посадить это поведение
    private void changeTopicCleanUpPolicy() {
        ConfigResource configResource = new ConfigResource(ConfigResource.Type.TOPIC, config.getSignalTopic());
        ConfigEntry configEntry;
        try {
            Map<ConfigResource, Config> configResourceConfigMap = adminClient.describeConfigs(List.of(configResource))
                    .all()
                    .get(5, TimeUnit.SECONDS);
            Config signalTopicConfig = configResourceConfigMap.values().stream()
                    .findFirst()
                    .orElseThrow(
                            () -> new SwitchoverException("Configs for signal topic not found")
                    );
            configEntry = signalTopicConfig.get(CLEAN_UP_POLICY_CONFIG_NAME);
            if (configEntry == null) {
                throw new SwitchoverException("cleanup.policy config for signal topic not found");
            }
        } catch (InterruptedException | ExecutionException | TimeoutException ex) {
            throw new SwitchoverException("Exception during describe config for signal topic", ex);
        }
        if (!CLEAN_UP_POLICY_COMPACT.equals(configEntry.value())) {
            try {
                AlterConfigOp alterConfigOp = new AlterConfigOp(changeCleanUpPolicyConfig(configEntry), AlterConfigOp.OpType.SET);
                adminClient.incrementalAlterConfigs(
                                Map.of(configResource, List.of(alterConfigOp))
                        )
                        .all()
                        .get(5, TimeUnit.SECONDS);
            } catch (InterruptedException | ExecutionException | TimeoutException ex) {
                throw new SwitchoverException("Exception during alter cleanup.policy config for signal topic", ex);
            }
        }
    }

    private ConfigEntry changeCleanUpPolicyConfig(ConfigEntry configEntry) {
        return new ConfigEntry(
                configEntry.name(),
                CLEAN_UP_POLICY_COMPACT,
                configEntry.source(),
                configEntry.isSensitive(),
                configEntry.isReadOnly(),
                configEntry.synonyms(),
                configEntry.type(),
                configEntry.documentation()
        );
    }

    private void clearEmptyConsumerGroups() {
        Collection<ConsumerGroupListing> consumerGroupListings = null;
        try {
            consumerGroupListings = adminClient.listConsumerGroups(
                            new ListConsumerGroupsOptions()
                                    .inStates(Set.of(ConsumerGroupState.EMPTY))
                    ).valid()
                    .get(5, TimeUnit.SECONDS);
            if (consumerGroupListings.isEmpty()) {
                LOGGER.info("There are no available consumer groups in kafka.");
                return;
            }
        } catch (Exception e) {
            LOGGER.warn("Can not properly get valid consumer groups on Kafka", e);
            return;
        }
        LOGGER.info("There are {} EMPTY consumer groups in kafka.", consumerGroupListings.size());
        List<String> suitableConsumerGroups = consumerGroupListings.stream()
                .map(ConsumerGroupListing::groupId)
                .filter(s -> s.startsWith(CONSUMER_GROUP_ID_PREFIX))
                .collect(Collectors.toList());
        LOGGER.info("{} of these groups will be deleted.", consumerGroupListings.size());
        try {
            adminClient.deleteConsumerGroups(suitableConsumerGroups).all().get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            LOGGER.warn("Can not properly delete empty consumer groups on Kafka", e);
        }
    }

    private Long findLatestOffset() {
        if (consumer == null) {
            initializeKafkaConsumer();
        }
        Long nextOffset = consumer.endOffsets(signalPartitionList).get(signalPartition);
        return Objects.equals(nextOffset, 0L) ? 0 : nextOffset - 1;
    }

    private void checkStateOnStartUp() {
        LOGGER.info("Start find initial mode for app start up.");
        Long latestOffset = findLatestOffset();
        ConsumerRecord<String, SignalResponse> latestRecord = null;
        latest_record:
        while (true) {
            if (consumer == null) {
                initializeKafkaConsumer();
            }
            if (Objects.equals(latestOffset, 0L)) {
                break;
            }
            var poll = consumer.poll(Duration.ofMillis(config.getPollTimeout().toMillis()));
            if (poll.isEmpty()) {
                OptionalLong optionalLong = consumer.currentLag(new TopicPartition(config.getSignalTopic(), 0));
                boolean isLagPresent = optionalLong.isPresent();
                if (isLagPresent) {
                    long lag = optionalLong.getAsLong();
                    if (lag == 0) {
                        LOGGER.warn(EMPTY_TOPIC_MESSAGE);
                        throw new SwitchoverException(EMPTY_TOPIC_MESSAGE);
                    }
                } else {
                    throw new SwitchoverException("There is EMPTY lag on the topic.");
                }
            }
            for (ConsumerRecord<String, SignalResponse> record : poll) {
                LOGGER.debug("Signal was received from {} offset", record.offset());
                if (record.offset() == latestOffset) {
                    latestRecord = record;
                    break latest_record;
                }
            }
        }
        try {
            locker.writeLock();
            if (latestRecord == null) {
                initializeDefaultStateOnStartUp();
                LOGGER.info("The verification of the initial connection mode is completed. Connection mode: {}", connectionMode);
            } else {
                updateConnectionMode(latestRecord);
                LOGGER.info("Switchover is initialized after receiving a signal message from kafka at startup. Connection mode: " + connectionMode);
            }
        } finally {
            if (!Objects.equals(latestOffset, 0L)) {
                consumer.commitSync();
            }
            locker.writeUnlock();
        }
    }

    private void initializeDefaultStateOnStartUp() {
        connectionMode = ConnectionMode.MAIN;
        this.dataSource.set(dispatcher.get(connectionMode));
        LOGGER.info("Switchover is initialized after receiving a signal message from kafka at startup. Connection mode: " + connectionMode);
    }

    private void initializeKafkaConsumer() {
        LOGGER.info("There is no switchover consumer. Creating a consumer");
        consumer = consumerFactory.createConsumer(List.of(config.getSignalTopic()));
        appUid = consumer.groupMetadata().groupId();
        LOGGER.info("App uid: {}", appUid);
    }

    private void initializeDatasource(Object bean) {
        LOGGER.debug("Initializing the switchover data source for switching");
        var dataSourceConfig = config.getDataSourceConfig();
        var noOpDataSource = dataSourceConfig.noOpDataSource(bean);
        dispatcher.put(ConnectionMode.MAIN, dataSourceConfig.mainDataSource(bean));
        dispatcher.put(ConnectionMode.SWITCH_TO_MAIN, noOpDataSource);
        dispatcher.put(ConnectionMode.SWITCH_TO_STANDIN, noOpDataSource);
        dispatcher.put(ConnectionMode.STANDIN, dataSourceConfig.standinDataSource(bean));
    }

    private void startStateChecker() {
        while (true) {
            try {
                checkState();
            } catch (InterruptedException | InterruptException e) {
                try {
                    LOGGER.info("State checker thread was interrupted, shutting down...");
                    Thread.currentThread().interrupt();
                } finally {
                    if (consumer != null) {
                        //TODO WakeUpException
                        consumer.wakeup();// Прерываем текущий poll()
                        //TODO Interrupt вылетает
                        consumer.close(Duration.ofSeconds(2)); // Ждём закрытия
                        consumer = null;
                    }
                }
            } catch (Exception ex) {
                LOGGER.warn("Exception during kafka status signal processing", ex);
            }
        }
    }

    private void checkState() throws InterruptedException {
        LOGGER.debug("Switchover status check.");
        if (Thread.currentThread().isInterrupted()) {
            throw new InterruptedException("Thread was interrupted");
        }
        if (consumer == null) {
            initializeKafkaConsumer();
        }
        ConsumerRecords<String, SignalResponse> poll = null;
        poll = consumer.poll(Duration.ofMillis(config.getPollTimeout().toMillis()));
        if (!poll.isEmpty()) {
            try {
                LOGGER.info("Switchover received a message from the signal topic. Processing {} messages.", poll.count());
                locker.writeLock();
                poll.forEach(this::updateConnectionMode);
            } finally {
                LOGGER.debug("Committing messages on signal topic");
                consumer.commitSync();
                locker.writeUnlock();
            }
        }
    }

    private void updateConnectionMode(ConsumerRecord<String, SignalResponse> record) {
        var signal = record.value();
        var newMode = TransitionManager.convertSignalToConnectionMode(signal);
        LOGGER.info("The transition from {} to {} mode has begun", connectionMode, newMode);
        if (isSameMode(connectionMode, newMode)) {
            saveMode(newMode, signal);
        } else if (isTransitionAllowed(connectionMode, newMode, signal.getSwitchType())) {
            watcher.closeAllConnections();
            saveMode(newMode, signal);
            connectionMode = newMode;
            this.dataSource.set(dispatcher.get(connectionMode));
            LOGGER.info("The application has been switched to mode:{}. Start processing incoming requests.", newMode);
        } else {
            LOGGER.warn("Switching from {} mode to {} mode is not possible. Check the signal in the topic {}, offset {}",
                    connectionMode, newMode, record.partition(), record.offset());
        }
    }

    private void saveMode(ConnectionMode newMode, SignalResponse signal) {
        try {
            retryTemplate.execute(ctx -> {
                try (Connection con = getConnectionForModeSave(newMode)) {
                    Throwable lastThrowable = ctx.getLastThrowable();
                    LOGGER.info("Try to save the switching signal to the database, retries {}, last exception {}.", ctx.getRetryCount(), lastThrowable == null ? "none" : lastThrowable.getMessage());
                    dbExecutor.executeUpsert(con, signal, appUid);
                    return null;
                }
            });
        } catch (Exception ex) {
            throw new SwitchoverException("The switching signal could not be saved to the database.", ex);
        }
    }

    private Connection getConnectionForModeSave(ConnectionMode newMode) throws SQLException {
        return newMode == ConnectionMode.SWITCH_TO_MAIN || newMode == ConnectionMode.STANDIN ?
                dispatcher.get(ConnectionMode.STANDIN).getConnection() :
                dispatcher.get(ConnectionMode.MAIN).getConnection();
    }

    //For Testing
    public ConnectionMode getConnectionMode() {
        return connectionMode;
    }

    //For Testing
    public String getAppName() {
        return appName;
    }

    @Override
    public Connection getConnection() throws SQLException {
        return locker.runInLock(() -> {
            var connection = dataSource.get().getConnection();
            watcher.addConnection(connection);
            return connection;
        });
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return locker.runInLock(() -> {
            var connection = dataSource.get().getConnection(username, password);
            watcher.addConnection(connection);
            return connection;
        });
    }

    @Override
    public PrintWriter getLogWriter() throws SQLException {
        return locker.runInLock(() -> dataSource.get().getLogWriter());
    }

    @Override
    public void setLogWriter(PrintWriter out) throws SQLException {
        locker.runInLock(() -> dataSource.get().setLogWriter(out));
    }

    @Override
    public void setLoginTimeout(int seconds) throws SQLException {
        locker.runInLock(() -> dataSource.get().setLoginTimeout(seconds));
    }

    @Override
    public int getLoginTimeout() throws SQLException {
        return locker.runInLock(() -> dataSource.get().getLoginTimeout());
    }

    @Override
    public ConnectionBuilder createConnectionBuilder() throws SQLException {
        return locker.runInLock(() -> dataSource.get().createConnectionBuilder());
    }

    @Override
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        return locker.runInLockT(() -> dataSource.get().getParentLogger());
    }

    @Override
    public ShardingKeyBuilder createShardingKeyBuilder() throws SQLException {
        return locker.runInLock(() -> dataSource.get().createShardingKeyBuilder());
    }

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        return locker.runInLock(() -> dataSource.get().unwrap(iface));
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        return locker.runInLock(() -> dataSource.get().isWrapperFor(iface));
    }

    public void destroy() {
        // 1. Завершаем ExecutorService
        executor.shutdown(); // Не shutdownNow(), чтобы дать потоку chance завершиться
        try {
            // Ждём завершения потока (например, 5 секунд)
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                LOGGER.warn("Executor did not terminate gracefully, forcing shutdown...");
                executor.shutdownNow(); // Принудительное завершение
            }
        } catch (InterruptedException e) {
            LOGGER.warn("Interrupted while waiting for executor shutdown", e);
            executor.shutdownNow(); // Принудительно закрываем, если прерваны
            Thread.currentThread().interrupt(); // Восстанавливаем флаг прерывания
        }

        // 2. Закрываем Kafka Consumer
        if (consumer != null) {
            try {
                //TODO KafkaConsumer is not safe for multi-threaded access. currentThread
                consumer.wakeup(); // Прерываем текущий poll()
                consumer.close(Duration.ofSeconds(2)); // Ждём закрытия
                consumer = null;
            } catch (Exception ex) {
                LOGGER.warn("Could not close consumer gracefully", ex);
            }
        }

        // 3. Закрываем watcher (например, соединения с БД)
        try {
            watcher.close();
        } catch (Exception ex) {
            LOGGER.warn("Failed to close watcher", ex);
        }
    }
}