package ru.sbrf.sidec.helper;

import org.springframework.stereotype.Service;
import ru.sbrf.sidec.db.ConnectionMode;
import ru.sbrf.sidec.kafka.domain.SignalMode;
import ru.sbrf.sidec.kafka.domain.SignalStatus;
import ru.sbrf.sidec.kafka.domain.SwitchType;

import static ru.sbrf.sidec.api.db.TransitionManager.isTransitionAllowed;
import static ru.sbrf.sidec.api.db.TransitionManager.transitState;

@Service
public class SignalBarrierService {

    private volatile ConnectionMode applicationConnectionMode;

    public boolean isSignalSentAllowed(SignalMode mode, SignalStatus status) {
        return isTransitionAllowed(applicationConnectionMode, transitState(mode, status), SwitchType.CONSISTENT);
    }

    public ConnectionMode getApplicationConnectionMode() {
        return applicationConnectionMode;
    }

    public void setApplicationConnectionMode(ConnectionMode applicationConnectionMode) {
        this.applicationConnectionMode = applicationConnectionMode;
    }
}
