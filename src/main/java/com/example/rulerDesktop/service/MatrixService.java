package com.example.rulerDesktop.service;

import com.example.rulerDesktop.model.DataPoint;
import com.example.rulerDesktop.model.CsvData;
import com.example.rulerDesktop.model.Matrix;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 矩阵服务类
 * 负责生成序列转移矩阵和相关性矩阵
 */
public class MatrixService {

    private final DataNormalizationService dataNormalizationService;

    public MatrixService() {
        this.dataNormalizationService = new DataNormalizationService();
    }

    public MatrixService(DataNormalizationService dataNormalizationService) {
        this.dataNormalizationService = dataNormalizationService;
    }

    /**
     * 为指定列生成Matrix数据（指定分箱数量）
     */
    public Matrix generateSingleMatrix(CsvData csvData, String columnName, int binCount) {
        if (!csvData.getHeaders().contains(columnName)) {
            throw new IllegalArgumentException("列 '" + columnName + "' 不存在");
        }
        if (binCount < 4 || binCount > 12) {
            throw new IllegalArgumentException("分箱数量必须在4-12之间");
        }

        Matrix matrix = new Matrix();

        matrix.setColumnName(columnName);
        matrix.setBinCount(binCount);

        int columnIndex = csvData.getHeaders().indexOf(columnName);

        // 1. 提取并标准化该列的所有值
        List<String> columnValues = new ArrayList<>();
        List<DataPoint> dataPoints = new ArrayList<>();

        for (int rowIndex = 0; rowIndex < csvData.getRows().size(); rowIndex++) {
            Map<String, String> row = csvData.getRows().get(rowIndex);
            String value = row.get(columnName);
            String normalizedValue = dataNormalizationService.normalizeValue(value);
            columnValues.add(normalizedValue);
            dataPoints.add(new DataPoint(value, rowIndex, columnIndex));
        }

        matrix.setOriginalValues(new ArrayList<>(columnValues));

        if (columnValues.size() < 2) {
            return matrix; // 少于2行数据无法生成序列
        }

        // 2. 进行等频分箱
        BinningResult binningResult = performEqualFrequencyBinning(columnValues, dataPoints, binCount);

        // 3. 设置分箱数据
        matrix.setValueToBinMapping(binningResult.valueToBinMapping);
        matrix.setBinDetails(binningResult.binDetails);
        matrix.setOrderedValues(new ArrayList<>(binningResult.binDetails.keySet()));
        matrix.setActualBinCount(binningResult.binDetails.size());

        // 4. 生成序列矩阵
        generateSequenceMatrix(matrix, binningResult.binnedValues);

        return matrix;
    }

    /**
     * 更新Matrix的分箱数量
     */
    public Matrix updateSingleMatrixBinCount(Matrix matrix, int newBinCount) {
        if (newBinCount < 4 || newBinCount > 12) {
            throw new IllegalArgumentException("分箱数量必须在4-12之间");
        }
        if (matrix.getOriginalValues() == null || matrix.getOriginalValues().isEmpty()) {
            throw new IllegalStateException("Matrix缺少原始值数据，无法重新分箱");
        }

        // 重建数据点
        List<DataPoint> dataPoints = matrix.getBinDetails().values().stream()
                .flatMap(List::stream)
                .sorted(Comparator.comparingInt(DataPoint::getRowIndex))
                .collect(Collectors.toList());

        if (dataPoints.isEmpty()) {
            for (int i = 0; i < matrix.getOriginalValues().size(); i++) {
                dataPoints.add(new DataPoint(matrix.getOriginalValues().get(i), i, 0));
            }
        }

        // 清空并重新分箱
        matrix.setBinCount(newBinCount);
        matrix.getBinDetails().clear();

        BinningResult binningResult = performEqualFrequencyBinning(
                matrix.getOriginalValues(), dataPoints, newBinCount);

        matrix.setValueToBinMapping(binningResult.valueToBinMapping);
        matrix.setBinDetails(binningResult.binDetails);
        matrix.setOrderedValues(new ArrayList<>(binningResult.binDetails.keySet()));
        matrix.setActualBinCount(binningResult.binDetails.size());

        generateSequenceMatrix(matrix, binningResult.binnedValues);
        return matrix;
    }

    /**
     * 批量生成所有列的Matrix
     */
    public Map<String, Matrix> generateAllMatrices(CsvData csvData, int binCount) {
        Map<String, Matrix> matrices = new LinkedHashMap<>();

        for (String columnName : csvData.getHeaders()) {
            try {
                Matrix matrix = generateSingleMatrix(csvData, columnName, binCount);
                matrices.put(columnName, matrix);
                System.out.println("成功生成列 '" + columnName + "' 的Matrix");
            } catch (Exception e) {
                System.err.println("生成列 '" + columnName + "' 的Matrix时出错: " + e.getMessage());
                e.printStackTrace();
            }
        }

        return matrices;
    }

    /**
     * 验证Matrix数据的完整性
     */
    public boolean validateMatrix(Matrix matrix) {
        if (matrix == null) {
            return false;
        }

        if (matrix.getColumnName() == null || matrix.getColumnName().isEmpty()) {
            return false;
        }

        if (matrix.getOrderedValues() == null || matrix.getOrderedValues().isEmpty()) {
            return false;
        }

        if (matrix.getMatrix() == null) {
            return false;
        }

        int expectedSize = matrix.getOrderedValues().size();
        if (matrix.getMatrix().length != expectedSize) {
            return false;
        }

        for (int[] row : matrix.getMatrix()) {
            if (row.length != expectedSize) {
                return false;
            }
        }

        return true;
    }

    /**
     * 获取矩阵的统计信息
     */
    public Map<String, Object> getMatrixStatistics(Matrix matrix) {
        Map<String, Object> stats = new HashMap<>();

        if (!validateMatrix(matrix)) {
            return stats;
        }

        int[][] matrixData = matrix.getMatrix();
        int size = matrixData.length;

        // 基本信息
        stats.put("columnName", matrix.getColumnName());
        stats.put("size", size);
        stats.put("totalSequences", matrix.getTotalSequences());

        // 计算统计信息
        int totalTransitions = 0;
        int maxTransition = 0;
        int nonZeroTransitions = 0;

        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                int value = matrixData[i][j];
                totalTransitions += value;
                maxTransition = Math.max(maxTransition, value);
                if (value > 0) {
                    nonZeroTransitions++;
                }
            }
        }

        stats.put("totalTransitions", totalTransitions);
        stats.put("maxTransition", maxTransition);
        stats.put("nonZeroTransitions", nonZeroTransitions);
        stats.put("sparsity", (double) nonZeroTransitions / (size * size));

        // 对角线元素（自转移）
        int diagonalSum = 0;
        for (int i = 0; i < size; i++) {
            diagonalSum += matrixData[i][i];
        }
        stats.put("selfTransitions", diagonalSum);
        stats.put("selfTransitionRate", totalTransitions > 0 ? (double) diagonalSum / totalTransitions : 0.0);

        return stats;
    }

    /**
     * 分箱结果内部类
     */
    private static class BinningResult {
        List<String> binnedValues = new ArrayList<>();
        Map<String, String> valueToBinMapping = new HashMap<>();
        Map<String, List<DataPoint>> binDetails = new LinkedHashMap<>();
    }

    /**
     * 执行等频分箱
     */
    private BinningResult performEqualFrequencyBinning(
            List<String> values, List<DataPoint> dataPoints, int binCount) {

        BinningResult result = new BinningResult();

        if (!dataNormalizationService.isNumericColumn(values)) {
            // 非数值类型直接使用原值
            for (int i = 0; i < values.size(); i++) {
                String value = values.get(i);
                result.binnedValues.add(value);
                result.valueToBinMapping.put(value, value);
                result.binDetails.computeIfAbsent(value, k -> new ArrayList<>()).add(dataPoints.get(i));
            }
            return result;
        }

        // 数值类型分箱逻辑
        Map<String, Long> valueFrequency = values.stream()
                .collect(Collectors.groupingBy(v -> v, LinkedHashMap::new, Collectors.counting()));

        if (valueFrequency.size() <= binCount) {
            // 不同值数量 <= 分箱数，直接使用原值
            for (int i = 0; i < values.size(); i++) {
                String value = values.get(i);
                result.binnedValues.add(value);
                result.valueToBinMapping.put(value, value);
                result.binDetails.computeIfAbsent(value, k -> new ArrayList<>()).add(dataPoints.get(i));
            }
            return result;
        }

        // 等频分箱
        List<String> sortedValues = valueFrequency.keySet().stream()
                .filter(v -> !v.equals("<NULL>") && !v.equals("<EMPTY>"))
                .sorted(dataNormalizationService::compareNumericValues)
                .collect(Collectors.toList());

        long totalFrequency = values.size();
        long targetPerBin = totalFrequency / binCount;
        long remainder = totalFrequency % binCount;

        int currentBin = 0;
        long currentFreq = 0;
        long target = targetPerBin + (currentBin < remainder ? 1 : 0);
        List<String> currentBinValues = new ArrayList<>();

        for (int i = 0; i < sortedValues.size(); i++) {
            String value = sortedValues.get(i);
            currentBinValues.add(value);
            currentFreq += valueFrequency.get(value);

            boolean shouldFinish = (currentFreq >= target && currentBin < binCount - 1)
                    || (i == sortedValues.size() - 1);

            if (shouldFinish) {
                String binLabel = dataNormalizationService.createNumericRangeLabel(currentBinValues);
                for (String binValue : currentBinValues) {
                    result.valueToBinMapping.put(binValue, binLabel);
                }

                if (i < sortedValues.size() - 1) {
                    currentBin++;
                    currentFreq = 0;
                    target = targetPerBin + (currentBin < remainder ? 1 : 0);
                    currentBinValues.clear();
                }
            }
        }

        // 处理NULL和EMPTY值
        valueFrequency.keySet().stream()
                .filter(v -> v.equals("<NULL>") || v.equals("<EMPTY>"))
                .forEach(v -> result.valueToBinMapping.put(v, v));

        // 映射原始值到分箱值并记录详情
        for (int i = 0; i < values.size(); i++) {
            String originalValue = values.get(i);
            String binLabel = result.valueToBinMapping.get(originalValue);
            result.binnedValues.add(binLabel);
            result.binDetails.computeIfAbsent(binLabel, k -> new ArrayList<>()).add(dataPoints.get(i));
        }

        return result;
    }

    /**
     * 生成序列矩阵
     */
    private void generateSequenceMatrix(Matrix matrix, List<String> binnedValues) {
        List<String> orderedValues = matrix.getOrderedValues();
        Map<String, Integer> valueIndex = new HashMap<>();
        for (int i = 0; i < orderedValues.size(); i++) {
            valueIndex.put(orderedValues.get(i), i);
        }

        int size = orderedValues.size();
        int[][] matrixData = new int[size][size];

        // 计算序列转移
        for (int i = 0; i < binnedValues.size() - 1; i++) {
            String from = binnedValues.get(i);
            String to = binnedValues.get(i + 1);

            Integer fromIndex = valueIndex.get(from);
            Integer toIndex = valueIndex.get(to);

            if (fromIndex != null && toIndex != null) {
                matrixData[fromIndex][toIndex]++;
            }
        }

        matrix.setMatrix(matrixData);
        matrix.setTotalSequences(binnedValues.size() - 1);
    }

    /**
     * 打印矩阵（用于调试）
     */
    public void printMatrix(Matrix matrix) {
        if (!validateMatrix(matrix)) {
            System.out.println("无效的矩阵数据");
            return;
        }

        System.out.println("Matrix for column: " + matrix.getColumnName());
        System.out.println("Bin count: " + matrix.getBinCount());
        System.out.println("Actual bin count: " + matrix.getActualBinCount());
        System.out.println("Total sequences: " + matrix.getTotalSequences());

        List<String> values = matrix.getOrderedValues();
        int[][] matrixData = matrix.getMatrix();

        // 打印表头
        System.out.print("\t");
        for (String value : values) {
            System.out.printf("%-10s", value);
        }
        System.out.println();

        // 打印矩阵数据
        for (int i = 0; i < values.size(); i++) {
            System.out.printf("%-10s", values.get(i));
            for (int j = 0; j < values.size(); j++) {
                System.out.printf("%-10d", matrixData[i][j]);
            }
            System.out.println();
        }
        System.out.println();
    }
}
