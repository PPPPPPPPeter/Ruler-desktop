package com.example.rulerfrontendj.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class BiPartiteGraph {
    private String leftColumnName;
    private String rightColumnName;
    private int totalConnections;
    private List<BiPartiteLink> links;
    private Map<String, List<ConnectionDetail>> connectionDetails;

    public BiPartiteGraph() {
        this.links = new ArrayList<>();
        this.connectionDetails = new LinkedHashMap<>();
    }

    // 内部类
    public static class BiPartiteLink {
        private String leftBin;
        private String rightBin;
        private int weight;
        private double normalizedWeight;
        private int visualWeight;
        private double percentage;

        public BiPartiteLink() {}

        // Getters and Setters
        public String getLeftBin() { return leftBin; }
        public void setLeftBin(String leftBin) { this.leftBin = leftBin; }
        public String getRightBin() { return rightBin; }
        public void setRightBin(String rightBin) { this.rightBin = rightBin; }
        public int getWeight() { return weight; }
        public void setWeight(int weight) { this.weight = weight; }
        public double getNormalizedWeight() { return normalizedWeight; }
        public void setNormalizedWeight(double normalizedWeight) { this.normalizedWeight = normalizedWeight; }
        public int getVisualWeight() { return visualWeight; }
        public void setVisualWeight(int visualWeight) { this.visualWeight = visualWeight; }
        public double getPercentage() { return percentage; }
        public void setPercentage(double percentage) { this.percentage = percentage; }
    }

    public static class ConnectionDetail {
        private int rowIndex;
        private String leftOriginalValue;
        private String rightOriginalValue;
        private String leftBin;
        private String rightBin;

        public ConnectionDetail() {}

        // Getters and Setters
        public int getRowIndex() { return rowIndex; }
        public void setRowIndex(int rowIndex) { this.rowIndex = rowIndex; }
        public String getLeftOriginalValue() { return leftOriginalValue; }
        public void setLeftOriginalValue(String leftOriginalValue) { this.leftOriginalValue = leftOriginalValue; }
        public String getRightOriginalValue() { return rightOriginalValue; }
        public void setRightOriginalValue(String rightOriginalValue) { this.rightOriginalValue = rightOriginalValue; }
        public String getLeftBin() { return leftBin; }
        public void setLeftBin(String leftBin) { this.leftBin = leftBin; }
        public String getRightBin() { return rightBin; }
        public void setRightBin(String rightBin) { this.rightBin = rightBin; }
    }

    // Getters and Setters
    public String getLeftColumnName() { return leftColumnName; }
    public void setLeftColumnName(String leftColumnName) { this.leftColumnName = leftColumnName; }
    public String getRightColumnName() { return rightColumnName; }
    public void setRightColumnName(String rightColumnName) { this.rightColumnName = rightColumnName; }
    public int getTotalConnections() { return totalConnections; }
    public void setTotalConnections(int totalConnections) { this.totalConnections = totalConnections; }
    public List<BiPartiteLink> getLinks() { return links; }
    public void setLinks(List<BiPartiteLink> links) { this.links = links; }
    public Map<String, List<ConnectionDetail>> getConnectionDetails() { return connectionDetails; }
    public void setConnectionDetails(Map<String, List<ConnectionDetail>> connectionDetails) { this.connectionDetails = connectionDetails; }
}
