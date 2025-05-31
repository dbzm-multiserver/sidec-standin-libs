package ru.sbrf.sidec.api.db;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ru.sbrf.sidec.db.ConnectionMode;
import ru.sbrf.sidec.exception.SwitchoverException;
import ru.sbrf.sidec.kafka.domain.SignalMode;
import ru.sbrf.sidec.kafka.domain.SignalResponse;
import ru.sbrf.sidec.kafka.domain.SignalStatus;
import ru.sbrf.sidec.kafka.domain.SwitchType;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class TransitionManagerTest {
    private static final String APP_NAME = "app_name";
    private static final TransitionManager transitionManager = new TransitionManager(APP_NAME);

    @ParameterizedTest(name = "{index}. Перевод из режима [{0}] при сигнале [{1}] с типом [{2}] со статусом [{3}]. Сохранено в базу [{4}] c режимом [{5}]")
    @CsvSource({
            //откуда                       сигнал с кафки                          куда и c каким режимом сохраняем
            ",                      MAIN, FORCE, STARTED,                           STANDIN, SWITCH_TO_MAIN",
            ",                      MAIN, FORCE, READY_TO_SWITCH,                   MAIN, MAIN",
            ",                      MAIN, CONSISTENT, STARTED,                      STANDIN, SWITCH_TO_MAIN",
            ",                      MAIN, CONSISTENT, READY_TO_SWITCH,              MAIN, MAIN",
            ",                      STANDIN, FORCE, STARTED,                        MAIN, SWITCH_TO_STANDIN",
            ",                      STANDIN, FORCE, READY_TO_SWITCH,                STANDIN, STANDIN",
            ",                      STANDIN, CONSISTENT, STARTED,                   MAIN, SWITCH_TO_STANDIN",
            ",                      STANDIN, CONSISTENT, READY_TO_SWITCH,           STANDIN, STANDIN",
            "MAIN,                  MAIN, FORCE, STARTED,                           STANDIN, SWITCH_TO_MAIN",
            "MAIN,                  MAIN, FORCE, READY_TO_SWITCH,                   MAIN, MAIN",
            "MAIN,                  STANDIN, FORCE, STARTED,                        MAIN, SWITCH_TO_STANDIN",
            "MAIN,                  STANDIN, FORCE, READY_TO_SWITCH,                STANDIN, STANDIN",
            "MAIN,                  STANDIN, CONSISTENT, STARTED,                   MAIN, SWITCH_TO_STANDIN",
            "SWITCH_TO_MAIN,        MAIN, FORCE, STARTED,                           STANDIN, SWITCH_TO_MAIN",
            "SWITCH_TO_MAIN,        MAIN, FORCE, READY_TO_SWITCH,                   MAIN, MAIN",
            "SWITCH_TO_MAIN,        MAIN, CONSISTENT, READY_TO_SWITCH,              MAIN, MAIN",
            "SWITCH_TO_MAIN,        STANDIN, FORCE, STARTED,                        MAIN, SWITCH_TO_STANDIN",
            "SWITCH_TO_MAIN,        STANDIN, FORCE, READY_TO_SWITCH,                STANDIN, STANDIN",
            "STANDIN,               MAIN, FORCE, STARTED,                           STANDIN, SWITCH_TO_MAIN",
            "STANDIN,               MAIN, FORCE, READY_TO_SWITCH,                   MAIN, MAIN",
            "STANDIN,               MAIN, CONSISTENT, STARTED,                      STANDIN, SWITCH_TO_MAIN",
            "STANDIN,               STANDIN, FORCE, STARTED,                        MAIN, SWITCH_TO_STANDIN",
            "STANDIN,               STANDIN, FORCE, READY_TO_SWITCH,                STANDIN, STANDIN",
            "SWITCH_TO_STANDIN,     MAIN, FORCE, STARTED,                           STANDIN, SWITCH_TO_MAIN",
            "SWITCH_TO_STANDIN,     MAIN, FORCE, READY_TO_SWITCH,                   MAIN, MAIN",
            "SWITCH_TO_STANDIN,     STANDIN, FORCE, STARTED,                        MAIN, SWITCH_TO_STANDIN",
            "SWITCH_TO_STANDIN,     STANDIN, FORCE, READY_TO_SWITCH,                STANDIN, STANDIN",
            "SWITCH_TO_STANDIN,     STANDIN, CONSISTENT, READY_TO_SWITCH,           STANDIN, STANDIN"
    })
    public void manager_allow_transition_between_mode(
            ConnectionMode currentMode,
            SignalMode mode,
            SwitchType type,
            SignalStatus status,
            ConnectionMode targetDatabase,
            ConnectionMode targetMode
    ) {
        SignalResponse signalResponse = new SignalResponse(
                UUID.randomUUID(),
                mode,
                "AmI",
                "YaUdiVL",
                status,
                type
        );
        TransitionManager.ConnectionSaveEntity entity = transitionManager.convertSignalToConnection(currentMode, signalResponse, "group_id");
        assertThat(entity.getTargetTable()).isEqualTo(targetDatabase);
        assertThat(entity.getTargetConnection().getMode()).isEqualTo(targetMode);
    }

    @ParameterizedTest(name = "{index}. Неуспешный перевод из режима [{0}] при сигнале [{1}] с типом [{2}] со статусом [{3}]")
    @CsvSource({
            //откуда                     сигнал с кафки
            "MAIN,                  MAIN, CONSISTENT, STARTED",
            "MAIN,                  MAIN, CONSISTENT, READY_TO_SWITCH",
            "MAIN,                  STANDIN, CONSISTENT, READY_TO_SWITCH",
            "SWITCH_TO_MAIN,        MAIN, CONSISTENT, STARTED",
            "SWITCH_TO_MAIN,        STANDIN, CONSISTENT, STARTED",
            "SWITCH_TO_MAIN,        STANDIN, CONSISTENT, READY_TO_SWITCH",
            "STANDIN,               MAIN, CONSISTENT, READY_TO_SWITCH",
            "STANDIN,               STANDIN, CONSISTENT, STARTED",
            "STANDIN,               STANDIN, CONSISTENT, READY_TO_SWITCH",
            "SWITCH_TO_STANDIN,     MAIN, CONSISTENT, STARTED",
            "SWITCH_TO_STANDIN,     MAIN, CONSISTENT, READY_TO_SWITCH",
            "SWITCH_TO_STANDIN,     STANDIN, CONSISTENT, STARTED"
    })
    public void manager_not_allow_transition_between_mode(ConnectionMode currentMode, SignalMode mode, SwitchType type, SignalStatus status) {
        SignalResponse signalResponse = new SignalResponse(
                UUID.randomUUID(),
                mode,
                "AmI",
                "YaUdiVL",
                status,
                type
        );
        assertThatThrownBy(() -> {
            transitionManager.convertSignalToConnection(currentMode, signalResponse, "group_id");
        }).isInstanceOf(SwitchoverException.class)
                .hasMessageContaining("Transition is not allowed");
    }
}