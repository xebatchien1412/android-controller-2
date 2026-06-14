package org.example.ui;

import org.example.core.DeviceMonitor;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PhoneFarmFrame extends JFrame {
    private final JPanel gridPanel;
    private final JLabel lblTotalPhones;
    private final Map<String, PhoneCard> activeCardsMap = new HashMap<>();

    public PhoneFarmFrame() {
        setTitle("HỆ THỐNG QUẢN LÝ PHONE FARM TRỰC DIỆN - THUẦN JAVA");
        setSize(1000, 700);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(10, 10));

        // Thanh công cụ phía trên
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 10));
        topPanel.setBackground(new Color(230, 235, 240));
        topPanel.setBorder(BorderFactory.createMatteBorder(0, 0, 2, 0, Color.LIGHT_GRAY));
        lblTotalPhones = new JLabel("⚡ Thiết bị đang kết nối: 0");
        lblTotalPhones.setFont(new Font("Segoe UI", Font.BOLD, 14));
        topPanel.add(lblTotalPhones);
        add(topPanel, BorderLayout.NORTH);

        // Sử dụng cấu trúc lưới GridLayout để chia đều diện tích cho các điện thoại hiển thị hiển thị
        gridPanel = new JPanel(new GridLayout(0, 3, 15, 15));
        gridPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        gridPanel.setBackground(new Color(240, 242, 245));

        JScrollPane scrollPane = new JScrollPane(gridPanel);
        add(scrollPane, BorderLayout.CENTER);

        // Khởi chạy quét thiết bị cắm/rút dây cáp USB ngầm
        DeviceMonitor monitor = new DeviceMonitor(this::syncDevicesToUI);
        monitor.start();
    }

    private void syncDevicesToUI(List<String> connectedIDs) {
        SwingUtilities.invokeLater(() -> {
            boolean isChanged = false;

            // Thêm máy mới cắm vào thành một ô lưới độc lập tự vẽ hình ảnh
            for (String id : connectedIDs) {
                if (!activeCardsMap.containsKey(id)) {
                    PhoneCard card = new PhoneCard(id);
                    activeCardsMap.put(id, card);
                    gridPanel.add(card);
                    isChanged = true;
                }
            }

            // Xóa ô lưới khi điện thoại bị rút cáp khỏi máy tính
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