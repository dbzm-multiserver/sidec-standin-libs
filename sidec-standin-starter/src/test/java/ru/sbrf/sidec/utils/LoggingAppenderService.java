package ru.sbrf.sidec.utils;

import org.springframework.stereotype.Service;

@Service("appenderService")
public class LoggingAppenderService {
    private final MemoryAppender instance;

    public LoggingAppenderService() {
        instance = MemoryAppender.instance();
    }

    public MemoryAppender appenderInstance() {
        return this.instance;
    }

    public boolean isMessageExist(String message) {
        return this.instance.isMessageExist(message);
    }
}