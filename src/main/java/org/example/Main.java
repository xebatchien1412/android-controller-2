package org.example;


import org.example.ui.PhoneFarmFrame;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;

public class Main {
    public static void main(String[] args) {
        // Áp dụng giao diện giống hệ điều hành Windows hiện tại
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}

        // Khởi chạy giao diện chính trên luồng an toàn của Swing
        SwingUtilities.invokeLater(() -> {
            PhoneFarmFrame frame = new PhoneFarmFrame();
            frame.setVisible(true);
        });
    }
}
