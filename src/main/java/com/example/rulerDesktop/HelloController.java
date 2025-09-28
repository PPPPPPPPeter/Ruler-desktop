package com.example.rulerDesktop;

import com.example.rulerDesktop.model.CsvData;
import com.example.rulerDesktop.service.CsvParsingService;
import javafx.animation.TranslateTransition;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
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


    @Override
    public void initialize(URL location, ResourceBundle resources) {
        setupSidebars();
        setupCsvTable();
        setupTableResizing();
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
            column.setPrefWidth(330.0);
            column.setMinWidth(330.0);

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