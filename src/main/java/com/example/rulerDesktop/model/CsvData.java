package com.example.rulerDesktop.model;

import java.util.List;
import java.util.Map;

public class CsvData {
    private String fileName;
    private List<String> headers;
    private List<Map<String, String>> rows;
    private int totalRows;
    private int totalColumns;

    public CsvData() {}

    public CsvData(String fileName, List<String> headers, List<Map<String, String>> rows) {
        this.fileName = fileName;
        this.headers = headers;
        this.rows = rows;
        this.totalRows = rows.size();
        this.totalColumns = headers.size();
    }

    // Getters and Setters
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public List<String> getHeaders() { return headers; }
    public void setHeaders(List<String> headers) { this.headers = headers; }
    public List<Map<String, String>> getRows() { return rows; }
    public void setRows(List<Map<String, String>> rows) { this.rows = rows; }
    public int getTotalRows() { return totalRows; }
    public void setTotalRows(int totalRows) { this.totalRows = totalRows; }
    public int getTotalColumns() { return totalColumns; }
    public void setTotalColumns(int totalColumns) { this.totalColumns = totalColumns; }
}
