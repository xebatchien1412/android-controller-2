package org.example.ui;

import org.example.core.DeviceMonitor;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PhoneFarmFrame extends JFrame {
    private final JPanel gridPanel;
    private final JLabel lblTotalPhones;
    private final Map<String, PhoneCard> activeCardsMap = new HashMap<>();

    private ExecutorService farmThreadPool;
    private static final String CSV_URL = "https://google.com";

    // Khai báo biến các nút để quản lý bật/tắt trạng thái hiển thị
    private final JButton btnStartAll;
    private final JButton btnStopAll;

    public PhoneFarmFrame() {
        setTitle("HỆ THỐNG QUẢN LÝ PHONE FARM TRỰC DIỆN - THUẦN JAVA");
        setSize(1100, 750);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(10, 10));

        JPanel topPanel = new JPanel(new BorderLayout(15, 10));
        topPanel.setBackground(new Color(230, 235, 240));
        topPanel.setBorder(BorderFactory.createMatteBorder(0, 0, 2, 0, Color.LIGHT_GRAY));
        topPanel.setBorder(BorderFactory.createEmptyBorder(10, 15, 10, 15));

        lblTotalPhones = new JLabel("⚡ Thiết bị đang kết nối: 0");
        lblTotalPhones.setFont(new Font("Segoe UI", Font.BOLD, 14));
        topPanel.add(lblTotalPhones, BorderLayout.WEST);

        JPanel controlGroupPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        controlGroupPanel.setOpaque(false);

        // Khởi tạo các nút điều phối tổng
        btnStartAll = new JButton("🚀 START ALL CHOSEN");
        btnStartAll.setBackground(new Color(40, 167, 69));
        btnStartAll.setForeground(Color.WHITE);
        btnStartAll.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btnStartAll.setFocusPainted(false);

        btnStopAll = new JButton("🛑 STOP ALL CHOSEN");
        btnStopAll.setBackground(new Color(220, 53, 69));
        btnStopAll.setForeground(Color.WHITE);
        btnStopAll.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btnStopAll.setFocusPainted(false);

        controlGroupPanel.add(btnStartAll);
        controlGroupPanel.add(btnStopAll);
        topPanel.add(controlGroupPanel, BorderLayout.EAST);

        add(topPanel, BorderLayout.NORTH);

        gridPanel = new JPanel(new GridLayout(0, 3, 15, 15));
        gridPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        gridPanel.setBackground(new Color(240, 242, 245));

        JScrollPane scrollPane = new JScrollPane(gridPanel);
        add(scrollPane, BorderLayout.CENTER);

        btnStartAll.addActionListener(e -> triggerStartAllChosen());
        btnStopAll.addActionListener(e -> triggerStopAllChosen());

        DeviceMonitor monitor = new DeviceMonitor(this::syncDevicesToUI);
        monitor.start();
    }

    private void triggerStartAllChosen() {
        List<PhoneCard> chosenCards = new ArrayList<>();
        for (PhoneCard card : activeCardsMap.values()) {
            if (card.isSelected() && !card.isWorking()) { // Chỉ chạy máy được tích chọn và CHƯA LÀM VIỆC
                chosenCards.add(card);
            }
        }

        if (chosenCards.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Không có điện thoại nào rảnh rỗi hoặc được tích chọn để chạy!", "Thông báo", JOptionPane.WARNING_MESSAGE);
            return;
        }

        // CẢI TIẾN: Khóa ngay nút bấm Start All để chống nhấn đúp gây kích hoạt lỗi trùng luồng
        btnStartAll.setEnabled(false);

        Map<String, String[]> sheetDataMap = fetchGoogleSheetData();

        if (farmThreadPool != null && !farmThreadPool.isShutdown()) {
            farmThreadPool.shutdownNow();
        }
        farmThreadPool = Executors.newFixedThreadPool(chosenCards.size());

        System.out.println("🔥 Kích hoạt ThreadPool chạy song song cho " + chosenCards.size() + " máy được chọn.");

        for (PhoneCard card : chosenCards) {
            farmThreadPool.execute(() -> {
                String mockTitle = "Sản phẩm Hot cho máy " + card.getDeviceId();
                String mockDesc = "#shopee #automation";

                if (sheetDataMap.containsKey(card.getDeviceId())) {
                    mockTitle = sheetDataMap.get(card.getDeviceId())[0];
                    mockDesc = sheetDataMap.get(card.getDeviceId())[1];
                }

                card.startAutomate(mockTitle, mockDesc);
            });
        }
    }

    private void triggerStopAllChosen() {
        if (farmThreadPool != null) {
            farmThreadPool.shutdownNow();
        }
        for (PhoneCard card : activeCardsMap.values()) {
            if (card.isSelected()) {
                card.stopAutomate();
            }
        }

        // CẢI TIẾN: Mở khóa nút Start All khi người dùng chủ động click nút dừng khẩn cấp
        btnStartAll.setEnabled(true);
        System.out.println("🛑 Hệ thống đã dừng đồng loạt và giải phóng khóa giao diện điều khiển.");
    }

    private Map<String, String[]> fetchGoogleSheetData() {
        Map<String, String[]> dataMap = new HashMap<>();
        try {
            URL url = new URL(CSV_URL);
            try (BufferedReader br = new BufferedReader(new InputStreamReader(url.openStream(), "UTF-8"))) {
                String line;
                while ((line = br.readLine()) != null) {
                    String[] columns = line.split(",");
                    if (columns.length >= 3) {
                        String deviceIdKey = columns[0].trim();
                        String titleVal = columns[1].trim();
                        String descVal = columns[2].trim();
                        dataMap.put(deviceIdKey, new String[]{titleVal, descVal});
                    }
                }
            }
            System.out.println("📊 [Google Sheet] Đã đồng bộ dữ liệu CSV thành công!");
        } catch (Exception e) {
            System.out.println("⚠️ Cấu hình Google Sheet sử dụng Data MOCK: " + e.getMessage());
        }
        return dataMap;
    }

    private void syncDevicesToUI(List<String> connectedIDs) {
        SwingUtilities.invokeLater(() -> {
            boolean isChanged = false;

            for (String id : connectedIDs) {
                if (!activeCardsMap.containsKey(id)) {
                    PhoneCard card = new PhoneCard(id);
                    activeCardsMap.put(id, card);
                    gridPanel.add(card);
                    isChanged = true;
                }
            }

            List<String> removedIDs = new ArrayList<>();
            for (String activeId : activeCardsMap.keySet()) {
                if (!connectedIDs.contains(activeId)) {
                    removedIDs.add(activeId);
                }
            }
            for (String id : removedIDs) {
                PhoneCard cardToRemove = activeCardsMap.remove(id);
                cardToRemove.destroyWorker();
                gridPanel.remove(cardToRemove);
                isChanged = true;
            }

            if (isChanged) {
                lblTotalPhones.setText("⚡ Thiết bị đang kết nối: " + activeCardsMap.size());
                gridPanel.revalidate();
                gridPanel.repaint();
            }
        });
    }
}