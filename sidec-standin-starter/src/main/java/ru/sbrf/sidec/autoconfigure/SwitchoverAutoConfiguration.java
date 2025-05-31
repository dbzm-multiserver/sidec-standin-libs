package ru.sbrf.sidec.autoconfigure;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import ru.sbrf.sidec.config.SwitchoverConfig;
import ru.sbrf.sidec.config.SwitchoverKafkaConfig;
import ru.sbrf.sidec.config.SwitchoverDataSourceConfiguration;
import ru.sbrf.sidec.controller.SwitchoverSignalController;
import ru.sbrf.sidec.processor.SwitchoverDelegatorConfigurationBeanPostProcessor;
import ru.sbrf.sidec.retry.RetryProperties;
import ru.sbrf.sidec.retry.RetryService;

import javax.sql.DataSource;

@AutoConfiguration(after = DataSourceAutoConfiguration.class)
@Configuration
@ConditionalOnBean(DataSource.class)
@ConditionalOnProperty(name = SwitchoverAutoConfiguration.SWITCHOVER_ENABLED_CONFIG_PROPERTY, havingValue = "true")
@Import({
        SwitchoverKafkaConfig.class,
        SwitchoverDataSourceConfiguration.class,
        SwitchoverConfig.class,
        SwitchoverDelegatorConfigurationBeanPostProcessor.class,
        SwitchoverSignalController.class,
        RetryProperties.class,
        RetryService.class
})
public class SwitchoverAutoConfiguration {
    public static final String SWITCHOVER_ENABLED_CONFIG_PROPERTY = "sidec.switchover.enabled";
}