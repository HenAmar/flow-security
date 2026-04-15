package com.flowsecurity.model;

public class Alert {

    public enum Severity {
        MEDIUM, HIGH
    }

    private final Severity severity;
    private final String source;
    private final String destination;
    private final String dataType;
    private final long timestamp;

    public Alert(Severity severity, String source, String destination, String dataType) {
        this.severity = severity;
        this.source = source;
        this.destination = destination;
        this.dataType = dataType;
        this.timestamp = System.currentTimeMillis();
    }

    public Severity getSeverity() { return severity; }
    public String getSource() { return source; }
    public String getDestination() { return destination; }
    public String getDataType() { return dataType; }
    public long getTimestamp() { return timestamp; }

    @Override
    public String toString() {
        return "[ALERT][" + severity + "] " + dataType + ": " + source + " → " + destination;
    }
}
