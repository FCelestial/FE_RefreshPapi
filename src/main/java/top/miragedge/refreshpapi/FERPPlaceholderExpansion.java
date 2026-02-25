package top.miragedge.refreshpapi;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

public class FERPPlaceholderExpansion extends PlaceholderExpansion {
    private final FE_RefreshPapi plugin;

    public FERPPlaceholderExpansion(FE_RefreshPapi plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "ferp";
    }

    @Override
    public @NotNull String getAuthor() {
        return String.join(", ", plugin.getDescription().getAuthors());
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onRequest(@Nullable OfflinePlayer player, @NotNull String params) {
        if (params.startsWith("value_")) {
            String placeholderName = params.substring(6);
            PlaceholderData data = plugin.getPlaceholderData(placeholderName);
            if (data != null) {
                return String.valueOf(data.getValue());
            }
        } else if (params.startsWith("refresh_time_")) {
            String placeholderName = params.substring(13);
            PlaceholderData data = plugin.getPlaceholderData(placeholderName);
            if (data != null) {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                sdf.setTimeZone(TimeZone.getDefault());
                return sdf.format(new Date(data.getLastRefreshTime()));
            }
        } else if (params.startsWith("next_refresh_time_")) {
            String placeholderName = params.substring(18);
            PlaceholderData data = plugin.getPlaceholderData(placeholderName);
            if (data != null) {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                sdf.setTimeZone(TimeZone.getDefault());
                return sdf.format(new Date(data.getNextRefreshTime()));
            }
        }
        
        return null;
    }
}