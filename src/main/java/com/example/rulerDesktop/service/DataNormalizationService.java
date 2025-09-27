package com.example.rulerDesktop.service;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 数据标准化服务类
 * 负责数据清洗、标准化和类型检测
 */
public class DataNormalizationService {

    private static final Pattern NUMERIC_PATTERN = Pattern.compile("^-?\\d*\\.?\\d+$");
    private static final String NULL_VALUE = "<NULL>";
    private static final String EMPTY_VALUE = "<EMPTY>";

    /**
     * 标准化单个值
     */
    public String normalizeValue(String value) {
        if (value == null) {
            return NULL_VALUE;
        }

        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return EMPTY_VALUE;
        }

        // 处理常见的null值表示
        String lower = trimmed.toLowerCase();
        if (lower.equals("null") || lower.equals("n/a") || lower.equals("na") ||
                lower.equals("none") || lower.equals("undefined") || lower.equals("-")) {
            return NULL_VALUE;
        }

        return trimmed;
    }

    /**
     * 检测列是否为数值类型
     */
    public boolean isNumericColumn(List<String> values) {
        if (values.isEmpty()) {
            return false;
        }

        int numericCount = 0;
        int validValueCount = 0;

        for (String value : values) {
            if (!value.equals(NULL_VALUE) && !value.equals(EMPTY_VALUE)) {
                validValueCount++;
                if (isNumericValue(value)) {
                    numericCount++;
                }
            }
        }

        // 如果有效值中80%以上是数值，则认为是数值列
        return validValueCount > 0 && (double) numericCount / validValueCount >= 0.8;
    }

    /**
     * 检测单个值是否为数值
     */
    public boolean isNumericValue(String value) {
        if (value == null || value.equals(NULL_VALUE) || value.equals(EMPTY_VALUE)) {
            return false;
        }

        return NUMERIC_PATTERN.matcher(value).matches();
    }

    /**
     * 比较两个数值字符串
     */
    public int compareNumericValues(String value1, String value2) {
        // 处理特殊值
        if (value1.equals(NULL_VALUE) || value1.equals(EMPTY_VALUE)) {
            return value2.equals(NULL_VALUE) || value2.equals(EMPTY_VALUE) ? 0 : -1;
        }
        if (value2.equals(NULL_VALUE) || value2.equals(EMPTY_VALUE)) {
            return 1;
        }

        try {
            double num1 = Double.parseDouble(value1);
            double num2 = Double.parseDouble(value2);
            return Double.compare(num1, num2);
        } catch (NumberFormatException e) {
            // 如果不是数值，按字符串比较
            return value1.compareTo(value2);
        }
    }

    /**
     * 为数值范围创建标签
     */
    public String createNumericRangeLabel(List<String> values) {
        if (values.isEmpty()) {
            return "Empty";
        }

        if (values.size() == 1) {
            String value = values.get(0);
            if (value.equals(NULL_VALUE) || value.equals(EMPTY_VALUE)) {
                return value;
            }
            return value;
        }

        // 过滤出数值
        List<Double> numericValues = values.stream()
                .filter(this::isNumericValue)
                .map(Double::parseDouble)
                .sorted()
                .toList();

        if (numericValues.isEmpty()) {
            // 如果没有数值，使用第一个值作为标签
            return values.get(0);
        }

        double min = numericValues.get(0);
        double max = numericValues.get(numericValues.size() - 1);

        // 格式化数值范围
        if (min == max) {
            return formatNumber(min);
        } else {
            return formatNumber(min) + "-" + formatNumber(max);
        }
    }

    /**
     * 格式化数字显示
     */
    private String formatNumber(double number) {
        // 如果是整数，不显示小数点
        if (number == Math.floor(number)) {
            return String.valueOf((long) number);
        } else {
            // 保留2位小数，去掉末尾的0
            return String.format("%.2f", number).replaceAll("0*$", "").replaceAll("\\.$", "");
        }
    }

    /**
     * 获取数值的排序键（用于排序）
     */
    public Double getNumericSortKey(String value) {
        if (!isNumericValue(value)) {
            return null;
        }

        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 清理和标准化字符串值
     */
    public String cleanStringValue(String value) {
        if (value == null) {
            return NULL_VALUE;
        }

        String cleaned = value.trim();
        if (cleaned.isEmpty()) {
            return EMPTY_VALUE;
        }

        // 移除多余的空格
        cleaned = cleaned.replaceAll("\\s+", " ");

        return cleaned;
    }

    /**
     * 检测值的类型
     */
    public ValueType detectValueType(String value) {
        String normalized = normalizeValue(value);

        if (normalized.equals(NULL_VALUE)) {
            return ValueType.NULL;
        }
        if (normalized.equals(EMPTY_VALUE)) {
            return ValueType.EMPTY;
        }
        if (isNumericValue(normalized)) {
            return ValueType.NUMERIC;
        }

        return ValueType.TEXT;
    }

    /**
     * 值类型枚举
     */
    public enum ValueType {
        NULL, EMPTY, NUMERIC, TEXT
    }
}

