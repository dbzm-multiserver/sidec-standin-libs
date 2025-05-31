package ru.sbrf.sidec.extension;

import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.testcontainers.containers.GenericContainer;
import ru.sbrf.sidec.containers.SidecPostgresContainer;
import ru.sbrf.sidec.containers.TestcontainersGlobalConfig;

import java.util.List;

public class PostgresExtension implements BeforeAllCallback {

    public static final String DATASOURCE_URL_MAIN = "DATABASE_URL_MAIN";
    public static final String DATASOURCE_URL_STANDIN = "DATABASE_URL_STANDIN";
    private static final String POSTGRES = "postgres";
    private static GenericContainer<?> postgresMain;
    private static GenericContainer<?> postgresStandIn;

    @Override
    public void beforeAll(ExtensionContext context) {
        if (postgresMain == null) {
            postgresMain = createPostgresContainer(
                    postgresMain,
                    SidecPostgresContainer.EXTERNAL_PORT_MAIN,
                    "postgres-main",
                    "testcontainers-postgres-main",
                    DATASOURCE_URL_MAIN
            );
        }
        if (postgresStandIn == null) {
            postgresStandIn = createPostgresContainer(
                    postgresStandIn,
                    SidecPostgresContainer.EXTERNAL_PORT_STANDIN,
                    "postgres-stand-in",
                    "testcontainers-postgres-stand-in",
                    DATASOURCE_URL_STANDIN
            );
        }
    }

    private GenericContainer<?>  createPostgresContainer(
            GenericContainer<?> postgres,
            int externalPort,
            String networkAlias,
            String containerName,
            String databaseUrlProperty
    ) {
        if (
                postgres != null && postgres.isRunning()
        ) {
            return postgres;
        }
        postgres = SidecPostgresContainer.create(networkAlias)
                .withCommand("postgres", "-c", "wal_level=logical");
        if (TestcontainersGlobalConfig.BIND_PORTS_TO_HOST) {
            postgres.setPortBindings(
                    List.of("0.0.0.0:" + externalPort+ ":" + SidecPostgresContainer.INTERNAL_PORT)
            );
            postgres.withCreateContainerCmdModifier(x -> x.withName(containerName));
        }
        postgres.start();
        Integer portMain = postgres.getMappedPort(SidecPostgresContainer.INTERNAL_PORT);
        String jdbcUrlMain = String.format(
                "jdbc:postgresql://127.0.0.1:%d/%s",
                portMain, POSTGRES
        );
        System.setProperty(databaseUrlProperty, jdbcUrlMain);
        return postgres;
    }
}