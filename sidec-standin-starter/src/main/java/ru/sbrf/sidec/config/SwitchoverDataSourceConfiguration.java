package ru.sbrf.sidec.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import ru.sbrf.sidec.db.NoOpDataSourceInvocationHandler;

import javax.sql.DataSource;
import java.lang.reflect.Proxy;
import java.util.Objects;

import static ru.sbrf.sidec.autoconfigure.SwitchoverAutoConfiguration.SWITCHOVER_ENABLED_CONFIG_PROPERTY;
import static ru.sbrf.sidec.util.DataSourceUtil.createDataSource;

/**
 * Configuration properties for switchover data sources.
 * Provides main and stand-in data sources with no-op fallback.
 */
@ConfigurationProperties(prefix = "sidec.datasource")
@ConditionalOnProperty(name = SWITCHOVER_ENABLED_CONFIG_PROPERTY, havingValue = "true")
public class SwitchoverDataSourceConfiguration {
    public static final Logger LOGGER = LoggerFactory.getLogger(SwitchoverDataSourceConfiguration.class);

    @NestedConfigurationProperty
    private DataSourceProperties main;
    @NestedConfigurationProperty
    private DataSourceProperties standIn;

    public DataSource mainDataSource(Object baseDataSource) {
        Objects.requireNonNull(baseDataSource);
        return createDataSource((DataSource) baseDataSource, getMain());
    }

    public DataSource standInDataSource(Object baseDataSource) {
        Objects.requireNonNull(baseDataSource);
        return createDataSource((DataSource) baseDataSource, getStandIn());
    }

    public DataSource noOpDataSource(Object baseDataSource) {
        Objects.requireNonNull(baseDataSource);
        return (DataSource) Proxy.newProxyInstance(
                baseDataSource.getClass().getClassLoader(),
                new Class[]{DataSource.class},
                new NoOpDataSourceInvocationHandler()
        );
    }

    public DataSourceProperties getMain() {
        return main;
    }

    public void setMain(DataSourceProperties mainDataSourceProperties) {
        this.main = mainDataSourceProperties;
    }

    public DataSourceProperties getStandIn() {
        return standIn;
    }

    public void setStandIn(DataSourceProperties standInDataSourceProperties) {
        this.standIn = standInDataSourceProperties;
    }
}