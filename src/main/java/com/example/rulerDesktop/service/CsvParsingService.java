package com.example.rulerDesktop.service;

import com.example.rulerDesktop.model.CsvData;
import com.example.rulerDesktop.model.DataPoint;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.regex.Pattern;

/**
 * CSV文件解析服务类
 * 负责将CSV文件转换为Java易于处理的数据结构
 * 也可以导出数据到csv文件中 :)
 */
public class CsvParsingService {

    private static final String DEFAULT_DELIMITER = ",";
    private static final String QUOTE_CHAR = "\"";
    private static final Pattern CSV_PATTERN = Pattern.compile(
            "(?:^|,)\\s*(?:\"([^\"]*(?:\"\"[^\"]*)*)\"|([^\",]*))"
    );

    /**
     * 从文件路径加载并解析CSV
     */
    public CsvData loadAndAnalyzeCsv(String filePath) throws IOException {
        return loadAndAnalyzeCsv(new File(filePath));
    }

    /**
     * 从File对象加载并解析CSV
     */
    public CsvData loadAndAnalyzeCsv(File csvFile) throws IOException {
        if (!csvFile.exists()) {
            throw new FileNotFoundException("CSV文件不存在: " + csvFile.getAbsolutePath());
        }

        if (!csvFile.getName().toLowerCase().endsWith(".csv")) {
            throw new IllegalArgumentException("文件必须是CSV格式");
        }

        try (InputStream inputStream = Files.newInputStream(csvFile.toPath())) {
            CsvData result = loadAndAnalyzeCsv(inputStream);
            result.setFileName(csvFile.getName());
            return result;
        }
    }

    /**
     * 从InputStream加载并解析CSV
     */
    public CsvData loadAndAnalyzeCsv(InputStream inputStream) throws IOException {
        // 尝试检测编码
        String content = readStreamWithEncoding(inputStream);
        String[] lines = content.split("\r?\n");

        if (lines.length == 0) {
            throw new IllegalArgumentException("CSV文件为空");
        }

        // 解析CSV数据
        List<List<String>> allRows = new ArrayList<>();
        for (String line : lines) {
            if (!line.trim().isEmpty()) {
                List<String> row = parseCsvLine(line);
                allRows.add(row);
            }
        }

        if (allRows.isEmpty()) {
            throw new IllegalArgumentException("CSV文件没有有效数据");
        }

        // 构建CsvData对象
        return buildCsvData(allRows);
    }

    /**
     * 读取输入流并尝试检测编码
     */
    private String readStreamWithEncoding(InputStream inputStream) throws IOException {
        byte[] bytes;
        try (ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
            byte[] data = new byte[1024];
            int nRead;
            while ((nRead = inputStream.read(data, 0, data.length)) != -1) {
                buffer.write(data, 0, nRead);
            }
            bytes = buffer.toByteArray();
        }

        // 检测BOM
        if (bytes.length >= 3 &&
                bytes[0] == (byte) 0xEF &&
                bytes[1] == (byte) 0xBB &&
                bytes[2] == (byte) 0xBF) {
            // UTF-8 BOM
            return new String(bytes, 3, bytes.length - 3, StandardCharsets.UTF_8);
        }

        // 尝试不同编码
        String[] encodings = {"UTF-8", "GBK", "GB2312", "ISO-8859-1"};

        for (String encoding : encodings) {
            try {
                String content = new String(bytes, encoding);
                // 简单检测：如果没有乱码字符，就使用这个编码
                if (isValidEncoding(content)) {
                    return content;
                }
            } catch (Exception e) {
                // 继续尝试下一个编码
            }
        }

        // 默认使用UTF-8
        return new String(bytes, StandardCharsets.UTF_8);
    }

    /**
     * 简单检测编码是否有效
     */
    private boolean isValidEncoding(String content) {
        // 检测是否包含常见的乱码字符
        return !content.contains("�") &&
                !content.contains("锘�") &&
                content.length() > 0;
    }

    /**
     * 解析CSV行
     */
    private List<String> parseCsvLine(String line) {
        List<String> result = new ArrayList<>();

        // 处理空行
        if (line.trim().isEmpty()) {
            return result;
        }

        // 简单的CSV解析（处理引号和逗号）
        boolean inQuotes = false;
        StringBuilder currentField = new StringBuilder();

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);

            if (c == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    // 转义的引号
                    currentField.append('"');
                    i++; // 跳过下一个引号
                } else {
                    // 切换引号状态
                    inQuotes = !inQuotes;
                }
            } else if (c == ',' && !inQuotes) {
                // 字段分隔符
                result.add(currentField.toString().trim());
                currentField.setLength(0);
            } else {
                currentField.append(c);
            }
        }

        // 添加最后一个字段
        result.add(currentField.toString().trim());

        return result;
    }

    /**
     * 构建CsvData对象
     */
    private CsvData buildCsvData(List<List<String>> allRows) {
        if (allRows.isEmpty()) {
            throw new IllegalArgumentException("没有数据行");
        }

        // 第一行作为标题
        List<String> headers = new ArrayList<>(allRows.get(0));

        // 清理标题（移除引号，处理空标题和同名列）
        Map<String, Integer> headerCount = new HashMap<>();
        for (int i = 0; i < headers.size(); i++) {
            String header = headers.get(i);
            if (header.isEmpty()) {
                header = "Column_" + (i + 1);
            }
            header = cleanField(header);

            // 处理同名列：如果列名已存在，添加后缀
            String originalHeader = header;
            int count = headerCount.getOrDefault(originalHeader, 0);
            if (count > 0) {
                header = originalHeader + "_" + count;
            }
            headerCount.put(originalHeader, count + 1);

            headers.set(i, header);
        }

        // 处理数据行
        List<Map<String, String>> rows = new ArrayList<>();

        for (int rowIndex = 1; rowIndex < allRows.size(); rowIndex++) {
            List<String> rowData = allRows.get(rowIndex);

            // 跳过空行
            if (rowData.stream().allMatch(String::isEmpty)) {
                continue;
            }

            Map<String, String> rowMap = new LinkedHashMap<>();

            for (int colIndex = 0; colIndex < headers.size(); colIndex++) {
                String header = headers.get(colIndex);
                String value = "";

                if (colIndex < rowData.size()) {
                    value = cleanField(rowData.get(colIndex));
                }

                rowMap.put(header, value);
            }

            rows.add(rowMap);
        }

        // 创建CsvData对象
        CsvData csvData = new CsvData();
        csvData.setHeaders(headers);
        csvData.setRows(rows);
        csvData.setTotalRows(rows.size());
        csvData.setTotalColumns(headers.size());

        return csvData;
    }

    /**
     * 清理字段内容（移除引号，处理转义字符）
     */
    private String cleanField(String field) {
        if (field == null) {
            return "";
        }

        String cleaned = field.trim();

        // 移除首尾引号
        if (cleaned.length() >= 2 &&
                cleaned.startsWith("\"") &&
                cleaned.endsWith("\"")) {
            cleaned = cleaned.substring(1, cleaned.length() - 1);
            // 处理转义的引号
            cleaned = cleaned.replace("\"\"", "\"");
        }

        return cleaned;
    }

    /**
     * 获取指定列的所有数据点
     */
    public List<DataPoint> getColumnDataPoints(CsvData csvData, String columnName) {
        List<DataPoint> dataPoints = new ArrayList<>();

        if (!csvData.getHeaders().contains(columnName)) {
            throw new IllegalArgumentException("列不存在: " + columnName);
        }

        int columnIndex = csvData.getHeaders().indexOf(columnName);

        for (int rowIndex = 0; rowIndex < csvData.getRows().size(); rowIndex++) {
            Map<String, String> row = csvData.getRows().get(rowIndex);
            String value = row.get(columnName);

            if (value != null && !value.trim().isEmpty()) {
                dataPoints.add(new DataPoint(value.trim(), rowIndex, columnIndex));
            }
        }

        return dataPoints;
    }

    /**
     * 获取CSV数据的基本统计信息
     */
    public Map<String, Object> getDataSummary(CsvData csvData) {
        Map<String, Object> summary = new HashMap<>();

        summary.put("fileName", csvData.getFileName());
        summary.put("totalRows", csvData.getTotalRows());
        summary.put("totalColumns", csvData.getTotalColumns());
        summary.put("headers", csvData.getHeaders());

        // 每列的数据类型检测
        Map<String, String> columnTypes = new HashMap<>();
        Map<String, Integer> nonEmptyCount = new HashMap<>();

        for (String header : csvData.getHeaders()) {
            List<DataPoint> dataPoints = getColumnDataPoints(csvData, header);
            nonEmptyCount.put(header, dataPoints.size());
            columnTypes.put(header, detectColumnType(dataPoints));
        }

        summary.put("columnTypes", columnTypes);
        summary.put("nonEmptyCount", nonEmptyCount);

        return summary;
    }

    /**
     * 检测列的数据类型
     */
    private String detectColumnType(List<DataPoint> dataPoints) {
        if (dataPoints.isEmpty()) {
            return "EMPTY";
        }

        int numericCount = 0;
        int dateCount = 0;
        int totalCount = dataPoints.size();

        for (DataPoint dp : dataPoints) {
            String value = dp.getValue().trim();

            if (isNumeric(value)) {
                numericCount++;
            }

            if (isDate(value)) {
                dateCount++;
            }
        }

        // 如果80%以上是数字，认为是数值型
        if (numericCount > totalCount * 0.8) {
            return "NUMERIC";
        }

        // 如果80%以上是日期，认为是日期型
        if (dateCount > totalCount * 0.8) {
            return "DATE";
        }

        return "TEXT";
    }

    /**
     * 检测是否为数值
     */
    private boolean isNumeric(String str) {
        try {
            Double.parseDouble(str);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * 检测是否为日期
     */
    private boolean isDate(String str) {
        // 简单的日期格式检测
        String[] datePatterns = {
                "\\d{4}-\\d{2}-\\d{2}",           // 2023-12-25
                "\\d{2}/\\d{2}/\\d{4}",           // 12/25/2023
                "\\d{4}/\\d{2}/\\d{2}",           // 2023/12/25
                "\\d{2}-\\d{2}-\\d{4}",           // 25-12-2023
        };

        for (String pattern : datePatterns) {
            if (str.matches(pattern)) {
                return true;
            }
        }

        return false;
    }

    /**
     * 导出CSV数据到文件
     */
    public void exportCsvData(CsvData csvData, String filePath) throws IOException {
        exportCsvData(csvData, new File(filePath));
    }

    /**
     * 导出CSV数据到文件
     */
    public void exportCsvData(CsvData csvData, File file) throws IOException {
        try (PrintWriter writer = new PrintWriter(
                new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8))) {

            // 写入BOM以确保Excel正确识别UTF-8
            writer.write('\ufeff');

            // 写入标题行
            writer.println(String.join(",",
                    csvData.getHeaders().stream()
                            .map(this::escapeCsvField)
                            .toArray(String[]::new)));

            // 写入数据行
            for (Map<String, String> row : csvData.getRows()) {
                List<String> values = new ArrayList<>();
                for (String header : csvData.getHeaders()) {
                    String value = row.getOrDefault(header, "");
                    values.add(escapeCsvField(value));
                }
                writer.println(String.join(",", values));
            }
        }
    }

    /**
     * 转义CSV字段（处理逗号、引号、换行符）
     */
    private String escapeCsvField(String field) {
        if (field == null) {
            return "";
        }

        // 如果包含逗号、引号或换行符，需要用引号包围
        if (field.contains(",") || field.contains("\"") || field.contains("\n") || field.contains("\r")) {
            // 转义内部的引号
            String escaped = field.replace("\"", "\"\"");
            return "\"" + escaped + "\"";
        }

        return field;
    }

    /**
     * 验证CSV数据的完整性
     */
    public boolean validateCsvData(CsvData csvData) {
        if (csvData == null) {
            return false;
        }

        if (csvData.getHeaders() == null || csvData.getHeaders().isEmpty()) {
            return false;
        }

        if (csvData.getRows() == null) {
            return false;
        }

        // 检查每行数据的列数是否与标题一致
        int expectedColumns = csvData.getHeaders().size();
        for (Map<String, String> row : csvData.getRows()) {
            if (row.size() != expectedColumns) {
                return false;
            }

            // 检查是否包含所有标题对应的键
            for (String header : csvData.getHeaders()) {
                if (!row.containsKey(header)) {
                    return false;
                }
            }
        }

        return true;
    }
}
