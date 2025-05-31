package ru.sbrf.sidec.config;

import ru.sbrf.sidec.exception.SwitchoverException;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;

public class ProxyDataSourceInvocationHandler implements InvocationHandler {
    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        return InvocationHandler.invokeDefault(proxy, method, args);
    }
}