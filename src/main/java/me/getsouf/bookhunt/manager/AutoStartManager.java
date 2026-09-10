package me.getsouf.bookhunt.manager;

import me.getsouf.bookhunt.BookHunt;
import me.getsouf.bookhunt.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import java.util.Map;

public class AutoStartManager {

    private final BookHunt plugin;
    private BukkitTask task;
    private long consecutiveSeconds = 0;

    public AutoStartManager(BookHunt plugin) {
        this.plugin = plugin;
    }

    public void start() {
        // проверка каждые 20 секунд
        task = new BukkitRunnable() {
            @Override
            public void run() {
                tick();
            }
        }.runTaskTimer(plugin, 20L * 20, 20L * 20);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private void tick() {
        if (plugin.getEventManager().isRunning()) {
            consecutiveSeconds = 0;
            return;
        }

        int minPlayers = plugin.getConfig().getInt("min-players", 4);
        long requiredMinutes = plugin.getConfig().getLong("online-duration-minutes", 20);

        long online = Bukkit.getOnlinePlayers().stream()
                .filter(p -> p.hasPermission("bookhunt.play"))
                .count();

        if (online >= minPlayers) {
            consecutiveSeconds += 20;

            long minutesPassed = consecutiveSeconds / 60;
            if (minutesPassed > 0 && consecutiveSeconds % 300 < 20) {
                // сообщение примерно каждые 5 минут
                long left = requiredMinutes - minutesPassed;
                if (left > 0) {
                    Msg.broadcast(plugin, "auto-start", Map.of(
                            "minutes", String.valueOf(left),
                            "count", String.valueOf(online)
                    ));
                }
            }

            if (consecutiveSeconds >= requiredMinutes * 60) {
                consecutiveSeconds = 0;
                plugin.getEventManager().startEvent();
            }
        } else {
            consecutiveSeconds = 0;
        }
    }
}