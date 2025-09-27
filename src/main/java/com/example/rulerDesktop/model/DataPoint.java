package com.example.rulerDesktop.model;

public class DataPoint {
    private String value;
    private int rowIndex;
    private int columnIndex;

    public DataPoint() {}

    public DataPoint(String value, int rowIndex, int columnIndex) {
        this.value = value;
        this.rowIndex = rowIndex;
        this.columnIndex = columnIndex;
    }

    // Getters and Setters
    public String getValue() { return value; }
    public void setValue(String value) { this.value = value; }
    public int getRowIndex() { return rowIndex; }
    public void setRowIndex(int rowIndex) { this.rowIndex = rowIndex; }
    public int getColumnIndex() { return columnIndex; }
    public void setColumnIndex(int columnIndex) { this.columnIndex = columnIndex; }
}
