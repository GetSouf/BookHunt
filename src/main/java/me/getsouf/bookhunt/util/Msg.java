package me.getsouf.bookhunt.util;

import me.getsouf.bookhunt.BookHunt;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

import java.util.Map;

public final class Msg {

    private Msg() {}

    public static String color(String text) {
        if (text == null) return "";
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    public static void send(BookHunt plugin, CommandSender sender, String path) {
        send(plugin, sender, path, Map.of());
    }

    public static void send(BookHunt plugin, CommandSender sender, String path, Map<String, String> placeholders) {
        String msg = plugin.getConfig().getString("messages." + path, path);
        String prefix = plugin.getConfig().getString("prefix", "&6[BookHunt] &r");
        for (Map.Entry<String, String> e : placeholders.entrySet()) {
            msg = msg.replace("{" + e.getKey() + "}", e.getValue());
        }
        sender.sendMessage(color(prefix + msg));
    }

    public static void broadcast(BookHunt plugin, String path) {
        broadcast(plugin, path, Map.of());
    }

    public static void broadcast(BookHunt plugin, String path, Map<String, String> placeholders) {
        String msg = plugin.getConfig().getString("messages." + path, path);
        String prefix = plugin.getConfig().getString("prefix", "&6[BookHunt] &r");
        for (Map.Entry<String, String> e : placeholders.entrySet()) {
            msg = msg.replace("{" + e.getKey() + "}", e.getValue());
        }
        String full = color(prefix + msg);
        Bukkit.getOnlinePlayers().forEach(p -> p.sendMessage(full));
        Bukkit.getConsoleSender().sendMessage(full);
    }
}