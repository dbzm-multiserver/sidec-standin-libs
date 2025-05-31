package ru.sbrf.sidec.db;

import ru.sbrf.sidec.exception.SwitchoverException;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;

public class NoOpDataSourceInvocationHandler implements InvocationHandler {
    @Override
    public Object invoke(Object proxy, Method method, Object[] args) {
        throw new SwitchoverException("Switch between databases in progress. Wait it for finish");
    }
}