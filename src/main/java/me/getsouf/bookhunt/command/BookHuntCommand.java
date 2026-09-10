package me.getsouf.bookhunt.command;

import me.getsouf.bookhunt.BookHunt;
import me.getsouf.bookhunt.manager.EventManager;
import me.getsouf.bookhunt.util.Msg;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class BookHuntCommand implements CommandExecutor, TabCompleter {

    private final BookHunt plugin;

    public BookHuntCommand(BookHunt plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("bookhunt.admin")) {
            sender.sendMessage(Msg.color("&cНет прав."));
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        EventManager em = plugin.getEventManager();

        switch (args[0].toLowerCase()) {
            case "start" -> {
                em.forceStart();
                sender.sendMessage(Msg.color("&aЗапуск ивента..."));
            }
            case "stop" -> {
                em.stopEvent();
                sender.sendMessage(Msg.color("&aИвент остановлен."));
            }
            case "setspawn" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(Msg.color("&cТолько игрок может поставить точку."));
                    return true;
                }
                em.setArenaLocation(player.getLocation());
                Msg.send(plugin, player, "spawn-set");
            }
            case "status" -> {
                if (em.isRunning()) {
                    sender.sendMessage(Msg.color("&aИвент активен."));
                    sender.sendMessage(Msg.color("&7Участников: &e" + em.getHunterToTarget().size()));
                    em.getHunterToTarget().forEach((hunter, target) -> {
                        String h = plugin.getServer().getOfflinePlayer(hunter).getName();
                        String t = plugin.getServer().getOfflinePlayer(target).getName();
                        sender.sendMessage(Msg.color("&7- &f" + h + " &7→ &c" + t +
                                " &8(" + em.getKills(hunter) + " уб.)"));
                    });
                } else {
                    sender.sendMessage(Msg.color("&cИвент не идёт."));
                }
            }
            case "reload" -> {
                plugin.reloadConfig();
                sender.sendMessage(Msg.color("&aКонфиг перезагружен."));
            }
            default -> sendHelp(sender);
        }
        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(Msg.color("&6=== BookHunt ==="));
        sender.sendMessage(Msg.color("&e/bookhunt start &7- запустить"));
        sender.sendMessage(Msg.color("&e/bookhunt stop &7- остановить"));
        sender.sendMessage(Msg.color("&e/bookhunt setspawn &7- точка арены"));
        sender.sendMessage(Msg.color("&e/bookhunt status &7- статус"));
        sender.sendMessage(Msg.color("&e/bookhunt reload &7- перезагрузить конфиг"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("bookhunt.admin")) return List.of();
        if (args.length == 1) {
            return Arrays.asList("start", "stop", "setspawn", "status", "reload").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return List.of();
    }
}