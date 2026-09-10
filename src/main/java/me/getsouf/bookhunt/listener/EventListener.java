package me.getsouf.bookhunt.manager;

import me.getsouf.bookhunt.BookHunt;
import me.getsouf.bookhunt.util.Msg;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class EventManager {

    private final BookHunt plugin;

    private final Map<UUID, UUID> hunterToTarget = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> kills = new ConcurrentHashMap<>();
    private final Set<UUID> participants = ConcurrentHashMap.newKeySet();

    private boolean running = false;
    private boolean warningPhase = false;
    private BukkitTask endTask;
    private BukkitTask warningTask;

    public static final NamespacedKey BOOK_KEY = new NamespacedKey("bookhunt", "target");
    public static final NamespacedKey KILLS_KEY = new NamespacedKey("bookhunt", "kills");

    public EventManager(BookHunt plugin) {
        this.plugin = plugin;
    }

    public boolean isRunning() {
        return running;
    }

    public boolean isWarningPhase() {
        return warningPhase;
    }

    public boolean isParticipant(UUID uuid) {
        return participants.contains(uuid);
    }

    public UUID getTarget(UUID hunter) {
        return hunterToTarget.get(hunter);
    }

    public int getKills(UUID uuid) {
        return kills.getOrDefault(uuid, 0);
    }

    public Map<UUID, UUID> getHunterToTarget() {
        return Collections.unmodifiableMap(hunterToTarget);
    }

    public void forceStart() {
        if (running || warningPhase) {
            Msg.broadcast(plugin, "already-running");
            return;
        }
        startEvent();
    }

    public void startEvent() {
        if (running || warningPhase) return;

        Location arena = getArenaLocation();
        if (arena == null || arena.getWorld() == null) {
            Msg.broadcast(plugin, "no-spawn");
            return;
        }

        long onlineCount = Bukkit.getOnlinePlayers().stream()
                .filter(p -> p.hasPermission("bookhunt.play"))
                .count();

        int min = plugin.getConfig().getInt("min-players", 4);
        if (onlineCount < min) {
            Msg.broadcast(plugin, "not-enough-players", Map.of("min", String.valueOf(min)));
            return;
        }

        warningPhase = true;
        int warningSec = plugin.getConfig().getInt("warning-seconds", 60);
        Msg.broadcast(plugin, "warning", Map.of("seconds", String.valueOf(warningSec)));

        warningTask = new BukkitRunnable() {
            @Override
            public void run() {
                actuallyStart(arena);
            }
        }.runTaskLater(plugin, warningSec * 20L);
    }

    private void actuallyStart(Location arena) {
        warningPhase = false;
        warningTask = null;

        List<Player> players = Bukkit.getOnlinePlayers().stream()
                .filter(p -> p.hasPermission("bookhunt.play"))
                .filter(Player::isOnline)
                .collect(Collectors.toList());

        int min = plugin.getConfig().getInt("min-players", 4);
        if (players.size() < min) {
            Msg.broadcast(plugin, "not-enough-players", Map.of("min", String.valueOf(min)));
            return;
        }

        running = true;
        hunterToTarget.clear();
        kills.clear();
        participants.clear();

        Collections.shuffle(players);

        for (int i = 0; i < players.size(); i++) {
            Player hunter = players.get(i);
            Player target = players.get((i + 1) % players.size());

            participants.add(hunter.getUniqueId());
            hunterToTarget.put(hunter.getUniqueId(), target.getUniqueId());
            kills.put(hunter.getUniqueId(), 0);

            hunter.teleport(arena);
            giveBook(hunter, target.getUniqueId(), 0);
        }

        Msg.broadcast(plugin, "started");

        int durationMin = plugin.getConfig().getInt("event-duration-minutes", 10);
        endTask = new BukkitRunnable() {
            @Override
            public void run() {
                endByTime();
            }
        }.runTaskLater(plugin, durationMin * 60L * 20L);
    }

    public void stopEvent() {
        if (!running && !warningPhase) {
            Msg.broadcast(plugin, "not-running");
            return;
        }
        forceEnd("Остановлено администратором");
    }

    public void forceEnd(String reason) {
        if (warningTask != null) {
            warningTask.cancel();
            warningTask = null;
        }
        if (endTask != null) {
            endTask.cancel();
            endTask = null;
        }

        for (UUID uuid : new HashSet<>(participants)) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                removeBooks(p);
            }
        }

        // Награды ТОЛЬКО в конце
        double rewardPerKill = plugin.getConfig().getDouble("reward-per-kill", 50.0);
        for (Map.Entry<UUID, Integer> entry : kills.entrySet()) {
            int killCount = entry.getValue();
            if (killCount <= 0) continue;

            double amount = rewardPerKill * killCount;
            OfflinePlayer offline = Bukkit.getOfflinePlayer(entry.getKey());
            EconomyResponse response = plugin.getEconomy().depositPlayer(offline, amount);

            Player online = offline.getPlayer();
            if (response.transactionSuccess()) {
                if (online != null && online.isOnline()) {
                    Msg.send(plugin, online, "reward", Map.of(
                            "amount", plugin.getEconomy().format(amount),
                            "kills", String.valueOf(killCount)
                    ));
                }
            } else {
                plugin.getLogger().warning("Не удалось выдать $" + amount + " игроку " + offline.getName());
            }
        }

        for (UUID uuid : participants) {
            if (kills.getOrDefault(uuid, 0) > 0) continue;
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                Msg.send(plugin, p, "no-reward");
            }
        }

        running = false;
        warningPhase = false;
        hunterToTarget.clear();
        participants.clear();
        kills.clear();

        Bukkit.broadcastMessage(Msg.color(
                plugin.getConfig().getString("prefix", "") + "&cИвент завершён: " + reason
        ));
    }

    private void endByTime() {
        UUID winner = null;
        int max = -1;
        for (Map.Entry<UUID, Integer> e : kills.entrySet()) {
            if (e.getValue() > max) {
                max = e.getValue();
                winner = e.getKey();
            }
        }
        String name = winner != null
                ? Optional.ofNullable(Bukkit.getOfflinePlayer(winner).getName()).orElse("???")
                : "никто";
        Msg.broadcast(plugin, "ended-time");
        forceEnd("Время вышло. Лидер: " + name);
    }

    public void onKill(Player killer, Player victim) {
        if (!running) return;
        if (!participants.contains(killer.getUniqueId()) || !participants.contains(victim.getUniqueId())) {
            return;
        }

        UUID killerId = killer.getUniqueId();
        UUID victimId = victim.getUniqueId();

        UUID currentTarget = hunterToTarget.get(killerId);
        if (currentTarget == null || !currentTarget.equals(victimId)) {
            handleQuit(victim);
            return;
        }

        UUID nextTarget = hunterToTarget.get(victimId);
        hunterToTarget.remove(victimId);

        if (nextTarget != null && !nextTarget.equals(killerId)) {
            hunterToTarget.put(killerId, nextTarget);
        } else if (nextTarget != null && nextTarget.equals(killerId)) {
            hunterToTarget.remove(killerId);
            nextTarget = null;
        } else {
            hunterToTarget.remove(killerId);
        }

        int newKills = kills.getOrDefault(killerId, 0) + 1;
        kills.put(killerId, newKills);
        // счётчик жертвы НЕ удаляем

        removeBooks(victim);

        if (nextTarget != null) {
            giveBook(killer, nextTarget, newKills);
            String nextName = Optional.ofNullable(Bukkit.getOfflinePlayer(nextTarget).getName()).orElse("?");
            Msg.send(plugin, killer, "kill", Map.of(
                    "victim", victim.getName(),
                    "target", nextName
            ));
        } else {
            removeBooks(killer);
        }

        participants.remove(victimId);
        Msg.broadcast(plugin, "eliminated", Map.of("player", victim.getName()));

        checkWin(killerId);
    }

    private void checkWin(UUID lastKiller) {
        if (participants.size() <= 1 || hunterToTarget.size() <= 1) {
            String name = Optional.ofNullable(Bukkit.getOfflinePlayer(lastKiller).getName()).orElse("?");
            Msg.broadcast(plugin, "ended-winner", Map.of("player", name));
            forceEnd("Победитель: " + name);
        }
    }

    public void giveBook(Player player, UUID targetUuid, int killCount) {
        removeBooks(player);

        OfflinePlayer target = Bukkit.getOfflinePlayer(targetUuid);
        String targetName = target.getName() != null ? target.getName() : "???";

        ItemStack book = new ItemStack(Material.ENCHANTED_BOOK);
        ItemMeta meta = book.getItemMeta();
        if (meta == null) return;

        String name = plugin.getConfig().getString("book-name", "&d&lЗаказ: &c{target}")
                .replace("{target}", targetName);
        meta.setDisplayName(Msg.color(name));

        List<String> loreCfg = plugin.getConfig().getStringList("book-lore");
        List<String> lore = new ArrayList<>();
        for (String line : loreCfg) {
            lore.add(Msg.color(line
                    .replace("{target}", targetName)
                    .replace("{kills}", String.valueOf(killCount))));
        }
        meta.setLore(lore);
        meta.setEnchantmentGlintOverride(true);

        meta.getPersistentDataContainer().set(BOOK_KEY, PersistentDataType.STRING, targetUuid.toString());
        meta.getPersistentDataContainer().set(KILLS_KEY, PersistentDataType.INTEGER, killCount);

        book.setItemMeta(meta);

        if (player.getInventory().getItemInMainHand().getType().isAir()) {
            player.getInventory().setItemInMainHand(book);
        } else {
            HashMap<Integer, ItemStack> left = player.getInventory().addItem(book);
            if (!left.isEmpty()) {
                player.getInventory().setItemInOffHand(book);
            }
        }
        player.updateInventory();
    }

    public void removeBooks(Player player) {
        for (int i = 0; i < player.getInventory().getSize(); i++) {
            ItemStack item = player.getInventory().getItem(i);
            if (isHuntBook(item)) {
                player.getInventory().setItem(i, null);
            }
        }
        if (isHuntBook(player.getInventory().getItemInOffHand())) {
            player.getInventory().setItemInOffHand(null);
        }
        player.updateInventory();
    }

    public boolean isHuntBook(ItemStack item) {
        if (item == null || item.getType() != Material.ENCHANTED_BOOK) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        return meta.getPersistentDataContainer().has(BOOK_KEY, PersistentDataType.STRING);
    }

    public Location getArenaLocation() {
        String worldName = plugin.getConfig().getString("arena.world", "world");
        World world = Bukkit.getWorld(worldName);
        if (world == null) return null;

        return new Location(
                world,
                plugin.getConfig().getDouble("arena.x"),
                plugin.getConfig().getDouble("arena.y"),
                plugin.getConfig().getDouble("arena.z"),
                (float) plugin.getConfig().getDouble("arena.yaw"),
                (float) plugin.getConfig().getDouble("arena.pitch")
        );
    }

    public void setArenaLocation(Location loc) {
        plugin.getConfig().set("arena.world", loc.getWorld().getName());
        plugin.getConfig().set("arena.x", loc.getX());
        plugin.getConfig().set("arena.y", loc.getY());
        plugin.getConfig().set("arena.z", loc.getZ());
        plugin.getConfig().set("arena.yaw", (double) loc.getYaw());
        plugin.getConfig().set("arena.pitch", (double) loc.getPitch());
        plugin.saveConfig();
    }

    public void handleQuit(Player player) {
        if (!running) return;
        UUID id = player.getUniqueId();
        if (!participants.contains(id)) return;

        UUID victimTarget = hunterToTarget.remove(id);
        participants.remove(id);
        // kills НЕ удаляем — награда в конце

        removeBooks(player);

        for (Map.Entry<UUID, UUID> e : new HashMap<>(hunterToTarget).entrySet()) {
            if (!e.getValue().equals(id)) continue;

            if (victimTarget != null && !victimTarget.equals(e.getKey())) {
                hunterToTarget.put(e.getKey(), victimTarget);
                Player hunter = Bukkit.getPlayer(e.getKey());
                if (hunter != null) {
                    giveBook(hunter, victimTarget, kills.getOrDefault(e.getKey(), 0));
                }
            } else {
                hunterToTarget.remove(e.getKey());
                Player hunter = Bukkit.getPlayer(e.getKey());
                if (hunter != null) {
                    removeBooks(hunter);
                }
            }
        }

        Msg.broadcast(plugin, "eliminated", Map.of("player", player.getName()));
        checkWin(id);
    }
}