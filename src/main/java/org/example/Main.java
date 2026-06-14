package org.example;


import org.example.core.DatabaseManager;
import org.example.ui.PhoneFarmFrame;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;

public class Main {
    public static void main(String[] args) {
        // 1. Kích hoạt và dọn dẹp hệ thống cơ sở dữ liệu SQLite đầu tiên
        DatabaseManager.initDatabase();

        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}

        SwingUtilities.invokeLater(() -> {
            PhoneFarmFrame frame = new PhoneFarmFrame();
            frame.setVisible(true);
        });
    }
}
