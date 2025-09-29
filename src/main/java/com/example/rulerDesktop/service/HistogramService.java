package com.example.rulerDesktop.service;

import com.example.rulerDesktop.model.DataPoint;
import com.example.rulerDesktop.model.CsvData;
import com.example.rulerDesktop.model.Histogram;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 直方图服务类
 * 负责生成单列数据的直方图分布
 */
public class HistogramService {

    private final DataNormalizationService dataNormalizationService;

    private final BinningService binningService; // 新增

    public HistogramService() {

        this.dataNormalizationService = new DataNormalizationService();
        this.binningService = new BinningService(dataNormalizationService); // 新增

    }

    public HistogramService(DataNormalizationService dataNormalizationService) {
        this.dataNormalizationService = dataNormalizationService;
        this.binningService = new BinningService(dataNormalizationService); // 新增
    }

    /**
     * 为指定列生成Histogram数据
     * 更新版本：使用BinningService
     */
    public Histogram generateSingleHistogram(CsvData csvData, String columnName, int binCount) {
        if (csvData == null) {
            throw new IllegalArgumentException("CSV数据不能为空");
        }
        if (!csvData.getHeaders().contains(columnName)) {
            throw new IllegalArgumentException("列 '" + columnName + "' 不存在");
        }
        // 使用BinningService的范围
        if (binCount < BinningService.MIN_BIN_COUNT || binCount > BinningService.MAX_BIN_COUNT) {
            throw new IllegalArgumentException(
                    String.format("分箱数量必须在%d-%d之间",
                            BinningService.MIN_BIN_COUNT, BinningService.MAX_BIN_COUNT));
        }

        Histogram histogram = new Histogram();
        histogram.setColumnName(columnName);
        histogram.setBinCount(binCount);

        int columnIndex = csvData.getHeaders().indexOf(columnName);

        // 提取并标准化列值
        List<String> columnValues = new ArrayList<>();
        List<DataPoint> dataPoints = new ArrayList<>();

        for (int rowIndex = 0; rowIndex < csvData.getRows().size(); rowIndex++) {
            Map<String, String> row = csvData.getRows().get(rowIndex);
            String value = row.get(columnName);
            String normalizedValue = dataNormalizationService.normalizeValue(value);

            columnValues.add(normalizedValue);
            dataPoints.add(new DataPoint(value, rowIndex, columnIndex));
        }

        histogram.setOriginalValues(new ArrayList<>(columnValues));
        histogram.setTotalRecords(columnValues.size());

        if (columnValues.isEmpty()) {
            return histogram;
        }

        // 使用BinningService执行分箱
        BinningService.BinningResult binningResult = binningService.performBinning(
                columnValues,
                dataPoints,
                binCount,
                BinningService.BinningStrategy.AUTO
        );

        // 设置结果
        histogram.setBinDetails(binningResult.getBinDetails());
        histogram.setValueToBinMapping(binningResult.getValueToBinMapping());
        histogram.setOrderedValues(binningResult.getOrderedBinLabels());
        histogram.setActualBinCount(binningResult.getActualBinCount());

        // 计算频次
        Map<String, Integer> frequency = new LinkedHashMap<>();
        for (Map.Entry<String, List<DataPoint>> entry : binningResult.getBinDetails().entrySet()) {
            frequency.put(entry.getKey(), entry.getValue().size());
        }
        histogram.setValueFrequency(frequency);

        return histogram;
    }

    /**
     * 更新Histogram的分箱数量
     * 更新版本：使用BinningService
     */
    public Histogram updateSingleHistogramBinCount(Histogram histogram, int newBinCount) {
        if (histogram == null) {
            throw new IllegalArgumentException("Histogram不能为空");
        }
        // 使用BinningService的范围
        if (newBinCount < BinningService.MIN_BIN_COUNT || newBinCount > BinningService.MAX_BIN_COUNT) {
            throw new IllegalArgumentException(
                    String.format("分箱数量必须在%d-%d之间",
                            BinningService.MIN_BIN_COUNT, BinningService.MAX_BIN_COUNT));
        }
        if (histogram.getOriginalValues() == null || histogram.getOriginalValues().isEmpty()) {
            throw new IllegalStateException("Histogram缺少原始值数据，无法重新分箱");
        }

        // 重建数据点
        List<DataPoint> dataPoints = histogram.getBinDetails().values().stream()
                .flatMap(List::stream)
                .sorted(Comparator.comparingInt(DataPoint::getRowIndex))
                .collect(Collectors.toList());

        if (dataPoints.isEmpty()) {
            for (int i = 0; i < histogram.getOriginalValues().size(); i++) {
                dataPoints.add(new DataPoint(histogram.getOriginalValues().get(i), i, 0));
            }
        }

        // 重新分箱
        histogram.setBinCount(newBinCount);
        histogram.getBinDetails().clear();
        histogram.getValueToBinMapping().clear();

        // 使用BinningService执行分箱
        BinningService.BinningResult binningResult = binningService.performBinning(
                histogram.getOriginalValues(),
                dataPoints,
                newBinCount,
                BinningService.BinningStrategy.AUTO
        );

        histogram.setBinDetails(binningResult.getBinDetails());
        histogram.setValueToBinMapping(binningResult.getValueToBinMapping());
        histogram.setOrderedValues(binningResult.getOrderedBinLabels());
        histogram.setActualBinCount(binningResult.getActualBinCount());

        // 重新计算频次
        Map<String, Integer> frequency = new LinkedHashMap<>();
        for (Map.Entry<String, List<DataPoint>> entry : binningResult.getBinDetails().entrySet()) {
            frequency.put(entry.getKey(), entry.getValue().size());
        }
        histogram.setValueFrequency(frequency);

        return histogram;
    }

    /**
     * 批量生成所有列的Histogram
     */
    public Map<String, Histogram> generateAllHistograms(CsvData csvData, int binCount) {
        if (csvData == null) {
            throw new IllegalArgumentException("CSV数据不能为空");
        }

        Map<String, Histogram> histograms = new LinkedHashMap<>();

        for (String columnName : csvData.getHeaders()) {
            try {
                Histogram histogram = generateSingleHistogram(csvData, columnName, binCount);
                histograms.put(columnName, histogram);
                System.out.println("成功生成列 '" + columnName + "' 的Histogram");
            } catch (Exception e) {
                System.err.println("生成列 '" + columnName + "' 的Histogram时出错: " + e.getMessage());
                e.printStackTrace();
            }
        }

        return histograms;
    }

    /**
     * 验证Histogram数据的完整性
     */
    public boolean validateHistogram(Histogram histogram) {
        if (histogram == null) {
            return false;
        }

        if (histogram.getColumnName() == null || histogram.getColumnName().isEmpty()) {
            return false;
        }

        if (histogram.getOrderedValues() == null) {
            return false;
        }

        if (histogram.getValueFrequency() == null) {
            return false;
        }

        if (histogram.getBinDetails() == null) {
            return false;
        }

        // 验证频次和详情数据的一致性
        for (String binLabel : histogram.getOrderedValues()) {
            Integer frequency = histogram.getValueFrequency().get(binLabel);
            List<DataPoint> details = histogram.getBinDetails().get(binLabel);

            if (frequency == null || details == null) {
                return false;
            }

            if (frequency != details.size()) {
                return false;
            }
        }

        return true;
    }

    /**
     * 获取Histogram的统计信息
     */
    public Map<String, Object> getHistogramStatistics(Histogram histogram) {
        Map<String, Object> stats = new HashMap<>();

        if (!validateHistogram(histogram)) {
            return stats;
        }

        // 基本信息
        stats.put("columnName", histogram.getColumnName());
        stats.put("binCount", histogram.getBinCount());
        stats.put("actualBinCount", histogram.getActualBinCount());
        stats.put("totalRecords", histogram.getTotalRecords());

        // 频次统计
        Map<String, Integer> frequency = histogram.getValueFrequency();
        if (!frequency.isEmpty()) {
            int maxFreq = frequency.values().stream().mapToInt(Integer::intValue).max().orElse(0);
            int minFreq = frequency.values().stream().mapToInt(Integer::intValue).min().orElse(0);
            double avgFreq = frequency.values().stream().mapToInt(Integer::intValue).average().orElse(0.0);

            stats.put("maxFrequency", maxFreq);
            stats.put("minFrequency", minFreq);
            stats.put("avgFrequency", avgFreq);
        }

        // 分布特征
        int nonEmptyBins = (int) frequency.values().stream().filter(f -> f > 0).count();
        stats.put("nonEmptyBins", nonEmptyBins);
        stats.put("emptyBins", histogram.getActualBinCount() - nonEmptyBins);

        return stats;
    }

    /**
     * 获取指定分箱的详细信息
     */
    public Map<String, Object> getBinDetails(Histogram histogram, String binLabel) {
        if (!validateHistogram(histogram)) {
            return new HashMap<>();
        }

        List<DataPoint> dataPoints = histogram.getBinDetails().get(binLabel);
        if (dataPoints == null) {
            return new HashMap<>();
        }

        Map<String, Object> details = new HashMap<>();
        details.put("binLabel", binLabel);
        details.put("frequency", dataPoints.size());
        details.put("percentage", (double) dataPoints.size() / histogram.getTotalRecords() * 100);

        // 如果是数值类型，计算统计值
        List<Double> numericValues = dataPoints.stream()
                .map(DataPoint::getValue)
                .filter(dataNormalizationService::isNumericValue)
                .map(Double::parseDouble)
                .sorted()
                .collect(Collectors.toList());

        if (!numericValues.isEmpty()) {
            details.put("min", numericValues.get(0));
            details.put("max", numericValues.get(numericValues.size() - 1));
            details.put("avg", numericValues.stream().mapToDouble(Double::doubleValue).average().orElse(0.0));

            // 中位数
            int size = numericValues.size();
            if (size % 2 == 0) {
                details.put("median", (numericValues.get(size / 2 - 1) + numericValues.get(size / 2)) / 2.0);
            } else {
                details.put("median", numericValues.get(size / 2));
            }
        }

        details.put("dataPoints", dataPoints);

        return details;
    }

    /**
     * 比较两个Histogram
     */
    public Map<String, Object> compareHistograms(Histogram hist1, Histogram hist2) {
        Map<String, Object> comparison = new HashMap<>();

        if (!validateHistogram(hist1) || !validateHistogram(hist2)) {
            comparison.put("valid", false);
            return comparison;
        }

        comparison.put("valid", true);
        comparison.put("column1", hist1.getColumnName());
        comparison.put("column2", hist2.getColumnName());

        // 基本比较
        comparison.put("sameActualBinCount", hist1.getActualBinCount() == hist2.getActualBinCount());
        comparison.put("sameTotalRecords", hist1.getTotalRecords() == hist2.getTotalRecords());

        // 分布相似性（简单的卡方检验概念）
        Map<String, Integer> freq1 = hist1.getValueFrequency();
        Map<String, Integer> freq2 = hist2.getValueFrequency();

        Set<String> allBins = new HashSet<>();
        allBins.addAll(freq1.keySet());
        allBins.addAll(freq2.keySet());

        double chiSquare = 0.0;
        for (String bin : allBins) {
            int f1 = freq1.getOrDefault(bin, 0);
            int f2 = freq2.getOrDefault(bin, 0);
            int expected = (f1 + f2) / 2;
            if (expected > 0) {
                chiSquare += Math.pow(f1 - expected, 2) / expected;
                chiSquare += Math.pow(f2 - expected, 2) / expected;
            }
        }

        comparison.put("chiSquare", chiSquare);
        comparison.put("similarity", Math.exp(-chiSquare / allBins.size())); // 简化的相似度指标

        return comparison;
    }

    /**
     * 分箱结果内部类
     */
    private static class BinningResult {
        Map<String, List<DataPoint>> binDetails = new LinkedHashMap<>();
        Map<String, String> valueToBinMapping = new HashMap<>();
    }

    /**
     * 打印Histogram信息（用于调试）
     */
    public void printHistogram(Histogram histogram) {
        if (!validateHistogram(histogram)) {
            System.out.println("无效的Histogram数据");
            return;
        }

        System.out.println("Histogram for column: " + histogram.getColumnName());
        System.out.println("Bin count: " + histogram.getBinCount());
        System.out.println("Actual bin count: " + histogram.getActualBinCount());
        System.out.println("Total records: " + histogram.getTotalRecords());
        System.out.println();

        System.out.printf("%-20s %-10s %-10s%n", "Bin Label", "Frequency", "Percentage");
        System.out.println("-".repeat(45));

        for (String binLabel : histogram.getOrderedValues()) {
            int frequency = histogram.getValueFrequency().get(binLabel);
            double percentage = (double) frequency / histogram.getTotalRecords() * 100;
            System.out.printf("%-20s %-10d %-10.2f%%%n", binLabel, frequency, percentage);
        }
        System.out.println();
    }

    /**
     * 导出Histogram数据为CSV格式字符串
     */
    public String exportHistogramToCsv(Histogram histogram) {
        if (!validateHistogram(histogram)) {
            return "";
        }

        StringBuilder csv = new StringBuilder();
        csv.append("Bin Label,Frequency,Percentage\n");

        for (String binLabel : histogram.getOrderedValues()) {
            int frequency = histogram.getValueFrequency().get(binLabel);
            double percentage = (double) frequency / histogram.getTotalRecords() * 100;
            csv.append(String.format("%s,%d,%.2f\n", binLabel, frequency, percentage));
        }

        return csv.toString();
    }
}
