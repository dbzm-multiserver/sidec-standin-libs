package ru.sbrf.sidec.db;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.sbrf.sidec.api.db.TransitionManager;
import ru.sbrf.sidec.config.SwitchoverDataSourceConfiguration;
import ru.sbrf.sidec.exception.SwitchoverException;
import ru.sbrf.sidec.kafka.domain.SignalResponse;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class ConnectionTableQueryExecutor {
    public static final Logger LOGGER = LoggerFactory.getLogger(SwitchoverDataSourceConfiguration.class);
    private static final String UPSERT_QUERY =
            """
                    INSERT INTO sidec.app_connection
                    (app_uid, signal_uid, mode, signal_author, app_name, updated_at, signal_switch_type, additional_data)
                    values (?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT (app_uid)
                    DO UPDATE SET signal_uid =?, mode =?, signal_author =?, app_name =?, updated_at =?, signal_switch_type =?, additional_data =?
                    WHERE app_connection.app_uid = ?
                    """;
    private final TransitionManager transitionManager;
    private final String appName;

    public ConnectionTableQueryExecutor(String appName) {
        this.transitionManager = new TransitionManager(appName);
        this.appName = appName;
    }

    public void executeUpsert(Connection con, SignalResponse signal, String groupId) throws SQLException {
        try {
            LOGGER.debug("Insert upsert signal [{}] with group id [{}], app name [{}] and database url [{}]", signal, groupId, appName, con.getMetaData().getURL());
            var connectionEntity = transitionManager.convertSignalToConnection(signal, groupId);
            con.setAutoCommit(false);
            try (PreparedStatement pstmt = con.prepareStatement(UPSERT_QUERY)) {
                pstmt.setString(1, connectionEntity.getAppUid());
                pstmt.setObject(2, connectionEntity.getSignalUid());
                pstmt.setString(3, connectionEntity.getMode().toString().toLowerCase());
                pstmt.setString(4, connectionEntity.getSignalAuthor());
                pstmt.setString(5, connectionEntity.getAppName());
                pstmt.setObject(6, connectionEntity.getUpdatedAt());
                pstmt.setString(7, connectionEntity.getSignalSwitchType().toString().toLowerCase());
                pstmt.setString(8, connectionEntity.getAdditionalData());

                pstmt.setObject(9, connectionEntity.getSignalUid());
                pstmt.setString(10, connectionEntity.getMode().toString().toLowerCase());
                pstmt.setString(11, connectionEntity.getSignalAuthor());
                pstmt.setString(12, connectionEntity.getAppName());
                pstmt.setObject(13, connectionEntity.getUpdatedAt());
                pstmt.setString(14, connectionEntity.getSignalSwitchType().toString().toLowerCase());
                pstmt.setString(15, connectionEntity.getAdditionalData());
                pstmt.setString(16, connectionEntity.getAppUid());

                pstmt.executeUpdate();
            }
            con.commit();
            con.setAutoCommit(true);
        } catch (SQLException e) {
            throw new SwitchoverException(e.getMessage(), e);
        }
    }
}