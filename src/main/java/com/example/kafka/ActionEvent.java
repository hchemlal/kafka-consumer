package com.example.kafka;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.Instant;

public class ActionEvent {
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX", timezone = "America/New_York")
    private String timestamp;

    public ActionEvent() {
    }

    public ActionEvent(String timestamp) {
        this.timestamp = timestamp;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }
}
