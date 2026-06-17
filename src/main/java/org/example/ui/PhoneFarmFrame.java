package org.example.ui;

import org.example.core.DeviceMonitor;
import org.example.core.DatabaseManager; // Thêm thư viện gọi DB

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.io.File;

public class PhoneFarmFrame extends JFrame {
    private final JPanel gridPanel;
    private final JLabel lblTotalPhones;
    private final JTextField txtVideoFolder;
    private final JButton btnBrowse;
    private final JButton btnResetDB;
    private final JCheckBox chkSelectAll;
    private final Map<String, PhoneCard> activeCardsMap = new HashMap<>();

    private ExecutorService farmThreadPool;
    private final JButton btnStartAll;
    private final JButton btnStopAll;

    public PhoneFarmFrame() {
        setTitle("HỆ THỐNG QUẢN LÝ PHONE FARM");
        setSize(1200, 750); // Nhích rộng giao diện ra một chút để chứa nút mới gọn gàng
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(10, 10));

        // 🛠️ THANH CÔNG CỤ PHÍA TRÊN (TOP BAR CẢI TIẾN)
        JPanel topPanel = new JPanel(new GridBagLayout());
        topPanel.setBackground(new Color(230, 235, 240));
        topPanel.setBorder(BorderFactory.createMatteBorder(0, 0, 2, 0, Color.LIGHT_GRAY));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 10, 8, 10);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Cột 1: Số máy đang kết nối
        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0.12;
        lblTotalPhones = new JLabel("⚡ Thiết bị: 0");
        lblTotalPhones.setFont(new Font("Segoe UI", Font.BOLD, 13));
        topPanel.add(lblTotalPhones, gbc);

        // Cột 2: Checkbox Chọn nhanh tất cả máy
        gbc.gridx = 1; gbc.gridy = 0; gbc.weightx = 0.08;
        chkSelectAll = new JCheckBox("Chọn hết");
        chkSelectAll.setSelected(true);
        chkSelectAll.setOpaque(false);
        chkSelectAll.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        topPanel.add(chkSelectAll, gbc);

        // Cột 3: Bộ chọn thư mục Video Input
        gbc.gridx = 2; gbc.gridy = 0; gbc.weightx = 0.45;
        JPanel folderPanel = new JPanel(new BorderLayout(5, 0));
        folderPanel.setOpaque(false);
        folderPanel.add(new JLabel("📂 Folder: "), BorderLayout.WEST);

        txtVideoFolder = new JTextField("C:\\FarmVideos");
        txtVideoFolder.setEditable(false);
        btnBrowse = new JButton("Duyệt...");
        btnBrowse.setForeground(Color.BLACK); // Đồng bộ chữ đen dễ nhìn

        folderPanel.add(txtVideoFolder, BorderLayout.CENTER);
        folderPanel.add(btnBrowse, BorderLayout.EAST);
        topPanel.add(folderPanel, gbc);

        // Cột 4: Nút Reset DB chủ động (THÊM MỚI)
        gbc.gridx = 3; gbc.gridy = 0; gbc.weightx = 0.1;
        btnResetDB = new JButton("🔄 Reset DB");
        btnResetDB.setBackground(new Color(255, 193, 7)); // Màu vàng cảnh báo trực quan
        btnResetDB.setForeground(Color.BLACK); // Đổi chữ đen rõ ràng
        btnResetDB.setFont(new Font("Segoe UI", Font.BOLD, 11));
        btnResetDB.setFocusPainted(false);
        topPanel.add(btnResetDB, gbc);

        // Cột 5: Cặp nút điều phối tổng hàng loạt chữ đen
        gbc.gridx = 4; gbc.gridy = 0; gbc.weightx = 0.25;
        JPanel batchButtonsPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        batchButtonsPanel.setOpaque(false);

        btnStartAll = new JButton("🚀 START ALL CHOSEN");
        btnStartAll.setBackground(new Color(40, 167, 69));
        btnStartAll.setForeground(Color.BLACK); // ĐỔI SANG CHỮ ĐEN DỄ NHÌN
        btnStartAll.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btnStartAll.setFocusPainted(false);

        btnStopAll = new JButton("🛑 STOP ALL CHOSEN");
        btnStopAll.setBackground(new Color(220, 53, 69));
        btnStopAll.setForeground(Color.BLACK); // ĐỔI SANG CHỮ ĐEN DỄ NHÌN
        btnStopAll.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btnStopAll.setFocusPainted(false);

        batchButtonsPanel.add(btnStartAll);
        batchButtonsPanel.add(btnStopAll);
        topPanel.add(batchButtonsPanel, gbc);

        add(topPanel, BorderLayout.NORTH);

        gridPanel = new JPanel(new GridLayout(0, 3, 15, 15));
        gridPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        gridPanel.setBackground(new Color(240, 242, 245));

        JScrollPane scrollPane = new JScrollPane(gridPanel);
        add(scrollPane, BorderLayout.CENTER);

        initEvents();

        DeviceMonitor monitor = new DeviceMonitor(this::syncDevicesToUI);
        monitor.start();
    }

    private void initEvents() {
        btnBrowse.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            chooser.setCurrentDirectory(new File(txtVideoFolder.getText()));
            int result = chooser.showOpenDialog(this);
            if (result == JFileChooser.APPROVE_OPTION) {
                File selectedFolder = chooser.getSelectedFile();
                txtVideoFolder.setText(selectedFolder.getAbsolutePath());
            }
        });

        // HỘP THOẠI CONFIRM RESET DATABASE CHỦ ĐỘNG (THÊM MỚI)
        btnResetDB.addActionListener(e -> {
            int confirm = JOptionPane.showConfirmDialog(
                    this,
                    "Bạn có chắc chắn muốn XÓA LỊCH SỬ ĐĂNG VIDEO không?\nHành động này sẽ đặt lại toàn bộ trạng thái về PENDING để chạy lại từ đầu.",
                    "⚠️ Xác nhận Reset Database",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE
            );

            if (confirm == JOptionPane.YES_OPTION) {
                DatabaseManager.resetAllVideoStatus();
                JOptionPane.showMessageDialog(this, "🔄 Đã dọn dẹp sạch lịch sử SQLite! Toàn bộ video đã sẵn sàng chờ đăng mới.");
            }
        });

        chkSelectAll.addActionListener(e -> {
            boolean status = chkSelectAll.isSelected();
            for (PhoneCard card : activeCardsMap.values()) {
                card.setSelected(status);
            }
        });

        btnStartAll.addActionListener(e -> triggerStartAllChosen());
        btnStopAll.addActionListener(e -> triggerStopAllChosen());
    }

    private void triggerStartAllChosen() {
        List<PhoneCard> chosenCards = new ArrayList<>();
        for (PhoneCard card : activeCardsMap.values()) {
            if (card.isSelected() && !card.isWorking()) {
                chosenCards.add(card);
            }
        }

        if (chosenCards.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Không có điện thoại nào rảnh rỗi hoặc được tích chọn để chạy!", "Thông báo", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String selectedFolderPath = txtVideoFolder.getText().trim();
        File inputFolder = new File(selectedFolderPath);
        if (!inputFolder.exists() || !inputFolder.isDirectory()) {
            JOptionPane.showMessageDialog(this, "Thư mục nguồn chứa video không hợp lệ!", "Lỗi", JOptionPane.ERROR_MESSAGE);
            return;
        }

        btnStartAll.setEnabled(false);
        btnBrowse.setEnabled(false);
        btnResetDB.setEnabled(false); // Khóa luôn nút reset khi đang chạy để an toàn dữ liệu

        if (farmThreadPool != null && !farmThreadPool.isShutdown()) {
            farmThreadPool.shutdownNow();
        }
        farmThreadPool = Executors.newFixedThreadPool(chosenCards.size());

        System.out.println("🔥 Kích hoạt ThreadPool chạy song song cho " + chosenCards.size() + " máy được chọn.");

        for (PhoneCard card : chosenCards) {
            farmThreadPool.execute(() -> {
                card.startAutomateWithFolder(selectedFolderPath);
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
        btnStartAll.setEnabled(true);
        btnBrowse.setEnabled(true);
        btnResetDB.setEnabled(true); // Mở khóa nút reset
        System.out.println("🛑 Hệ thống đã dừng đồng loạt và giải phóng khóa giao diện.");
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
                lblTotalPhones.setText("⚡ Thiết bị: " + activeCardsMap.size());
                gridPanel.revalidate();
                gridPanel.repaint();
            }
        });
    }
}