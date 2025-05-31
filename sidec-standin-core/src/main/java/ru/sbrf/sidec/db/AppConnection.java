package ru.sbrf.sidec.db;

import ru.sbrf.sidec.kafka.domain.SwitchType;

import java.time.OffsetDateTime;
import java.util.UUID;

public class AppConnection {
    private String appUid;
    private UUID signalUid;
    private ConnectionMode mode;
    private String signalAuthor;
    private String appName;
    private OffsetDateTime updatedAt;
    private SwitchType signalSwitchType;
    private String additionalData;

    public AppConnection() {
    }

    public AppConnection(String appUid, UUID signalUid, ConnectionMode mode, String signalAuthor, String appName, OffsetDateTime updatedAt, SwitchType signalSwitchType, String additionalData) {
        this.appUid = appUid;
        this.signalUid = signalUid;
        this.mode = mode;
        this.signalAuthor = signalAuthor;
        this.appName = appName;
        this.updatedAt = updatedAt;
        this.signalSwitchType = signalSwitchType;
        this.additionalData = additionalData;
    }

    public String getAppUid() {
        return appUid;
    }

    public void setAppUid(String appUid) {
        this.appUid = appUid;
    }

    public UUID getSignalUid() {
        return signalUid;
    }

    public void setSignalUid(UUID signalUid) {
        this.signalUid = signalUid;
    }

    public ConnectionMode getMode() {
        return mode;
    }

    public void setMode(ConnectionMode mode) {
        this.mode = mode;
    }

    public String getSignalAuthor() {
        return signalAuthor;
    }

    public void setSignalAuthor(String signalAuthor) {
        this.signalAuthor = signalAuthor;
    }

    public String getAppName() {
        return appName;
    }

    public void setAppName(String appName) {
        this.appName = appName;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public SwitchType getSignalSwitchType() {
        return signalSwitchType;
    }

    public void setSignalSwitchType(SwitchType signalSwitchType) {
        this.signalSwitchType = signalSwitchType;
    }

    public String getAdditionalData() {
        return additionalData;
    }

    public void setAdditionalData(String additionalData) {
        this.additionalData = additionalData;
    }

    public static ConnectionBuilder builder() {
        return new ConnectionBuilder();
    }

    public static class ConnectionBuilder {
        private String appUid;
        private UUID signalUid;
        private ConnectionMode mode;
        private String signalAuthor;
        private String appName;
        private OffsetDateTime updatedAt;
        private SwitchType signalSwitchType;
        private String additionalData;

        ConnectionBuilder() {
        }

        public ConnectionBuilder appUid(String appUid) {
            this.appUid = appUid;
            return this;
        }

        public ConnectionBuilder signalUid(UUID signalUid) {
            this.signalUid = signalUid;
            return this;
        }

        public ConnectionBuilder mode(ConnectionMode mode) {
            this.mode = mode;
            return this;
        }

        public ConnectionBuilder signalAuthor(String signalAuthor) {
            this.signalAuthor = signalAuthor;
            return this;
        }

        public ConnectionBuilder appName(String appName) {
            this.appName = appName;
            return this;
        }

        public ConnectionBuilder updatedAt(OffsetDateTime updatedAt) {
            this.updatedAt = updatedAt;
            return this;
        }

        public ConnectionBuilder signalSwitchType(SwitchType signalSwitchType) {
            this.signalSwitchType = signalSwitchType;
            return this;
        }

        public ConnectionBuilder additionalData(String additionalData) {
            this.additionalData = additionalData;
            return this;
        }

        public AppConnection build() {
            return new AppConnection(
                    this.appUid,
                    this.signalUid,
                    this.mode,
                    this.signalAuthor,
                    this.appName,
                    this.updatedAt,
                    this.signalSwitchType,
                    this.additionalData
            );
        }
    }

    @Override
    public String toString() {
        return "Connection{" +
                "appUid=" + appUid +
                ", signalUid=" + signalUid +
                ", mode=" + mode +
                ", signalAuthor='" + signalAuthor + '\'' +
                ", appName='" + appName + '\'' +
                ", updatedAt=" + updatedAt +
                ", signalSwitchType=" + signalSwitchType +
                ", additionalData='" + additionalData + '\'' +
                '}';
    }
}