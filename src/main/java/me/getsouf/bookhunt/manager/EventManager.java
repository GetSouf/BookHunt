package me.getsouf.bookhunt.manager;

import me.getsouf.bookhunt.BookHunt;
import me.getsouf.bookhunt.util.Msg;
import me.getsouf.bookhunt.util.Sounds;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.*;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class EventManager {

    private static final int CELEBRATION_SECONDS = 10;

    private final BookHunt plugin;

    private final Map<UUID, UUID> hunterToTarget = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> kills = new ConcurrentHashMap<>();
    private final Set<UUID> participants = ConcurrentHashMap.newKeySet();
    private final Map<UUID, InventorySnapshot> savedInventories = new ConcurrentHashMap<>();
    private final Map<UUID, Location> savedLocations = new ConcurrentHashMap<>();
    private final Map<UUID, GameMode> savedGameModes = new ConcurrentHashMap<>();
    private final Set<UUID> spectators = ConcurrentHashMap.newKeySet();

    private boolean running = false;
    private boolean warningPhase = false;
    private boolean ending = false;

    private BukkitTask endTask;
    private BukkitTask warningTask;
    private BukkitTask celebrationTask;

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

    public boolean isEnding() {
        return ending;
    }

    public boolean isParticipant(UUID uuid) {
        return participants.contains(uuid);
    }

    public boolean isSpectator(UUID uuid) {
        return spectators.contains(uuid);
    }

    public UUID getTarget(UUID hunter) {
        return hunterToTarget.get(hunter);
    }

    public boolean canDamage(UUID attackerId, UUID victimId) {
        UUID myTarget = hunterToTarget.get(attackerId);
        if (myTarget != null && myTarget.equals(victimId)) {
            return true;
        }
        UUID theirTarget = hunterToTarget.get(victimId);
        return theirTarget != null && theirTarget.equals(attackerId);
    }

    public int getKills(UUID uuid) {
        return kills.getOrDefault(uuid, 0);
    }

    public Map<UUID, UUID> getHunterToTarget() {
        return Collections.unmodifiableMap(hunterToTarget);
    }

    public boolean hasSavedData(UUID uuid) {
        return savedInventories.containsKey(uuid) || savedLocations.containsKey(uuid);
    }

    public Location peekSavedLocation(UUID uuid) {
        return savedLocations.get(uuid);
    }

    private void broadcastRules() {
        List<String> rules = plugin.getConfig().getStringList("event-rules");
        if (rules == null || rules.isEmpty()) return;
        for (String line : rules) {
            Bukkit.broadcastMessage(Msg.color(line));
        }
    }

    public void forceStart() {
        if (running || warningPhase || ending) {
            Msg.broadcast(plugin, "already-running");
            return;
        }
        startEvent();
    }

    public void startEvent() {
        if (running || warningPhase || ending) return;

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
        broadcastRules();
        Sounds.warning();

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
        ending = false;
        clearStateMaps(false);

        Collections.shuffle(players);

        for (int i = 0; i < players.size(); i++) {
            Player hunter = players.get(i);
            Player target = players.get((i + 1) % players.size());

            UUID id = hunter.getUniqueId();
            participants.add(id);
            hunterToTarget.put(id, target.getUniqueId());
            kills.put(id, 0);

            savedInventories.put(id, new InventorySnapshot(hunter));
            savedLocations.put(id, hunter.getLocation().clone());
            savedGameModes.put(id, hunter.getGameMode());

            giveEventKit(hunter);
            giveBook(hunter, target.getUniqueId(), 0);
            hunter.teleport(arena);
        }

        Msg.broadcast(plugin, "started");
        Sounds.started();

        int durationMin = plugin.getConfig().getInt("event-duration-minutes", 10);
        endTask = new BukkitRunnable() {
            @Override
            public void run() {
                endByTime();
            }
        }.runTaskLater(plugin, durationMin * 60L * 20L);
    }

    public void giveEventKit(Player player) {
        PlayerInventory inv = player.getInventory();
        inv.clear();

        inv.setHelmet(new ItemStack(Material.DIAMOND_HELMET));
        inv.setChestplate(new ItemStack(Material.DIAMOND_CHESTPLATE));
        inv.setLeggings(new ItemStack(Material.DIAMOND_LEGGINGS));
        inv.setBoots(new ItemStack(Material.DIAMOND_BOOTS));

        inv.setItem(0, new ItemStack(Material.DIAMOND_SWORD));
        inv.setItem(1, new ItemStack(Material.BOW));
        inv.setItem(2, new ItemStack(Material.ARROW, 64));
        inv.setItem(3, new ItemStack(Material.COOKED_BEEF, 32));
        inv.setItem(4, new ItemStack(Material.TNT, 16));
        inv.setItem(5, new ItemStack(Material.FLINT_AND_STEEL));
        inv.setItem(6, createStrengthPotion());
        inv.setHeldItemSlot(0);
        player.updateInventory();
    }

    private ItemStack createStrengthPotion() {
        ItemStack potion = new ItemStack(Material.POTION);
        PotionMeta meta = (PotionMeta) potion.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(Msg.color("&cЗелье силы"));
            meta.addCustomEffect(new PotionEffect(PotionEffectType.STRENGTH, 20 * 60 * 3, 0), true);
            potion.setItemMeta(meta);
        }
        return potion;
    }

    public void stopEvent() {
        if (!running && !warningPhase && !ending) {
            Msg.broadcast(plugin, "not-running");
            return;
        }
        beginEnding("Остановлено администратором", findTopKiller());
    }

    /** Мгновенное завершение (выключение плагина) — без праздника */
    public void forceEnd(String reason) {
        cancelTasks();
        if (running || ending || !savedInventories.isEmpty()) {
            running = false;
            warningPhase = false;
            ending = false;
            payRewards();
            restoreEveryone();
            clearStateMaps(true);
            Sounds.ended();
            Bukkit.broadcastMessage(Msg.color(
                    plugin.getConfig().getString("prefix", "") + "&cИвент завершён: " + reason
            ));
        }
    }

    private void beginEnding(String reason, UUID winnerId) {
        if (ending) return;
        ending = true;
        running = false;
        warningPhase = false;

        cancelTasks();

        String winnerName = winnerId != null
                ? Optional.ofNullable(Bukkit.getOfflinePlayer(winnerId).getName()).orElse("?")
                : "никто";

        Msg.broadcast(plugin, "ended-winner", Map.of("player", winnerName));
        Bukkit.broadcastMessage(Msg.color(
                plugin.getConfig().getString("prefix", "")
                        + "&eПраздник победителя: &a" + CELEBRATION_SECONDS + " сек&e..."
        ));
        Sounds.ended();

        // все выбывшие — в спектаторы (если уже онлайн и живы)
        for (UUID id : new HashSet<>(spectators)) {
            Player p = Bukkit.getPlayer(id);
            if (p != null && p.isOnline() && !p.isDead()) {
                putInSpectator(p);
            }
        }

        startCelebration(winnerId, reason);
    }

    private void startCelebration(UUID winnerId, String reason) {
        final int[] left = {CELEBRATION_SECONDS};

        celebrationTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (left[0] <= 0) {
                    cancel();
                    celebrationTask = null;
                    finishAfterCelebration(reason);
                    return;
                }

                Player winner = winnerId != null ? Bukkit.getPlayer(winnerId) : null;
                if (winner != null && winner.isOnline()) {
                    spawnFirework(winner.getLocation());
                    if (left[0] % 2 == 0) {
                        spawnFirework(winner.getLocation().clone().add(1.5, 0, 0));
                        spawnFirework(winner.getLocation().clone().add(-1.5, 0, 1));
                    }
                }

                left[0]--;
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    private void spawnFirework(Location loc) {
        if (loc == null || loc.getWorld() == null) return;

        Firework fw = (Firework) loc.getWorld().spawnEntity(loc, EntityType.FIREWORK_ROCKET);
        FireworkMeta meta = fw.getFireworkMeta();
        meta.setPower(1);
        meta.addEffect(FireworkEffect.builder()
                .with(FireworkEffect.Type.BALL_LARGE)
                .withColor(Color.YELLOW, Color.ORANGE, Color.RED, Color.AQUA)
                .withFade(Color.WHITE)
                .flicker(true)
                .trail(true)
                .build());
        fw.setFireworkMeta(meta);
    }

    private void finishAfterCelebration(String reason) {
        ending = false;
        payRewards();
        restoreEveryone();

        // боевое состояние сбрасываем; снимки мёртвых игроков остаются до респавна
        hunterToTarget.clear();
        participants.clear();
        spectators.clear();
        kills.clear();

        Bukkit.broadcastMessage(Msg.color(
                plugin.getConfig().getString("prefix", "") + "&cИвент завершён: " + reason
        ));
    }

    private void payRewards() {
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
        // also spectators with 0 kills
        for (UUID uuid : spectators) {
            if (kills.getOrDefault(uuid, 0) > 0) continue;
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                Msg.send(plugin, p, "no-reward");
            }
        }
    }

    private void restoreEveryone() {
        Set<UUID> all = new HashSet<>();
        all.addAll(savedInventories.keySet());
        all.addAll(savedLocations.keySet());
        all.addAll(savedGameModes.keySet());

        for (UUID uuid : all) {
            Player p = Bukkit.getPlayer(uuid);
            if (p == null || !p.isOnline()) continue;
            if (p.isDead()) {
                // респавн обработает restore через hasSavedData — оставляем снимок
                continue;
            }
            restorePlayerFully(p);
        }
    }

    /** Полный возврат: режим → инвентарь/опыт → координаты */
    public void restorePlayerFully(Player player) {
        if (player == null || !player.isOnline()) return;

        UUID id = player.getUniqueId();
        removeBooks(player);

        GameMode mode = savedGameModes.remove(id);
        if (mode != null) {
            player.setGameMode(mode);
        } else if (player.getGameMode() == GameMode.SPECTATOR) {
            player.setGameMode(GameMode.SURVIVAL);
        }

        InventorySnapshot snap = savedInventories.remove(id);
        if (snap != null) {
            snap.restore(player);
        }

        Location loc = savedLocations.remove(id);
        if (loc != null && loc.getWorld() != null) {
            player.teleport(loc);
        }

        spectators.remove(id);
    }

    public void putInSpectator(Player player) {
        if (player == null || !player.isOnline()) return;
        spectators.add(player.getUniqueId());
        player.setGameMode(GameMode.SPECTATOR);
        player.sendMessage(Msg.color(
                plugin.getConfig().getString("prefix", "") + "&7Ты выбыл. Режим наблюдателя до конца ивента."
        ));
    }

    private void endByTime() {
        UUID winner = findTopKiller();
        String name = winner != null
                ? Optional.ofNullable(Bukkit.getOfflinePlayer(winner).getName()).orElse("???")
                : "никто";
        Msg.broadcast(plugin, "ended-time");
        beginEnding("Время вышло. Лидер: " + name, winner);
    }

    private UUID findTopKiller() {
        UUID winner = null;
        int max = -1;
        for (Map.Entry<UUID, Integer> e : kills.entrySet()) {
            if (e.getValue() > max) {
                max = e.getValue();
                winner = e.getKey();
            }
        }
        if (winner == null && participants.size() == 1) {
            winner = participants.iterator().next();
        }
        return winner;
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
        } else {
            hunterToTarget.remove(killerId);
            nextTarget = null;
        }

        int newKills = kills.getOrDefault(killerId, 0) + 1;
        kills.put(killerId, newKills);

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

        Sounds.kill(killer);

        participants.remove(victimId);
        spectators.add(victimId);

        Msg.broadcast(plugin, "eliminated", Map.of("player", victim.getName()));
        Sounds.eliminated();

        checkWin(killerId);
    }

    private void checkWin(UUID lastKiller) {
        if (participants.size() <= 1 || hunterToTarget.size() <= 1) {
            UUID winner = participants.size() == 1
                    ? participants.iterator().next()
                    : lastKiller;
            beginEnding("Победитель: " + Optional.ofNullable(Bukkit.getOfflinePlayer(winner).getName()).orElse("?"),
                    winner);
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

        List<String> lore = new ArrayList<>();
        for (String line : plugin.getConfig().getStringList("book-lore")) {
            lore.add(Msg.color(line
                    .replace("{target}", targetName)
                    .replace("{kills}", String.valueOf(killCount))));
        }
        meta.setLore(lore);
        meta.setEnchantmentGlintOverride(true);
        meta.getPersistentDataContainer().set(BOOK_KEY, PersistentDataType.STRING, targetUuid.toString());
        meta.getPersistentDataContainer().set(KILLS_KEY, PersistentDataType.INTEGER, killCount);
        book.setItemMeta(meta);

        if (player.getInventory().getItemInOffHand().getType().isAir()) {
            player.getInventory().setItemInOffHand(book);
        } else {
            HashMap<Integer, ItemStack> left = player.getInventory().addItem(book);
            if (!left.isEmpty()) {
                player.getInventory().setItem(8, book);
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
        spectators.add(id);

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
        Sounds.eliminated();
        checkWin(id);
    }

    private void cancelTasks() {
        if (warningTask != null) {
            warningTask.cancel();
            warningTask = null;
        }
        if (endTask != null) {
            endTask.cancel();
            endTask = null;
        }
        if (celebrationTask != null) {
            celebrationTask.cancel();
            celebrationTask = null;
        }
    }

    private void clearStateMaps(boolean includeSaves) {
        hunterToTarget.clear();
        participants.clear();
        spectators.clear();
        kills.clear();
        if (includeSaves) {
            savedInventories.clear();
            savedLocations.clear();
            savedGameModes.clear();
        } else {
            savedInventories.clear();
            savedLocations.clear();
            savedGameModes.clear();
        }
    }
}