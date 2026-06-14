package org.example.core;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class ADBCommand {
    private static final String ADB_PATH = "C:\\adb\\adb.exe";

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
     * Thực thi một lệnh shell bất kỳ trên một thiết bị cụ thể (Để trống để tích hợp sau)
     */
    public static void executeShell(String deviceId, String... commands) {
        // Sau này sẽ điền logic adb tap, input text, swipe vào đây
        System.out.println("[ADB] Gửi lệnh tới " + deviceId);
    }
}
