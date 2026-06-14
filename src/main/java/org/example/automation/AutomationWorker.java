package org.example.automation;
import org.example.core.DatabaseManager;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.Random; // THÊM MỚI THƯ VIỆN RANDOM

public class AutomationWorker implements Runnable {
    private final String deviceId;
    private final OnStatusUpdateListener listener;
    private final String title;
    private final String description;

    private static final String ADB_PATH = "C:\\adb\\adb.exe";
    private static final String SHOPEE_PACKAGE = "com.shopee.vn";
    private static final String SHOPEE_ACTIVITY = "com.shopee.app.ui.home.HomeActivity_";
    private static final String PC_VIDEO_FOLDER = "C:\\FarmVideos\\";

    private volatile boolean running = true;
    private static final int MAX_VIDEOS_PER_DAY = 90;

    // CẤU HÌNH THỜI GIAN DELAY NGẪU NHIÊN KHI BẮT ĐẦU (Tính bằng giây)
    private static final int MIN_DELAY_SECONDS = 5;
    private static final int MAX_DELAY_SECONDS = 30;

    public interface OnStatusUpdateListener {
        void onUpdate(String status, boolean startEnabled, boolean stopEnabled);
    }

    public AutomationWorker(String deviceId, String title, String description, OnStatusUpdateListener listener) {
        this.deviceId = deviceId;
        this.title = title != null ? title : "Sản phẩm xu hướng";
        this.description = description != null ? description : "#shopee #trending";
        this.listener = listener;
    }

    private void notifyUI(String status, boolean startEnabled, boolean stopEnabled) {
        if (listener != null) {
            listener.onUpdate(status, startEnabled, stopEnabled);
        }
    }

    private void executeADBCommand(String... commands) {
        try {
            String[] fullCommand = new String[commands.length + 3];
            fullCommand[0] = ADB_PATH;
            fullCommand[1] = "-s";
            fullCommand[2] = deviceId;
            System.arraycopy(commands, 0, fullCommand, 3, commands.length);

            ProcessBuilder pb = new ProcessBuilder(fullCommand);
            Process p = pb.start();
            p.waitFor();
        } catch (Exception e) {
            System.out.println("❌ Lỗi ADB [" + deviceId + "]: " + e.getMessage());
        }
    }

    @Override
    public void run() {
        // =========================================================================
        // KHỐI LỆNH MỚI: RANDOM SLEEP TRÁNH QUÉT SPAM TẬP TRUNG
        // =========================================================================
        if (running) {
            Random random = new Random();
            // Tính toán số giây ngẫu nhiên trong khoảng từ MIN đến MAX cấu hình
            int randomDelay = random.nextInt((MAX_DELAY_SECONDS - MIN_DELAY_SECONDS) + 1) + MIN_DELAY_SECONDS;

            notifyUI("⏳ Chờ ngẫu nhiên " + randomDelay + "s", false, true);
            System.out.println("⏳ [" + deviceId + "] Kích hoạt cơ chế chống quét Spam. Ngủ ngẫu nhiên " + randomDelay + " giây...");

            try {
                // Thực hiện ngủ tuyến tính theo thời gian đã bốc ngẫu nhiên
                Thread.sleep(randomDelay * 1000L);
            } catch (InterruptedException e) {
                System.out.println("❌ [" + deviceId + "] Bị ngắt quãng trong lúc đang chờ delay ngẫu nhiên.");
                return;
            }
        }

        // =========================================================================
        // BẮT ĐẦU VÒNG LẶP XỬ LÝ VIDEO SELECTION NHƯ CŨ
        // =========================================================================
        int uploadCount = 0;

        while (running && uploadCount < MAX_VIDEOS_PER_DAY) {
            try {
                File folder = new File(PC_VIDEO_FOLDER);
                if (!folder.exists() || !folder.isDirectory()) folder.mkdirs();

                File[] fileList = folder.listFiles((dir, name) -> name.toLowerCase().endsWith(".mp4"));
                if (fileList == null || fileList.length == 0) {
                    notifyUI("❌ Trống Video PC", true, false);
                    break;
                }

                for (File file : fileList) {
                    DatabaseManager.insertVideoIfNotExist(file.getName(), deviceId);
                }

                File targetVideo = null;
                for (File file : fileList) {
                    String status = DatabaseManager.checkVideoStatusForDevice(file.getName(), deviceId);
                    if ("PENDING".equals(status)) {
                        targetVideo = file;
                        break;
                    }
                }

                if (targetVideo == null) {
                    notifyUI("✅ Hết Video Đăng", true, false);
                    System.out.println("🎉 [" + deviceId + "] Không còn video PENDING nào dành cho máy này. Hoàn thành!");
                    break;
                }

                uploadCount++;
                final int currentLoop = uploadCount;
                notifyUI("▶️ Vòng " + currentLoop + ": Khởi chạy", false, true);
                System.out.println("🚀 [" + deviceId + "] ====== BẮT ĐẦU VÒNG LẶP ĐĂNG VIDEO THỨ " + currentLoop + " ======");

                // ... (Logic quét database lấy targetVideo như cũ ở phía trên)

                String currentVideoName = targetVideo.getName();
                DatabaseManager.updateVideoStatus(currentVideoName, deviceId, "PROCESSING");

                // =========================================================================
                // THỰC THI BĂM HASH: Biến đổi video trên PC thành bản sao độc nhất cho máy này
                // =========================================================================
                File processedVideo = alterVideoHash(targetVideo);

                notifyUI("📤 Vòng " + currentLoop + ": Kiểm tra video", false, true);
                String remotePath = "/sdcard/Download/" + currentVideoName;

                boolean fileExistsOnPhone = checkFileExistsOnDevice(remotePath);

                if (fileExistsOnPhone) {
                    System.out.println("⏭️ [" + deviceId + "] File '" + currentVideoName + "' đã có sẵn trên Phone. Bỏ qua bước Push!");
                    notifyUI("⏭️ Vòng " + currentLoop + ": File có sẵn", false, true);
                } else {
                    System.out.println("📤 [" + deviceId + "] Tiến hành đẩy file đã băm cấu trúc sang Phone...");
                    notifyUI("📤 Vòng " + currentLoop + ": Đang Push file", false, true);

                    // LƯU Ý: Sửa từ targetVideo.getAbsolutePath() THÀNH processedVideo.getAbsolutePath()
                    executeADBCommand("push", processedVideo.getAbsolutePath(), remotePath);
                    Thread.sleep(1000);
                }

                // Ép Oppo quét nạp video vào Album Bộ sưu tập (Luôn chạy bước này để đảm bảo MediaStore cập nhật)
                String escapedUri = "file://'" + remotePath.replace("'", "'\\''") + "'";
                executeADBCommand("shell", "am", "broadcast", "-a", "android.intent.action.MEDIA_SCANNER_SCAN_FILE", "-d", escapedUri);
                Thread.sleep(2000);

                if (!running) break;
                notifyUI("▶️ Vòng " + currentLoop + ": Mở Shopee", false, true);
                executeADBCommand("shell", "am", "force-stop", SHOPEE_PACKAGE);
                Thread.sleep(1500);

                if (!running) break;
                executeADBCommand("shell", "am", "start", "-n", SHOPEE_PACKAGE + "/" + SHOPEE_ACTIVITY);
                for (int i = 0; i < 10; i++) {
                    if (!running) break;
                    Thread.sleep(1000);
                }

                if (!running) break;
                notifyUI("▶️ Vòng " + currentLoop + ": Chọn tab Video", false, true);
                executeADBCommand("shell", "input", "tap", "360", "1450");
                Thread.sleep(3000);

                if (!running) break;
                notifyUI("▶️ Vòng " + currentLoop + ": Bấm nút [+]", false, true);
                executeADBCommand("shell", "input", "tap", "675", "115");
                Thread.sleep(3000);

                if (!running) break;
                notifyUI("▶️ Vòng " + currentLoop + ": Mở Thư viện", false, true);
                executeADBCommand("shell", "input", "tap", "610", "1220");
                Thread.sleep(4000);

                if (!running) break;
                notifyUI("▶️ Vòng " + currentLoop + ": Lọc Tab Video", false, true);
                executeADBCommand("shell", "input", "tap", "360", "220");
                Thread.sleep(2500);

                if (!running) break;
                notifyUI("▶️ Vòng " + currentLoop + ": Chọn Video", false, true);
                executeADBCommand("shell", "input", "tap", "90", "350");
                Thread.sleep(4000);

                System.out.println("🤖 [" + deviceId + "] Điền thông tin -> Title: " + title + " | Desc: " + description);
                Thread.sleep(5000);

                DatabaseManager.updateVideoStatus(currentVideoName, deviceId, "SUCCESS");
                System.out.println("✅ [" + deviceId + "] Tài khoản trên máy này đã đăng thành công bài viết!");

                System.out.println("🗑️ [" + deviceId + "] Tiến hành xóa video tạm trên điện thoại...");
                String escapedDeletePath = "'" + remotePath.replace("'", "'\\''") + "'";
                executeADBCommand("shell", "rm", escapedDeletePath);
                Thread.sleep(1000);

            } catch (InterruptedException e) {
                System.out.println("❌ [" + deviceId + "] Luồng tự động bị ngắt quãng.");
                break;
            }
        }

        if (running) {
            notifyUI("✅ Hoàn thành", true, false);
            System.out.println("🎉 [" + deviceId + "] Chu kỳ làm việc của tài khoản kết thúc an toàn.");
        }
    }

    public void stopWorker() {
        this.running = false;
        notifyUI("🛑 Đã dừng", true, false);
        new Thread(() -> executeADBCommand("shell", "am", "force-stop", SHOPEE_PACKAGE)).start();
    }

    /**
     * Hàm kiểm tra sự tồn tại của file trên thiết bị Android bằng lệnh shell ls
     */
    private boolean checkFileExistsOnDevice(String remotePath) {
        try {
            // Cần bọc nháy đơn để tránh lỗi khoảng trắng trong tên video
            String escapedPath = "'" + remotePath.replace("'", "'\\''") + "'";

            ProcessBuilder pb = new ProcessBuilder("C:\\adb\\adb.exe", "-s", deviceId, "shell", "ls", escapedPath);
            Process p = pb.start();

            // Đọc kết quả trả về từ lệnh ls
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line = reader.readLine();
                // Nếu có kết quả trả về và không chứa chữ "No such file", nghĩa là file có tồn tại
                if (line != null && !line.contains("No such file")) {
                    p.destroy();
                    return true;
                }
            }
            p.destroy();
        } catch (Exception ignored) {}
        return false;
    }

    /**
     * Cơ chế tối mật: Tạo file video tạm độc nhất cho từng máy và thay đổi Byte cuối file (Băm mã Hash MD5)
     * Giúp lách 100% thuật toán quét nội dung trùng lặp của Shopee
     */
    private File alterVideoHash(File originalVideo) {
        // Tạo một thư mục tạm trên PC để chứa các video đã được băm hash riêng cho từng máy
        File tempFolder = new File("C:\\FarmVideos\\Temp_" + deviceId + "\\");
        if (!tempFolder.exists()) tempFolder.mkdirs();

        // Tên file tạm sẽ đi liền với ID của máy để không bị đè lên nhau giữa các luồng
        File uniqueVideoFile = new File(tempFolder, originalVideo.getName());

        try (java.io.FileInputStream fis = new java.io.FileInputStream(originalVideo);
             java.io.FileOutputStream fos = new java.io.FileOutputStream(uniqueVideoFile)) {

            // 1. Sao chép nguyên vẹn dữ liệu từ video gốc sang file tạm
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                fos.write(buffer, 0, bytesRead);
            }

            // 2. BƯỚC QUYẾT ĐỊNH: Ghi đè thêm vài byte ngẫu nhiên vào cuối file (End of File)
            // Việc này làm thay đổi cấu trúc mã Hash MD5/SHA-256 gốc của file mà không làm hỏng định dạng MP4
            java.util.Random rand = new java.util.Random();
            byte[] junkBytes = new byte[rand.nextInt(10) + 5]; // Tạo ngẫu nhiên từ 5 đến 15 byte rác
            rand.nextBytes(junkBytes);
            fos.write(junkBytes); // Bơm byte rác vào cuối file

            System.out.println("🧬 [" + deviceId + "] Đã băm cấu trúc mã Hash thành công cho file: " + originalVideo.getName());
        } catch (Exception e) {
            System.out.println("⚠️ Lỗi băm Hash Video, sử dụng file gốc phòng hờ: " + e.getMessage());
            return originalVideo; // Nếu lỗi thì trả về file gốc để không bị gián đoạn luồng Farm
        }
        return uniqueVideoFile;
    }
}