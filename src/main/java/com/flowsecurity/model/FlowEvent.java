package com.flowsecurity.model;

import java.util.Map;

public class FlowEvent {

    private String date;
    private String source;
    private String destination;
    private Map<String, String> values;

    public FlowEvent() {}

    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public String getDestination() { return destination; }
    public void setDestination(String destination) { this.destination = destination; }

    public Map<String, String> getValues() { return values; }
    public void setValues(Map<String, String> values) { this.values = values; }

    @Override
    public String toString() {
        return "FlowEvent{source='" + source + "', destination='" + destination + "', values=" + values + "}";
    }
}
