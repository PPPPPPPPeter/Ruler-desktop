package com.example.rulerDesktop.service;

import com.example.rulerDesktop.model.CsvData;
import com.example.rulerDesktop.model.BiPartiteGraph;
import com.example.rulerDesktop.model.Histogram;

import java.util.*;

/**
 * 二分图服务类
 * 负责基于直方图数据生成相邻列之间的连接关系图
 */
public class BiPartiteGraphService {

    public BiPartiteGraphService() {
    }

    /**
     * 基于两个已存在的Histogram生成BiPartiteGraph
     * 核心：不进行独立分箱，而是使用Histogram的分箱结果
     */
    public BiPartiteGraph generateBiPartiteGraphFromHistograms(
            CsvData csvData, Histogram leftHistogram, Histogram rightHistogram) {

        if (csvData == null) {
            throw new IllegalArgumentException("CSV数据不能为空");
        }
        if (leftHistogram == null || rightHistogram == null) {
            throw new IllegalArgumentException("Histogram不能为空");
        }

        String leftColumnName = leftHistogram.getColumnName();
        String rightColumnName = rightHistogram.getColumnName();

        // 验证列名
        if (!csvData.getHeaders().contains(leftColumnName) ||
                !csvData.getHeaders().contains(rightColumnName)) {
            throw new IllegalArgumentException("指定的列不存在于CSV数据中");
        }

        // 验证列的相邻性
        List<String> headers = csvData.getHeaders();
        int leftIndex = headers.indexOf(leftColumnName);
        int rightIndex = headers.indexOf(rightColumnName);
        if (Math.abs(leftIndex - rightIndex) != 1) {
            throw new IllegalArgumentException("BiPartiteGraph只能连接相邻的两列");
        }

        BiPartiteGraph biPartiteGraph = new BiPartiteGraph();
        biPartiteGraph.setLeftColumnName(leftColumnName);
        biPartiteGraph.setRightColumnName(rightColumnName);
        biPartiteGraph.setTotalConnections(csvData.getRows().size());

        if (csvData.getRows().isEmpty()) {
            return biPartiteGraph;
        }

        // 构建连接关系：使用Histogram的分箱结果
        buildConnectionsFromHistograms(biPartiteGraph, csvData, leftHistogram, rightHistogram);

        return biPartiteGraph;
    }

    /**
     * 批量生成所有相邻列对的BiPartiteGraph
     * 依赖于已生成的Histogram集合
     */
    public Map<String, BiPartiteGraph> generateAllBiPartiteGraphsFromHistograms(
            CsvData csvData, Map<String, Histogram> histograms) {

        if (csvData == null) {
            throw new IllegalArgumentException("CSV数据不能为空");
        }
        if (histograms == null) {
            throw new IllegalArgumentException("Histogram集合不能为空");
        }

        Map<String, BiPartiteGraph> biPartiteGraphs = new LinkedHashMap<>();
        List<String> headers = csvData.getHeaders();

        // 遍历所有相邻列对
        for (int i = 0; i < headers.size() - 1; i++) {
            String leftColumn = headers.get(i);
            String rightColumn = headers.get(i + 1);

            Histogram leftHistogram = histograms.get(leftColumn);
            Histogram rightHistogram = histograms.get(rightColumn);

            if (leftHistogram != null && rightHistogram != null) {
                try {
                    BiPartiteGraph biPartiteGraph = generateBiPartiteGraphFromHistograms(
                            csvData, leftHistogram, rightHistogram);
                    String key = leftColumn + "-" + rightColumn;
                    biPartiteGraphs.put(key, biPartiteGraph);
                    System.out.println("成功生成列对 '" + leftColumn + "-" + rightColumn + "' 的BiPartiteGraph");
                } catch (Exception e) {
                    System.err.println("生成列对 '" + leftColumn + "-" + rightColumn +
                            "' 的BiPartiteGraph时出错: " + e.getMessage());
                    e.printStackTrace();
                }
            } else {
                System.out.println("跳过列对 '" + leftColumn + "-" + rightColumn + "'：缺少对应的Histogram");
            }
        }

        return biPartiteGraphs;
    }

    /**
     * 更新BiPartiteGraph - 当相关的Histogram更新后调用
     */
    public BiPartiteGraph updateSingleBiPartiteGraphFromHistograms(
            BiPartiteGraph biPartiteGraph, CsvData csvData,
            Histogram leftHistogram, Histogram rightHistogram) {

        if (biPartiteGraph == null) {
            throw new IllegalArgumentException("BiPartiteGraph不能为空");
        }
        if (csvData == null) {
            throw new IllegalArgumentException("CSV数据不能为空");
        }
        if (leftHistogram == null || rightHistogram == null) {
            throw new IllegalArgumentException("Histogram不能为空");
        }

        // 验证列名匹配
        if (!biPartiteGraph.getLeftColumnName().equals(leftHistogram.getColumnName()) ||
                !biPartiteGraph.getRightColumnName().equals(rightHistogram.getColumnName())) {
            throw new IllegalArgumentException("Histogram列名与BiPartiteGraph不匹配");
        }

        // 清空现有连接
        biPartiteGraph.getLinks().clear();
        biPartiteGraph.getConnectionDetails().clear();

        // 重新构建连接
        buildConnectionsFromHistograms(biPartiteGraph, csvData, leftHistogram, rightHistogram);

        return biPartiteGraph;
    }

    /**
     * 验证BiPartiteGraph数据的完整性
     */
    public boolean validateBiPartiteGraph(BiPartiteGraph biPartiteGraph) {
        if (biPartiteGraph == null) {
            return false;
        }

        if (biPartiteGraph.getLeftColumnName() == null || biPartiteGraph.getLeftColumnName().isEmpty()) {
            return false;
        }

        if (biPartiteGraph.getRightColumnName() == null || biPartiteGraph.getRightColumnName().isEmpty()) {
            return false;
        }

        if (biPartiteGraph.getLinks() == null) {
            return false;
        }

        if (biPartiteGraph.getConnectionDetails() == null) {
            return false;
        }

        // 验证连接数据的一致性
        for (BiPartiteGraph.BiPartiteLink link : biPartiteGraph.getLinks()) {
            String connectionKey = link.getLeftBin() + "->" + link.getRightBin();
            List<BiPartiteGraph.ConnectionDetail> details = biPartiteGraph.getConnectionDetails().get(connectionKey);

            if (details == null || details.size() != link.getWeight()) {
                return false;
            }
        }

        return true;
    }

    /**
     * 获取BiPartiteGraph的统计信息
     */
    public Map<String, Object> getBiPartiteGraphStatistics(BiPartiteGraph biPartiteGraph) {
        Map<String, Object> stats = new HashMap<>();

        if (!validateBiPartiteGraph(biPartiteGraph)) {
            return stats;
        }

        // 基本信息
        stats.put("leftColumnName", biPartiteGraph.getLeftColumnName());
        stats.put("rightColumnName", biPartiteGraph.getRightColumnName());
        stats.put("totalConnections", biPartiteGraph.getTotalConnections());

        List<BiPartiteGraph.BiPartiteLink> links = biPartiteGraph.getLinks();
        if (!links.isEmpty()) {
            // 连接强度统计
            int maxWeight = links.stream().mapToInt(BiPartiteGraph.BiPartiteLink::getWeight).max().orElse(0);
            int minWeight = links.stream().mapToInt(BiPartiteGraph.BiPartiteLink::getWeight).min().orElse(0);
            double avgWeight = links.stream().mapToInt(BiPartiteGraph.BiPartiteLink::getWeight).average().orElse(0.0);
            int totalUniqueConnections = links.size();

            stats.put("maxWeight", maxWeight);
            stats.put("minWeight", minWeight);
            stats.put("avgWeight", Math.round(avgWeight * 100.0) / 100.0);
            stats.put("totalUniqueConnections", totalUniqueConnections);

            // 连接分布
            long strongConnections = links.stream().filter(link -> link.getNormalizedWeight() > 0.7).count();
            long mediumConnections = links.stream().filter(link ->
                    link.getNormalizedWeight() > 0.3 && link.getNormalizedWeight() <= 0.7).count();
            long weakConnections = links.stream().filter(link -> link.getNormalizedWeight() <= 0.3).count();

            stats.put("strongConnections", strongConnections);
            stats.put("mediumConnections", mediumConnections);
            stats.put("weakConnections", weakConnections);

            // 连接密度
            Set<String> leftBins = new HashSet<>();
            Set<String> rightBins = new HashSet<>();
            for (BiPartiteGraph.BiPartiteLink link : links) {
                leftBins.add(link.getLeftBin());
                rightBins.add(link.getRightBin());
            }

            int maxPossibleConnections = leftBins.size() * rightBins.size();
            double density = maxPossibleConnections > 0 ? (double) totalUniqueConnections / maxPossibleConnections : 0.0;

            stats.put("leftBinCount", leftBins.size());
            stats.put("rightBinCount", rightBins.size());
            stats.put("connectionDensity", Math.round(density * 10000.0) / 100.0); // 百分比，保留2位小数
        }

        return stats;
    }

    /**
     * 获取指定连接的详细信息
     */
    public Map<String, Object> getConnectionDetails(BiPartiteGraph biPartiteGraph, String leftBin, String rightBin) {
        if (!validateBiPartiteGraph(biPartiteGraph)) {
            return new HashMap<>();
        }

        String connectionKey = leftBin + "->" + rightBin;
        List<BiPartiteGraph.ConnectionDetail> details = biPartiteGraph.getConnectionDetails().get(connectionKey);

        Map<String, Object> result = new HashMap<>();
        result.put("leftBin", leftBin);
        result.put("rightBin", rightBin);
        result.put("connectionKey", connectionKey);

        if (details != null) {
            result.put("connectionCount", details.size());
            result.put("details", details);

            // 找到对应的链接信息
            BiPartiteGraph.BiPartiteLink link = biPartiteGraph.getLinks().stream()
                    .filter(l -> l.getLeftBin().equals(leftBin) && l.getRightBin().equals(rightBin))
                    .findFirst()
                    .orElse(null);

            if (link != null) {
                result.put("weight", link.getWeight());
                result.put("normalizedWeight", link.getNormalizedWeight());
                result.put("visualWeight", link.getVisualWeight());
                result.put("percentage", link.getPercentage());
            }
        } else {
            result.put("connectionCount", 0);
            result.put("details", new ArrayList<>());
        }

        return result;
    }

    /**
     * 基于Histogram的分箱结果构建连接关系
     * 核心逻辑：将每行数据映射到对应的bin，然后统计bin之间的连接
     */
    private void buildConnectionsFromHistograms(BiPartiteGraph biPartiteGraph, CsvData csvData,
                                                Histogram leftHistogram, Histogram rightHistogram) {

        String leftColumnName = biPartiteGraph.getLeftColumnName();
        String rightColumnName = biPartiteGraph.getRightColumnName();

        // 获取Histogram的分箱映射
        Map<String, String> leftValueToBinMapping = leftHistogram.getValueToBinMapping();
        Map<String, String> rightValueToBinMapping = rightHistogram.getValueToBinMapping();

        Map<String, Integer> connectionCounts = new HashMap<>();
        Map<String, List<BiPartiteGraph.ConnectionDetail>> connectionDetails = new LinkedHashMap<>();

        // 遍历每一行数据，建立连接
        for (int rowIndex = 0; rowIndex < csvData.getRows().size(); rowIndex++) {
            Map<String, String> row = csvData.getRows().get(rowIndex);

            String leftValue = row.get(leftColumnName);
            String rightValue = row.get(rightColumnName);

            // 查找对应的bin标签
            String leftBin = findBinForValue(leftValue, leftValueToBinMapping, leftHistogram);
            String rightBin = findBinForValue(rightValue, rightValueToBinMapping, rightHistogram);

            if (leftBin != null && rightBin != null) {
                String connectionKey = leftBin + "->" + rightBin;

                // 统计连接数量
                connectionCounts.merge(connectionKey, 1, Integer::sum);

                // 记录连接详情
                connectionDetails.computeIfAbsent(connectionKey, k -> new ArrayList<>())
                        .add(new BiPartiteGraph.ConnectionDetail(
                                rowIndex, leftValue, rightValue, leftBin, rightBin));
            }
        }

        // 找到最大权重，用于标准化
        int maxWeight = connectionCounts.values().stream().mapToInt(Integer::intValue).max().orElse(1);

        // 构建链接列表，包含标准化权重
        List<BiPartiteGraph.BiPartiteLink> links = new ArrayList<>();
        int totalConnections = biPartiteGraph.getTotalConnections();

        for (Map.Entry<String, Integer> entry : connectionCounts.entrySet()) {
            String[] parts = entry.getKey().split("->");
            if (parts.length == 2) {
                String leftBin = parts[0];
                String rightBin = parts[1];
                int weight = entry.getValue();
                double percentage = totalConnections > 0 ? (double) weight / totalConnections * 100 : 0.0;

                // 传入maxWeight用于计算标准化权重
                links.add(new BiPartiteGraph.BiPartiteLink(
                        leftBin, rightBin, weight, percentage, maxWeight));
            }
        }

        // 按权重排序 - 最强的连接排在前面
        links.sort((a, b) -> Integer.compare(b.getWeight(), a.getWeight()));

        biPartiteGraph.setLinks(links);
        biPartiteGraph.setConnectionDetails(connectionDetails);
    }

    /**
     * 为给定值查找对应的bin标签
     */
    private String findBinForValue(String value, Map<String, String> valueToBinMapping, Histogram histogram) {
        // 首先尝试直接映射
        String bin = valueToBinMapping.get(value);
        if (bin != null) {
            return bin;
        }

        // 如果直接映射失败，检查是否在orderedValues中（用于处理边界情况）
        List<String> orderedValues = histogram.getOrderedValues();
        if (orderedValues.contains(value)) {
            return value;
        }

        // 如果都失败，返回null（表示该值不在任何bin中，可能是异常数据）
        return null;
    }

    /**
     * 打印BiPartiteGraph信息（用于调试）
     */
    public void printBiPartiteGraph(BiPartiteGraph biPartiteGraph) {
        if (!validateBiPartiteGraph(biPartiteGraph)) {
            System.out.println("无效的BiPartiteGraph数据");
            return;
        }

        System.out.println("BiPartiteGraph: " + biPartiteGraph.getLeftColumnName() +
                " -> " + biPartiteGraph.getRightColumnName());
        System.out.println("Total connections: " + biPartiteGraph.getTotalConnections());
        System.out.println("Unique connections: " + biPartiteGraph.getLinks().size());
        System.out.println();

        System.out.printf("%-20s %-20s %-10s %-10s %-10s %-10s%n",
                "Left Bin", "Right Bin", "Weight", "Norm.W", "Visual.W", "Percentage");
        System.out.println("-".repeat(90));

        for (BiPartiteGraph.BiPartiteLink link : biPartiteGraph.getLinks()) {
            System.out.printf("%-20s %-20s %-10d %-10.3f %-10d %-10.2f%%%n",
                    link.getLeftBin(), link.getRightBin(), link.getWeight(),
                    link.getNormalizedWeight(), link.getVisualWeight(), link.getPercentage());
        }
        System.out.println();
    }

    /**
     * 导出BiPartiteGraph数据为CSV格式字符串
     */
    public String exportBiPartiteGraphToCsv(BiPartiteGraph biPartiteGraph) {
        if (!validateBiPartiteGraph(biPartiteGraph)) {
            return "";
        }

        StringBuilder csv = new StringBuilder();
        csv.append("Left Bin,Right Bin,Weight,Normalized Weight,Visual Weight,Percentage\n");

        for (BiPartiteGraph.BiPartiteLink link : biPartiteGraph.getLinks()) {
            csv.append(String.format("%s,%s,%d,%.3f,%d,%.2f\n",
                    link.getLeftBin(), link.getRightBin(), link.getWeight(),
                    link.getNormalizedWeight(), link.getVisualWeight(), link.getPercentage()));
        }

        return csv.toString();
    }
}
