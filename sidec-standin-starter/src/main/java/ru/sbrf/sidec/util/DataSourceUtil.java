package ru.sbrf.sidec.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.jdbc.datasource.lookup.JndiDataSourceLookup;
import ru.sbrf.sidec.config.SwitchoverDataSourceConfiguration;

import javax.sql.DataSource;

public class DataSourceUtil {
    public static final Logger LOGGER = LoggerFactory.getLogger(SwitchoverDataSourceConfiguration.class);
    private static final String[] NOT_COPIED_PROPERTIES = {
            "jdbcUrl",
            "username",
            "password",
            "driverClassName",
            "exceptionOverrideClassName"
    };

    public static DataSource createDataSource(DataSource baseDataSource, DataSourceProperties config) {
        if (config.getJndiName() != null) {
            JndiDataSourceLookup lookup = new JndiDataSourceLookup();
            return lookup.getDataSource(config.getJndiName());
        } else {
            var dataSource = createDataSource(
                    config,
                    baseDataSource.getClass(),
                    config.getClassLoader()
            );
            BeanUtils.copyProperties(baseDataSource, dataSource, NOT_COPIED_PROPERTIES);
            return (DataSource) dataSource;
        }
    }

    private static <T> T createDataSource(DataSourceProperties config,
                                          Class<? extends DataSource> type,
                                          ClassLoader classLoader) {
        LOGGER.debug("Creating a data source with the type {}", type);
        return (T) DataSourceBuilder.create(classLoader)
                .type(type)
                .driverClassName(config.determineDriverClassName())
                .url(config.determineUrl())
                .username(config.determineUsername())
                .password(config.determinePassword())
                .build();
    }
}