package com.example.rulerfrontendj;

import javafx.animation.TranslateTransition;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.net.URL;
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

    private boolean leftSidebarExpanded = false;
    private boolean rightSidebarExpanded = false;

    // 使用你设置的参数
    private final double SIDEBAR_WIDTH = 500.0;
    private final Duration ANIMATION_DURATION = Duration.millis(300);

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        setupSidebars();
    }


    // 新增的按钮事件处理方法
    @FXML
    private void handleImportCsv() {
        System.out.println("Import CSV button clicked");
        // TODO: 实现CSV导入功能
    }

    @FXML
    private void handleExportCsv() {
        System.out.println("Export CSV button clicked");
        // TODO: 实现CSV导出功能
    }

    @FXML
    private void handleResetAll() {
        System.out.println("Reset All button clicked");
        // TODO: 实现重置所有数据功能
    }

    private void setupSidebars() {
        // 初始化左侧边栏位置（隐藏状态）
        leftSidebar.setTranslateX(-SIDEBAR_WIDTH);

        // 初始化右侧边栏位置（隐藏状态）
        rightSidebar.setTranslateX(SIDEBAR_WIDTH);
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

    @FXML
    private void toggleRightSidebar() {
        animateRightSidebar();
    }

    @FXML
    private void closeRightSidebar() {
        if (rightSidebarExpanded) {
            animateRightSidebar();
        }
    }

    private void animateLeftSidebar() {
        TranslateTransition transition = new TranslateTransition(ANIMATION_DURATION, leftSidebar);

        if (leftSidebarExpanded) {
            // 收起左侧边栏
            transition.setToX(-SIDEBAR_WIDTH);
        } else {
            // 展开左侧边栏
            transition.setToX(0);
        }

        transition.play();
        leftSidebarExpanded = !leftSidebarExpanded;
    }

    private void animateRightSidebar() {
        TranslateTransition transition = new TranslateTransition(ANIMATION_DURATION, rightSidebar);

        if (rightSidebarExpanded) {
            // 收起右侧边栏
            transition.setToX(SIDEBAR_WIDTH);
        } else {
            // 展开右侧边栏
            transition.setToX(0);
        }

        transition.play();
        rightSidebarExpanded = !rightSidebarExpanded;
    }


}
