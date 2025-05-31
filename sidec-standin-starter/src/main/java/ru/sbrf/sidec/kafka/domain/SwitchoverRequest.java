package ru.sbrf.sidec.kafka.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

public class SwitchoverRequest {
    private UUID uid;
    private SignalMode mode;
    private String author;
    private String description;
    @JsonProperty("switch_type")
    private SwitchType switchType;

    public SwitchoverRequest(UUID uid, SignalMode mode, String author, String description, SwitchType switchType) {
        this.uid = uid;
        this.mode = mode;
        this.author = author;
        this.description = description;
        this.switchType = switchType;
    }

    public SwitchoverRequest() {
    }

    public UUID getUid() {
        return uid;
    }

    public void setUid(UUID uid) {
        this.uid = uid;
    }

    public SignalMode getMode() {
        return mode;
    }

    public void setMode(SignalMode mode) {
        this.mode = mode;
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

    public SwitchType getSwitchType() {
        return switchType;
    }

    public void setSwitchType(SwitchType switchType) {
        this.switchType = switchType;
    }

    @Override
    public String toString() {
        return "SwitchoverRequest{" +
                "uid=" + uid +
                ", mode=" + mode +
                ", author='" + author + '\'' +
                ", description='" + description + '\'' +
                ", switchType=" + switchType +
                '}';
    }
}