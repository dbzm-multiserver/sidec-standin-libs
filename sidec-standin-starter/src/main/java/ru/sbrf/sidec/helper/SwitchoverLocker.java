package ru.sbrf.sidec.helper;

import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class SwitchoverLocker {

    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final ReentrantReadWriteLock.WriteLock writeLock = lock.writeLock();
    private final ReentrantReadWriteLock.ReadLock readLock = lock.readLock();

    @FunctionalInterface
    public interface DataSourceSupplierThrowingSqlException<T> {
        T get() throws SQLException;
    }

    @FunctionalInterface
    public interface DataSourceSupplierThrowingSqlNotSupportedException<T> {
        T get() throws SQLFeatureNotSupportedException;
    }

    @FunctionalInterface
    public interface DataSourceRunnable {
        void run() throws SQLException;
    }


    public <T> T runInLockT(DataSourceSupplierThrowingSqlNotSupportedException<T> supplier) throws SQLFeatureNotSupportedException{
        try {
            readLock.lock();
            return supplier.get();
        } finally {
            readLock.unlock();
        }
    }

    public <T> T runInLock(DataSourceSupplierThrowingSqlException<T> supplier) throws SQLException{
        try {
            readLock.lock();
            return supplier.get();
        } finally {
            readLock.unlock();
        }
    }

    public void runInLock(DataSourceRunnable runnable) throws SQLException {
        try {
            readLock.lock();
            runnable.run();
        } finally {
            readLock.unlock();
        }
    }

    public void writeLock(){
        writeLock.lock();
    }

    public void writeUnlock(){
        writeLock.unlock();
    }
}