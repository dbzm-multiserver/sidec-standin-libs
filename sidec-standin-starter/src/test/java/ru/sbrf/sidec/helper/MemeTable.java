package ru.sbrf.sidec.helper;

import java.util.UUID;

public class MemeTable {
    public static final String TABLE_NAME = "sidec.meme";

    public static String CREATE_TABLE_QUERY =
            """
                    create table sidec.meme (
                        id uuid not null primary key,
                        meme varchar(256) not null,
                        meme_description varchar(2048)
                    );
                    """;

    public static String INSERT_MEME_SIGNAL_PREPARED_STATEMENT =
            """
                    insert into sidec.meme 
                    (id, meme, meme_description) 
                    values (?, ?, ?);
                    """;
}