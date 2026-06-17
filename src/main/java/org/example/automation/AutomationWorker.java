package org.example.automation;

import org.example.core.DatabaseManager;
import org.example.core.ADBCommand; // Import lớp ADB nâng cấp

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.Random;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class AutomationWorker implements Runnable {
    private final String deviceId;
    private final OnStatusUpdateListener listener;
    private final String rootFolderPath;

    private static final String ADB_PATH = "C:\\adb\\adb.exe";
    private static final String SHOPEE_PACKAGE = "com.shopee.vn";
    private static final String SHOPEE_ACTIVITY = "com.shopee.app.ui.home.HomeActivity_";

    private volatile boolean running = true;
    private static final int MAX_VIDEOS_PER_DAY = 90;
    private static final int MIN_DELAY_SECONDS = 5;
    private static final int MAX_DELAY_SECONDS = 30;

    public interface OnStatusUpdateListener {
        void onUpdate(String status, boolean startEnabled, boolean stopEnabled);
    }

    public AutomationWorker(String deviceId, String rootFolderPath, OnStatusUpdateListener listener) {
        this.deviceId = deviceId;
        this.rootFolderPath = rootFolderPath;
        this.listener = listener;
    }

    private void notifyUI(String status, boolean startEnabled, boolean stopEnabled) {
        if (listener != null) {
            listener.onUpdate(status, startEnabled, stopEnabled);
        }
    }

    private void executeADBCommand(String... commands) {
        // Chuyển hướng xử lý qua hàm tập trung của ADBCommand để tối ưu tài nguyên
        ADBCommand.executeADB(deviceId, commands);
    }

    private String parseMetadata(File txtFile) {
        String desc = "#shopee #trending";
        List<String> affiliateLinks = new ArrayList<>();

        try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(txtFile), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.toLowerCase().startsWith("description:")) {
                    desc = line.substring(12).trim();
                } else if (line.toLowerCase().startsWith("link affiliate:")) {
                    String rawLinks = line.substring(15).trim();
                    String[] parts = rawLinks.split(",");
                    for (String part : parts) {
                        if (!part.trim().isEmpty() && affiliateLinks.size() < 6) {
                            affiliateLinks.add(part.trim());
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("⚠️ Lỗi đọc file metadata.txt, dùng text mặc định: " + e.getMessage());
        }

        StringBuilder linksBuilder = new StringBuilder();
        for (int i = 0; i < affiliateLinks.size(); i++) {
            linksBuilder.append(" Link ").append(i + 1).append(": ").append(affiliateLinks.get(i));
        }

        return desc + linksBuilder.toString();
    }

    private File alterVideoHash(File originalVideo) {
        File tempFolder = new File("C:\\FarmVideos\\Temp_" + deviceId + "\\");
        if (!tempFolder.exists()) tempFolder.mkdirs();
        File uniqueVideoFile = new File(tempFolder, originalVideo.getName());

        try (java.io.FileInputStream fis = new java.io.FileInputStream(originalVideo);
             java.io.FileOutputStream fos = new java.io.FileOutputStream(uniqueVideoFile)) {
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                fos.write(buffer, 0, bytesRead);
            }
            Random rand = new Random();
            byte[] junkBytes = new byte[rand.nextInt(10) + 5];
            rand.nextBytes(junkBytes);
            fos.write(junkBytes);
            System.out.println("🧬 [" + deviceId + "] Đã băm cấu trúc mã Hash xong cho: " + originalVideo.getName());
        } catch (Exception e) {
            return originalVideo;
        }
        return uniqueVideoFile;
    }

    private boolean checkFileExistsOnDevice(String remotePath) {
        try {
            String escapedPath = "'" + remotePath.replace("'", "'\\''") + "'";
            String res = ADBCommand.executeADB(deviceId, "shell", "ls", escapedPath);
            if (res != null && !res.isEmpty() && !res.contains("No such file")) {
                return true;
            }
        } catch (Exception ignored) {}
        return false;
    }

    @Override
    public void run() {
        try {
            ADBCommand.installADBKeyboardIfMissing(deviceId);
            // [TÍNH NĂNG NÂNG CAO]: Bảo vệ phần cứng và chuẩn bị bộ gõ khi bắt đầu luồng
            ADBCommand.optimizeHardwareScreen(deviceId, true); // Hạ độ sáng màn hình về 0%
            ADBCommand.enableADBKeyboard(deviceId);            // Bật bộ gõ ADB Tiếng Việt

            // Khởi động cơ chế Random Sleep để chống quét Farm tập trung
            if (running) {
                Random random = new Random();
                int randomDelay = random.nextInt((MAX_DELAY_SECONDS - MIN_DELAY_SECONDS) + 1) + MIN_DELAY_SECONDS;
                notifyUI("⏳ Chờ ngẫu nhiên " + randomDelay + "s", false, true);
                System.out.println("⏳ [" + deviceId + "] Chống quét Spam. Ngủ ngẫu nhiên " + randomDelay + " giây...");
                try {
                    Thread.sleep(randomDelay * 1000L);
                } catch (InterruptedException e) {
                    System.out.println("❌ [" + deviceId + "] Luồng bị ngắt khi đang ngủ ngẫu nhiên.");
                    return;
                }
            }

            int uploadCount = 0;

            // BẮT ĐẦU CHU KỲ QUÉT THƯ MỤC CẤU TRÚC MỚI
            while (running && uploadCount < MAX_VIDEOS_PER_DAY) {
                try {
                    File rootDir = new File(rootFolderPath);
                    if (!rootDir.exists() || !rootDir.isDirectory()) {
                        notifyUI("❌ Thư mục gốc lỗi", true, false);
                        break;
                    }

                    File[] subFolders = rootDir.listFiles(File::isDirectory);
                    if (subFolders == null || subFolders.length == 0) {
                        notifyUI("❌ Trống Pack Video", true, false);
                        break;
                    }

                    File targetVideoFile = null;
                    File targetMetadataFile = null;
                    String currentPackName = "";

                    for (File subFolder : subFolders) {
                        File[] mp4Files = subFolder.listFiles((dir, name) -> name.toLowerCase().endsWith(".mp4"));
                        File[] txtFiles = subFolder.listFiles((dir, name) -> name.toLowerCase().equals("metadata.txt"));

                        if (mp4Files != null && mp4Files.length > 0 && txtFiles != null && txtFiles.length > 0) {
                            File videoFile = mp4Files[0];
                            String status = DatabaseManager.checkVideoStatusForDevice(videoFile.getName(), deviceId);

                            if ("PENDING".equals(status) || "NOT_EXISTS".equals(status)) {
                                targetVideoFile = videoFile;
                                targetMetadataFile = txtFiles[0];
                                currentPackName = subFolder.getName();
                                DatabaseManager.insertVideoIfNotExist(videoFile.getName(), deviceId);
                                break;
                            }
                        }
                    }

                    if (targetVideoFile == null) {
                        notifyUI("✅ Hết Video Đăng", true, false);
                        System.out.println("🎉 [" + deviceId + "] Đã xử lý toàn bộ các Pack Video trong thư mục. Dừng luồng!");
                        break;
                    }

                    uploadCount++;
                    final int currentLoop = uploadCount;
                    String currentVideoName = targetVideoFile.getName();

                    notifyUI("▶️ Vòng " + currentLoop + ": Đang chạy", false, true);
                    System.out.println("🚀 [" + deviceId + "] ====== BẮT ĐẦU VÒNG " + currentLoop + " [Pack: " + currentPackName + "] ======");

                    DatabaseManager.updateVideoStatus(currentVideoName, deviceId, "PROCESSING");

                    File processedVideo = alterVideoHash(targetVideoFile);

                    String remotePath = "/sdcard/Download/" + currentVideoName;
                    boolean fileExistsOnPhone = checkFileExistsOnDevice(remotePath);

                    if (fileExistsOnPhone) {
                        System.out.println("⏭️ [" + deviceId + "] File đã nằm sẵn trên Phone. Bỏ qua bước Push.");
                    } else {
                        System.out.println("📤 [" + deviceId + "] Đang push file video đã băm cấu trúc sang Phone...");
                        executeADBCommand("push", processedVideo.getAbsolutePath(), remotePath);
                        Thread.sleep(1000);
                    }

                    String escapedUri = "file://'" + remotePath.replace("'", "'\\''") + "'";
                    executeADBCommand("shell", "am", "broadcast", "-a", "android.intent.action.MEDIA_SCANNER_SCAN_FILE", "-d", escapedUri);
                    Thread.sleep(2000);

                    // KỊCH BẢN GIẢ LẬP CLICK UI VÀ NHẬP TEXT TRÊN APP SHOPEE
                    notifyUI("▶️ Vòng " + currentLoop + ": Mở Shopee", false, true);
                    executeADBCommand("shell", "am", "force-stop", SHOPEE_PACKAGE);
                    Thread.sleep(2000);

                    executeADBCommand("shell", "am", "start", "-n", SHOPEE_PACKAGE + "/" + SHOPEE_ACTIVITY);
                    for (int i = 0; i < 10; i++) {
                        if (!running) break;
                        Thread.sleep(1000);
                    }

                    if (!running) break;
                    notifyUI("▶️ Vòng " + currentLoop + ": Chọn tab Video", false, true);
                    executeADBCommand("shell", "input", "tap", "360", "1510");
                    Thread.sleep(4000);

                    if (!running) break;
                    notifyUI("▶️ Vòng " + currentLoop + ": Bấm nút [+]", false, true);
                    executeADBCommand("shell", "input", "tap", "660", "100");
                    Thread.sleep(4000);

                    if (!running) break;
                    notifyUI("▶️ Vòng " + currentLoop + ": Mở Thư viện", false, true);
                    executeADBCommand("shell", "input", "tap", "610", "1220");
                    Thread.sleep(5000);

                    if (!running) break;
                    notifyUI("▶️ Vòng " + currentLoop + ": Lọc Tab Video", false, true);
                    executeADBCommand("shell", "input", "tap", "360", "220");
                    Thread.sleep(3000);

                    if (!running) break;
                    notifyUI("▶️ Vòng " + currentLoop + ": Chọn Video Ô 1", false, true);
                    executeADBCommand("shell", "input", "tap", "90", "270");
                    Thread.sleep(3000);

                    if (!running) break;
                    notifyUI("▶️ Vòng " + currentLoop + ": Bấm Tiếp theo 1", false, true);
                    executeADBCommand("shell", "input", "tap", "600", "1375");
                    Thread.sleep(6000);

                    if (!running) break;
                    notifyUI("▶️ Vòng " + currentLoop + ": Qua màn Edit", false, true);
                    executeADBCommand("shell", "input", "tap", "595", "1430");
                    Thread.sleep(6000);

                    if (!running) break;
                    notifyUI("▶️ Vòng " + currentLoop + ": Thêm sản phẩm", false, true);
                    executeADBCommand("shell", "input", "tap", "570", "330");
                    Thread.sleep(4000);

                    if (!running) break;
                    notifyUI("▶️ Vòng " + currentLoop + ": Chọn dán link", false, true);
                    executeADBCommand("shell", "input", "tap", "670", "80");
                    Thread.sleep(4000);

                    if (!running) break;
                    notifyUI("▶️ Vòng " + currentLoop + ": Kích hoạt ô nhập", false, true);
                    executeADBCommand("shell", "input", "tap", "360", "250");
                    Thread.sleep(2000);

                    List<String> rawAffiliateLinks = new ArrayList<>();
                    try (BufferedReader br = new BufferedReader(new java.io.InputStreamReader(new java.io.FileInputStream(targetMetadataFile), java.nio.charset.StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = br.readLine()) != null) {
                            if (line.trim().toLowerCase().startsWith("link affiliate:")) {
                                String[] parts = line.substring(15).trim().split(",");
                                for (String part : parts) {
                                    if (!part.trim().isEmpty() && rawAffiliateLinks.size() < 6) {
                                        rawAffiliateLinks.add(part.trim());
                                    }
                                }
                            }
                        }
                    } catch (Exception ignored) {}

                    StringBuilder finalLinksText = new StringBuilder();
                    for (int i = 0; i < rawAffiliateLinks.size(); i++) {
                        finalLinksText.append(rawAffiliateLinks.get(i));
                        if (i < rawAffiliateLinks.size() - 1) {
                            finalLinksText.append("\n");
                        }
                    }

                    System.out.println("🤖 [" + deviceId + "] Đẩy link vào bộ nhớ tạm hệ thống...");
                    executeADBCommand("shell", "content", "insert", "--uri", "content://clipboard/text", "--bind", "text:s:\"" + finalLinksText.toString() + "\"");
                    Thread.sleep(1500);

                    executeADBCommand("shell", "input", "keyevent", "279");
                    Thread.sleep(2000);

                    notifyUI("▶️ Vòng " + currentLoop + ": Ấn nút Nhập", false, true);
                    executeADBCommand("shell", "input", "tap", "490", "420");
                    Thread.sleep(4000);

                    if (!running) break;
                    notifyUI("▶️ Vòng " + currentLoop + ": Tích Chọn tất cả", false, true);
                    executeADBCommand("shell", "input", "tap", "100", "1370");
                    Thread.sleep(2000);

                    if (!running) break;
                    notifyUI("▶️ Vòng " + currentLoop + ": Xác nhận Thêm", false, true);
                    executeADBCommand("shell", "input", "tap", "540", "1370");
                    Thread.sleep(6000);

                    if (!running) break;
                    notifyUI("▶️ Vòng " + currentLoop + ": Kích hoạt mô tả", false, true);
                    executeADBCommand("shell", "input", "tap", "550", "130");
                    Thread.sleep(2500); // Tăng lên 2.5s để bàn phím ADBKeyboard nạp sẵn sàng

                    // =========================================================================
                    // NÂNG CẤP BỘ GÕ TIẾNG VIỆT ĐỘC QUYỀN TRÁNH LỖI MẤT KÝ TỰ / CHẶN CLIPBOARD
                    // =========================================================================
                    String finalDescriptionText = parseMetadata(targetMetadataFile);
                    System.out.println("🤖 [" + deviceId + "] Gửi văn bản Tiếng Việt có dấu trực tiếp qua ADBKeyboard...");
                    ADBCommand.sendVietnameseText(deviceId, finalDescriptionText);
                    Thread.sleep(3000); // Chờ ký tự đổ vào ô text hoàn tất

                    if (!running) break;
                    notifyUI("▶️ Vòng " + currentLoop + ": Lưu mô tả", false, true);
                    executeADBCommand("shell", "input", "tap", "660", "75");
                    Thread.sleep(3000);

                    if (!running) break;
                    notifyUI("▶️ Vòng " + currentLoop + ": Tiến hành Đăng bài", false, true);
                    executeADBCommand("shell", "input", "tap", "580", "1430");

                    // =========================================================================
                    // CƠ CHẾ POLLING KIỂM TRA ACTIVITY ĐỘNG CẢI TIẾN (CHỊU TẢI THÔNG MINH)
                    // =========================================================================
                    System.out.println("⏳ [" + deviceId + "] Đang theo dõi tiến trình upload video lên mạng...");

                    boolean uploadFinished = false;
                    for (int checkRound = 1; checkRound <= 40; checkRound++) {
                        if (!running) break;

                        int secondsElapsed = checkRound * 3;
                        notifyUI("⏳ Upload: Đang đợi " + secondsElapsed + "s", false, true);
                        Thread.sleep(3000);

                        // Gọi hàm Polling quét sâu
                        boolean isStillOnUploadScreen = checkCurrentActivityContains("VideoShareActivity")
                                || checkCurrentActivityContains("PostVideoActivity")
                                || checkCurrentActivityContains("sharing.ShareActivity"); // Thêm activity share ngầm của bản cập nhật

                        if (!isStillOnUploadScreen) {
                            System.out.println("✅ [MÁY " + deviceId + "]: Upload thành công ở giây thứ " + secondsElapsed + "! Chuyển sang vòng tiếp theo.");
                            uploadFinished = true;
                            break;
                        }
                    }

                    if (!uploadFinished) {
                        System.out.println("⚠️ [MÁY " + deviceId + "]: Quá thời gian upload (Timeout). Ép đóng Shopee để cứu luồng.");
                        executeADBCommand("shell", "am", "force-stop", SHOPEE_PACKAGE);
                    }

                    DatabaseManager.updateVideoStatus(currentVideoName, deviceId, "SUCCESS");
                    System.out.println("✅ [" + deviceId + "] Hoàn tất xuất bản Pack Video thành công!");

                    System.out.println("🗑️ [" + deviceId + "] Tiến hành xóa video tạm trên điện thoại...");
                    String escapedDeletePath = "'" + remotePath.replace("'", "'\\''") + "'";
                    executeADBCommand("shell", "rm", escapedDeletePath);

                    if (processedVideo.exists() && !processedVideo.equals(targetVideoFile)) {
                        processedVideo.delete();
                    }
                    Thread.sleep(1000);

                    // KHỐI LỆNH BẢO DƯỠNG MÁY SAU MỖI 10 VÒNG ĐĂNG
                    if (currentLoop % 10 == 0) {
                        System.out.println("🧹 [" + deviceId + "] Hệ thống chạm mốc 10 video. Tiến hành dọn dẹp RAM và giải nhiệt cho Oppo...");
                        notifyUI("🧹 Vòng " + currentLoop + ": Đang bảo dưỡng máy", false, true);

                        executeADBCommand("shell", "pm", "clear-current-user-media-cache", SHOPEE_PACKAGE);
                        executeADBCommand("shell", "am", "kill-all");
                        Thread.sleep(1000);

                        executeADBCommand("shell", "am", "force-stop", "com.android.providers.media");
                        executeADBCommand("shell", "am", "force-stop", "com.coloros.gallery3d");
                        Thread.sleep(1000);

                        System.out.println("⏳ [" + deviceId + "] Cho phép máy nghỉ 15 giây để hạ nhiệt độ phần cứng...");
                        for (int i = 15; i > 0; i--) {
                            if (!running) break;
                            notifyUI("⏳ Hạ nhiệt: " + i + "s", false, true);
                            Thread.sleep(1000);
                        }
                    }
                } catch (InterruptedException e) {
                    System.out.println("❌ [" + deviceId + "] Tiến trình bị ngắt quãng.");
                    break;
                }
            }
        } finally {
            // [AN TOÀN TUYỆT ĐỐI]: Luôn luôn trả lại màn hình và bàn phím gốc cho máy khi kết thúc hoặc lỗi luồng
            ADBCommand.resetDefaultKeyboard(deviceId);
            ADBCommand.optimizeHardwareScreen(deviceId, false);

            if (running) {
                notifyUI("✅ Hoàn thành", true, false);
                System.out.println("🎉 [" + deviceId + "] Chu kỳ làm việc kết thúc an toàn.");
            }
        }
    }

    public void stopWorker() {
        this.running = false;
        notifyUI("🛑 Đã dừng", true, false);
        // Chạy bất đồng bộ việc dập app khi ấn Stop khẩn cấp
        new Thread(() -> {
            executeADBCommand("shell", "am", "force-stop", SHOPEE_PACKAGE);
            ADBCommand.resetDefaultKeyboard(deviceId);
            ADBCommand.optimizeHardwareScreen(deviceId, false);
        }).start();
    }

    /**
     * Hàm Polling Quét Động Activity Đã Sửa Lại Tối Ưu Tận Dụng executeADB Tập Trung
     */
    private boolean checkCurrentActivityContains(String activityName) {
        try {
            String dumpsysResult = ADBCommand.executeADB(deviceId, "shell", "dumpsys", "window", "displays");
            if (dumpsysResult == null || dumpsysResult.isEmpty()) return false;

            String[] lines = dumpsysResult.split("\n");
            for (String line : lines) {
                if (line.contains("mCurrentFocus") || line.contains("mFocusedApp") || line.contains("topFocusedApp") || line.contains("mTopActivityComponent")) {
                    if (line.contains(activityName)) {
                        return true;
                    }
                }
            }
        } catch (Exception ignored) {}
        return false;
    }
}