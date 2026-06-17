package org.example.core;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

public class DatabaseManager {
    private static final String DB_URL = "jdbc:sqlite:phonefarm.db";

    public static void initDatabase() {
        try (Connection conn = DriverManager.getConnection(DB_URL);
             Statement stmt = conn.createStatement()) {

            // Khởi tạo cấu trúc bảng với ràng buộc UNIQUE cặp đôi Máy + Video
            String createTableSQL = "CREATE TABLE IF NOT EXISTS video_logs (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "video_name TEXT," +
                    "device_id TEXT," +
                    "status TEXT," + // PENDING, PROCESSING, SUCCESS
                    "updated_at DATETIME DEFAULT CURRENT_TIMESTAMP," +
                    "UNIQUE(video_name, device_id)" +
                    ");";
            stmt.execute(createTableSQL);

            // ĐÃ XÓA BỎ LỆNH UPDATE TỰ ĐỘNG KHỞI TẠO ĐỂ BẢO VỆ NHẬT KÝ CHẠY THẬT
            System.out.println("🗄️ [Database] Khởi tạo cấu trúc UNIQUE (Video + Device ID) thành công!");
        } catch (Exception e) {
            System.out.println("❌ Lỗi khởi tạo Database: " + e.getMessage());
        }
    }

    /**
     * HÀM NÂNG CẤP: Chủ động đặt lại toàn bộ trạng thái Video về PENDING khi được gọi từ UI
     */
    public static void resetAllVideoStatus() {
        String resetSQL = "UPDATE video_logs SET status = 'PENDING';";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             Statement stmt = conn.createStatement()) {
            stmt.execute(resetSQL);
            System.out.println("🗄️ [Database] Đã đặt lại toàn bộ trạng thái Video về PENDING thành công!");
        } catch (Exception e) {
            System.out.println("❌ Lỗi Reset Database: " + e.getMessage());
        }
    }

    public static String checkVideoStatusForDevice(String videoName, String deviceId) {
        String query = "SELECT status FROM video_logs WHERE video_name = ? AND device_id = ?";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setString(1, videoName);
            pstmt.setString(2, deviceId);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getString("status");
            }
        } catch (Exception ignored) {}
        return "NOT_EXISTS";
    }

    public static void insertVideoIfNotExist(String videoName, String deviceId) {
        String sql = "INSERT OR IGNORE INTO video_logs(video_name, device_id, status) VALUES(?, ?, 'PENDING')";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, videoName);
            pstmt.setString(2, deviceId);
            pstmt.executeUpdate();
        } catch (Exception ignored) {}
    }

    public static void updateVideoStatus(String videoName, String deviceId, String status) {
        String sql = "INSERT OR REPLACE INTO video_logs(video_name, device_id, status, updated_at) " +
                "VALUES(?, ?, ?, CURRENT_TIMESTAMP)";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, videoName);
            pstmt.setString(2, deviceId);
            pstmt.setString(3, status);
            pstmt.executeUpdate();
            System.out.println("💾 [DB SUCCESS] Đã lưu trạng thái '" + status + "' cho file: " + videoName);
        } catch (Exception e) {
            System.out.println("❌ Lỗi nghiêm trọng SQLite: " + e.getMessage());
        }
    }
}