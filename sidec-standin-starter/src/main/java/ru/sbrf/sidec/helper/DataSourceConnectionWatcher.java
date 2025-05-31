package ru.sbrf.sidec.helper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.sbrf.sidec.config.SwitchoverConfig;
import ru.sbrf.sidec.exception.SwitchoverException;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class DataSourceConnectionWatcher {
    public static final Logger LOGGER = LoggerFactory.getLogger(DataSourceConnectionWatcher.class);

    private final ScheduledExecutorService watcher = Executors.newSingleThreadScheduledExecutor();
    private final ConcurrentMap<Connection, Object> activeConnections = new ConcurrentHashMap<>();
    private final SwitchoverConfig config;

    public DataSourceConnectionWatcher(SwitchoverConfig config) {
        this.config = config;
    }

    public void watch() {
        LOGGER.info("Start connection watcher");
        watcher.scheduleAtFixedRate(this::checkClosedConnections, config.getWatcherDelayMs(), config.getWatcherDelayMs(), TimeUnit.MILLISECONDS);
        LOGGER.info("Сonnection watcher started");
    }

    public void addConnection(Connection connection) {
        LOGGER.debug("Add connection to {}", DataSourceConnectionWatcher.class);
        activeConnections.put(connection, Boolean.TRUE);
    }

    private void checkClosedConnections() {
        LOGGER.debug("Retry to clean up closed connections");
        try {
            Iterator<Connection> iterator = activeConnections.keySet().iterator();
            while (iterator.hasNext()) {
                var connection = iterator.next();
                try {
                    if (connection.isClosed()) {
                        iterator.remove();
                    }
                } catch (SQLException sqle) {
                    LOGGER.warn("Error during deletion closed connections");
                    iterator.remove();
                }
            }
        } catch (Throwable t) {
            // Ignore any exceptions to avoid scheduled executor silent termination
        }
    }

    //TODO метрика на время работы
    public void closeAllConnections() {
        LOGGER.info("Retry to clean up all connections. Number of active connections: {}", activeConnections.size());
        Iterator<Connection> iterator = activeConnections.keySet().iterator();
        while (iterator.hasNext()) {
            var connection = iterator.next();
            try {
                if (!connection.isClosed()) {
                    LOGGER.debug("Retry to close connection");
                    try {
                        connection.rollback();
                    } catch (SQLException ex) {
                        LOGGER.warn("Error during connection rollback", ex);
                    }
                    connection.close();
                }
            } catch (SQLException e) {
                LOGGER.warn("Error during deletion closed connections");
            } finally {
                iterator.remove();
            }
        }
    }

    public void close() {
        watcher.shutdownNow();
        activeConnections.clear();
    }
}