package com.example.rulerfrontendj.service;

public class ApiConfig {
    public static final String BASE_URL = "http://localhost:3000"; // 根据你的后端端口调整
    public static final String CSV_DATA_ENDPOINT = "/api/csv_data";
    public static final String HISTOGRAM_ENDPOINT = "/api/histogram";
    public static final String MATRIX_ENDPOINT = "/api/matrix";

    // 完整的API路径
    public static final String CSV_UPLOAD_URL = BASE_URL + CSV_DATA_ENDPOINT + "/upload";
    public static final String HISTOGRAM_GENERATE_ALL_URL = BASE_URL + HISTOGRAM_ENDPOINT + "/generate-all";
    public static final String MATRIX_GENERATE_ALL_URL = BASE_URL + MATRIX_ENDPOINT + "/generate-all";
}

