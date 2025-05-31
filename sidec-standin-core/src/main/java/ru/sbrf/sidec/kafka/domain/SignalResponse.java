package ru.sbrf.sidec.kafka.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

public class SignalResponse {
    private UUID uid;
    @JsonProperty("mode")
    private SignalMode signalMode;
    private String author;
    private String description;
    private SignalStatus status;
    @JsonProperty("switch_type")
    private SwitchType switchType;

    public SignalResponse() {
    }

    public SignalResponse(UUID uid, SignalMode signalMode, String author, String description, SignalStatus status, SwitchType switchType) {
        this.uid = uid;
        this.signalMode = signalMode;
        this.author = author;
        this.description = description;
        this.status = status;
        this.switchType = switchType;
    }

    public UUID getUid() {
        return uid;
    }

    public void setUid(UUID uid) {
        this.uid = uid;
    }

    public SignalMode getMode() {
        return signalMode;
    }

    public void setMode(SignalMode signalMode) {
        this.signalMode = signalMode;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public SignalStatus getStatus() {
        return status;
    }

    public void setStatus(SignalStatus status) {
        this.status = status;
    }

    public SwitchType getSwitchType() {
        return switchType;
    }

    public void setSwitchType(SwitchType switchType) {
        this.switchType = switchType;
    }

    @Override
    public String toString() {
        return "Signal{" +
                "uid=" + uid +
                ", signalMode=" + signalMode +
                ", author='" + author + '\'' +
                ", description='" + description + '\'' +
                ", status=" + status +
                ", switchType=" + switchType +
                '}';
    }
}