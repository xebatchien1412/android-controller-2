package org.example.ui;

import org.example.automation.AutomationWorker;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.InputStream;

public class PhoneCard extends JPanel {
    private final String deviceId;
    private final JLabel lblStatus;
    private final JButton btnStart;
    private final JButton btnPause;
    private final JCheckBox chkSelect;
    private AutomationWorker worker;
    private Process streamProcess;

    private BufferedImage screenImage = null;
    private final JPanel renderPanel;
    private volatile boolean isStreaming = true;

    // Trạng thái chặn việc tạo trùng luồng
    private volatile boolean isWorking = false;

    public PhoneCard(String deviceId) {
        this.deviceId = deviceId;

        setLayout(new BorderLayout(5, 5));
        setBorder(BorderFactory.createLineBorder(new Color(218, 222, 229), 2));
        setBackground(Color.WHITE);

        renderPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                if (screenImage != null) {
                    g.drawImage(screenImage, 0, 0, getWidth(), getHeight(), null);
                } else {
                    g.setColor(Color.LIGHT_GRAY);
                    g.drawString("Đang nạp màn hình Oppo...", getWidth() / 2 - 65, getHeight() / 2);
                }
            }
        };
        renderPanel.setBackground(Color.BLACK);
        add(renderPanel, BorderLayout.CENTER);

        JPanel footerPanel = new JPanel(new BorderLayout(5, 5));
        footerPanel.setBackground(new Color(245, 245, 245));
        footerPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        JPanel infoPanel = new JPanel(new BorderLayout());
        infoPanel.setOpaque(false);

        JPanel leftInfoPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        leftInfoPanel.setOpaque(false);
        chkSelect = new JCheckBox();
        chkSelect.setSelected(true);
        chkSelect.setOpaque(false);
        JLabel lblId = new JLabel("📱 ID: " + deviceId);
        lblId.setFont(new Font("Segoe UI", Font.BOLD, 12));
        leftInfoPanel.add(chkSelect);
        leftInfoPanel.add(lblId);

        lblStatus = new JLabel("Chờ lệnh ");
        lblStatus.setForeground(Color.GRAY);
        lblStatus.setFont(new Font("Segoe UI", Font.BOLD, 11));

        infoPanel.add(leftInfoPanel, BorderLayout.WEST);
        infoPanel.add(lblStatus, BorderLayout.EAST);
        footerPanel.add(infoPanel, BorderLayout.NORTH);

        JPanel buttonsPanel = new JPanel(new GridLayout(1, 2, 5, 0));
        btnStart = new JButton("Start");
        btnStart.setBackground(new Color(40, 167, 69));
        btnStart.setForeground(Color.BLACK); // ĐỔI SANG MÀU ĐEN
        btnStart.setFocusPainted(false);

        btnPause = new JButton("Stop");
        btnPause.setBackground(new Color(220, 53, 69)); // Thêm màu đỏ cho đồng bộ nút tổng nếu chưa có
        btnPause.setForeground(Color.BLACK); // ĐỔI SANG MÀU ĐEN
        btnPause.setEnabled(false);
        btnPause.setFocusPainted(false);

        buttonsPanel.add(btnStart);
        buttonsPanel.add(btnPause);
        footerPanel.add(buttonsPanel, BorderLayout.SOUTH);

        add(footerPanel, BorderLayout.SOUTH);

        initEvents();
        killExistingScrcpy();
        startPureJavaStream();
    }

    /**
     * HÀM CHUẨN HOÁ: Khởi tạo Worker và truyền thẳng đường dẫn folder do User chỉ định từ UI vào.
     * Được gọi đồng bộ từ nút tổng START ALL CHOSEN trên PhoneFarmFrame.
     */
    public void startAutomateWithFolder(String customFolderPath) {
        if (isWorking) {
            System.out.println("⚠️ Thiết bị [" + deviceId + "] đang làm việc. Bỏ qua yêu cầu.");
            return;
        }

        // Phòng hờ nếu đường dẫn truyền vào lỗi hoặc null, gán thư mục mặc định để tránh crash luồng
        String finalPath = (customFolderPath == null || customFolderPath.trim().isEmpty())
                ? "D:\\demo" : customFolderPath; // Đổi sang ổ D đồng bộ thực tế của bạn

        isWorking = true;
        worker = new AutomationWorker(deviceId, finalPath, this::updateButtonStates);
        new Thread(worker).start();
    }

    public void stopAutomate() {
        if (worker != null) {
            worker.stopWorker();
        }
        isWorking = false;
    }

    public boolean isSelected() {
        return chkSelect.isSelected();
    }

    public void setSelected(boolean selected) {
        SwingUtilities.invokeLater(() -> {
            chkSelect.setSelected(selected);
        });
    }

    public boolean isWorking() {
        return isWorking;
    }

    public String getDeviceId() {
        return deviceId;
    }

    private void killExistingScrcpy() {
        try {
            new ProcessBuilder("taskkill", "/F", "/IM", "scrcpy.exe").start().waitFor();
        } catch (Exception ignored) {}
    }

    private void startPureJavaStream() {
        Thread streamThread = new Thread(() -> {
            try {
                Thread.sleep(500);
                String[] command = {"C:\\adb\\adb.exe", "-s", deviceId, "exec-out", "screencap", "-p"};
                while (isStreaming) {
                    long startTime = System.currentTimeMillis();
                    ProcessBuilder pb = new ProcessBuilder(command);
                    streamProcess = pb.start();

                    try (InputStream is = new BufferedInputStream(streamProcess.getInputStream())) {
                        BufferedImage img = javax.imageio.ImageIO.read(is);
                        if (img != null) {
                            this.screenImage = img;
                            renderPanel.repaint();
                        }
                    }
                    streamProcess.destroy();

                    long elapsed = System.currentTimeMillis() - startTime;
                    if (elapsed < 40) Thread.sleep(40 - elapsed);
                }
            } catch (Exception ignored) {}
        });
        streamThread.setDaemon(true);
        streamThread.start();
    }

    private void initEvents() {
        btnStart.addActionListener(e -> {
            // Tự động tìm lên Frame tổng (PhoneFarmFrame) để lấy đường dẫn thực tế trong JTextField
            String currentPath = "D:\\demo"; // Thao tác phòng hờ mặc định
            Container parent = getTopLevelAncestor();
            if (parent instanceof JFrame) {
                // Quét tìm JTextField chứa folder trên giao diện
                try {
                    // Cách này bốc trực tiếp text động từ giao diện của bạn mà không cần sửa kiến trúc Frame
                    for (Component comp : ((JFrame) parent).getContentPane().getComponents()) {
                        if (comp instanceof JPanel) {
                            for (Component subComp : ((JPanel) comp).getComponents()) {
                                if (subComp instanceof JPanel) {
                                    for (Component folderComp : ((JPanel) subComp).getComponents()) {
                                        if (folderComp instanceof JTextField) {
                                            currentPath = ((JTextField) folderComp).getText().trim();
                                        }
                                    }
                                }
                            }
                        }
                    }
                } catch (Exception ignored) {}
            }
            startAutomateWithFolder(currentPath);
        });

        btnPause.addActionListener(e -> stopAutomate());
    }

    private void updateButtonStates(String statusText, boolean startEnabled, boolean pauseEnabled) {
        SwingUtilities.invokeLater(() -> {
            lblStatus.setText(statusText);
            if (statusText.contains("Vòng")) {
                lblStatus.setForeground(new Color(0, 123, 255));
            } else if (statusText.contains("dừng") || statusText.contains("❌")) {
                lblStatus.setForeground(Color.RED);
                isWorking = false; // Trả trạng thái tự do khi bị dừng hoặc lỗi hoãn luồng
            } else if (statusText.contains("Hoàn thành") || statusText.contains("✅")) {
                lblStatus.setForeground(new Color(40, 167, 69));
                isWorking = false; // Trả trạng thái tự do khi chạy xong hết video
            }

            btnStart.setEnabled(startEnabled);
            btnPause.setEnabled(pauseEnabled);
        });
    }

    public void destroyWorker() {
        this.isStreaming = false;
        stopAutomate();
        if (streamProcess != null) streamProcess.destroy();
    }
}