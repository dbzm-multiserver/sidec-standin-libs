package ru.sbrf.sidec.utils;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.slf4j.LoggerFactory;

import java.util.concurrent.locks.ReentrantLock;

public class MemoryAppender extends ListAppender<ILoggingEvent> {
    public static final String ROOT_LOGGER_NAME = "ROOT";
    private static final ReentrantLock lock = new ReentrantLock();

    @Override
    protected void append(ILoggingEvent iLoggingEvent) {
        try {
            lock.lock();
            super.append(iLoggingEvent);
        } finally {
            lock.unlock();
        }
    }

    public boolean isMessageExist(String message){
        try {
            lock.lock();
            return this.list.stream().anyMatch(x -> x.getFormattedMessage().contains(message));
        } finally {
            lock.unlock();
        }
    }

    public static MemoryAppender instance() {
        MemoryAppender appender = new MemoryAppender();
        Logger logger = (Logger) LoggerFactory.getLogger(ROOT_LOGGER_NAME);
        logger.addAppender(appender);
        appender.setContext((LoggerContext) LoggerFactory.getILoggerFactory());
        appender.start();
        return appender;
    }
}