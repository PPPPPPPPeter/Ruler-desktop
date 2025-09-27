package com.example.rulerDesktop.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class Matrix {
    private String columnName;
    private int binCount;
    private int actualBinCount;
    private List<String> orderedValues;
    private int[][] matrix;
    private int totalSequences;
    private Map<String, List<DataPoint>> binDetails;
    private List<String> originalValues;
    private Map<String, String> valueToBinMapping;

    public Matrix() {
        this.orderedValues = new ArrayList<>();
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
    public List<String> getOrderedValues() { return orderedValues; }
    public void setOrderedValues(List<String> orderedValues) { this.orderedValues = orderedValues; }
    public int[][] getMatrix() { return matrix; }
    public void setMatrix(int[][] matrix) { this.matrix = matrix; }
    public int getTotalSequences() { return totalSequences; }
    public void setTotalSequences(int totalSequences) { this.totalSequences = totalSequences; }
    public Map<String, List<DataPoint>> getBinDetails() { return binDetails; }
    public void setBinDetails(Map<String, List<DataPoint>> binDetails) { this.binDetails = binDetails; }
    public List<String> getOriginalValues() { return originalValues; }
    public void setOriginalValues(List<String> originalValues) { this.originalValues = originalValues; }
    public Map<String, String> getValueToBinMapping() { return valueToBinMapping; }
    public void setValueToBinMapping(Map<String, String> valueToBinMapping) { this.valueToBinMapping = valueToBinMapping; }
}
