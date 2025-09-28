package com.example.rulerDesktop;

import com.example.rulerDesktop.model.CsvData;
import com.example.rulerDesktop.model.Matrix;
import com.example.rulerDesktop.service.CsvParsingService;
import com.example.rulerDesktop.service.MatrixService;
import javafx.animation.TranslateTransition;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;

import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import javafx.scene.shape.Rectangle;
import javafx.stage.FileChooser;
import javafx.util.Duration;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;

public class HelloController implements Initializable {

    @FXML
    private AnchorPane mainContainer;

    @FXML
    private VBox leftSidebar;

    @FXML
    private VBox rightSidebar;

    @FXML
    private Button leftToggleBtn;

    @FXML
    private Button rightToggleBtn;

    @FXML
    private Button leftCloseBtn;

    @FXML
    private Button rightCloseBtn;

    @FXML
    private Button importCsvBtn;

    @FXML
    private Button exportCsvBtn;

    @FXML
    private Button resetAllBtn;

    @FXML
    private AnchorPane contentArea;

    // CSV表格相关组件
    @FXML
    private VBox csvTableContainer;

    @FXML
    private HBox csvTableHeader;

    @FXML
    private TableView<Map<String, String>> csvTableView;

    @FXML
    private Label csvInfoLabel;

    @FXML
    private ScrollPane mainScrollPane;

    @FXML
    private VBox mainContentContainer;

    @FXML
    private Label noDataLabel;

    // Matrix相关组件
    @FXML
    private ComboBox<String> matrixColumnComboBox;

    @FXML
    private Spinner<Integer> matrixBinSpinner;

    @FXML
    private Canvas matrixCanvas;

    @FXML
    private Label matrixInfoLabel;

    @FXML
    private Label matrixStatsLabel;

    @FXML
    private Button refreshMatrixBtn;

    @FXML
    private Button exportMatrixBtn;

    private boolean leftSidebarExpanded = false;
    private boolean rightSidebarExpanded = false;

    // 使用你设置的参数
    private final double SIDEBAR_WIDTH = 500.0;
    private final Duration ANIMATION_DURATION = Duration.millis(300);

    // 添加CSV解析服务
    private final CsvParsingService csvParsingService = new CsvParsingService();
    private CsvData currentCsvData; // 存储当前加载的CSV数据

    // 用于记录拖拽状态
    private boolean isDragging = false;
    private double dragStartY = 0;
    private double initialHeight = 190.0;

    // Matrix相关服务和数据
    private final MatrixService matrixService = new MatrixService();
    private Map<String, Matrix> currentMatrices; // 存储当前的Matrix数据
    private VBox matrixContainer; // Matrix组件的容器
    private Matrix currentMatrix; // 当前显示的Matrix

    // Matrix渲染常量
    private static final double MATRIX_SIZE = 250.0;
    private static final Color MATRIX_GRID_COLOR = Color.LIGHTGRAY;
    private static final Color MATRIX_BACKGROUND_COLOR = Color.WHITE;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        setupSidebars();
        setupCsvTable();
        setupTableResizing();
        setupMatrix();
    }

    // 新增：初始化Matrix组件
    private void setupMatrix() {
        // 初始化Matrix相关组件
        matrixColumnComboBox.setDisable(true);
        matrixBinSpinner.setDisable(true);
        refreshMatrixBtn.setDisable(true);
        exportMatrixBtn.setDisable(true);

        // 设置Spinner变化监听器
        matrixBinSpinner.valueProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null && currentMatrix != null && !oldValue.equals(newValue)) {
                handleMatrixBinChange();
            }
        });

        // 设置Canvas鼠标事件
        setupMatrixCanvasInteractions();

        // 初始化Canvas
        clearMatrixCanvas();
    }

    // 新增：设置Matrix Canvas交互
    private void setupMatrixCanvasInteractions() {
        matrixCanvas.setOnMouseMoved(event -> {
            if (currentMatrix == null) return;

            double x = event.getX();
            double y = event.getY();

            List<String> orderedValues = currentMatrix.getOrderedValues();
            int binCount = orderedValues.size();

            if (binCount > 0 && x >= 0 && y >= 0 && x < MATRIX_SIZE && y < MATRIX_SIZE) {
                double cellSize = MATRIX_SIZE / binCount;
                int col = (int) (x / cellSize);
                int row = (int) (y / cellSize);

                if (row < binCount && col < binCount) {
                    int[][] matrixData = currentMatrix.getMatrix();
                    int value = matrixData[row][col];
                    String fromValue = orderedValues.get(row);
                    String toValue = orderedValues.get(col);

                    // 更新tooltip信息
                    String tooltipText = String.format("From: %s → To: %s | Count: %d",
                            fromValue, toValue, value);
                    Tooltip tooltip = new Tooltip(tooltipText);
                    Tooltip.install(matrixCanvas, tooltip);
                }
            }
        });

        matrixCanvas.setOnMouseClicked(event -> {
            if (currentMatrix == null) return;

            double x = event.getX();
            double y = event.getY();

            List<String> orderedValues = currentMatrix.getOrderedValues();
            int binCount = orderedValues.size();

            if (binCount > 0 && x >= 0 && y >= 0 && x < MATRIX_SIZE && y < MATRIX_SIZE) {
                double cellSize = MATRIX_SIZE / binCount;
                int col = (int) (x / cellSize);
                int row = (int) (y / cellSize);

                if (row < binCount && col < binCount) {
                    int[][] matrixData = currentMatrix.getMatrix();
                    int value = matrixData[row][col];
                    String fromValue = orderedValues.get(row);
                    String toValue = orderedValues.get(col);

                    System.out.printf("Matrix cell clicked: [%d,%d] %s → %s (Count: %d)%n",
                            row, col, fromValue, toValue, value);
                }
            }
        });
    }

    // 新增：Matrix相关事件处理方法
    @FXML
    private void handleMatrixColumnChange() {
        String selectedColumn = matrixColumnComboBox.getSelectionModel().getSelectedItem();
        if (selectedColumn != null && currentCsvData != null) {
            generateMatrix(selectedColumn, matrixBinSpinner.getValue());
        }
    }

    @FXML
    private void handleMatrixBinChange() {
        String selectedColumn = matrixColumnComboBox.getSelectionModel().getSelectedItem();
        if (selectedColumn != null && currentMatrix != null) {
            int newBinCount = matrixBinSpinner.getValue();
            try {
                // 更新现有Matrix的分箱数量
                currentMatrix = matrixService.updateSingleMatrixBinCount(currentMatrix, newBinCount);
                renderMatrix();
                updateMatrixInfo();
                System.out.println("Matrix bins updated to: " + newBinCount);
            } catch (Exception e) {
                System.err.println("更新Matrix分箱时出错: " + e.getMessage());
                showAlert("错误", "更新Matrix分箱失败: " + e.getMessage());
            }
        }
    }

    @FXML
    private void handleRefreshMatrix() {
        String selectedColumn = matrixColumnComboBox.getSelectionModel().getSelectedItem();
        if (selectedColumn != null && currentCsvData != null) {
            generateMatrix(selectedColumn, matrixBinSpinner.getValue());
        }
    }

    @FXML
    private void handleExportMatrix() {
        if (currentMatrix == null) {
            showAlert("提示", "没有可导出的Matrix数据");
            return;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("保存Matrix数据");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("文本文件 (*.txt)", "*.txt"));

        File file = fileChooser.showSaveDialog(mainContainer.getScene().getWindow());
        if (file != null) {
            try {
                // 这里可以实现Matrix数据导出逻辑
                matrixService.printMatrix(currentMatrix);
                System.out.println("Matrix导出功能待实现");
                showAlert("信息", "Matrix导出功能待实现");
            } catch (Exception e) {
                showAlert("错误", "导出失败: " + e.getMessage());
            }
        }
    }

    // 新增：生成Matrix数据
    private void generateMatrix(String columnName, int binCount) {
        try {
            currentMatrix = matrixService.generateSingleMatrix(currentCsvData, columnName, binCount);
            renderMatrix();
            updateMatrixInfo();
            System.out.println("成功生成列 '" + columnName + "' 的Matrix");
        } catch (Exception e) {
            System.err.println("生成Matrix时出错: " + e.getMessage());
            showAlert("错误", "生成Matrix失败: " + e.getMessage());
            clearMatrixCanvas();
        }
    }

    // 新增：渲染Matrix到Canvas
    private void renderMatrix() {
        if (currentMatrix == null) {
            clearMatrixCanvas();
            return;
        }

        GraphicsContext gc = matrixCanvas.getGraphicsContext2D();
        gc.clearRect(0, 0, MATRIX_SIZE, MATRIX_SIZE);

        List<String> orderedValues = currentMatrix.getOrderedValues();
        int[][] matrixData = currentMatrix.getMatrix();
        int binCount = orderedValues.size();

        if (binCount == 0) {
            return;
        }

        double cellSize = MATRIX_SIZE / binCount;

        // 找到最大值用于标准化颜色
        int maxValue = 0;
        for (int i = 0; i < binCount; i++) {
            for (int j = 0; j < binCount; j++) {
                maxValue = Math.max(maxValue, matrixData[i][j]);
            }
        }

        // 绘制Matrix单元格
        for (int i = 0; i < binCount; i++) {
            for (int j = 0; j < binCount; j++) {
                double x = j * cellSize;
                double y = i * cellSize;

                int value = matrixData[i][j];

                // 计算颜色强度：值越大，颜色越深（越接近黑色）
                double intensity = maxValue > 0 ? (double) value / maxValue : 0.0;

                // 设置填充颜色：从白色(0.0)到黑色(1.0)
                if (value > 0) {
                    // 使用灰度：白色到黑色，intensity越大越黑
                    double grayLevel = 1.0 - intensity; // 反转：高频转移 = 深色 = 低grayLevel
                    gc.setFill(Color.gray(grayLevel));
                } else {
                    // 空值使用白色背景
                    gc.setFill(MATRIX_BACKGROUND_COLOR);
                }

                // 填充单元格
                gc.fillRect(x, y, cellSize, cellSize);

                // 为空单元格添加斜线标记（类似论文中的设计）
                if (value == 0 && cellSize > 10) {
                    gc.setStroke(Color.LIGHTGRAY);
                    gc.setLineWidth(1);
                    gc.strokeLine(x, y, x + cellSize, y + cellSize);
                }

                // 绘制网格线
                gc.setStroke(MATRIX_GRID_COLOR);
                gc.setLineWidth(0.5);
                gc.strokeRect(x, y, cellSize, cellSize);

                // 在较大的单元格中显示数值
                if (cellSize > 25 && value > 0) {
                    // 根据背景颜色选择文字颜色：深背景用白字，浅背景用黑字
                    if (intensity > 0.5) {
                        gc.setFill(Color.WHITE); // 深色背景用白字
                    } else {
                        gc.setFill(Color.BLACK); // 浅色背景用黑字
                    }

                    gc.setFont(javafx.scene.text.Font.font(Math.min(cellSize / 3, 12)));

                    // 计算文字居中位置
                    String text = String.valueOf(value);
                    double textWidth = text.length() * (cellSize / 6);
                    double textX = x + (cellSize - textWidth) / 2;
                    double textY = y + cellSize / 2 + 3;

                    gc.fillText(text, textX, textY);
                }
            }
        }

        // 绘制外边框
        gc.setStroke(Color.BLACK);
        gc.setLineWidth(2);
        gc.strokeRect(0, 0, MATRIX_SIZE, MATRIX_SIZE);
    }

    // 新增：清空Matrix Canvas
    private void clearMatrixCanvas() {
        GraphicsContext gc = matrixCanvas.getGraphicsContext2D();
        gc.setFill(MATRIX_BACKGROUND_COLOR);
        gc.fillRect(0, 0, MATRIX_SIZE, MATRIX_SIZE);
        gc.setStroke(MATRIX_GRID_COLOR);
        gc.setLineWidth(1);
        gc.strokeRect(0, 0, MATRIX_SIZE, MATRIX_SIZE);

        // 显示提示文字
        gc.setFill(Color.GRAY);
        gc.fillText("No Matrix Data", MATRIX_SIZE / 2 - 30, MATRIX_SIZE / 2);
    }

    // 新增：更新Matrix信息显示
    private void updateMatrixInfo() {
        if (currentMatrix == null) {
            matrixInfoLabel.setText("No matrix data");
            matrixStatsLabel.setText("Statistics will appear here");
            return;
        }

        String info = String.format("Column: %s | Bins: %d/%d | Sequences: %d",
                currentMatrix.getColumnName(),
                currentMatrix.getActualBinCount(),
                currentMatrix.getBinCount(),
                currentMatrix.getTotalSequences());
        matrixInfoLabel.setText(info);

        // 获取统计信息
        Map<String, Object> stats = matrixService.getMatrixStatistics(currentMatrix);
        String statsText = String.format("Max Transition: %d | Self-transitions: %d (%.1f%%) | Sparsity: %.2f",
                stats.getOrDefault("maxTransition", 0),
                stats.getOrDefault("selfTransitions", 0),
                (Double) stats.getOrDefault("selfTransitionRate", 0.0) * 100,
                stats.getOrDefault("sparsity", 0.0));
        matrixStatsLabel.setText(statsText);
    }

    // 新增：初始化Matrix组件
    private void initializeMatrixComponents() {
        if (currentCsvData == null || currentCsvData.getHeaders().isEmpty()) {
            return;
        }

        // 填充列选择下拉框
        ObservableList<String> columns = FXCollections.observableArrayList(currentCsvData.getHeaders());
        matrixColumnComboBox.setItems(columns);

        // 默认选择第一列
        matrixColumnComboBox.getSelectionModel().selectFirst();

        // 启用相关组件
        matrixColumnComboBox.setDisable(false);
        matrixBinSpinner.setDisable(false);
        refreshMatrixBtn.setDisable(false);
        exportMatrixBtn.setDisable(false);

        // 生成第一列的Matrix
        String firstColumn = currentCsvData.getHeaders().get(0);
        generateMatrix(firstColumn, matrixBinSpinner.getValue());
    }

    // 新增：重置Matrix组件
    private void resetMatrixComponents() {
        matrixColumnComboBox.getItems().clear();
        matrixColumnComboBox.setDisable(true);
        matrixBinSpinner.setDisable(true);
        refreshMatrixBtn.setDisable(true);
        exportMatrixBtn.setDisable(true);

        matrixInfoLabel.setText("No matrix data");
        matrixStatsLabel.setText("Statistics will appear here");
        currentMatrix = null;

        clearMatrixCanvas();
    }

    private void setupCsvTable() {
        // 设置表格的基本属性
        csvTableView.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        csvTableView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
    }

    private void setupTableResizing() {
        // 设置拖拽调整表格高度的功能
        csvTableHeader.setOnMousePressed(event -> {
            isDragging = true;
            dragStartY = event.getScreenY();
            initialHeight = csvTableContainer.getPrefHeight();
        });

        csvTableHeader.setOnMouseDragged(event -> {
            if (isDragging) {
                double deltaY = dragStartY - event.getScreenY();
                double newHeight = Math.max(100, Math.min(500, initialHeight + deltaY));
                csvTableContainer.setPrefHeight(newHeight);
            }
        });

        csvTableHeader.setOnMouseReleased(event -> {
            isDragging = false;
        });
    }

    private void updateMainContentAreaForLeftSidebar() {
        if (leftSidebarExpanded) {
            // 左侧边栏展开时，主内容区域左边距调整
            AnchorPane.setLeftAnchor(mainScrollPane, leftSidebar.getPrefWidth());
        } else {
            // 左侧边栏收起时，主内容区域左边距为0
            AnchorPane.setLeftAnchor(mainScrollPane, 0.0);
        }
    }

    // 实现CSV导入功能
    @FXML
    private void handleImportCsv() {
        // 创建文件选择器
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("选择CSV文件");

        // 设置文件扩展名过滤器
        FileChooser.ExtensionFilter csvFilter = new FileChooser.ExtensionFilter("CSV文件 (*.csv)", "*.csv");
        fileChooser.getExtensionFilters().add(csvFilter);

        // 显示文件选择对话框
        File selectedFile = fileChooser.showOpenDialog(mainContainer.getScene().getWindow());

        if (selectedFile != null) {
            try {
                // 使用CsvParsingService处理CSV文件
                currentCsvData = csvParsingService.loadAndAnalyzeCsv(selectedFile);

                // 更新表格显示
                updateCsvTable();

                // 初始化Matrix组件
                initializeMatrixComponents();

                // 输出处理完成信息到控制台
                System.out.println(selectedFile.getName() + " 处理完成");

            } catch (IOException e) {
                System.err.println("CSV文件处理失败: " + e.getMessage());
                e.printStackTrace();
                showAlert("错误", "CSV文件处理失败: " + e.getMessage());
            } catch (Exception e) {
                System.err.println("处理CSV文件时发生错误: " + e.getMessage());
                e.printStackTrace();
                showAlert("错误", "处理CSV文件时发生错误: " + e.getMessage());
            }
        }
    }

    private void updateCsvTable() {
        if (currentCsvData == null) {
            return;
        }

        // 隐藏无数据提示，显示表格
        noDataLabel.setVisible(false);
        noDataLabel.setManaged(false);
        csvTableContainer.setVisible(true);
        csvTableContainer.setManaged(true);

        // 清空现有列
        csvTableView.getColumns().clear();

        // 创建列
        List<String> headers = currentCsvData.getHeaders();
        for (int colIndex = 0; colIndex < headers.size(); colIndex++) {
            final int columnIndex = colIndex;
            String header = headers.get(colIndex);

            TableColumn<Map<String, String>, String> column = new TableColumn<>(header);
            column.setPrefWidth(480.0);
            column.setMinWidth(480.0);

            // 禁用排序功能
            column.setSortable(false);

            // 设置列标题样式（加粗）
            column.setStyle("-fx-font-weight: bold;");

            // 设置单元格值工厂
            column.setCellValueFactory(cellData -> {
                Map<String, String> rowData = cellData.getValue();
                String value = rowData.get(header);

                // 获取行索引
                int rowIndex = csvTableView.getItems().indexOf(rowData);

                // 格式化显示：[x, y] 数据值
                String displayValue = String.format("[%d, %d] %s",
                        rowIndex, columnIndex, value != null ? value : "");

                return new SimpleStringProperty(displayValue);
            });

            csvTableView.getColumns().add(column);
        }

        // 设置数据
        ObservableList<Map<String, String>> data = FXCollections.observableArrayList(currentCsvData.getRows());
        csvTableView.setItems(data);

        // 更新信息标签 - 新格式
        String fileName = currentCsvData.getFileName() != null ? currentCsvData.getFileName() : "Unknown";
        csvInfoLabel.setText(String.format("CSV Data Table | Rows: %d, Columns: %d, File: %s",
                currentCsvData.getTotalRows(),
                currentCsvData.getTotalColumns(),
                fileName));
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    @FXML
    private void handleExportCsv() {
        if (currentCsvData == null) {
            showAlert("Alert", "No CSV data loaded. Click 'Import CSV' to load a file.");
            return;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save .CSV File");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("CSV file (*.csv)", "*.csv"));

        File file = fileChooser.showSaveDialog(mainContainer.getScene().getWindow());

        if (file != null) {
            try {
                csvParsingService.exportCsvData(currentCsvData, file);
                System.out.println("CSV file export successful: " + file.getName());
            } catch (IOException e) {
                System.err.println("CSV file export failed: " + e.getMessage());
                showAlert("Error", "CSV file export failed: " + e.getMessage());
            }
        }
    }

    @FXML
    private void handleResetAll() {
        currentCsvData = null;
        currentMatrices = null;

        // 清理Matrix容器
        if (matrixContainer != null) {
            mainContentContainer.getChildren().remove(matrixContainer);
            matrixContainer = null;
        }

        // 重置Matrix组件
        resetMatrixComponents();

        csvTableView.getColumns().clear();
        csvTableView.getItems().clear();

        // 显示无数据提示，隐藏表格
        noDataLabel.setVisible(true);
        noDataLabel.setManaged(true);
        csvTableContainer.setVisible(false);
        csvTableContainer.setManaged(false);

        // 重置表格高度
        csvTableContainer.setPrefHeight(190.0);

        System.out.println("所有数据已重置");
    }

    private void setupSidebars() {
        // 初始化左侧边栏位置（隐藏状态）
        leftSidebar.setTranslateX(-leftSidebar.getPrefWidth());

        // 确保主内容区域初始状态正确
        updateMainContentAreaForLeftSidebar();
    }

    @FXML
    private void toggleLeftSidebar() {
        animateLeftSidebar();
    }

    @FXML
    private void closeLeftSidebar() {
        if (leftSidebarExpanded) {
            animateLeftSidebar();
        }
    }

    private void animateLeftSidebar() {
        TranslateTransition transition = new TranslateTransition(ANIMATION_DURATION, leftSidebar);

        if (leftSidebarExpanded) {
            // 收起左侧边栏
            transition.setToX(-leftSidebar.getPrefWidth());
            transition.setOnFinished(e -> updateMainContentAreaForLeftSidebar());
        } else {
            // 展开左侧边栏
            transition.setToX(0);
            transition.setOnFinished(e -> updateMainContentAreaForLeftSidebar());
        }

        transition.play();
        leftSidebarExpanded = !leftSidebarExpanded;
    }

}

