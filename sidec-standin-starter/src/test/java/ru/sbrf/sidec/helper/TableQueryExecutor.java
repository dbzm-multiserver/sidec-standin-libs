package ru.sbrf.sidec.helper;

import org.springframework.jdbc.core.JdbcTemplate;
import ru.sbrf.sidec.db.AppConnection;
import ru.sbrf.sidec.db.ConnectionMode;
import ru.sbrf.sidec.kafka.domain.SwitchType;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static ru.sbrf.sidec.config.SidecConfig.CONSUMER_GROUP_ID_PREFIX;
import static ru.sbrf.sidec.helper.AppConnectionTable.TABLE_NAME;


public class TableQueryExecutor {
    private JdbcTemplate jdbcTemplate;

    public TableQueryExecutor(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void create_app_connection_table() {
        jdbcTemplate.execute(AppConnectionTable.CREATE_TABLE_QUERY);
    }

    public void drop_app_connection_table() {
        jdbcTemplate.execute(AppConnectionTable.DROP_TABLE_QUERY);
    }

    public void insert_into_app_connection_table(String mode) {
        insert_into_app_connection_table(
                CONSUMER_GROUP_ID_PREFIX + UUID.randomUUID(),
                UUID.randomUUID(),
                mode,
                "ad",
                "ads",
                LocalDateTime.now(),
                "adsa",
                "da"
        );
    }

    public void insert_into_app_connection_table(String appUid,
                                                 UUID signalUid,
                                                 String mode,
                                                 String signalAuthor,
                                                 String appName,
                                                 LocalDateTime updatedAt,
                                                 String signalSwitchType,
                                                 String additionalData) {
        jdbcTemplate.update(AppConnectionTable.INSERT_APP_CONNECTION_SIGNAL_PREPARED_STATEMENT,
                appUid,
                signalUid,
                mode,
                signalAuthor,
                appName,
                updatedAt,
                signalSwitchType,
                additionalData
        );
    }


    public List<AppConnection> select_from_app_connection_table(UUID signalUid) {
        return jdbcTemplate.query("select * from " + TABLE_NAME + " where signal_uid = '" + signalUid + "' ", (rs, rowNum) -> convertResultSetToConnection(rs));
    }

    public List<AppConnection> select_from_app_connection_table() {
        return jdbcTemplate.query("select * from " + TABLE_NAME, (rs, rowNum) -> convertResultSetToConnection(rs));
    }

    private AppConnection convertResultSetToConnection(ResultSet rs) throws SQLException {
        return AppConnection.builder()
                .appUid(rs.getString(1))
                .signalUid(rs.getObject(2, UUID.class))
                .mode(ConnectionMode.valueOf(rs.getString(3).toUpperCase()))
                .signalAuthor(rs.getString(4))
                .appName(rs.getString(5))
                .updatedAt(rs.getObject(6, OffsetDateTime.class))
                .signalSwitchType(SwitchType.valueOf(rs.getString(7).toUpperCase()))
                .additionalData(rs.getString(8))
                .build();
    }

    public void create_sidec_schema() {
        jdbcTemplate.execute(TableSchemas.CREATE_SIDEC_SCHEMA);
    }

    public void drop_sidec_schema() {
        jdbcTemplate.execute(TableSchemas.DROP_SIDEC_SCHEMA);
    }

    public void create_meme_table() {
        jdbcTemplate.execute(MemeTable.CREATE_TABLE_QUERY);
    }

    public void insert_into_meme_table(UUID uuid, String meme, String memeDescription) {
        jdbcTemplate.update(MemeTable.INSERT_MEME_SIGNAL_PREPARED_STATEMENT,
                uuid,
                meme,
                memeDescription
        );
    }

    public List<Meme> select_from_meme_table() {
        return jdbcTemplate.query(
                "select * from " + MemeTable.TABLE_NAME, (rs, rowNum) ->
                        new Meme(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3))
        );
    }

    public List<Meme> select_from_meme_table(UUID Uid) {
        return jdbcTemplate.query(
                "select * from " + MemeTable.TABLE_NAME + " where id = '" + Uid + "' ", (rs, rowNum) ->
                        new Meme(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3))
        );
    }

    public static class ExecutorsTestPool {
        private TableQueryExecutor appExecutor;
        private TableQueryExecutor mainExecutor;
        private TableQueryExecutor standInExecutor;

        public ExecutorsTestPool(TableQueryExecutor appExecutor, TableQueryExecutor mainExecutor, TableQueryExecutor standInExecutor) {
            this.appExecutor = appExecutor;
            this.mainExecutor = mainExecutor;
            this.standInExecutor = standInExecutor;
        }

        public TableQueryExecutor app() {
            return appExecutor;
        }

        public TableQueryExecutor main() {
            return mainExecutor;
        }

        public TableQueryExecutor standin() {
            return standInExecutor;
        }
    }
}