package top.miragedge.refreshpapi;

import org.bukkit.OfflinePlayer;
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
        // 全局占位符表
        String globalSql = "CREATE TABLE IF NOT EXISTS global_placeholders (" +
                     "name TEXT PRIMARY KEY NOT NULL, " +
                     "value INTEGER NOT NULL, " +
                     "last_updated INTEGER NOT NULL)";
        
        // 玩家特定占位符表
        String playerSql = "CREATE TABLE IF NOT EXISTS player_placeholders (" +
                     "player_uuid TEXT NOT NULL, " +
                     "name TEXT NOT NULL, " +
                     "value INTEGER NOT NULL, " +
                     "last_updated INTEGER NOT NULL, " +
                     "PRIMARY KEY (player_uuid, name))";
        
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(globalSql);
            stmt.execute(playerSql);
            plugin.getLogger().info("数据库表创建成功");
        } catch (SQLException e) {
            plugin.getLogger().severe("创建数据库表失败: " + e.getMessage());
        }
    }

    // 全局占位符操作
    public int getGlobalValue(String name) {
        return getGlobalValue(name, 0); // 默认值为0
    }

    public int getGlobalValue(String name, int defaultValue) {
        String sql = "SELECT value FROM global_placeholders WHERE name = ?";
        
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, name);
            ResultSet rs = pstmt.executeQuery();
            
            if (rs.next()) {
                return rs.getInt("value");
            } else {
                // 如果不存在，插入默认值
                updateGlobalValue(name, defaultValue);
                return defaultValue;
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("获取全局值失败: " + e.getMessage());
            return defaultValue;
        }
    }

    public void updateGlobalValue(String name, int value) {
        String sql = "INSERT OR REPLACE INTO global_placeholders (name, value, last_updated) VALUES (?, ?, ?)";
        
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, name);
            pstmt.setInt(2, value);
            pstmt.setLong(3, System.currentTimeMillis());
            pstmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("更新全局值失败: " + e.getMessage());
        }
    }

    public void removeGlobalPlaceholder(String name) {
        String sql = "DELETE FROM global_placeholders WHERE name = ?";
        
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, name);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("移除全局变量失败: " + e.getMessage());
        }
    }

    // 玩家特定占位符操作
    public int getPlayerValue(OfflinePlayer player, String name) {
        return getPlayerValue(player, name, 0); // 默认值为0
    }

    public int getPlayerValue(OfflinePlayer player, String name, int defaultValue) {
        if (player == null) {
            plugin.getLogger().warning("尝试获取null玩家的值");
            return defaultValue;
        }
        
        String sql = "SELECT value FROM player_placeholders WHERE player_uuid = ? AND name = ?";
        
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, player.getUniqueId().toString());
            pstmt.setString(2, name);
            ResultSet rs = pstmt.executeQuery();
            
            if (rs.next()) {
                return rs.getInt("value");
            } else {
                // 如果不存在，插入默认值
                updatePlayerValue(player, name, defaultValue);
                return defaultValue;
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("获取玩家值失败: " + e.getMessage());
            e.printStackTrace();
            return defaultValue;
        }
    }

    public void updatePlayerValue(OfflinePlayer player, String name, int value) {
        String sql = "INSERT OR REPLACE INTO player_placeholders (player_uuid, name, value, last_updated) VALUES (?, ?, ?, ?)";
        
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, player.getUniqueId().toString());
            pstmt.setString(2, name);
            pstmt.setInt(3, value);
            pstmt.setLong(4, System.currentTimeMillis());
            pstmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("更新玩家值失败: " + e.getMessage());
        }
    }

    public void removePlayerPlaceholder(OfflinePlayer player, String name) {
        String sql = "DELETE FROM player_placeholders WHERE player_uuid = ? AND name = ?";
        
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, player.getUniqueId().toString());
            pstmt.setString(2, name);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("移除玩家变量失败: " + e.getMessage());
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