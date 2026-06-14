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
    private AutomationWorker worker;
    private Process streamProcess;

    // Bức ảnh chứa nội dung màn hình điện thoại do Java tự vẽ
    private BufferedImage screenImage = null;
    private final JPanel renderPanel;
    private volatile boolean isStreaming = true;

    public PhoneCard(String deviceId) {
        this.deviceId = deviceId;

        setLayout(new BorderLayout(5, 5));
        setBorder(BorderFactory.createLineBorder(new Color(218, 222, 229), 2));
        setBackground(Color.WHITE);

        // A. PHẦN TRÊN: Vùng tự vẽ màn hình điện thoại (Xóa bỏ ô đen tĩnh cũ, giải thoát hoàn toàn chuột)
        renderPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                if (screenImage != null) {
                    // Tự động co dãn bức ảnh khít 100% theo ô lưới responsive của Tool
                    g.drawImage(screenImage, 0, 0, getWidth(), getHeight(), null);
                } else {
                    g.setColor(Color.LIGHT_GRAY);
                    g.drawString("Đang nạp màn hình Oppo...", getWidth() / 2 - 65, getHeight() / 2);
                }
            }
        };
        renderPanel.setBackground(Color.BLACK);
        add(renderPanel, BorderLayout.CENTER);

        // B. PHẦN PHÍA DƯỚI: Thanh điều khiển hiển thị ID và các nút bấm gọn gàng
        JPanel footerPanel = new JPanel(new BorderLayout(5, 5));
        footerPanel.setBackground(new Color(245, 245, 245));
        footerPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        JPanel infoPanel = new JPanel(new BorderLayout());
        infoPanel.setOpaque(false);
        JLabel lblId = new JLabel("📱 ID: " + deviceId);
        lblId.setFont(new Font("Segoe UI", Font.BOLD, 12));
        lblStatus = new JLabel("Chờ lệnh ");
        lblStatus.setForeground(Color.GRAY);
        lblStatus.setFont(new Font("Segoe UI", Font.BOLD, 11));
        infoPanel.add(lblId, BorderLayout.WEST);
        infoPanel.add(lblStatus, BorderLayout.EAST);
        footerPanel.add(infoPanel, BorderLayout.NORTH);

        JPanel buttonsPanel = new JPanel(new GridLayout(1, 2, 5, 0));
        buttonsPanel.setOpaque(false);
        btnStart = new JButton("Start");
        btnStart.setBackground(new Color(40, 167, 69));
        btnStart.setForeground(Color.WHITE);
        btnStart.setFocusPainted(false);

        btnPause = new JButton("Pause");
        btnPause.setEnabled(false);
        btnPause.setFocusPainted(false);

        buttonsPanel.add(btnStart);
        buttonsPanel.add(btnPause);
        footerPanel.add(buttonsPanel, BorderLayout.SOUTH);

        add(footerPanel, BorderLayout.SOUTH);

        initEvents();

        // Tắt toàn bộ scrcpy chạy lỗi trước đó
        killExistingScrcpy();

        // Kích hoạt luồng nạp ảnh màn hình liên tục truyền dữ liệu thô vào Java vẽ
        startPureJavaStream();
    }

    private void killExistingScrcpy() {
        try {
            ProcessBuilder pb = new ProcessBuilder("taskkill", "/F", "/IM", "scrcpy.exe");
            pb.start().waitFor();
        } catch (Exception ignored) {}
    }

    /**
     * GIẢI PHÁP TỐI HẬU: Dùng lệnh ADB bẩm sinh chụp ảnh màn hình liên tục truyền dữ liệu thô vào Java vẽ.
     * Giải thoát chuột 100%, không mở cửa sổ rời, nằm lọt thỏm ngay bên trong thân Tool.
     */
    private void startPureJavaStream() {
        Thread streamThread = new Thread(() -> {
            try {
                Thread.sleep(500);

                // Lệnh lấy luồng ảnh thô trực tiếp qua cáp USB rất nhẹ và mượt
                String[] command = {
                        "C:\\adb\\adb.exe", "-s", deviceId, "exec-out", "screencap", "-p"
                };

                while (isStreaming) {
                    long startTime = System.currentTimeMillis();

                    ProcessBuilder pb = new ProcessBuilder(command);
                    streamProcess = pb.start();

                    // Đọc luồng dữ liệu byte ảnh từ điện thoại đổ về
                    try (InputStream is = new BufferedInputStream(streamProcess.getInputStream())) {
                        BufferedImage img = javax.imageio.ImageIO.read(is);
                        if (img != null) {
                            this.screenImage = img;
                            // Ép Java vẽ lại khung hình mới lên giao diện Tool thay thế màn đen
                            renderPanel.repaint();
                        }
                    }
                    streamProcess.destroy();

                    // Khống chế tốc độ quét ảnh để máy tính không bị quá tải RAM
                    long elapsed = System.currentTimeMillis() - startTime;
                    if (elapsed < 40) {
                        Thread.sleep(40 - elapsed);
                    }
                }
            } catch (Exception e) {
                System.out.println("Luồng ảnh dừng: " + e.getMessage());
            }
        });
        streamThread.setDaemon(true);
        streamThread.start();
    }

    private void initEvents() {
        // Thay đổi chữ hiển thị của nút thứ hai thành Stop cho đúng tính năng
        btnPause.setText("Stop");

        btnStart.addActionListener(e -> {
            // Khi bấm Start, nếu luồng cũ đang chạy thì ép hủy đi trước để chạy lại luồng mới sạch hoàn toàn
            if (worker != null) {
                worker.stopWorker();
            }
            // Khởi tạo luồng mới chạy từ đầu
            worker = new AutomationWorker(deviceId, this::updateButtonStates);
            new Thread(worker).start();
        });

        btnPause.addActionListener(e -> {
            if (worker != null) {
                worker.stopWorker(); // Gọi lệnh dừng kịch bản và đóng app Shopee
            }
        });
    }

    private void updateButtonStates(String statusText, boolean startEnabled, boolean pauseEnabled) {
        SwingUtilities.invokeLater(() -> {
            lblStatus.setText(statusText);
            if (statusText.contains("Đang chạy")) lblStatus.setForeground(new Color(0, 123, 255));
            else if (statusText.contains("Tạm dừng")) lblStatus.setForeground(Color.ORANGE);
            else if (statusText.contains("Hoàn thành")) lblStatus.setForeground(new Color(40, 167, 69));

            btnStart.setEnabled(startEnabled);
            btnPause.setEnabled(pauseEnabled);
        });
    }

    public void destroyWorker() {
        this.isStreaming = false;
        if (worker != null) {
            worker.stopWorker();
        }
        if (streamProcess != null) {
            streamProcess.destroy();
        }
    }
}