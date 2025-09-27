package com.example.rulerDesktop.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class Histogram {
    private String columnName;
    private int binCount;
    private int actualBinCount;
    private int totalRecords;
    private List<String> orderedValues;
    private Map<String, Integer> valueFrequency;
    private Map<String, List<DataPoint>> binDetails;
    private List<String> originalValues;
    private Map<String, String> valueToBinMapping;

    public Histogram() {
        this.orderedValues = new ArrayList<>();
        this.valueFrequency = new LinkedHashMap<>();
        this.binDetails = new LinkedHashMap<>();
        this.originalValues = new ArrayList<>();
        this.valueToBinMapping = new LinkedHashMap<>();
    }

    // Getters and Setters
    public String getColumnName() { return columnName; }
    public void setColumnName(String columnName) { this.columnName = columnName; }
    public int getBinCount() { return binCount; }
    public void setBinCount(int binCount) { this.binCount = binCount; }
    public int getActualBinCount() { return actualBinCount; }
    public void setActualBinCount(int actualBinCount) { this.actualBinCount = actualBinCount; }
    public int getTotalRecords() { return totalRecords; }
    public void setTotalRecords(int totalRecords) { this.totalRecords = totalRecords; }
    public List<String> getOrderedValues() { return orderedValues; }
    public void setOrderedValues(List<String> orderedValues) { this.orderedValues = orderedValues; }
    public Map<String, Integer> getValueFrequency() { return valueFrequency; }
    public void setValueFrequency(Map<String, Integer> valueFrequency) { this.valueFrequency = valueFrequency; }
    public Map<String, List<DataPoint>> getBinDetails() { return binDetails; }
    public void setBinDetails(Map<String, List<DataPoint>> binDetails) { this.binDetails = binDetails; }
    public List<String> getOriginalValues() { return originalValues; }
    public void setOriginalValues(List<String> originalValues) { this.originalValues = originalValues; }
    public Map<String, String> getValueToBinMapping() { return valueToBinMapping; }
    public void setValueToBinMapping(Map<String, String> valueToBinMapping) { this.valueToBinMapping = valueToBinMapping; }
}
