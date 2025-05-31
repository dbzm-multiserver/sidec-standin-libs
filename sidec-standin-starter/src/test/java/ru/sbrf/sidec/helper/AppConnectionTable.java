package ru.sbrf.sidec.helper;


public class AppConnectionTable {
    public static final String TABLE_NAME = "sidec.app_connection";

    public static String CREATE_TABLE_QUERY =
            """
                    create table if not exists sidec.app_connection (
                        app_uid varchar not null primary key,
                        signal_uid uuid not null,
                        mode varchar(256) not null,
                        signal_author varchar(100) null,
                        app_name varchar(100) null,
                        updated_at timestamptz not null default now(),
                        signal_switch_type varchar(20) not null,
                        additional_data text null
                    );
                    """;

    public static String DROP_TABLE_QUERY = "drop table if exists sidec.app_connection;";

    public static String INSERT_APP_CONNECTION_SIGNAL_PREPARED_STATEMENT =
            """
                    insert into sidec.app_connection 
                    (app_uid, signal_uid, mode, signal_author, app_name, updated_at, signal_switch_type, additional_data) 
                    values (?, ?, ?, ?, ?, ?, ?, ?);
                    """;

}