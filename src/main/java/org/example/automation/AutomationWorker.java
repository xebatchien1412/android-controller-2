package org.example.automation;

import org.example.core.DatabaseManager;
import org.example.core.ADBCommand;

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
        try {
            // 1. Đẩy thẳng vào thư mục Temp của hệ thống Windows (C:\Users\...\AppData\Local\Temp)
            // Thư mục chạy tool của bạn sẽ sạch sẽ 100%, không bị đẻ folder rác bừa bãi
            File tempDir = new File(System.getProperty("java.io.tmpdir"), "Shopee_Farm_Temp_" + deviceId);
            if (!tempDir.exists()) tempDir.mkdirs();

            File uniqueVideoFile = new File(tempDir, originalVideo.getName());

            // 2. Bảo hiểm nâng cao: Tự động xóa file này ngay khi ứng dụng/JVM tắt
            // (Phòng hờ trường hợp luồng chính bị crash bất ngờ không kịp chạy lệnh xóa)
            uniqueVideoFile.deleteOnExit();

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
            }
            return uniqueVideoFile;
        } catch (Exception e) {
            System.out.println("⚠️ Lỗi băm Hash, dùng file gốc: " + e.getMessage());
            return originalVideo;
        }
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
            // [TÍNH NĂNG NÂNG CAO]: Thực thi chuẩn bị môi trường máy bộc phá ngay lập tức khi bấm Start
            ADBCommand.installADBKeyboardIfMissing(deviceId);
            ADBCommand.optimizeHardwareScreen(deviceId, true);
            ADBCommand.enableADBKeyboard(deviceId);

            // =========================================================================
            // 🔥 THÊM MỚI: DỌN SẠCH FILE CŨ CÒN SÓT LẠI DO LẦN PAUSE/STOP TRƯỚC ĐÓ
            // =========================================================================
            System.out.println("🧹 [" + deviceId + "] Khởi động máy: Tiến hành dọn sạch file video.mp4 dư thừa cũ trên điện thoại...");
            executeADBCommand("shell", "rm", "-f", "/sdcard/Download/video.mp4");
            Thread.sleep(1000); // Nghỉ 1 giây để Android cập nhật lại phân vùng lưu trữ

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
                        File[] txtFiles = subFolder.listFiles((dir, name) -> name.toLowerCase().endsWith("metadata.txt"));

                        if (mp4Files != null && mp4Files.length > 0 && txtFiles != null && txtFiles.length > 0) {
                            File videoFile = mp4Files[0];
                            currentPackName = subFolder.getName(); // Lấy tên thư mục con làm định danh (Ví dụ: Alexander Ferros 5052S)

                            // Kiểm tra trạng thái trong DB bằng TÊN PACK thay vì tên file video.mp4 trùng lặp
                            String status = DatabaseManager.checkVideoStatusForDevice(currentPackName, deviceId);

                            if ("PENDING".equals(status) || "NOT_EXISTS".equals(status)) {
                                targetVideoFile = videoFile;
                                targetMetadataFile = txtFiles[0];

                                // Đăng ký tên Pack vào database để cô lập luồng
                                DatabaseManager.insertVideoIfNotExist(currentPackName, deviceId);
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

                    DatabaseManager.updateVideoStatus(currentPackName, deviceId, "PROCESSING");

                    File processedVideo = alterVideoHash(targetVideoFile);

                    String remotePath = "/sdcard/Download/" + currentVideoName;
                    boolean fileExistsOnPhone = checkFileExistsOnDevice(remotePath);

                    if (fileExistsOnPhone) {
                        System.out.println("⏭️ [" + deviceId + "] File đã nằm sẵn trên Phone. Bỏ qua bước Push.");
                    } else {
                        System.out.println("📤 [" + deviceId + "] Đang push file video đã băm cấu trúc sang Phone...");
                        executeADBCommand("push", "\"" + processedVideo.getAbsolutePath() + "\"", remotePath);
                        Thread.sleep(1000);
                    }

                    String escapedUri = "file://'" + remotePath.replace("'", "'\\''") + "'";
                    executeADBCommand("shell", "am", "broadcast", "-a", "android.intent.action.MEDIA_SCANNER_SCAN_FILE", "-d", escapedUri);
                    Thread.sleep(2000);

                    // =========================================================================
// 🔥 ĐỌC FILE TEXT DUY NHẤT 1 LẦN: LẤY SẠCH CẢ LINK VÀ DESCRIPTION
// =========================================================================
                    String finalDescriptionText = "#shopee #trending"; // Văn bản mặc định phòng hờ
                    List<String> rawAffiliateLinks = new ArrayList<>();

                    try (BufferedReader br = new BufferedReader(new java.io.InputStreamReader(new java.io.FileInputStream(targetMetadataFile), java.nio.charset.StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = br.readLine()) != null) {
                            String trimmedLine = line.trim();
                            String lowerLine = trimmedLine.toLowerCase();

                            // 1. Nếu trúng dòng Link Affiliate
                            if (lowerLine.startsWith("link affiliate:")) {
                                String linksPart = trimmedLine.substring(trimmedLine.indexOf(":") + 1).trim();
                                String[] parts = linksPart.split(",");
                                for (String part : parts) {
                                    if (!part.trim().isEmpty() && rawAffiliateLinks.size() < 6) {
                                        rawAffiliateLinks.add(part.trim());
                                    }
                                }
                            }
                            // 2. Nếu trúng dòng Description
                            else if (lowerLine.startsWith("description:")) {
                                finalDescriptionText = trimmedLine.substring(trimmedLine.indexOf(":") + 1).trim();
                            }
                        }
                    } catch (Exception e) {
                        System.out.println("⚠️ Lỗi đọc file metadata.txt: " + e.getMessage());
                    }

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
                    // CHon tab 1 video 1 lan nua de skip alert
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
                    executeADBCommand("shell", "input", "tap", "610", "1440");
                    Thread.sleep(6000);

                    if (!running) break;
                    notifyUI("▶️ Vòng " + currentLoop + ": Qua màn Edit", false, true);
                    executeADBCommand("shell", "input", "tap", "595", "1430");
                    Thread.sleep(6000);

                    if (!running) break;
                    notifyUI("▶️ Vòng " + currentLoop + ": Thêm sản phẩm", false, true);
                    executeADBCommand("shell", "input", "tap", "600", "530");
                    Thread.sleep(10000);

                    if (!running) break;
                    notifyUI("▶️ Vòng " + currentLoop + ": Chọn dán link", false, true);
                    // Tọa độ chuẩn đã hạ thấp xuống dưới thanh trạng thái Oppo: (665, 115)
                    executeADBCommand("shell", "input", "tap", "665", "115");
                    Thread.sleep(4000);

                    if (!running) break;
                    notifyUI("▶️ Vòng " + currentLoop + ": Kích hoạt ô nhập", false, true);
                    executeADBCommand("shell", "input", "tap", "345", "405");
                    Thread.sleep(2000);

                    if (!running) break;
                    notifyUI("▶️ Vòng " + currentLoop + ": Dọn sạch ô nhập cũ", false, true);
                    executeADBCommand("shell", "input", "tap", "600", "225");
                    Thread.sleep(1500); // Chờ Shopee xóa sạch các link cũ về ô trống tinh khôi

                    System.out.println("🤖 [" + deviceId + "] Tìm thấy " + rawAffiliateLinks.size() + " liên kết hợp lệ.");

                    for (int i = 0; i < rawAffiliateLinks.size(); i++) {
                        String singleLink = rawAffiliateLinks.get(i);
                        System.out.println("🔗 -> Đang gõ link " + (i + 1) + ": " + singleLink);

                        // 🔥 THAY ĐỔI CHÍ MẠNG: Dùng lệnh 'input text' nguyên bản của ADB thay vì ADBKeyboard
                        // Lệnh này truyền chuỗi URL chứa dấu ':' và '/' chính xác 100%, không bao giờ bị nuốt chữ
                        executeADBCommand("shell", "input", "text", singleLink);
                        Thread.sleep(2000); // Đợi 2 giây để điện thoại hiển thị trọn vẹn tiến trình gõ link

                        // Gửi lệnh bấm phím ENTER (KEYCODE_ENTER = 66) để Shopee xác nhận và tự động tạo dòng mới
                        executeADBCommand("shell", "input", "keyevent", "66");
                        Thread.sleep(1200); // Đợi con trỏ chuột ổn định ở dòng tiếp theo
                    }

                    System.out.println("🎉 [" + deviceId + "] Đã hoàn thành gõ toàn bộ danh sách link!");

                    if (!running) break;
                    notifyUI("▶️ Vòng " + currentLoop + ": Ấn nút Nhập", false, true);
                    executeADBCommand("shell", "input", "tap", "360", "670");
                    Thread.sleep(7000);

                    if (!running) break;
                    notifyUI("▶️ Vòng " + currentLoop + ": Tích Chọn tất cả", false, true);
                    executeADBCommand("shell", "input", "tap", "60", "1380");
                    Thread.sleep(2000);

                    if (!running) break;
                    notifyUI("▶️ Vòng " + currentLoop + ": Xác nhận Thêm", false, true);
                    executeADBCommand("shell", "input", "tap", "540", "1370");
                    Thread.sleep(6000);

                    if (!running) break;
                    notifyUI("▶️ Vòng " + currentLoop + ": Kích hoạt mô tả", false, true);
                    executeADBCommand("shell", "input", "tap", "500", "230");
                    Thread.sleep(2500);

                    // 🔥 BƯỚC 1: Ép máy Oppo bật lại ADBKeyboard để giành quyền gõ chữ
                    System.out.println("⌨️ [" + deviceId + "] Đang ép kích hoạt lại ADBKeyboard cho ô nhập mới...");
                    ADBCommand.enableADBKeyboard(deviceId);
                    Thread.sleep(1500);

                    System.out.println("📝 Nội dung mô tả gốc: " + finalDescriptionText);

                    String escapedDescription = finalDescriptionText
                            .replace(" ", "\\ ")     // Biến khoảng trắng thành dấu cách thô hợp lệ cho Shell
                            .replace("#", "\\#")     // Biến dấu # thành ký tự thường, không bị hiểu nhầm là lệnh Comment
                            .replace("'", "\\'")     // Bảo vệ dấu nháy đơn nếu có
                            .replace("\"", "\\\"");   // Bảo vệ dấu nháy kép nếu có

                    System.out.println("🤖 [" + deviceId + "] Đang tiến hành gõ Mô tả trực tiếp qua lệnh 'input text'...");
                    executeADBCommand("shell", "input", "text", escapedDescription);
                    Thread.sleep(5000); // Tăng lên 5 giây vì chuỗi mô tả khá dài, chờ máy tuôn chữ xong

                    if (!running) break;
                    notifyUI("▶️ Vòng " + currentLoop + ": Lưu mô tả", false, true);
                    executeADBCommand("shell", "input", "tap", "640", "115");
                    Thread.sleep(4000); // Tăng lên 4 giây để app xử lý lưu và chuyển trang an toàn

//                    if (!running) break;
//                    notifyUI("▶️ Vòng " + currentLoop + ": Tiến hành Đăng bài", false, true);
//                    executeADBCommand("shell", "input", "tap", "580", "1430");
//
//                    System.out.println("⏳ [" + deviceId + "] Đang theo dõi tiến trình upload video lên mạng...");
//
//                    boolean uploadFinished = false;
//                    for (int checkRound = 1; checkRound <= 40; checkRound++) {
//                        if (!running) break;
//
//                        int secondsElapsed = checkRound * 3;
//                        notifyUI("⏳ Upload: Đang đợi " + secondsElapsed + "s", false, true);
//                        Thread.sleep(3000);
//
//                        boolean isStillOnUploadScreen = checkCurrentActivityContains("VideoShareActivity")
//                                || checkCurrentActivityContains("PostVideoActivity")
//                                || checkCurrentActivityContains("sharing.ShareActivity");
//
//                        if (!isStillOnUploadScreen) {
//                            System.out.println("✅ [MÁY " + deviceId + "]: Upload thành công ở giây thứ " + secondsElapsed + "! Chuyển sang vòng tiếp theo.");
//                            uploadFinished = true;
//                            break;
//                        }
//                    }
//
//                    if (!uploadFinished) {
//                        System.out.println("⚠️ [MÁY " + deviceId + "]: Quá thời gian upload (Timeout). Ép đóng Shopee để cứu luồng.");
//                        executeADBCommand("shell", "am", "force-stop", SHOPEE_PACKAGE);
//                    }
//
//                    DatabaseManager.updateVideoStatus(currentPackName, deviceId, "SUCCESS");
//                    System.out.println("✅ [" + deviceId + "] Hoàn tất xuất bản Pack Video thành công!");

                    System.out.println("🗑️ [" + deviceId + "] Tiến hành xóa video tạm trên điện thoại...");
                    String escapedDeletePath = "'" + remotePath.replace("'", "'\\''") + "'";
                    executeADBCommand("shell", "rm", escapedDeletePath);

                    if (processedVideo.exists() && !processedVideo.equals(targetVideoFile)) {
                        processedVideo.delete();
                    }
                    Thread.sleep(1000);

//                    if (currentLoop % 10 == 0) {
//                        System.out.println("🧹 [" + deviceId + "] Hệ thống chạm mốc 10 video. Tiến hành dọn dẹp RAM và giải nhiệt cho Oppo...");
//                        notifyUI("🧹 Vòng " + currentLoop + ": Đang bảo dưỡng máy", false, true);
//
//                        executeADBCommand("shell", "pm", "clear-current-user-media-cache", SHOPEE_PACKAGE);
//                        executeADBCommand("shell", "am", "kill-all");
//                        Thread.sleep(1000);
//
//                        executeADBCommand("shell", "am", "force-stop", "com.android.providers.media");
//                        executeADBCommand("shell", "am", "force-stop", "com.coloros.gallery3d");
//                        Thread.sleep(1000);
//
//                        System.out.println("⏳ [" + deviceId + "] Cho phép máy nghỉ 15 giây để hạ nhiệt độ phần cứng...");
//                        for (int i = 15; i > 0; i--) {
//                            if (!running) break;
//                            notifyUI("⏳ Hạ nhiệt: " + i + "s", false, true);
//                            Thread.sleep(1000);
//                        }
//                    }
                } catch (InterruptedException e) {
                    System.out.println("❌ [" + deviceId + "] Tiến trình bị ngắt quãng.");
                    break;
                }
            }
        } catch (InterruptedException e) {
            e.printStackTrace();

            ADBCommand.resetDefaultKeyboard(deviceId);
            ADBCommand.optimizeHardwareScreen(deviceId, false);

            if (running) {
                notifyUI("✅ Hoàn thành", true, false);
                System.out.println("🎉 [" + deviceId + "] Chu kỳ làm việc kết thoát an toàn.");
            }
        }
    }

    public void stopWorker() {
        this.running = false;
        notifyUI("🛑 Đã dừng", true, false);
        new Thread(() -> {
            executeADBCommand("shell", "am", "force-stop", SHOPEE_PACKAGE);
            ADBCommand.resetDefaultKeyboard(deviceId);
            ADBCommand.optimizeHardwareScreen(deviceId, false);
        }).start();
    }

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