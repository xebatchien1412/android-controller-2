package org.example.automation;

public class AutomationWorker implements Runnable {
    private final String deviceId;
    private final OnStatusUpdateListener listener;

    private static final String ADB_PATH = "C:\\adb\\adb.exe";
    private static final String SHOPEE_PACKAGE = "com.shopee.vn";
    private static final String SHOPEE_ACTIVITY = "com.shopee.app.ui.home.HomeActivity_";

    private volatile boolean running = true;

    public interface OnStatusUpdateListener {
        void onUpdate(String status, boolean startEnabled, boolean stopEnabled);
    }

    public AutomationWorker(String deviceId, OnStatusUpdateListener listener) {
        this.deviceId = deviceId;
        this.listener = listener;
    }

    @Override
    public void run() {
        notifyUI("▶️ Khởi động...", false, true);
        try {
            String shopeePackage = "com.shopee.vn";
            String shopeeActivity = "com.shopee.app.ui.home.HomeActivity_";

            // =========================================================================
            // BƯỚC 1: ÉP ĐÓNG APP TRƯỚC ĐỂ ĐẢM BẢO SẠCH FLOW
            // =========================================================================
            if (!running) return;
            notifyUI("▶️ B1: Khởi tạo App...", false, true);
            System.out.println("🤖 [" + deviceId + "] Ép đóng Shopee dọn sạch bộ nhớ...");
            executeADBCommand("shell", "am", "force-stop", shopeePackage);
            Thread.sleep(1500);

            // =========================================================================
            // BƯỚC 2: MỞ LẠI APP SHOPEE
            // =========================================================================
            if (!running) return;
            notifyUI("▶️ B2: Mở Shopee...", false, true);
            System.out.println("🤖 [" + deviceId + "] Mở lại Shopee sạch từ màn hình gốc...");
            executeADBCommand("shell", "am", "start", "-n", shopeePackage + "/" + shopeeActivity);

            // CHỜ DATA LOAD: Thao tác đúng như bạn chỉ định, đợi hẳn 10 giây cho mạng load xong data ổn định
            System.out.println("🤖 [" + deviceId + "] Đang chờ 10 giây cho thiết bị tải dữ liệu mạng ổn định...");
            for (int i = 0; i < 10; i++) {
                if (!running) return;
                Thread.sleep(1000);
            }

            // =========================================================================
            // BƯỚC 3: CLICK VÀO TAB "LIVE & VIDEO" (TỌA ĐỘ CHUẨN XÁC THEO ẢNH: 360, 1450)
            // =========================================================================
            if (!running) return;
            notifyUI("▶️ B3: Chọn tab Video...", false, true);
            System.out.println("🤖 [" + deviceId + "] Định vị: Click vào tab Live & Video (Tọa độ 360, 1450)...");
            executeADBCommand("shell", "input", "tap", "360", "1450");
            Thread.sleep(3000); // Chờ 3 giây cho tab chuyển mạch xong

            // =========================================================================
            // BƯỚC 4: CLICK VÀO NÚT DẤU CỘNG [+] ĐỂ ĐĂNG BÀI (TỌA ĐỘ CHUẨN XÁC THEO ẢNH: 675, 115)
            // =========================================================================
            if (!running) return;
            notifyUI("▶️ B4: Bấm nút [+]...", false, true);
            System.out.println("🤖 [" + deviceId + "] Hành động: Click vào nút [+] đăng bài (Tọa độ 675, 115)...");
            executeADBCommand("shell", "input", "tap", "675", "115");

            // Chờ 4 giây xem màn hình đã chuyển sang giao diện Đăng bài/Chọn video chưa
            for (int i = 0; i < 4; i++) {
                if (!running) return;
                Thread.sleep(1000);
            }

            if (running) {
                notifyUI("✅ Hoàn thành Flow", true, false);
                System.out.println("🎉 [" + deviceId + "] Đã kích hoạt menu đăng bài thành công!");
            }

        } catch (InterruptedException e) {
            System.out.println("❌ [" + deviceId + "] Luồng tự động hóa bị ngắt.");
        }
    }



    public void stopWorker() {
        this.running = false;
        notifyUI("🛑 Đã dừng", true, false);
        System.out.println("🛑 [" + deviceId + "] Bấm Stop: Tiến hành đóng ứng dụng khẩn cấp...");
        new Thread(() -> {
            executeADBCommand("shell", "am", "force-stop", SHOPEE_PACKAGE);
        }).start();
    }

    private void executeADBCommand(String... subCommands) {
        try {
            java.util.List<String> fullCommand = new java.util.ArrayList<>();
            fullCommand.add(ADB_PATH);
            fullCommand.add("-s");
            fullCommand.add(deviceId);
            for (String cmd : subCommands) {
                fullCommand.add(cmd);
            }
            ProcessBuilder pb = new ProcessBuilder(fullCommand);
            Process p = pb.start();
            p.waitFor();
        } catch (Exception e) {
            System.out.println("❌ Lỗi gửi lệnh ADB tới thiết bị " + deviceId + ": " + e.getMessage());
        }
    }

    private void notifyUI(String status, boolean startEnabled, boolean stopEnabled) {
        if (listener != null) {
            listener.onUpdate(status, startEnabled, stopEnabled);
        }
    }
}
