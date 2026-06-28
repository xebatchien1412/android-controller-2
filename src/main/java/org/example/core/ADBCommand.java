package org.example.core;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

public class ADBCommand {
    private static final String ADB_PATH = "C:\\adb\\adb.exe";

    /**
     * Hàm Core: Thực thi lệnh ADB và trả về kết quả dạng String (Hỗ trợ quét Activity)
     */
    public static String executeADB(String deviceId, String... commands) {
        StringBuilder output = new StringBuilder();
        try {
            List<String> fullCommand = new ArrayList<>();
            fullCommand.add(ADB_PATH);
            if (deviceId != null && !deviceId.isEmpty()) {
                fullCommand.add("-s");
                fullCommand.add(deviceId);
            }
            for (String cmd : commands) {
                fullCommand.add(cmd);
            }

            ProcessBuilder pb = new ProcessBuilder(fullCommand);
            Process p = pb.start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }
            p.waitFor();
        } catch (Exception e) {
            System.out.println("❌ Lỗi Core ADB [" + deviceId + "]: " + e.getMessage());
        }
        return output.toString().trim();
    }

    /**
     * Quét danh sách ID thiết bị đang kết nối
     */
    public static List<String> getConnectedDevices() {
        List<String> devices = new ArrayList<>();
        try {
            ProcessBuilder pb = new ProcessBuilder(ADB_PATH, "devices");
            Process process = pb.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            reader.readLine(); // Bỏ qua dòng tiêu đề
            while ((line = reader.readLine()) != null) {
                if (!line.trim().isEmpty() && line.contains("device")) {
                    String[] parts = line.split("\\s+");
                    if (parts.length >= 2 && "device".equals(parts[1])) {
                        devices.add(parts[0]);
                    }
                }
            }
            process.waitFor();
        } catch (Exception ignored) {}
        return devices;
    }

    /**
     * Hàm điều khiển độ sáng phần cứng Oppo về 0% (Tiết kiệm 30% nhiệt năng, chống chai pin)
     */
    public static void optimizeHardwareScreen(String deviceId, boolean turnOff) {
        // Đã gỡ bỏ tính năng tự động thay đổi độ sáng màn hình theo yêu cầu người dùng
    }

    /**
     * Kích hoạt bộ gõ ADBKeyboard làm bàn phím chính thức
     */
    public static void enableADBKeyboard(String deviceId) {
        executeADB(deviceId, "shell", "ime", "enable", "com.android.adbkeyboard/.AdbIME");
        executeADB(deviceId, "shell", "ime", "set", "com.android.adbkeyboard/.AdbIME");
    }

    /**
     * Trả lại bàn phím hệ thống mặc định
     */
    public static void resetDefaultKeyboard(String deviceId) {
        executeADB(deviceId, "shell", "ime", "reset");
    }

    /**
     * Gửi tiếng Việt có dấu mã hóa Base64 qua ADBKeyboard (Chuẩn chỉ 100% không mất chữ)
     */
    public static void sendVietnameseText(String deviceId, String text) {
        try {
            String base64Text = Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));
            // Phát broadcast lệnh nhập chuỗi Base64 tới ADBKeyboard
            executeADB(deviceId, "shell", "am", "broadcast", "-a", "ADB_INPUT_B64", "--es", "msg", base64Text);
        } catch (Exception e) {
            System.out.println("❌ Lỗi gửi text Base64 [" + deviceId + "]: " + e.getMessage());
        }
    }

    /**
     * Nhập text an toàn bằng cách tự động ngắt từ ngay sau mỗi nguyên âm và chữ d (ví dụ: a, e, i, o, u, y, d)
     * Giúp tránh tuyệt đối việc gõ Telex ghép dấu từ xa (ví dụ: y...j ghép thành ỵ) trên mọi bàn phím tiếng Việt.
     */
    public static void typeTextWithoutTelex(String deviceId, String text) {
        if (text == null || text.isEmpty()) return;

        String vowelsAndD = "aeiouydAEIOUYD";
        StringBuilder currentChunk = new StringBuilder();

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            currentChunk.append(c);

            // Nếu ký tự vừa thêm là nguyên âm hoặc chữ d, ta gửi chunk đi và ngắt từ bằng SPACE + BACKSPACE
            if (vowelsAndD.indexOf(c) >= 0) {
                executeADB(deviceId, "shell", "input", "text", currentChunk.toString());
                currentChunk.setLength(0);

                // Gửi phím SPACE (62) và BACKSPACE (67) để xoá bộ đệm gõ Telex trên điện thoại
                executeADB(deviceId, "shell", "input", "keyevent", "62");
                executeADB(deviceId, "shell", "input", "keyevent", "67");
            }
        }

        if (currentChunk.length() > 0) {
            executeADB(deviceId, "shell", "input", "text", currentChunk.toString());
        }
    }

    /**
     * Giữ nguyên cấu trúc cũ để không ảnh hưởng đến các thành phần khác nếu có gọi
     */
    public static void executeShell(String deviceId, String... commands) {
        executeADB(deviceId, commands);
    }

    public static void installADBKeyboardIfMissing(String deviceId) {
        // Kiểm tra xem máy đã cài gói này chưa
        String check = executeADB(deviceId, "shell", "pm", "list", "packages", "com.android.adbkeyboard");
        if (!check.contains("com.android.adbkeyboard")) {
            System.out.println("📥 [MÁY " + deviceId + "] Chưa có ADBKeyboard. Tiến hành cài đặt tự động...");
            // Đường dẫn tới file APK bạn tải về để trong thư mục dự án trên PC
            executeADB(deviceId, "install", "C:\\adb\\ADBKeyboard.apk");
        }
    }
}