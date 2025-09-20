package com.example.rulerfrontendj.service;

import com.example.rulerfrontendj.model.CsvData;
import com.example.rulerfrontendj.model.Histogram;
import com.example.rulerfrontendj.model.BiPartiteGraph;
import com.example.rulerfrontendj.model.Matrix;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import okhttp3.*;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class ApiService {
    private final OkHttpClient client;
    private final ObjectMapper objectMapper;

    public ApiService() {
        this.client = new OkHttpClient.Builder()
                .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                .writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                .build();

        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    /**
     * 上传CSV文件并解析
     */
    public CompletableFuture<CsvData> uploadCsv(File csvFile) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                RequestBody fileBody = RequestBody.create(csvFile, MediaType.parse("text/csv"));
                MultipartBody requestBody = new MultipartBody.Builder()
                        .setType(MultipartBody.FORM)
                        .addFormDataPart("file", csvFile.getName(), fileBody)
                        .build();

                Request request = new Request.Builder()
                        .url(ApiConfig.CSV_UPLOAD_URL)
                        .post(requestBody)
                        .build();

                try (Response response = client.newCall(request).execute()) {
                    if (!response.isSuccessful()) {
                        throw new RuntimeException("CSV上传失败: " + response.code() + " " + response.message());
                    }

                    String responseBody = response.body().string();
                    Map<String, Object> responseMap = objectMapper.readValue(responseBody,
                            new TypeReference<Map<String, Object>>() {});

                    Boolean success = (Boolean) responseMap.get("success");
                    if (success != null && success) {
                        Map<String, Object> dataMap = (Map<String, Object>) responseMap.get("data");
                        return objectMapper.convertValue(dataMap, CsvData.class);
                    } else {
                        String message = (String) responseMap.get("message");
                        throw new RuntimeException("CSV处理失败: " + message);
                    }
                }
            } catch (IOException e) {
                throw new RuntimeException("网络请求失败: " + e.getMessage(), e);
            }
        });
    }

    /**
     * 生成所有直方图和二分图
     */
    public CompletableFuture<Map<String, Object>> generateAllHistogramsAndBiPartiteGraphs(CsvData csvData, int binCount) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String jsonData = objectMapper.writeValueAsString(csvData);
                RequestBody body = RequestBody.create(jsonData, MediaType.parse("application/json"));

                HttpUrl url = HttpUrl.parse(ApiConfig.HISTOGRAM_GENERATE_ALL_URL).newBuilder()
                        .addQueryParameter("binCount", String.valueOf(binCount))
                        .build();

                Request request = new Request.Builder()
                        .url(url)
                        .post(body)
                        .build();

                try (Response response = client.newCall(request).execute()) {
                    if (!response.isSuccessful()) {
                        throw new RuntimeException("直方图生成失败: " + response.code() + " " + response.message());
                    }

                    String responseBody = response.body().string();
                    return objectMapper.readValue(responseBody, new TypeReference<Map<String, Object>>() {});
                }
            } catch (IOException e) {
                throw new RuntimeException("网络请求失败: " + e.getMessage(), e);
            }
        });
    }

    /**
     * 生成所有矩阵
     */
    public CompletableFuture<Map<String, Map<String, Object>>> generateAllMatrices(CsvData csvData, int binCount) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String jsonData = objectMapper.writeValueAsString(csvData);
                RequestBody body = RequestBody.create(jsonData, MediaType.parse("application/json"));

                HttpUrl url = HttpUrl.parse(ApiConfig.MATRIX_GENERATE_ALL_URL).newBuilder()
                        .addQueryParameter("binCount", String.valueOf(binCount))
                        .build();

                Request request = new Request.Builder()
                        .url(url)
                        .post(body)
                        .build();

                try (Response response = client.newCall(request).execute()) {
                    if (!response.isSuccessful()) {
                        throw new RuntimeException("矩阵生成失败: " + response.code() + " " + response.message());
                    }

                    String responseBody = response.body().string();
                    return objectMapper.readValue(responseBody,
                            new TypeReference<Map<String, Map<String, Object>>>() {});
                }
            } catch (IOException e) {
                throw new RuntimeException("网络请求失败: " + e.getMessage(), e);
            }
        });
    }

    /**
     * 关闭客户端资源
     */
    public void shutdown() {
        client.dispatcher().executorService().shutdown();
        client.connectionPool().evictAll();
    }
}
