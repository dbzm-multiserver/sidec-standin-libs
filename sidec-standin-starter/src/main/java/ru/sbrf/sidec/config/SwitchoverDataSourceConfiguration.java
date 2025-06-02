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

import static ru.sbrf.sidec.autoconfigure.SwitchoverAutoConfiguration.SWITCHOVER_ENABLED_CONFIG_PROPERTY;
import static ru.sbrf.sidec.util.DataSourceUtil.createDataSource;

@ConfigurationProperties(prefix = "sidec.datasource")
@ConditionalOnProperty(name = SWITCHOVER_ENABLED_CONFIG_PROPERTY, havingValue = "true")
public class SwitchoverDataSourceConfiguration {
    public static final Logger LOGGER = LoggerFactory.getLogger(SwitchoverDataSourceConfiguration.class);

    @NestedConfigurationProperty
    private DataSourceProperties mainDataSourceProperties;
    @NestedConfigurationProperty
    private DataSourceProperties standInDataSourceProperties;

    public DataSource mainDataSource(Object baseDataSource) {
        return createDataSource((DataSource) baseDataSource, getMainDataSourceProperties());
    }

    public DataSource standinDataSource(Object baseDataSource) {
        return createDataSource((DataSource) baseDataSource, getStandInDataSourceProperties());
    }

    public DataSource noOpDataSource(Object baseDataSource) {
        return (DataSource) Proxy.newProxyInstance(
                baseDataSource.getClass().getClassLoader(),
                new Class[]{DataSource.class},
                new NoOpDataSourceInvocationHandler()
        );
    }

    public DataSourceProperties getMainDataSourceProperties() {
        return mainDataSourceProperties;
    }

    public void setMain(DataSourceProperties mainDataSourceProperties) {
        this.mainDataSourceProperties = mainDataSourceProperties;
    }

    public DataSourceProperties getStandInDataSourceProperties() {
        return standInDataSourceProperties;
    }

    public void setStandIn(DataSourceProperties standInDataSourceProperties) {
        this.standInDataSourceProperties = standInDataSourceProperties;
    }
}