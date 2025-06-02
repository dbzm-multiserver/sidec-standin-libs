package ru.sbrf.sidec.api.db;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.sbrf.sidec.db.AppConnection;
import ru.sbrf.sidec.db.ConnectionMode;
import ru.sbrf.sidec.exception.SwitchoverException;
import ru.sbrf.sidec.kafka.domain.SignalMode;
import ru.sbrf.sidec.kafka.domain.SignalResponse;
import ru.sbrf.sidec.kafka.domain.SignalStatus;
import ru.sbrf.sidec.kafka.domain.SwitchType;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static ru.sbrf.sidec.db.ConnectionMode.MAIN;
import static ru.sbrf.sidec.db.ConnectionMode.STANDIN;
import static ru.sbrf.sidec.db.ConnectionMode.SWITCH_TO_MAIN;
import static ru.sbrf.sidec.db.ConnectionMode.SWITCH_TO_STANDIN;
import static ru.sbrf.sidec.kafka.domain.SwitchType.FORCE;

public class TransitionManager {
    public static final Logger LOGGER = LoggerFactory.getLogger(TransitionManager.class);

    private static final String SWITCH_MESSAGE = "Switch between databases was requested to [{}] mode. current_mode: [{}]. switch_type = [{}]";
    private static final String CONSISTENT_MESSAGE = SWITCH_MESSAGE + " with transactional integrity control.";
    private static final String FORCE_MESSAGE = SWITCH_MESSAGE + " without guarantees of integrity. Possible collisions main - standin";
    private static final String NOT_ALLOWED_MESSAGE = SWITCH_MESSAGE + ". Switching is not possible.";

    private final String appName;

    public TransitionManager(String appName) {
        this.appName = appName;
    }

    private static final Map<ConnectionMode, ConnectionMode> allowedTransitionMap = new HashMap<ConnectionMode, ConnectionMode>() {{
        put(MAIN, SWITCH_TO_STANDIN);
        put(SWITCH_TO_MAIN, MAIN);
        put(SWITCH_TO_STANDIN, STANDIN);
        put(STANDIN, SWITCH_TO_MAIN);
    }};

    /**
     * @param currentMode - текущий режим в котором находится приложение
     * @param signal      - сигнал, на изменение, полученный из Kafka
     * @param groupId     - group.id инстанса приложения (Kafka consumer), получившего сигнал
     * @return {@link ConnectionSaveEntity}
     */
    public ConnectionSaveEntity convertSignalToConnection(ConnectionMode currentMode, SignalResponse signal, String groupId) {
        ConnectionMode newMode = convertSignalToConnectionMode(signal);
        SwitchType switchType = signal.getSwitchType();
        if (!isTransitionAllowed(currentMode, newMode, switchType)) {
            throw new SwitchoverException("Transition is not allowed.");
        }
        AppConnection appConnection = AppConnection.builder()
                .appUid(groupId)
                .signalUid(signal.getUid())
                .mode(newMode)
                .signalAuthor(signal.getAuthor())
                .appName(appName)
                .updatedAt(OffsetDateTime.now())
                .signalSwitchType(switchType)
                .additionalData(null)
                .build();
        return new ConnectionSaveEntity(targetDatabaseToSave(newMode), appConnection);
    }

    /**
     * Используется только для sidec-standin-starter.
     * Вместо этого метода необходимо использовать convertSignalToConnection(ConnectionMode currentMode, SignalResponse signal, String groupId)
     */
    @Deprecated
    public AppConnection convertSignalToConnection(SignalResponse signal, String groupId) {
        return AppConnection.builder()
                .appUid(groupId)
                .signalUid(signal.getUid())
                .mode(convertSignalToConnectionMode(signal))
                .signalAuthor(signal.getAuthor())
                .appName(appName)
                .updatedAt(OffsetDateTime.now())
                .signalSwitchType(signal.getSwitchType())
                .additionalData(null)
                .build();
    }

    public static ConnectionMode convertSignalToConnectionMode(SignalResponse signal) {
        SignalMode mode = signal.getMode();
        SignalStatus status = signal.getStatus();
        ConnectionMode connectionMode = transitState(mode, status);
        LOGGER.info("Signal mode[{}] with status [{}] converted to connection mode [{}]", mode, status, connectionMode);
        return connectionMode;
    }

    private static ConnectionMode transitState(SignalMode mode, SignalStatus status) {
        if (SignalMode.MAIN.equals(mode)) {
            if (SignalStatus.STARTED.equals(status)) return SWITCH_TO_MAIN;
            if (SignalStatus.READY_TO_SWITCH.equals(status)) return MAIN;
        }
        if (SignalMode.STANDIN.equals(mode)) {
            if (SignalStatus.STARTED.equals(status)) return SWITCH_TO_STANDIN;
            if (SignalStatus.READY_TO_SWITCH.equals(status)) return STANDIN;
        }
        throw new SwitchoverException("No valid transition for mode: [" + mode + "]");
    }

    public static boolean isTransitionAllowed(ConnectionMode before, ConnectionMode after, SwitchType switchType) {
        if (switchType == FORCE) {
            LOGGER.info(FORCE_MESSAGE, after, before, switchType);
            return true;
        }
        if (before == null || Objects.equals(allowedTransitionMap.get(before), after)) {
            LOGGER.info(CONSISTENT_MESSAGE, after, before, switchType);
            return true;
        }
        LOGGER.info(NOT_ALLOWED_MESSAGE, after, before, switchType);
        return false;
    }

    public static boolean isSameMode(ConnectionMode before, ConnectionMode after) {
        if (before == after) {
            LOGGER.info("Switch between databases was requested to mode: [{}]. current_mode: [{}]. Request was not completed.", after, before);
            return true;
        } else {
            return false;
        }
    }


    public static ConnectionMode targetDatabaseToSave(ConnectionMode newMode) {
        return newMode == ConnectionMode.SWITCH_TO_MAIN || newMode == ConnectionMode.STANDIN ? ConnectionMode.STANDIN : ConnectionMode.MAIN;
    }

    /**
     * Cущность для сохранения.
     * targetTable - Алиас таблицы для сохранения сигнала.
     * targetConnection - Сохраняемый сигнал.
     */
    public static class ConnectionSaveEntity {
        private ConnectionMode targetTable;
        private AppConnection targetConnection;

        public ConnectionSaveEntity(ConnectionMode targetTable, AppConnection targetConnection) {
            this.targetTable = targetTable;
            this.targetConnection = targetConnection;
        }

        public ConnectionSaveEntity() {
        }

        public void setTargetTable(ConnectionMode targetTable) {
            this.targetTable = targetTable;
        }

        public void setTargetConnection(AppConnection targetConnection) {
            this.targetConnection = targetConnection;
        }

        public ConnectionMode getTargetTable() {
            return targetTable;
        }

        public AppConnection getTargetConnection() {
            return targetConnection;
        }

    }
}