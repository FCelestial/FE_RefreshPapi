package top.miragedge.refreshpapi;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public final class FE_RefreshPapi extends JavaPlugin {

    private DatabaseManager databaseManager;
    private FERPPlaceholderExpansion placeholderExpansion;
    private ScheduledExecutorService scheduler;
    private Map<String, PlaceholderData> placeholders = new ConcurrentHashMap<>();
    private BukkitTask refreshTask;
    private Logger logger;

    @Override
    public void onEnable() {
        logger = getLogger();
        
        // 保存默认配置文件
        saveDefaultConfig();
        
        // 初始化数据库
        databaseManager = new DatabaseManager(this);
        databaseManager.initializeDatabase();
        
        // 加载配置
        loadConfigData();
        
        // 初始化调度器
        int threadPoolSize = getConfig().getInt("global_settings.thread_pool_size", 4);
        scheduler = Executors.newScheduledThreadPool(threadPoolSize);
        
        // 启动定时刷新任务
        startRefreshTask();
        
        // 注册PlaceholderAPI扩展
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            placeholderExpansion = new FERPPlaceholderExpansion(this);
            placeholderExpansion.register();
            logger.info("PlaceholderAPI 扩展已注册");
        } else {
            logger.warning("PlaceholderAPI 未找到，占位符功能将不可用");
        }
        
        logger.info("FE_RefreshPapi 插件已启用");
    }

    @Override
    public void onDisable() {
        // 取消定时任务
        if (refreshTask != null) {
            refreshTask.cancel();
        }
        
        // 关闭调度器
        if (scheduler != null) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        
        // 关闭数据库连接
        if (databaseManager != null) {
            databaseManager.close();
        }
        
        logger.info("FE_RefreshPapi 插件已禁用");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("ferp")) {
            if (!sender.hasPermission("ferp.admin")) {
                sender.sendMessage("§c你没有权限使用此命令！");
                return true;
            }
            return handleFerpCommand(sender, args);
        }
        return false;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("ferp.admin")) {
            return null;
        }
        
        if (command.getName().equalsIgnoreCase("ferp") && args.length == 2) {
            List<String> completions = new ArrayList<>();
            String subCommand = args[0].toLowerCase();
            
            if (subCommand.equals("set") || subCommand.equals("add") || subCommand.equals("remove")) {
                // 添加所有配置中的占位符名称作为补全选项
                if (getConfig().isConfigurationSection("refresh_placeholders")) {
                    for (String key : getConfig().getConfigurationSection("refresh_placeholders").getKeys(false)) {
                        completions.add(key);
                    }
                }
            }
            
            return completions;
        } else if (command.getName().equalsIgnoreCase("ferp") && args.length == 1) {
            List<String> completions = new ArrayList<>();
            completions.add("set");
            completions.add("add");
            completions.add("remove");
            completions.add("refresh");
            completions.add("reload");
            return completions;
        }
        return null;
    }

    private boolean handleFerpCommand(CommandSender sender, String[] args) {
        if (args.length == 0) {
            sender.sendMessage("§c用法: /ferp [set/add/remove/refresh/reload] [变量名] [数值]");
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "set":
                if (args.length != 3) {
                    sender.sendMessage("§c用法: /ferp set [变量名] [数值]");
                    return true;
                }
                return handleSetCommand(sender, args[1], args[2]);
            case "add":
                if (args.length != 3) {
                    sender.sendMessage("§c用法: /ferp add [变量名] [数值]");
                    return true;
                }
                return handleAddCommand(sender, args[1], args[2]);
            case "remove":
                if (args.length != 2) {
                    sender.sendMessage("§c用法: /ferp remove [变量名]");
                    return true;
                }
                return handleRemoveCommand(sender, args[1]);
            case "refresh":
                if (args.length != 1) {
                    sender.sendMessage("§c用法: /ferp refresh");
                    return true;
                }
                return handleRefreshCommand(sender);
            case "reload":
                if (args.length != 1) {
                    sender.sendMessage("§c用法: /ferp reload");
                    return true;
                }
                return handleReloadCommand(sender);
            default:
                sender.sendMessage("§c未知命令。用法: /ferp [set/add/remove/refresh/reload] [变量名] [数值]");
                return true;
        }
    }

    private boolean handleSetCommand(CommandSender sender, String placeholderName, String valueStr) {
        try {
            int value = Integer.parseInt(valueStr);
            
            // 更新数据库中的值
            databaseManager.updateValue(placeholderName, value);
            
            // 更新内存中的值
            PlaceholderData data = placeholders.get(placeholderName);
            if (data != null) {
                data.setValue(value);
                data.setLastRefreshTime(System.currentTimeMillis());
                // 重新计算下次刷新时间
                data.setNextRefreshTime(data.calculateNextRefreshTime());
            }
            
            sender.sendMessage("§a成功将变量 " + placeholderName + " 的值设置为 " + value);
            return true;
        } catch (NumberFormatException e) {
            sender.sendMessage("§c错误：数值必须是整数");
            return true;
        }
    }

    private boolean handleAddCommand(CommandSender sender, String placeholderName, String valueStr) {
        try {
            int value = Integer.parseInt(valueStr);
            
            // 获取当前值并加上新值
            int currentValue = databaseManager.getValue(placeholderName);
            int newValue = currentValue + value;
            
            // 更新数据库中的值
            databaseManager.updateValue(placeholderName, newValue);
            
            // 更新内存中的值
            PlaceholderData data = placeholders.get(placeholderName);
            if (data != null) {
                data.setValue(newValue);
                data.setLastRefreshTime(System.currentTimeMillis());
                // 重新计算下次刷新时间
                data.setNextRefreshTime(data.calculateNextRefreshTime());
            }
            
            sender.sendMessage("§a成功将变量 " + placeholderName + " 的值增加 " + value + "，当前值为 " + newValue);
            return true;
        } catch (NumberFormatException e) {
            sender.sendMessage("§c错误：数值必须是整数");
            return true;
        }
    }

    private boolean handleRemoveCommand(CommandSender sender, String placeholderName) {
        // 检查变量是否存在
        if (!placeholders.containsKey(placeholderName)) {
            sender.sendMessage("§c错误：变量 " + placeholderName + " 不存在");
            return true;
        }
        
        // 从数据库中移除记录
        databaseManager.removePlaceholder(placeholderName);
        
        // 从内存中移除
        placeholders.remove(placeholderName);
        
        sender.sendMessage("§a成功移除变量 " + placeholderName);
        return true;
    }

    private boolean handleRefreshCommand(CommandSender sender) {
        // 立即执行一次刷新
        refreshPlaceholders();
        sender.sendMessage("§a已触发所有变量的立即刷新");
        return true;
    }

    private boolean handleReloadCommand(CommandSender sender) {
        // 重新加载配置
        reloadConfig();
        loadConfigData();
        
        // 重新启动刷新任务
        if (refreshTask != null) {
            refreshTask.cancel();
        }
        startRefreshTask();
        
        sender.sendMessage("§a配置已重新加载");
        return true;
    }

    private void loadConfigData() {
        placeholders.clear();
        
        if (getConfig().isConfigurationSection("refresh_placeholders")) {
            for (String key : getConfig().getConfigurationSection("refresh_placeholders").getKeys(false)) {
                String path = "refresh_placeholders." + key;
                
                // 获取刷新模式
                String refreshMode = getConfig().getString(path + ".refresh_mode", "interval");
                
                // 根据模式获取相应参数
                int interval = 0;
                String cronExpression = null;
                
                if ("cron".equals(refreshMode)) {
                    cronExpression = getConfig().getString(path + ".cron_expression");
                    if (cronExpression == null) {
                        logger.warning("占位符 " + key + " 的cron_expression 未配置，跳过该占位符");
                        continue;
                    }
                } else {
                    if (!getConfig().isInt(path + ".interval")) {
                        logger.warning("占位符 " + key + " 的interval 未配置，跳过该占位符");
                        continue;
                    }
                    interval = getConfig().getInt(path + ".interval");
                }
                
                if (!getConfig().isInt(path + ".initial_value")) {
                    logger.warning("占位符 " + key + " 的initial_value 未配置，跳过该占位符");
                    continue;
                }
                
                int initialValue = getConfig().getInt(path + ".initial_value");
                String updateRule = getConfig().getString(path + ".update_rule", "increment");
                int minValue = getConfig().getInt(path + ".min_value", 0);
                int maxValue = getConfig().getInt(path + ".max_value", 100);
                
                // 从数据库获取当前值，如果不存在则使用初始值
                int currentValue = databaseManager.getValue(key, initialValue);
                
                PlaceholderData data = new PlaceholderData(key, refreshMode, interval, cronExpression, currentValue, updateRule, minValue, maxValue);
                data.setLastRefreshTime(System.currentTimeMillis()); // 设置为当前时间
                data.setNextRefreshTime(data.calculateNextRefreshTime()); // 设置下次刷新时间
                placeholders.put(key, data);
            }
        }
    }

    private void startRefreshTask() {
        int refreshInterval = 1; // 每秒检查一次是否需要刷新
        refreshTask = Bukkit.getScheduler().runTaskTimerAsynchronously(this, this::refreshPlaceholders, 20L, 20L * refreshInterval);
    }

    private void refreshPlaceholders() {
        long currentTime = System.currentTimeMillis();
        
        // 使用迭代器避免并发修改异常
        for (Map.Entry<String, PlaceholderData> entry : placeholders.entrySet()) {
            PlaceholderData data = entry.getValue();
            
            // 检查是否需要刷新
            if (data.isReadyToRefresh()) {
                // 根据更新规则更新值
                int newValue;
                if ("set".equals(data.getUpdateRule())) {
                    // set模式：重置为初始值
                    String path = "refresh_placeholders." + data.getName() + ".initial_value";
                    newValue = getConfig().getInt(path, data.getValue());
                } else if ("random".equals(data.getUpdateRule())) {
                    // random模式：生成随机值
                    newValue = data.getMinValue() + (int) (Math.random() * (data.getMaxValue() - data.getMinValue() + 1));
                } else { // 默认为increment
                    // increment模式：当前值加1
                    newValue = data.getValue() + 1;
                }
                
                // 更新数据库
                databaseManager.updateValue(data.getName(), newValue);
                
                // 更新内存中的值
                data.setValue(newValue);
                data.setLastRefreshTime(currentTime);
                // 重新计算下次刷新时间
                data.setNextRefreshTime(data.calculateNextRefreshTime());
                
                logger.fine("刷新变量 " + data.getName() + " 为值 " + newValue);
            }
        }
    }

    public PlaceholderData getPlaceholderData(String name) {
        return placeholders.get(name);
    }
    
    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }
    
    public Map<String, PlaceholderData> getPlaceholders() {
        return new HashMap<>(placeholders);
    }
}
