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

    // Matrix容器 - 放置在CSV表格上方
    @FXML
    private HBox matrixRowContainer;

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
    private Map<String, Matrix> currentMatrices; // 存储所有列的Matrix数据

    // Matrix渲染常量
    private static final double MATRIX_SIZE = 180.0;
    private static final Color MATRIX_GRID_COLOR = Color.LIGHTGRAY;
    private static final Color MATRIX_BACKGROUND_COLOR = Color.WHITE;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        setupSidebars();
        setupCsvTable();
        setupTableResizing();
    }

    // 为所有列生成Matrix并放置在CSV表格上方
    private void generateAllMatrices() {
        if (currentCsvData == null || currentCsvData.getHeaders().isEmpty()) {
            return;
        }

        try {
            // 使用MatrixService批量生成所有列的Matrix
            currentMatrices = matrixService.generateAllMatrices(currentCsvData, 6); // 默认6个bins

            // 清空并重新构建Matrix容器
            matrixRowContainer.getChildren().clear();

            // 为每一列创建Matrix组件
            List<String> headers = currentCsvData.getHeaders();
            for (String columnName : headers) {
                Matrix matrix = currentMatrices.get(columnName);
                if (matrix != null) {
                    VBox matrixCell = createMatrixCell(matrix, columnName);
                    matrixRowContainer.getChildren().add(matrixCell);
                }
            }

            // 显示Matrix容器
            matrixRowContainer.setVisible(true);
            matrixRowContainer.setManaged(true);

            System.out.println("成功为所有 " + headers.size() + " 列生成Matrix");

        } catch (Exception e) {
            System.err.println("生成所有Matrix时出错: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // 创建单个Matrix单元格组件
    private VBox createMatrixCell(Matrix matrix, String columnName) {
        VBox cell = new VBox(5);
        cell.setPrefWidth(480.0);
        cell.setMinWidth(480.0);
        cell.setMaxWidth(480.0);
        cell.setAlignment(Pos.TOP_CENTER);
        cell.setStyle("-fx-padding: 5;");

        // ===== 新增：添加列标题 =====
        Label columnLabel = new Label(columnName);
        columnLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #2c3e50;");
        columnLabel.setAlignment(Pos.CENTER);
        cell.getChildren().add(columnLabel);
        // ===== 修改结束 =====

        // 创建水平布局，将控制器放在Canvas左侧
        HBox mainLayout = new HBox(10);
        mainLayout.setAlignment(Pos.CENTER);

        //创建Canvas
        Canvas canvas = new Canvas(MATRIX_SIZE, MATRIX_SIZE);
        renderMatrixToCanvas(canvas, matrix);

        // 创建分箱控制器（垂直布局）
        VBox controls = new VBox(5);
        controls.setAlignment(Pos.CENTER);

        Label binLabel = new Label("Bins:");
        Spinner<Integer> binSpinner = new Spinner<>(4, 12, matrix.getActualBinCount());
        binSpinner.setPrefWidth(50);
        binSpinner.setEditable(false);

        // 检查是否为非数字类型，禁用Spinner
        List<String> originalValues = matrix.getOriginalValues();
        boolean isNonNumeric = false;
        if (originalValues != null && !originalValues.isEmpty()) {
            isNonNumeric = !new com.example.rulerDesktop.service.DataNormalizationService()
                    .isNumericColumn(originalValues);
        }

        if (isNonNumeric) {
            binSpinner.setDisable(true);
            binLabel.setStyle("-fx-text-fill: #999;");
            // 将Spinner的值设置为实际分箱数
            binSpinner.getValueFactory().setValue(matrix.getActualBinCount());
        }

        // 分箱数量变化监听（现在canvas已经声明了）
        binSpinner.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && !newVal.equals(oldVal)) {
                updateMatrixBinCount(columnName, newVal, canvas);
            }
        });

        controls.getChildren().addAll(binLabel, binSpinner);

        // 将控制器放在Canvas左侧
        mainLayout.getChildren().addAll(controls, canvas);

        // 将水平布局添加到单元格
        cell.getChildren().add(mainLayout);

        // 添加鼠标交互
        setupCanvasInteraction(canvas, matrix);

        return cell;
    }

    // 渲染Matrix到指定Canvas
    private void renderMatrixToCanvas(Canvas canvas, Matrix matrix) {
        if (matrix == null) {
            return;
        }

        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.clearRect(0, 0, MATRIX_SIZE, MATRIX_SIZE);

        List<String> orderedValues = matrix.getOrderedValues();
        int[][] matrixData = matrix.getMatrix();
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

                // 为空单元格添加斜线标记
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
                    // 根据背景颜色选择文字颜色
                    if (intensity > 0.5) {
                        gc.setFill(Color.WHITE);
                    } else {
                        gc.setFill(Color.BLACK);
                    }

                    gc.setFont(javafx.scene.text.Font.font(Math.min(cellSize/3, 12)));

                    String text = String.valueOf(value);
                    double textWidth = text.length() * (cellSize/6);
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

    // 更新Matrix的分箱数量
    private void updateMatrixBinCount(String columnName, int newBinCount, Canvas canvas) {
        try {
            Matrix matrix = currentMatrices.get(columnName);
            if (matrix != null) {
                matrix = matrixService.updateSingleMatrixBinCount(matrix, newBinCount);
                currentMatrices.put(columnName, matrix);
                renderMatrixToCanvas(canvas, matrix);
                setupCanvasInteraction(canvas, matrix);
                System.out.println("列 '" + columnName + "' 的Matrix bins更新为: " + newBinCount);
            }
        } catch (Exception e) {
            System.err.println("更新Matrix分箱时出错: " + e.getMessage());
        }
    }

    // 设置Canvas交互
    private void setupCanvasInteraction(Canvas canvas, Matrix matrix) {
        canvas.setOnMouseMoved(event -> {
            if (matrix == null) return;

            double x = event.getX();
            double y = event.getY();

            List<String> orderedValues = matrix.getOrderedValues();
            int binCount = orderedValues.size();

            if (binCount > 0 && x >= 0 && y >= 0 && x < MATRIX_SIZE && y < MATRIX_SIZE) {
                double cellSize = MATRIX_SIZE / binCount;
                int col = (int) (x / cellSize);
                int row = (int) (y / cellSize);

                if (row < binCount && col < binCount) {
                    int[][] matrixData = matrix.getMatrix();
                    int value = matrixData[row][col];
                    String fromValue = orderedValues.get(row);
                    String toValue = orderedValues.get(col);

                    String tooltipText = String.format("%s: %s → %s | Count: %d",
                            matrix.getColumnName(), fromValue, toValue, value);
                    Tooltip tooltip = new Tooltip(tooltipText);
                    Tooltip.install(canvas, tooltip);
                }
            }
        });

        canvas.setOnMouseClicked(event -> {
            if (matrix == null) return;

            double x = event.getX();
            double y = event.getY();

            List<String> orderedValues = matrix.getOrderedValues();
            int binCount = orderedValues.size();

            if (binCount > 0 && x >= 0 && y >= 0 && x < MATRIX_SIZE && y < MATRIX_SIZE) {
                double cellSize = MATRIX_SIZE / binCount;
                int col = (int) (x / cellSize);
                int row = (int) (y / cellSize);

                if (row < binCount && col < binCount) {
                    int[][] matrixData = matrix.getMatrix();
                    int value = matrixData[row][col];
                    String fromValue = orderedValues.get(row);
                    String toValue = orderedValues.get(col);

                    System.out.printf("[%s] Matrix cell clicked: [%d,%d] %s → %s (Count: %d)%n",
                            matrix.getColumnName(), row, col, fromValue, toValue, value);
                }
            }
        });
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

                // 生成所有列的Matrix
                generateAllMatrices();

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
        matrixRowContainer.getChildren().clear();
        matrixRowContainer.setVisible(false);
        matrixRowContainer.setManaged(false);

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