package ru.sbrf.sidec.exception;

public class SwitchoverException extends RuntimeException{
    public SwitchoverException(String message) {
        super(message);
    }

    public SwitchoverException(String message, Throwable cause) {
        super(message, cause);
    }
}