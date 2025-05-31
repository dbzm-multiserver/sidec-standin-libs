package ru.sbrf.sidec.kafka.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.OffsetDateTime;
import java.util.UUID;

public class SignalRequest extends SignalResponse {
    @JsonProperty("created_at")
    private OffsetDateTime createdAt;
    public SignalRequest() {
    }

    public SignalRequest(UUID uid, SignalMode signalMode, String author, String description, SignalStatus status, OffsetDateTime createdAt, SwitchType switchType) {
        super(uid, signalMode, author, description, status,switchType);
        this.createdAt = createdAt;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public String toString() {
        return "Signal{" +
                "uid=" + getUid() +
                ", signalMode=" + getMode() +
                ", author='" + getAuthor() + '\'' +
                ", description='" + getDescription() + '\'' +
                ", status=" + getStatus() +
                ", createdAt=" + createdAt +
                ", switchType=" + getSwitchType() +
                '}';
    }
}