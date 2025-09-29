package com.example.rulerDesktop.service;

import com.example.rulerDesktop.model.DataPoint;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 智能分箱服务类
 * 提供多种分箱策略和自适应分箱功能
 *
 * 使用示例：
 * BinningService binningService = new BinningService(dataNormalizationService);
 * BinningService.BinningResult result = binningService.performBinning(
 *     values, dataPoints, binCount, BinningService.BinningStrategy.AUTO);
 */
public class BinningService {

    private final DataNormalizationService dataNormalizationService;

    // 分箱配置常量
    public static final int MIN_BIN_COUNT = 1;
    public static final int MAX_BIN_COUNT = 50;
    public static final int DEFAULT_BIN_COUNT = 10;
    public static final int MIN_BIN_FREQUENCY = 1; // 每个bin至少包含的元素数
    public static final double LOW_FREQUENCY_THRESHOLD = 0.01; // 低频值阈值（1%）

    /**
     * 分箱策略枚举
     */
    public enum BinningStrategy {
        EQUAL_FREQUENCY,  // 等频分箱（数值）
        EQUAL_WIDTH,      // 等宽分箱（数值）
        NATURAL_BREAKS,   // 自然断点（Jenks）（数值）
        STURGES,          // Sturges规则（数值）
        TOP_K,            // Top-K分组（分类）：保留频次最高的K个值
        FREQUENCY_THRESHOLD, // 频次阈值分组（分类）：保留频次高于阈值的值
        ALPHABETICAL,     // 字母顺序分组（分类）：按字母顺序选择前K个
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
     * 主分箱方法 - 使用指定策略
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

        // 检查是否为数值列
        if (!dataNormalizationService.isNumericColumn(values)) {
            return performCategoricalBinning(values, dataPoints, requestedBinCount, result, strategy);
        }

        // 根据策略选择分箱方法
        BinningStrategy actualStrategy = strategy;
        if (strategy == BinningStrategy.AUTO) {
            actualStrategy = selectBestStrategy(values, requestedBinCount);
        }

        result.usedStrategy = actualStrategy;

        switch (actualStrategy) {
            case EQUAL_FREQUENCY:
                return performEqualFrequencyBinning(values, dataPoints, requestedBinCount, result);
            case EQUAL_WIDTH:
                return performEqualWidthBinning(values, dataPoints, requestedBinCount, result);
            case NATURAL_BREAKS:
                return performNaturalBreaksBinning(values, dataPoints, requestedBinCount, result);
            case STURGES:
                int sturgesBins = calculateSturgesBins(values.size());
                return performEqualWidthBinning(values, dataPoints, sturgesBins, result);
            case TOP_K:
            case FREQUENCY_THRESHOLD:
            case ALPHABETICAL:
                // 这些是分类数据策略，但数据是数值型，使用等频分箱
                return performEqualFrequencyBinning(values, dataPoints, requestedBinCount, result);
            default:
                return performEqualFrequencyBinning(values, dataPoints, requestedBinCount, result);
        }
    }

    /**
     * 自动选择最佳分箱策略
     */
    private BinningStrategy selectBestStrategy(List<String> values, int binCount) {
        // 获取数值列表
        List<Double> numericValues = values.stream()
                .filter(v -> !v.equals("<NULL>") && !v.equals("<EMPTY>"))
                .filter(dataNormalizationService::isNumericValue)
                .map(Double::parseDouble)
                .collect(Collectors.toList());

        if (numericValues.isEmpty()) {
            return BinningStrategy.EQUAL_FREQUENCY;
        }

        // 计算数据分布特征
        double mean = numericValues.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double variance = numericValues.stream()
                .mapToDouble(v -> Math.pow(v - mean, 2))
                .average().orElse(0.0);
        double stdDev = Math.sqrt(variance);
        double coefficientOfVariation = mean != 0 ? stdDev / Math.abs(mean) : 0;

        // 计算偏度
        double skewness = calculateSkewness(numericValues, mean, stdDev);

        // 根据数据特征选择策略
        if (Math.abs(skewness) > 1.0 || coefficientOfVariation > 1.0) {
            // 数据偏斜严重，使用自然断点
            return BinningStrategy.NATURAL_BREAKS;
        } else if (numericValues.size() < 30) {
            // 数据量小，使用Sturges规则
            return BinningStrategy.STURGES;
        } else {
            // 默认使用等频分箱
            return BinningStrategy.EQUAL_FREQUENCY;
        }
    }

    /**
     * 计算偏度
     */
    private double calculateSkewness(List<Double> values, double mean, double stdDev) {
        if (stdDev == 0 || values.isEmpty()) return 0;

        double sum = values.stream()
                .mapToDouble(v -> Math.pow((v - mean) / stdDev, 3))
                .sum();

        return sum / values.size();
    }

    /**
     * 计算Sturges规则的bin数量
     */
    private int calculateSturgesBins(int n) {
        return Math.max(MIN_BIN_COUNT,
                Math.min(MAX_BIN_COUNT,
                        (int) Math.ceil(1 + Math.log(n) / Math.log(2))));
    }

    /**
     * 分类数据分箱（非数值）
     * 改进版：支持多种分组策略
     */
    private BinningResult performCategoricalBinning(List<String> values,
                                                    List<DataPoint> dataPoints,
                                                    int binCount,
                                                    BinningResult result,
                                                    BinningStrategy strategy) {

        // 统计每个分类值的频次
        Map<String, Long> valueFrequency = values.stream()
                .collect(Collectors.groupingBy(v -> v, LinkedHashMap::new, Collectors.counting()));

        int uniqueValueCount = valueFrequency.size();

        // 情况1：唯一值数量少于或等于请求的bin数量，直接使用原值
        if (uniqueValueCount <= binCount) {
            result.usedStrategy = BinningStrategy.EQUAL_FREQUENCY;

            for (int i = 0; i < values.size(); i++) {
                String value = values.get(i);
                result.binnedValues.add(value);
                result.valueToBinMapping.put(value, value);
                result.binDetails.computeIfAbsent(value, k -> new ArrayList<>()).add(dataPoints.get(i));
            }
        } else {
            // 情况2：唯一值数量多于bin数量，需要分组

            // 根据策略选择分组方法
            BinningStrategy actualStrategy = strategy;
            if (strategy == BinningStrategy.AUTO) {
                // 自动选择：默认使用TOP_K
                actualStrategy = BinningStrategy.TOP_K;
            } else if (strategy == BinningStrategy.EQUAL_FREQUENCY ||
                    strategy == BinningStrategy.EQUAL_WIDTH ||
                    strategy == BinningStrategy.NATURAL_BREAKS ||
                    strategy == BinningStrategy.STURGES) {
                // 数值策略，对分类数据使用TOP_K
                actualStrategy = BinningStrategy.TOP_K;
            }

            result.usedStrategy = actualStrategy;

            Set<String> keptValues;

            switch (actualStrategy) {
                case TOP_K:
                    keptValues = getTopKValues(valueFrequency, binCount - 1);
                    break;
                case FREQUENCY_THRESHOLD:
                    keptValues = getFrequentValues(valueFrequency, values.size(), binCount - 1);
                    break;
                case ALPHABETICAL:
                    keptValues = getAlphabeticalValues(valueFrequency, binCount - 1);
                    break;
                default:
                    keptValues = getTopKValues(valueFrequency, binCount - 1);
            }

            boolean hasOther = keptValues.size() < uniqueValueCount;

            // 映射值到bins
            for (int i = 0; i < values.size(); i++) {
                String originalValue = values.get(i);
                String binLabel;

                if (keptValues.contains(originalValue)) {
                    binLabel = originalValue;
                } else if (hasOther) {
                    binLabel = "Other";
                } else {
                    binLabel = originalValue;
                }

                result.binnedValues.add(binLabel);
                result.valueToBinMapping.put(originalValue, binLabel);
                result.binDetails.computeIfAbsent(binLabel, k -> new ArrayList<>())
                        .add(dataPoints.get(i));
            }
        }

        result.orderedBinLabels = new ArrayList<>(result.binDetails.keySet());
        result.actualBinCount = result.orderedBinLabels.size();

        calculateBinStatistics(result, values);

        return result;
    }

    /**
     * 获取Top-K个最频繁的值
     */
    private Set<String> getTopKValues(Map<String, Long> valueFrequency, int k) {
        return valueFrequency.entrySet().stream()
                .sorted((e1, e2) -> Long.compare(e2.getValue(), e1.getValue()))
                .limit(k)
                .map(Map.Entry::getKey)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * 获取频次高于阈值的值
     */
    private Set<String> getFrequentValues(Map<String, Long> valueFrequency,
                                          long totalCount, int maxCount) {
        double threshold = totalCount * LOW_FREQUENCY_THRESHOLD;

        return valueFrequency.entrySet().stream()
                .filter(e -> e.getValue() >= threshold)
                .sorted((e1, e2) -> Long.compare(e2.getValue(), e1.getValue()))
                .limit(maxCount)
                .map(Map.Entry::getKey)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * 按字母顺序获取前K个值
     */
    private Set<String> getAlphabeticalValues(Map<String, Long> valueFrequency, int k) {
        return valueFrequency.keySet().stream()
                .sorted()
                .limit(k)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * 改进的等频分箱
     */
    private BinningResult performEqualFrequencyBinning(List<String> values,
                                                       List<DataPoint> dataPoints,
                                                       int binCount,
                                                       BinningResult result) {
        // 统计频次
        Map<String, Long> valueFrequency = values.stream()
                .collect(Collectors.groupingBy(v -> v, LinkedHashMap::new, Collectors.counting()));

        // 分离特殊值和数值
        Set<String> specialValues = new HashSet<>();
        List<String> numericValueList = new ArrayList<>();

        for (String value : valueFrequency.keySet()) {
            if (value.equals("<NULL>") || value.equals("<EMPTY>")) {
                specialValues.add(value);
            } else {
                numericValueList.add(value);
            }
        }

        // 如果唯一值数量小于等于请求的bin数量，直接使用原值
        if (numericValueList.size() <= binCount) {
            for (int i = 0; i < values.size(); i++) {
                String value = values.get(i);
                result.binnedValues.add(value);
                result.valueToBinMapping.put(value, value);
                result.binDetails.computeIfAbsent(value, k -> new ArrayList<>()).add(dataPoints.get(i));
            }
        } else {
            // 排序数值
            List<String> sortedValues = numericValueList.stream()
                    .sorted(dataNormalizationService::compareNumericValues)
                    .collect(Collectors.toList());

            // 改进的等频分箱算法
            long totalFreq = values.size() - specialValues.stream()
                    .mapToLong(valueFrequency::get).sum();

            List<List<String>> bins = createBalancedBins(sortedValues, valueFrequency,
                    totalFreq, binCount);

            // 为每个bin创建标签
            for (List<String> bin : bins) {
                String binLabel = dataNormalizationService.createNumericRangeLabel(bin);
                for (String value : bin) {
                    result.valueToBinMapping.put(value, binLabel);
                }
            }

            // 映射原始值到分箱值
            for (int i = 0; i < values.size(); i++) {
                String originalValue = values.get(i);
                String binLabel = result.valueToBinMapping.get(originalValue);

                if (binLabel != null) {
                    result.binnedValues.add(binLabel);
                    result.binDetails.computeIfAbsent(binLabel, k -> new ArrayList<>())
                            .add(dataPoints.get(i));
                } else {
                    // 特殊值
                    result.binnedValues.add(originalValue);
                    result.valueToBinMapping.put(originalValue, originalValue);
                    result.binDetails.computeIfAbsent(originalValue, k -> new ArrayList<>())
                            .add(dataPoints.get(i));
                }
            }
        }

        result.orderedBinLabels = new ArrayList<>(result.binDetails.keySet());
        result.actualBinCount = result.orderedBinLabels.size();

        calculateBinStatistics(result, values);

        return result;
    }

    /**
     * 创建平衡的bins
     */
    private List<List<String>> createBalancedBins(List<String> sortedValues,
                                                  Map<String, Long> valueFrequency,
                                                  long totalFreq, int binCount) {
        List<List<String>> bins = new ArrayList<>();
        long targetPerBin = totalFreq / binCount;
        long remainder = totalFreq % binCount;

        int valueIndex = 0;
        for (int binIndex = 0; binIndex < binCount && valueIndex < sortedValues.size(); binIndex++) {
            List<String> currentBin = new ArrayList<>();
            long currentFreq = 0;
            long target = targetPerBin + (binIndex < remainder ? 1 : 0);

            while (valueIndex < sortedValues.size()) {
                String value = sortedValues.get(valueIndex);
                long freq = valueFrequency.get(value);

                // 如果添加这个值会严重超过目标，且当前bin不为空，则停止
                if (currentFreq > 0 && currentFreq + freq > target * 1.5 && binIndex < binCount - 1) {
                    break;
                }

                currentBin.add(value);
                currentFreq += freq;
                valueIndex++;

                // 达到目标且不是最后一个bin
                if (currentFreq >= target && binIndex < binCount - 1) {
                    break;
                }
            }

            if (!currentBin.isEmpty()) {
                bins.add(currentBin);
            }
        }

        return bins;
    }

    /**
     * 等宽分箱
     */
    private BinningResult performEqualWidthBinning(List<String> values,
                                                   List<DataPoint> dataPoints,
                                                   int binCount,
                                                   BinningResult result) {
        // 获取数值列表
        List<Double> numericValues = new ArrayList<>();
        Map<String, String> specialValues = new HashMap<>();

        for (String value : values) {
            if (value.equals("<NULL>") || value.equals("<EMPTY>")) {
                specialValues.put(value, value);
            } else if (dataNormalizationService.isNumericValue(value)) {
                numericValues.add(Double.parseDouble(value));
            }
        }

        if (numericValues.isEmpty()) {
            return performCategoricalBinning(values, dataPoints, binCount, result, BinningStrategy.TOP_K);
        }

        double min = numericValues.stream().min(Double::compare).orElse(0.0);
        double max = numericValues.stream().max(Double::compare).orElse(0.0);
        double width = (max - min) / binCount;

        // 创建bins
        for (int i = 0; i < values.size(); i++) {
            String value = values.get(i);
            String binLabel;

            if (specialValues.containsKey(value)) {
                binLabel = value;
            } else if (dataNormalizationService.isNumericValue(value)) {
                double numValue = Double.parseDouble(value);
                int binIndex = (int) ((numValue - min) / width);
                if (binIndex >= binCount) binIndex = binCount - 1;

                double binMin = min + binIndex * width;
                double binMax = min + (binIndex + 1) * width;
                binLabel = formatRange(binMin, binMax);
            } else {
                binLabel = value;
            }

            result.binnedValues.add(binLabel);
            result.valueToBinMapping.put(value, binLabel);
            result.binDetails.computeIfAbsent(binLabel, k -> new ArrayList<>()).add(dataPoints.get(i));
        }

        result.orderedBinLabels = new ArrayList<>(result.binDetails.keySet());
        result.actualBinCount = result.orderedBinLabels.size();

        calculateBinStatistics(result, values);

        return result;
    }

    /**
     * 自然断点分箱 (Jenks Natural Breaks)
     * 简化版实现
     */
    private BinningResult performNaturalBreaksBinning(List<String> values,
                                                      List<DataPoint> dataPoints,
                                                      int binCount,
                                                      BinningResult result) {
        // 获取排序的数值列表
        List<Double> numericValues = values.stream()
                .filter(v -> !v.equals("<NULL>") && !v.equals("<EMPTY>"))
                .filter(dataNormalizationService::isNumericValue)
                .map(Double::parseDouble)
                .sorted()
                .collect(Collectors.toList());

        if (numericValues.isEmpty() || numericValues.size() <= binCount) {
            return performCategoricalBinning(values, dataPoints, binCount, result, BinningStrategy.TOP_K);
        }

        // 使用简化的Jenks算法找到最佳断点
        List<Double> breakpoints = findNaturalBreakpoints(numericValues, binCount);

        // 创建bins
        for (int i = 0; i < values.size(); i++) {
            String value = values.get(i);
            String binLabel;

            if (value.equals("<NULL>") || value.equals("<EMPTY>")) {
                binLabel = value;
            } else if (dataNormalizationService.isNumericValue(value)) {
                double numValue = Double.parseDouble(value);
                binLabel = findBinLabelForValue(numValue, breakpoints);
            } else {
                binLabel = value;
            }

            result.binnedValues.add(binLabel);
            result.valueToBinMapping.put(value, binLabel);
            result.binDetails.computeIfAbsent(binLabel, k -> new ArrayList<>()).add(dataPoints.get(i));
        }

        result.orderedBinLabels = new ArrayList<>(result.binDetails.keySet());
        result.actualBinCount = result.orderedBinLabels.size();

        calculateBinStatistics(result, values);

        return result;
    }

    /**
     * 寻找自然断点（简化版）
     */
    private List<Double> findNaturalBreakpoints(List<Double> sortedValues, int binCount) {
        List<Double> breakpoints = new ArrayList<>();
        int n = sortedValues.size();
        int step = n / binCount;

        for (int i = 1; i < binCount; i++) {
            int index = i * step;
            if (index < n) {
                // 寻找局部最大gap
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

    /**
     * 根据断点找到值所属的bin标签
     */
    private String findBinLabelForValue(double value, List<Double> breakpoints) {
        double min = value;
        double max = value;

        for (int i = 0; i < breakpoints.size(); i++) {
            if (value < breakpoints.get(i)) {
                max = breakpoints.get(i);
                min = (i == 0) ? value : breakpoints.get(i - 1);
                break;
            }
        }

        if (value >= breakpoints.get(breakpoints.size() - 1)) {
            min = breakpoints.get(breakpoints.size() - 1);
            max = value;
        }

        return formatRange(min, max);
    }

    /**
     * 格式化数值范围
     */
    private String formatRange(double min, double max) {
        String minStr = formatNumber(min);
        String maxStr = formatNumber(max);

        if (minStr.equals(maxStr)) {
            return minStr;
        }

        return minStr + "-" + maxStr;
    }

    /**
     * 格式化数字
     */
    private String formatNumber(double number) {
        if (number == Math.floor(number) && !Double.isInfinite(number)) {
            return String.valueOf((long) number);
        } else {
            return String.format("%.2f", number).replaceAll("0*$", "").replaceAll("\\.$", "");
        }
    }

    /**
     * 计算每个bin的统计信息
     */
    private void calculateBinStatistics(BinningResult result, List<String> originalValues) {
        int totalCount = originalValues.size();

        for (String binLabel : result.orderedBinLabels) {
            List<DataPoint> binDataPoints = result.binDetails.get(binLabel);
            int count = binDataPoints.size();
            double percentage = (double) count / totalCount * 100;

            // 提取数值计算统计
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

    /**
     * 推荐最佳bin数量
     */
    public int recommendBinCount(List<String> values) {
        if (values == null || values.isEmpty()) {
            return DEFAULT_BIN_COUNT;
        }

        // 过滤数值
        List<Double> numericValues = values.stream()
                .filter(v -> !v.equals("<NULL>") && !v.equals("<EMPTY>"))
                .filter(dataNormalizationService::isNumericValue)
                .map(Double::parseDouble)
                .collect(Collectors.toList());

        if (numericValues.isEmpty()) {
            // 分类数据，返回唯一值数量
            return Math.min(MAX_BIN_COUNT,
                    (int) values.stream().distinct().count());
        }

        // 使用多种规则取平均
        int n = numericValues.size();
        int sturges = calculateSturgesBins(n);
        int scott = calculateScottBins(numericValues);
        int freedmanDiaconis = calculateFreedmanDiaconisBins(numericValues);

        int recommended = (sturges + scott + freedmanDiaconis) / 3;
        return Math.max(MIN_BIN_COUNT, Math.min(MAX_BIN_COUNT, recommended));
    }

    /**
     * Scott规则计算bin数量
     */
    private int calculateScottBins(List<Double> values) {
        double mean = values.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double variance = values.stream()
                .mapToDouble(v -> Math.pow(v - mean, 2))
                .average().orElse(0.0);
        double stdDev = Math.sqrt(variance);

        double min = values.stream().min(Double::compare).orElse(0.0);
        double max = values.stream().max(Double::compare).orElse(0.0);
        double range = max - min;

        if (stdDev == 0 || range == 0) return DEFAULT_BIN_COUNT;

        double binWidth = 3.5 * stdDev / Math.cbrt(values.size());
        int bins = (int) Math.ceil(range / binWidth);

        return Math.max(MIN_BIN_COUNT, Math.min(MAX_BIN_COUNT, bins));
    }

    /**
     * Freedman-Diaconis规则计算bin数量
     */
    private int calculateFreedmanDiaconisBins(List<Double> values) {
        List<Double> sorted = values.stream().sorted().collect(Collectors.toList());
        int n = sorted.size();

        double q1 = sorted.get(n / 4);
        double q3 = sorted.get(3 * n / 4);
        double iqr = q3 - q1;

        double min = sorted.get(0);
        double max = sorted.get(n - 1);
        double range = max - min;

        if (iqr == 0 || range == 0) return DEFAULT_BIN_COUNT;

        double binWidth = 2.0 * iqr / Math.cbrt(n);
        int bins = (int) Math.ceil(range / binWidth);

        return Math.max(MIN_BIN_COUNT, Math.min(MAX_BIN_COUNT, bins));
    }
}