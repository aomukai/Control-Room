package com.miniide.models;

import java.util.ArrayList;
import java.util.List;

public class TelemetryIndex {
    private List<TelemetrySessionInfo> sessions = new ArrayList<>();

    public List<TelemetrySessionInfo> getSessions() {
        return sessions;
    }

    public void setSessions(List<TelemetrySessionInfo> sessions) {
        this.sessions = sessions != null ? sessions : new ArrayList<>();
    }
}
