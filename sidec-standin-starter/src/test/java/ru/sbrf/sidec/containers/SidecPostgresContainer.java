package ru.sbrf.sidec.containers;

import org.testcontainers.containers.Network;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

public class SidecPostgresContainer {
    public static final DockerImageName IMAGE = DockerImageName.parse("base/docker.io/postgres:13.15-alpine3.20")
            .asCompatibleSubstituteFor("postgres");
    public static final String NETWORK_ALIAS = "postgres";
    public static final int INTERNAL_PORT = PostgreSQLContainer.POSTGRESQL_PORT;
    public static final int EXTERNAL_PORT_MAIN = PostgreSQLContainer.POSTGRESQL_PORT;
    public static final int EXTERNAL_PORT_STANDIN = 6543;
    public static final String USERNAME = "postgres";
    public static final String PASSWORD = "postgres";
    public static final String DATABASE_NAME = "postgres";

    public static PostgreSQLContainer<?> create(String networkAlias) {
        return new PostgreSQLContainer<>(IMAGE)
                .withNetwork(Network.newNetwork())
                .withDatabaseName(DATABASE_NAME)
                .withUsername(USERNAME)
                .withPassword(PASSWORD)
                .withNetworkAliases(networkAlias);
    }
}