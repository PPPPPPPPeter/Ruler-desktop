package com.example.rulerDesktop.service;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 数据标准化服务类（改进版）
 * 负责数据清洗、标准化和类型检测
 */
public class DataNormalizationService {

    private static final Pattern NUMERIC_PATTERN = Pattern.compile("^-?\\d*\\.?\\d+([eE][-+]?\\d+)?$");
    private static final String NULL_VALUE = "<NULL>";
    private static final String EMPTY_VALUE = "<EMPTY>";

    // 数字格式化器（线程安全）
    private static final ThreadLocal<DecimalFormat> DECIMAL_FORMAT =
            ThreadLocal.withInitial(() -> {
                DecimalFormat df = new DecimalFormat("#.##",
                        DecimalFormatSymbols.getInstance(Locale.US));
                df.setMinimumFractionDigits(0);
                df.setMaximumFractionDigits(6);
                df.setGroupingUsed(false);
                return df;
            });

    /**
     * 区间类型枚举
     */
    public enum IntervalType {
        CLOSED,      // [a, b] 闭区间
        OPEN,        // (a, b) 开区间
        LEFT_OPEN,   // (a, b] 左开右闭
        RIGHT_OPEN   // [a, b) 左闭右开（常用于分箱）
    }

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
                lower.equals("none") || lower.equals("undefined") || lower.equals("-") ||
                lower.equals("nan") || lower.equals("inf") || lower.equals("infinity")) {
            return NULL_VALUE;
        }

        return trimmed;
    }

    /**
     * 检测列是否为数值类型
     * 改进：提高判断准确性
     */
    public boolean isNumericColumn(List<String> values) {
        if (values == null || values.isEmpty()) {
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
     * 改进：支持科学计数法
     */
    public boolean isNumericValue(String value) {
        if (value == null || value.equals(NULL_VALUE) || value.equals(EMPTY_VALUE)) {
            return false;
        }

        // 处理特殊数值
        String lower = value.toLowerCase();
        if (lower.equals("nan") || lower.equals("inf") || lower.equals("infinity") ||
                lower.equals("-inf") || lower.equals("-infinity")) {
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
     * 为数值范围创建标签（简单版，兼容旧代码）
     */
    public String createNumericRangeLabel(List<String> values) {
        return createNumericRangeLabel(values, IntervalType.RIGHT_OPEN);
    }

    /**
     * 为数值范围创建标签（增强版，支持区间类型）
     *
     * @param values 值列表
     * @param intervalType 区间类型
     * @return 格式化的区间标签，如 "[29.0, 66.2)" 或 "29-66.2"
     */
    public String createNumericRangeLabel(List<String> values, IntervalType intervalType) {
        if (values == null || values.isEmpty()) {
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
            return formatInterval(min, max, intervalType);
        }
    }

    /**
     * 格式化区间
     *
     * @param min 最小值
     * @param max 最大值
     * @param intervalType 区间类型
     * @return 格式化的区间字符串
     */
    public String formatInterval(double min, double max, IntervalType intervalType) {
        String minStr = formatNumber(min);
        String maxStr = formatNumber(max);

        if (min == max) {
            return minStr;
        }

        switch (intervalType) {
            case CLOSED:
                return String.format("[%s, %s]", minStr, maxStr);
            case OPEN:
                return String.format("(%s, %s)", minStr, maxStr);
            case LEFT_OPEN:
                return String.format("(%s, %s]", minStr, maxStr);
            case RIGHT_OPEN:
                return String.format("[%s, %s)", minStr, maxStr);
            default:
                // 默认使用简单格式（向后兼容）
                return minStr + "-" + maxStr;
        }
    }

    /**
     * 创建简单的区间标签（不带括号，用于向后兼容）
     */
    public String createSimpleRangeLabel(double min, double max) {
        String minStr = formatNumber(min);
        String maxStr = formatNumber(max);

        if (min == max) {
            return minStr;
        }

        return minStr + "-" + maxStr;
    }

    /**
     * 格式化数字显示（改进版）
     * 改进点：
     * 1. 使用 DecimalFormat 更专业
     * 2. 自动处理小数位数
     * 3. 支持科学计数法的大数
     */
    public String formatNumber(double number) {
        // 处理特殊值
        if (Double.isNaN(number)) return "NaN";
        if (Double.isInfinite(number)) return number > 0 ? "∞" : "-∞";

        // 如果是整数
        if (number == Math.floor(number) && !Double.isInfinite(number)) {
            // 如果数字太大，使用科学计数法
            if (Math.abs(number) > 1e10) {
                return String.format("%.2e", number);
            }
            return String.valueOf((long) number);
        }

        // 小数的情况
        if (Math.abs(number) < 0.01 && number != 0) {
            // 非常小的数，使用科学计数法
            return String.format("%.2e", number);
        }

        // 使用 DecimalFormat
        String formatted = DECIMAL_FORMAT.get().format(number);

        // 去掉不必要的尾随零
        if (formatted.contains(".")) {
            formatted = formatted.replaceAll("0+$", "").replaceAll("\\.$", "");
        }

        return formatted;
    }

    /**
     * 解析数值字符串为 Double
     */
    public Double parseNumericValue(String value) {
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
     * 获取数值的排序键（用于排序）
     */
    public Double getNumericSortKey(String value) {
        return parseNumericValue(value);
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
     * 判断一个值是否在指定范围内
     */
    public boolean isValueInRange(String value, double min, double max, IntervalType intervalType) {
        Double numValue = parseNumericValue(value);
        if (numValue == null) {
            return false;
        }

        switch (intervalType) {
            case CLOSED:
                return numValue >= min && numValue <= max;
            case OPEN:
                return numValue > min && numValue < max;
            case LEFT_OPEN:
                return numValue > min && numValue <= max;
            case RIGHT_OPEN:
                return numValue >= min && numValue < max;
            default:
                return numValue >= min && numValue <= max;
        }
    }

    /**
     * 计算数值的精度（小数位数）
     */
    public int getDecimalPrecision(String value) {
        if (!isNumericValue(value)) {
            return 0;
        }

        String[] parts = value.split("\\.");
        if (parts.length < 2) {
            return 0;
        }

        return parts[1].length();
    }

    /**
     * 获取列的推荐精度（根据所有值）
     */
    public int getRecommendedPrecision(List<String> values) {
        int maxPrecision = 0;

        for (String value : values) {
            if (isNumericValue(value)) {
                int precision = getDecimalPrecision(value);
                maxPrecision = Math.max(maxPrecision, precision);
            }
        }

        // 限制最大精度为6位小数
        return Math.min(maxPrecision, 6);
    }

    /**
     * 根据推荐精度格式化数字
     */
    public String formatNumberWithPrecision(double number, int precision) {
        if (precision == 0) {
            return String.valueOf((long) number);
        }

        String format = "%." + precision + "f";
        return String.format(format, number)
                .replaceAll("0+$", "")
                .replaceAll("\\.$", "");
    }

    /**
     * 获取常量值（供外部使用）
     */
    public String getNullValue() {
        return NULL_VALUE;
    }

    public String getEmptyValue() {
        return EMPTY_VALUE;
    }

    /**
     * 值类型枚举
     */
    public enum ValueType {
        NULL, EMPTY, NUMERIC, TEXT
    }
}