package ru.sbrf.sidec.processor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.config.DestructionAwareBeanPostProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import ru.sbrf.sidec.config.SwitchoverConfig;
import ru.sbrf.sidec.helper.SignalBarrierService;
import ru.sbrf.sidec.exception.SwitchoverException;
import ru.sbrf.sidec.proxy.SwitchoverDataSourceDelegator;
import ru.sbrf.sidec.retry.RetryService;

import javax.sql.DataSource;

@ConditionalOnProperty(name = "sidec.kafka.bootstrap-servers")
public class SwitchoverDelegatorConfigurationBeanPostProcessor implements BeanPostProcessor, DestructionAwareBeanPostProcessor {
    public static final Logger LOGGER = LoggerFactory.getLogger(SwitchoverDelegatorConfigurationBeanPostProcessor.class);
    private final SwitchoverConfig config;
    private final RetryService retryService;
    private final SignalBarrierService barrierService;
    private SwitchoverDataSourceDelegator delegator;

    public SwitchoverDelegatorConfigurationBeanPostProcessor(SwitchoverConfig config, RetryService retryService, SignalBarrierService barrierService) {
        this.config = config;
        this.retryService = retryService;
        this.barrierService = barrierService;
    }

    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (bean instanceof DataSource) {
            if (AopUtils.isAopProxy(bean) || AopUtils.isCglibProxy(bean) || AopUtils.isJdkDynamicProxy(bean)) {
                throw new SwitchoverException("Failed to replace datasource. Switchover not supported proxy beans");
            }
            try {
                LOGGER.info("Received DataSource bean = {}. Try to replace it with switchover delegator", bean);
                if (delegator != null) {
                    throw new SwitchoverException("Find more that one datasource beans in context. Switchover can't create for bean: " + bean);
                }
                if (bean instanceof SwitchoverDataSourceDelegator) {
                    delegator = (SwitchoverDataSourceDelegator) bean;
                } else {
                    delegator = new SwitchoverDataSourceDelegator(bean, config, retryService, barrierService);
                }
                return delegator;
            } catch (Exception ex) {
                throw new SwitchoverException("Datasource replacement failed.", ex);
            }
        }
        return bean;
    }

    @Override
    public void postProcessBeforeDestruction(Object bean, String beanName) throws BeansException {
        if (delegator != null) {
            delegator.destroy();
            delegator = null;
        }
    }
}