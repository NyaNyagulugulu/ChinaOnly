package neko.chinaOnly;

import com.google.gson.JsonObject;

import java.io.File;
import java.sql.*;

public class DatabaseManager {
    private static final String DB_FILE = "chinaonly.db";
    private static final String DB_PATH = "plugins/ChinaOnly/" + DB_FILE;
    private Connection connection;

    public DatabaseManager() {
        initDatabase();
    }

    private void initDatabase() {
        try {
            // 创建插件数据目录
            File dataFolder = new File("plugins/ChinaOnly");
            if (!dataFolder.exists()) {
                dataFolder.mkdirs();
            }

            // 连接到SQLite数据库
            connection = DriverManager.getConnection("jdbc:sqlite:" + DB_PATH);
            
            // 创建IP信息表
            String createTableSQL = "CREATE TABLE IF NOT EXISTS ip_info (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "ip TEXT UNIQUE NOT NULL," +
                    "country_code TEXT," +
                    "country TEXT," +
                    "region TEXT," +
                    "city TEXT," +
                    "isp TEXT," +
                    "org TEXT," +
                    "proxy BOOLEAN," +
                    "is_china_region BOOLEAN," +
                    "last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                    ");";
            
            try (Statement stmt = connection.createStatement()) {
                stmt.execute(createTableSQL);
            }
            
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /**
     * 从数据库中获取IP信息
     */
    public IPInfo getIPInfo(String ip) {
        String selectSQL = "SELECT * FROM ip_info WHERE ip = ?";
        
        try (PreparedStatement pstmt = connection.prepareStatement(selectSQL)) {
            pstmt.setString(1, ip);
            ResultSet rs = pstmt.executeQuery();
            
            if (rs.next()) {
                return new IPInfo(
                    rs.getString("ip"),
                    rs.getString("country_code"),
                    rs.getString("country"),
                    rs.getString("region"),
                    rs.getString("city"),
                    rs.getString("isp"),
                    rs.getString("org"),
                    rs.getBoolean("proxy"),
                    rs.getBoolean("is_china_region"),
                    rs.getTimestamp("last_updated")
                );
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        
        return null;
    }

    /**
     * 将IP信息保存到数据库
     */
    public void saveIPInfo(String ip, JsonObject jsonResponse, boolean isFromChinaRegion) {
        String insertSQL = "INSERT OR REPLACE INTO ip_info " +
                "(ip, country_code, country, region, city, isp, org, proxy, is_china_region) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        
        try (PreparedStatement pstmt = connection.prepareStatement(insertSQL)) {
            pstmt.setString(1, ip);
            pstmt.setString(2, jsonResponse.has("countryCode") ? jsonResponse.get("countryCode").getAsString() : "");
            pstmt.setString(3, jsonResponse.has("country") ? jsonResponse.get("country").getAsString() : "");
            pstmt.setString(4, jsonResponse.has("regionName") ? jsonResponse.get("regionName").getAsString() : "");
            pstmt.setString(5, jsonResponse.has("city") ? jsonResponse.get("city").getAsString() : "");
            pstmt.setString(6, jsonResponse.has("isp") ? jsonResponse.get("isp").getAsString() : "");
            pstmt.setString(7, jsonResponse.has("org") ? jsonResponse.get("org").getAsString() : "");
            pstmt.setBoolean(8, jsonResponse.has("proxy") ? jsonResponse.get("proxy").getAsBoolean() : false);
            pstmt.setBoolean(9, isFromChinaRegion);
            
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /**
     * 关闭数据库连接
     */
    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}