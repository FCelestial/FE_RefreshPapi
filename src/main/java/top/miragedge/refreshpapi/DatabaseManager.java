package top.miragedge.refreshpapi;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.*;

public class DatabaseManager {
    private FE_RefreshPapi plugin;
    private Connection connection;

    public DatabaseManager(FE_RefreshPapi plugin) {
        this.plugin = plugin;
    }

    public void initializeDatabase() {
        try {
            Class.forName("org.sqlite.JDBC");
            String dbPath = plugin.getDataFolder().getAbsolutePath() + File.separator + 
                           plugin.getConfig().getString("global_settings.database_file", "data.db");
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
            
            // 创建表
            createTables();
            
            plugin.getLogger().info("数据库连接成功");
        } catch (Exception e) {
            plugin.getLogger().severe("数据库初始化失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void createTables() {
        String sql = "CREATE TABLE IF NOT EXISTS placeholders (" +
                     "name TEXT PRIMARY KEY NOT NULL, " +
                     "value INTEGER NOT NULL, " +
                     "last_updated INTEGER NOT NULL)";
        
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(sql);
            plugin.getLogger().info("数据库表创建成功");
        } catch (SQLException e) {
            plugin.getLogger().severe("创建数据库表失败: " + e.getMessage());
        }
    }

    public int getValue(String name) {
        return getValue(name, 0); // 默认值为0
    }

    public int getValue(String name, int defaultValue) {
        String sql = "SELECT value FROM placeholders WHERE name = ?";
        
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, name);
            ResultSet rs = pstmt.executeQuery();
            
            if (rs.next()) {
                return rs.getInt("value");
            } else {
                // 如果不存在，插入默认值
                updateValue(name, defaultValue);
                return defaultValue;
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("获取值失败: " + e.getMessage());
            return defaultValue;
        }
    }

    public void updateValue(String name, int value) {
        String sql = "INSERT OR REPLACE INTO placeholders (name, value, last_updated) VALUES (?, ?, ?)";
        
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, name);
            pstmt.setInt(2, value);
            pstmt.setLong(3, System.currentTimeMillis());
            pstmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("更新值失败: " + e.getMessage());
        }
    }

    public void removePlaceholder(String name) {
        String sql = "DELETE FROM placeholders WHERE name = ?";
        
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, name);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("移除变量失败: " + e.getMessage());
        }
    }

    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                plugin.getLogger().info("数据库连接已关闭");
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("关闭数据库连接失败: " + e.getMessage());
        }
    }

    public Connection getConnection() {
        return connection;
    }
}