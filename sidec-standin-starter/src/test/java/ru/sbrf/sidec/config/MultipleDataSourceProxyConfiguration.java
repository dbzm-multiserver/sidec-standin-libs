package ru.sbrf.sidec.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import ru.sbrf.sidec.db.NoOpDataSourceInvocationHandler;

import javax.sql.DataSource;

import java.lang.reflect.Proxy;

import static ru.sbrf.sidec.extension.PostgresExtension.DATASOURCE_URL_MAIN;
import static ru.sbrf.sidec.extension.PostgresExtension.DATASOURCE_URL_STANDIN;

@TestConfiguration
public class MultipleDataSourceProxyConfiguration {

    @Bean
    public JdbcTemplate jdbcTemplateMain(){
        return new JdbcTemplate(dataSourceMain());
    }
    @Bean
    public JdbcTemplate jdbcTemplateStandIn(){
        return new JdbcTemplate(dataSourceStandIn());
    }

    @Bean
    public DataSource dataSourcePostgres() {
        return (DataSource) Proxy.newProxyInstance(
                DriverManagerDataSource.class.getClassLoader(),
                new Class[] {DataSource.class},
                new ProxyDataSourceInvocationHandler()
        );
    }

    public DataSource dataSourceMain() {
        return createDataSource(DATASOURCE_URL_MAIN);
    }

    public DataSource dataSourceStandIn() {
        return createDataSource(DATASOURCE_URL_STANDIN);
    }

    public DataSource createDataSource(String url) {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.postgresql.Driver");
        dataSource.setUrl(System.getProperty(url));
        dataSource.setUsername("postgres");
        dataSource.setPassword("postgres");
        return dataSource;
    }

}