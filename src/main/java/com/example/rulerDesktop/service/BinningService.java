package com.example.rulerDesktop.service;

import com.example.rulerDesktop.model.DataPoint;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 增强的分箱服务类 - 支持强制精确分箱
 *
 * 核心特性：
 * 1. 支持1-50箱的强制精确分箱
 * 2. 自动处理空值（NULL/EMPTY）
 * 3. 支持数值型和分类型数据
 * 4. 分箱数量自动调整以不超过实际数据量
 */
public class BinningService {

    private final DataNormalizationService dataNormalizationService;

    // 分箱配置常量
    public static final int MIN_BIN_COUNT = 2;
    public static final int MAX_BIN_COUNT = 50;
    public static final int DEFAULT_BIN_COUNT = 10;

    /**
     * 分箱策略枚举
     */
    public enum BinningStrategy {
        EQUAL_FREQUENCY,  // 等频分箱（数值）
        EQUAL_WIDTH,      // 等宽分箱（数值）
        NATURAL_BREAKS,   // 自然断点（数值）
        STURGES,          // Sturges规则（数值）
        TOP_K,            // Top-K分组（分类）
        FREQUENCY_THRESHOLD, // 频次阈值分组（分类）
        ALPHABETICAL,     // 字母顺序分组（分类）
        AUTO              // 自动选择最佳策略
    }

    /**
     * 分箱结果类
     */
    public static class BinningResult {
        private List<String> binnedValues = new ArrayList<>();
        private Map<String, String> valueToBinMapping = new HashMap<>();
        private Map<String, List<DataPoint>> binDetails = new LinkedHashMap<>();
        private List<String> orderedBinLabels = new ArrayList<>();
        private BinningStrategy usedStrategy;
        private int actualBinCount;
        private Map<String, BinStatistics> binStatistics = new LinkedHashMap<>();

        public List<String> getBinnedValues() { return binnedValues; }
        public Map<String, String> getValueToBinMapping() { return valueToBinMapping; }
        public Map<String, List<DataPoint>> getBinDetails() { return binDetails; }
        public List<String> getOrderedBinLabels() { return orderedBinLabels; }
        public BinningStrategy getUsedStrategy() { return usedStrategy; }
        public int getActualBinCount() { return actualBinCount; }
        public Map<String, BinStatistics> getBinStatistics() { return binStatistics; }
    }

    /**
     * 单个bin的统计信息
     */
    public static class BinStatistics {
        private double min;
        private double max;
        private double mean;
        private int count;
        private double percentage;

        public BinStatistics(double min, double max, double mean, int count, double percentage) {
            this.min = min;
            this.max = max;
            this.mean = mean;
            this.count = count;
            this.percentage = percentage;
        }

        public double getMin() { return min; }
        public double getMax() { return max; }
        public double getMean() { return mean; }
        public int getCount() { return count; }
        public double getPercentage() { return percentage; }
    }

    public BinningService() {
        this.dataNormalizationService = new DataNormalizationService();
    }

    public BinningService(DataNormalizationService dataNormalizationService) {
        this.dataNormalizationService = dataNormalizationService;
    }

    /**
     * 主分箱方法 - 强制精确分箱模式
     *
     * 示例：20个数据，4个空值，16个有效值，请求5箱
     * 结果：4个常规箱（从16个有效值中分出）+ 1个空值箱 = 5箱
     *
     * @param values 原始值列表
     * @param dataPoints 数据点列表
     * @param requestedBinCount 请求的分箱数量（1-50）
     * @param strategy 分箱策略
     * @return 分箱结果
     */
    public BinningResult performBinning(List<String> values, List<DataPoint> dataPoints,
                                        int requestedBinCount, BinningStrategy strategy) {

        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException("Values列表不能为空");
        }

        if (dataPoints == null || dataPoints.size() != values.size()) {
            throw new IllegalArgumentException("DataPoints数量必须与values数量一致");
        }

        if (requestedBinCount < MIN_BIN_COUNT || requestedBinCount > MAX_BIN_COUNT) {
            throw new IllegalArgumentException(
                    String.format("分箱数量必须在%d-%d之间", MIN_BIN_COUNT, MAX_BIN_COUNT));
        }

        BinningResult result = new BinningResult();

        // 分离空值和有效值
        List<String> validValues = new ArrayList<>();
        List<DataPoint> validDataPoints = new ArrayList<>();
        List<String> nullValues = new ArrayList<>();
        List<DataPoint> nullDataPoints = new ArrayList<>();

        for (int i = 0; i < values.size(); i++) {
            String value = values.get(i);
            if (value.equals("<NULL>") || value.equals("<EMPTY>")) {
                nullValues.add(value);
                nullDataPoints.add(dataPoints.get(i));
            } else {
                validValues.add(value);
                validDataPoints.add(dataPoints.get(i));
            }
        }

        // 如果没有有效值，只返回空值分组
        if (validValues.isEmpty()) {
            return handleOnlyNullValues(values, dataPoints, result);
        }

        // 计算有效值的唯一值数量
        Set<String> uniqueValidValues = new HashSet<>(validValues);
        int uniqueCount = uniqueValidValues.size();

        // 计算空值组数量（最多1组）
        int nullBinCount = nullValues.isEmpty() ? 0 : 1;


        // 调整分箱数量：为有效值预留箱数（总箱数 - 空值箱数）
        int availableBinsForValidValues = requestedBinCount - nullBinCount;
        availableBinsForValidValues = Math.max(1, availableBinsForValidValues); // 至少1箱
        int adjustedBinCount = Math.min(availableBinsForValidValues, uniqueCount);

        // 检查是否为数值列
        boolean isNumeric = dataNormalizationService.isNumericColumn(validValues);

        // 根据策略选择分箱方法
        BinningStrategy actualStrategy = strategy;
        if (strategy == BinningStrategy.AUTO) {
            actualStrategy = isNumeric ? BinningStrategy.EQUAL_FREQUENCY : BinningStrategy.TOP_K;
        }

        result.usedStrategy = actualStrategy;

        // 执行分箱
        if (isNumeric) {
            performNumericBinning(validValues, validDataPoints, adjustedBinCount, actualStrategy, result);
        } else {
            performCategoricalBinning(validValues, validDataPoints, adjustedBinCount, actualStrategy, result);
        }

        // 添加空值处理
        if (!nullValues.isEmpty()) {
            addNullValueHandling(nullValues, nullDataPoints, result);
        }

        // 映射所有原始值到分箱值
        mapAllValuesToBins(values, dataPoints, result);

        // 计算统计信息
        calculateBinStatistics(result, values);

        return result;
    }

    /**
     * 处理只有空值的情况
     */
    private BinningResult handleOnlyNullValues(List<String> values, List<DataPoint> dataPoints,
                                               BinningResult result) {
        result.usedStrategy = BinningStrategy.EQUAL_FREQUENCY;

        for (int i = 0; i < values.size(); i++) {
            String value = values.get(i);
            result.binnedValues.add(value);
            result.valueToBinMapping.put(value, value);
            result.binDetails.computeIfAbsent(value, k -> new ArrayList<>()).add(dataPoints.get(i));
        }

        result.orderedBinLabels = new ArrayList<>(result.binDetails.keySet());
        result.actualBinCount = result.orderedBinLabels.size();

        return result;
    }

    /**
     * 数值型数据分箱
     */
    private void performNumericBinning(List<String> validValues, List<DataPoint> validDataPoints,
                                       int binCount, BinningStrategy strategy, BinningResult result) {

        switch (strategy) {
            case EQUAL_FREQUENCY:
                performEqualFrequencyBinning(validValues, validDataPoints, binCount, result);
                break;
            case EQUAL_WIDTH:
                performEqualWidthBinning(validValues, validDataPoints, binCount, result);
                break;
            case NATURAL_BREAKS:
                performNaturalBreaksBinning(validValues, validDataPoints, binCount, result);
                break;
            case STURGES:
                int sturgesBins = calculateSturgesBins(validValues.size());
                sturgesBins = Math.min(sturgesBins, validValues.size());
                performEqualWidthBinning(validValues, validDataPoints, sturgesBins, result);
                break;
            default:
                performEqualFrequencyBinning(validValues, validDataPoints, binCount, result);
        }
    }

    /**
     * 等频分箱 - 强制精确分箱
     */
    private void performEqualFrequencyBinning(List<String> validValues, List<DataPoint> validDataPoints,
                                              int binCount, BinningResult result) {

        // 统计频次并排序
        Map<String, Long> valueFrequency = validValues.stream()
                .collect(Collectors.groupingBy(v -> v, LinkedHashMap::new, Collectors.counting()));

        List<String> sortedUniqueValues = valueFrequency.keySet().stream()
                .sorted(dataNormalizationService::compareNumericValues)
                .collect(Collectors.toList());

        int uniqueCount = sortedUniqueValues.size();

        // 如果唯一值数量等于分箱数，直接使用原值
        if (uniqueCount == binCount) {
            for (String value : sortedUniqueValues) {
                result.valueToBinMapping.put(value, value);
            }
            result.orderedBinLabels = new ArrayList<>(sortedUniqueValues);
        } else {
            // 强制分成指定数量的箱
            List<List<String>> bins = createExactBins(sortedUniqueValues, valueFrequency, binCount);

            // 为每个箱创建标签
            for (List<String> bin : bins) {
                String binLabel = dataNormalizationService.createNumericRangeLabel(bin);
                result.orderedBinLabels.add(binLabel);
                for (String value : bin) {
                    result.valueToBinMapping.put(value, binLabel);
                }
            }
        }
    }

    /**
     * 创建精确数量的bins
     */
    private List<List<String>> createExactBins(List<String> sortedValues,
                                               Map<String, Long> valueFrequency, int binCount) {
        List<List<String>> bins = new ArrayList<>();
        int uniqueCount = sortedValues.size();

        // 基础分配：每个bin至少包含baseSize个唯一值
        int baseSize = uniqueCount / binCount;
        int remainder = uniqueCount % binCount;

        int currentIndex = 0;
        for (int i = 0; i < binCount; i++) {
            List<String> bin = new ArrayList<>();
            // 前remainder个bin多分配一个值
            int binSize = baseSize + (i < remainder ? 1 : 0);

            for (int j = 0; j < binSize && currentIndex < uniqueCount; j++) {
                bin.add(sortedValues.get(currentIndex));
                currentIndex++;
            }

            if (!bin.isEmpty()) {
                bins.add(bin);
            }
        }

        return bins;
    }

    /**
     * 等宽分箱
     */
    private void performEqualWidthBinning(List<String> validValues, List<DataPoint> validDataPoints,
                                          int binCount, BinningResult result) {

        List<Double> numericValues = validValues.stream()
                .map(Double::parseDouble)
                .collect(Collectors.toList());

        double min = numericValues.stream().min(Double::compare).orElse(0.0);
        double max = numericValues.stream().max(Double::compare).orElse(0.0);

        if (min == max) {
            // 所有值相同
            String binLabel = formatNumber(min);
            result.orderedBinLabels.add(binLabel);
            for (String value : validValues) {
                result.valueToBinMapping.put(value, binLabel);
            }
            return;
        }

        double width = (max - min) / binCount;

        // 创建精确的bins
        for (int i = 0; i < binCount; i++) {
            double binMin = min + i * width;
            double binMax = min + (i + 1) * width;
            String binLabel = formatRange(binMin, binMax);
            result.orderedBinLabels.add(binLabel);
        }

        // 映射值到bins
        for (String value : validValues) {
            double numValue = Double.parseDouble(value);
            int binIndex = (int) ((numValue - min) / width);
            if (binIndex >= binCount) binIndex = binCount - 1;

            String binLabel = result.orderedBinLabels.get(binIndex);
            result.valueToBinMapping.put(value, binLabel);
        }
    }

    /**
     * 自然断点分箱
     */
    private void performNaturalBreaksBinning(List<String> validValues, List<DataPoint> validDataPoints,
                                             int binCount, BinningResult result) {

        List<Double> sortedNumericValues = validValues.stream()
                .map(Double::parseDouble)
                .sorted()
                .collect(Collectors.toList());

        // 简化的自然断点算法
        List<Double> breakpoints = findNaturalBreakpoints(sortedNumericValues, binCount);

        // 创建bins
        double prevBreak = sortedNumericValues.get(0);
        for (int i = 0; i < breakpoints.size(); i++) {
            double currentBreak = breakpoints.get(i);
            String binLabel = formatRange(prevBreak, currentBreak);
            result.orderedBinLabels.add(binLabel);
            prevBreak = currentBreak;
        }

        // 最后一个bin
        String lastBinLabel = formatRange(prevBreak, sortedNumericValues.get(sortedNumericValues.size() - 1));
        result.orderedBinLabels.add(lastBinLabel);

        // 映射值到bins
        for (String value : validValues) {
            double numValue = Double.parseDouble(value);
            String binLabel = findBinLabelForValue(numValue, breakpoints, sortedNumericValues);
            result.valueToBinMapping.put(value, binLabel);
        }
    }

    /**
     * 分类型数据分箱
     */
    private void performCategoricalBinning(List<String> validValues, List<DataPoint> validDataPoints,
                                           int binCount, BinningStrategy strategy, BinningResult result) {

        Map<String, Long> valueFrequency = validValues.stream()
                .collect(Collectors.groupingBy(v -> v, LinkedHashMap::new, Collectors.counting()));

        int uniqueCount = valueFrequency.size();

        // 如果唯一值数量小于等于分箱数，直接使用原值
        if (uniqueCount <= binCount) {
            List<String> sortedKeys = new ArrayList<>(valueFrequency.keySet());
            Collections.sort(sortedKeys);

            for (String value : sortedKeys) {
                result.orderedBinLabels.add(value);
                result.valueToBinMapping.put(value, value);
            }
        } else {
            // 需要合并：保留前(binCount-1)个，其余归为"Other"
            Set<String> keptValues;

            switch (strategy) {
                case TOP_K:
                    keptValues = getTopKValues(valueFrequency, binCount - 1);
                    break;
                case FREQUENCY_THRESHOLD:
                    keptValues = getFrequentValues(valueFrequency, validValues.size(), binCount - 1);
                    break;
                case ALPHABETICAL:
                    keptValues = getAlphabeticalValues(valueFrequency, binCount - 1);
                    break;
                default:
                    keptValues = getTopKValues(valueFrequency, binCount - 1);
            }

            // 排序保留的值
            List<String> sortedKeptValues = new ArrayList<>(keptValues);
            Collections.sort(sortedKeptValues);
            result.orderedBinLabels.addAll(sortedKeptValues);
            result.orderedBinLabels.add("Other");

            // 映射
            for (String value : valueFrequency.keySet()) {
                if (keptValues.contains(value)) {
                    result.valueToBinMapping.put(value, value);
                } else {
                    result.valueToBinMapping.put(value, "Other");
                }
            }
        }
    }

    private void addNullValueHandling(List<String> nullValues, List<DataPoint> nullDataPoints,
                                      BinningResult result) {

        if (nullValues.isEmpty()) {
            return;
        }

        // 强制合并：无论有几种空值类型，都只用一个标签
        String mergedNullLabel = "<NULL>";

        // 获取所有空值的唯一类型
        Map<String, Long> nullFrequency = nullValues.stream()
                .collect(Collectors.groupingBy(v -> v, Collectors.counting()));

        // 只添加一次到orderedBinLabels
        if (!result.orderedBinLabels.contains(mergedNullLabel)) {
            result.orderedBinLabels.add(mergedNullLabel);
        }

        // 所有空值类型都映射到同一个标签
        for (String nullType : nullFrequency.keySet()) {
            result.valueToBinMapping.put(nullType, mergedNullLabel);
        }
    }

    /**
     * 映射所有值到bins并填充binDetails
     */
    private void mapAllValuesToBins(List<String> values, List<DataPoint> dataPoints,
                                    BinningResult result) {

        for (int i = 0; i < values.size(); i++) {
            String originalValue = values.get(i);
            String binLabel = result.valueToBinMapping.get(originalValue);

            result.binnedValues.add(binLabel);
            result.binDetails.computeIfAbsent(binLabel, k -> new ArrayList<>()).add(dataPoints.get(i));
        }

        result.actualBinCount = result.binDetails.size();
    }

    /**
     * 计算bin统计信息
     */
    private void calculateBinStatistics(BinningResult result, List<String> originalValues) {
        int totalCount = originalValues.size();

        for (String binLabel : result.orderedBinLabels) {
            List<DataPoint> binDataPoints = result.binDetails.get(binLabel);
            if (binDataPoints == null) continue;

            int count = binDataPoints.size();
            double percentage = (double) count / totalCount * 100;

            List<Double> numericValues = binDataPoints.stream()
                    .map(DataPoint::getValue)
                    .filter(dataNormalizationService::isNumericValue)
                    .map(Double::parseDouble)
                    .collect(Collectors.toList());

            if (!numericValues.isEmpty()) {
                double min = numericValues.stream().min(Double::compare).orElse(0.0);
                double max = numericValues.stream().max(Double::compare).orElse(0.0);
                double mean = numericValues.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);

                result.binStatistics.put(binLabel,
                        new BinStatistics(min, max, mean, count, percentage));
            } else {
                result.binStatistics.put(binLabel,
                        new BinStatistics(0, 0, 0, count, percentage));
            }
        }
    }

    // ==================== 辅助方法 ====================

    private int calculateSturgesBins(int n) {
        return Math.max(MIN_BIN_COUNT,
                Math.min(MAX_BIN_COUNT,
                        (int) Math.ceil(1 + Math.log(n) / Math.log(2))));
    }

    private Set<String> getTopKValues(Map<String, Long> valueFrequency, int k) {
        return valueFrequency.entrySet().stream()
                .sorted((e1, e2) -> Long.compare(e2.getValue(), e1.getValue()))
                .limit(k)
                .map(Map.Entry::getKey)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private Set<String> getFrequentValues(Map<String, Long> valueFrequency,
                                          long totalCount, int maxCount) {
        double threshold = totalCount * 0.01;

        return valueFrequency.entrySet().stream()
                .filter(e -> e.getValue() >= threshold)
                .sorted((e1, e2) -> Long.compare(e2.getValue(), e1.getValue()))
                .limit(maxCount)
                .map(Map.Entry::getKey)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private Set<String> getAlphabeticalValues(Map<String, Long> valueFrequency, int k) {
        return valueFrequency.keySet().stream()
                .sorted()
                .limit(k)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private List<Double> findNaturalBreakpoints(List<Double> sortedValues, int binCount) {
        List<Double> breakpoints = new ArrayList<>();
        int n = sortedValues.size();
        int step = n / binCount;

        for (int i = 1; i < binCount; i++) {
            int index = i * step;
            if (index < n) {
                double maxGap = 0;
                int maxGapIndex = index;

                for (int j = Math.max(1, index - step/2);
                     j < Math.min(n - 1, index + step/2); j++) {
                    double gap = sortedValues.get(j) - sortedValues.get(j - 1);
                    if (gap > maxGap) {
                        maxGap = gap;
                        maxGapIndex = j;
                    }
                }

                breakpoints.add(sortedValues.get(maxGapIndex));
            }
        }

        return breakpoints;
    }

    private String findBinLabelForValue(double value, List<Double> breakpoints, List<Double> sortedValues) {
        double min = sortedValues.get(0);
        double max = sortedValues.get(sortedValues.size() - 1);

        for (int i = 0; i < breakpoints.size(); i++) {
            if (value < breakpoints.get(i)) {
                max = breakpoints.get(i);
                min = (i == 0) ? sortedValues.get(0) : breakpoints.get(i - 1);
                break;
            }
        }

        if (value >= breakpoints.get(breakpoints.size() - 1)) {
            min = breakpoints.get(breakpoints.size() - 1);
            max = sortedValues.get(sortedValues.size() - 1);
        }

        return formatRange(min, max);
    }

    private String formatRange(double min, double max) {
        String minStr = formatNumber(min);
        String maxStr = formatNumber(max);

        if (minStr.equals(maxStr)) {
            return minStr;
        }

        return minStr + "-" + maxStr;
    }

    private String formatNumber(double number) {
        if (number == Math.floor(number) && !Double.isInfinite(number)) {
            return String.valueOf((long) number);
        } else {
            return String.format("%.2f", number).replaceAll("0*$", "").replaceAll("\\.$", "");
        }
    }
}