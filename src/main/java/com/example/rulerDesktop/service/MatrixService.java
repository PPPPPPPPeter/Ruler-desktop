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

    private final BinningService binningService; // 新增

    public MatrixService() {

        this.dataNormalizationService = new DataNormalizationService();
        this.binningService = new BinningService(dataNormalizationService); // 新增
    }

    public MatrixService(DataNormalizationService dataNormalizationService) {
        this.dataNormalizationService = dataNormalizationService;
        this.binningService = new BinningService(dataNormalizationService); // 新增
    }

    /**
     * 为指定列生成Matrix数据（指定分箱数量）
     * 更新版本：使用BinningService
     */
    public Matrix generateSingleMatrix(CsvData csvData, String columnName, int binCount) {
        if (!csvData.getHeaders().contains(columnName)) {
            throw new IllegalArgumentException("列 '" + columnName + "' 不存在");
        }
        // 使用BinningService的范围
        if (binCount < BinningService.MIN_BIN_COUNT || binCount > BinningService.MAX_BIN_COUNT) {
            throw new IllegalArgumentException(
                    String.format("分箱数量必须在%d-%d之间",
                            BinningService.MIN_BIN_COUNT, BinningService.MAX_BIN_COUNT));
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

        // 2. 使用BinningService进行分箱
        BinningService.BinningResult binningResult = binningService.performBinning(
                columnValues,
                dataPoints,
                binCount,
                BinningService.BinningStrategy.AUTO // 使用自动策略
        );

        // 3. 设置分箱数据
        matrix.setValueToBinMapping(binningResult.getValueToBinMapping());
        matrix.setBinDetails(binningResult.getBinDetails());
        matrix.setOrderedValues(binningResult.getOrderedBinLabels());
        matrix.setActualBinCount(binningResult.getActualBinCount());

        // 4. 生成序列矩阵
        generateSequenceMatrix(matrix, binningResult.getBinnedValues());

        return matrix;
    }

    /**
     * 更新Matrix的分箱数量
     * 更新版本：使用BinningService
     */
    public Matrix updateSingleMatrixBinCount(Matrix matrix, int newBinCount) {
        // 使用BinningService的范围
        if (newBinCount < BinningService.MIN_BIN_COUNT || newBinCount > BinningService.MAX_BIN_COUNT) {
            throw new IllegalArgumentException(
                    String.format("分箱数量必须在%d-%d之间",
                            BinningService.MIN_BIN_COUNT, BinningService.MAX_BIN_COUNT));
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

        // 使用BinningService进行分箱
        BinningService.BinningResult binningResult = binningService.performBinning(
                matrix.getOriginalValues(),
                dataPoints,
                newBinCount,
                BinningService.BinningStrategy.AUTO
        );

        matrix.setValueToBinMapping(binningResult.getValueToBinMapping());
        matrix.setBinDetails(binningResult.getBinDetails());
        matrix.setOrderedValues(binningResult.getOrderedBinLabels());
        matrix.setActualBinCount(binningResult.getActualBinCount());

        generateSequenceMatrix(matrix, binningResult.getBinnedValues());
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
