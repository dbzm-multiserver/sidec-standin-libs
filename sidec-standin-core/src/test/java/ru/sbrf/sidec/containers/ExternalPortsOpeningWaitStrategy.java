package ru.sbrf.sidec.containers;

import org.awaitility.Awaitility;
import org.awaitility.Duration;
import org.testcontainers.containers.wait.internal.ExternalPortListeningCheck;
import org.testcontainers.containers.wait.strategy.AbstractWaitStrategy;

/**
 * В сберос нет команды nc - https://man.archlinux.org/man/nc.1.en, на основе которой
 * происходит проверка открытия портов внутри докера/подмана. Из-за этого стандартная
 * проверка открытия портов - {@link org.testcontainers.containers.wait.strategy.HostPortWaitStrategy} -
 * не проходит и контенйнер, хоть и заводится, но внутри падает с ошибкой, т.к. не может выполнить проверку
 */
public class ExternalPortsOpeningWaitStrategy extends AbstractWaitStrategy {
    @Override
    protected void waitUntilReady() {
        ExternalPortListeningCheck check = new ExternalPortListeningCheck(
                waitStrategyTarget,
                getLivenessCheckPorts()
        );
        Awaitility.await()
                .pollInSameThread()
                .pollInterval(Duration.ONE_HUNDRED_MILLISECONDS)
                .pollDelay(Duration.ZERO)
                .ignoreExceptions()
                .forever()
                .until(check);
    }
}