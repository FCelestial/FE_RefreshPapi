package top.miragedge.refreshpapi;

public class PlaceholderData {
    private String name;
    private String refreshMode; // 刷新模式: interval 或 cron
    private int interval; // 刷新间隔（秒），仅在interval模式下有效
    private String cronExpression; // Cron表达式，仅在cron模式下有效
    private int value; // 当前值
    private String updateRule; // 更新规则 (increment, random, set)
    private int minValue; // 最小值（用于random模式）
    private int maxValue; // 最大值（用于random模式）
    private long lastRefreshTime; // 最后刷新时间
    private long nextRefreshTime; // 下次刷新时间

    public PlaceholderData(String name, String refreshMode, int interval, String cronExpression, int value, String updateRule, int minValue, int maxValue) {
        this.name = name;
        this.refreshMode = refreshMode;
        this.interval = interval;
        this.cronExpression = cronExpression;
        this.value = value;
        this.updateRule = updateRule;
        this.minValue = minValue;
        this.maxValue = maxValue;
        this.lastRefreshTime = System.currentTimeMillis();
        this.nextRefreshTime = calculateNextRefreshTime();
    }

    // Getters and Setters
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getRefreshMode() {
        return refreshMode;
    }

    public void setRefreshMode(String refreshMode) {
        this.refreshMode = refreshMode;
    }

    public int getInterval() {
        return interval;
    }

    public void setInterval(int interval) {
        this.interval = interval;
    }

    public String getCronExpression() {
        return cronExpression;
    }

    public void setCronExpression(String cronExpression) {
        this.cronExpression = cronExpression;
    }

    public int getValue() {
        return value;
    }

    public void setValue(int value) {
        this.value = value;
    }

    public String getUpdateRule() {
        return updateRule;
    }

    public void setUpdateRule(String updateRule) {
        this.updateRule = updateRule;
    }

    public int getMinValue() {
        return minValue;
    }

    public void setMinValue(int minValue) {
        this.minValue = minValue;
    }

    public int getMaxValue() {
        return maxValue;
    }

    public void setMaxValue(int maxValue) {
        this.maxValue = maxValue;
    }

    public long getLastRefreshTime() {
        return lastRefreshTime;
    }

    public void setLastRefreshTime(long lastRefreshTime) {
        this.lastRefreshTime = lastRefreshTime;
    }

    public long getNextRefreshTime() {
        return nextRefreshTime;
    }

    public void setNextRefreshTime(long nextRefreshTime) {
        this.nextRefreshTime = nextRefreshTime;
    }

    // 计算下次刷新时间
    public long calculateNextRefreshTime() {
        if ("cron".equals(refreshMode) && cronExpression != null) {
            // 使用Cron表达式计算下次执行时间
            try {
                org.quartz.CronExpression cron = new org.quartz.CronExpression(cronExpression);
                java.util.Date nextDate = cron.getNextValidTimeAfter(new java.util.Date());
                return nextDate != null ? nextDate.getTime() : Long.MAX_VALUE;
            } catch (Exception e) {
                // 如果Cron表达式无效，记录错误并返回一个很大的值
                System.err.println("无效的Cron表达式 '" + cronExpression + "' 用于占位符: " + name);
                e.printStackTrace();
                return Long.MAX_VALUE;
            }
        } else {
            // 使用间隔时间计算下次执行时间
            return lastRefreshTime + (interval * 1000L);
        }
    }

    // 检查是否需要刷新
    public boolean isReadyToRefresh() {
        long currentTime = System.currentTimeMillis();
        if ("cron".equals(refreshMode) && cronExpression != null) {
            // Cron模式：检查当前时间是否已超过下次刷新时间
            return currentTime >= nextRefreshTime;
        } else {
            // 间隔模式：检查是否已超过间隔时间
            return currentTime - lastRefreshTime >= interval * 1000L;
        }
    }
}