package me.getsouf.bookhunt.listener;

import me.getsouf.bookhunt.BookHunt;
import me.getsouf.bookhunt.manager.EventManager;
import me.getsouf.bookhunt.util.Msg;
import me.getsouf.bookhunt.util.Sounds;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityResurrectEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.projectiles.ProjectileSource;

import java.util.Map;
import java.util.UUID;

public class EventListener implements Listener {

    private final BookHunt plugin;
    private final EventManager em;

    public EventListener(BookHunt plugin) {
        this.plugin = plugin;
        this.em = plugin.getEventManager();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!em.isRunning()) return;
        if (!(event.getEntity() instanceof Player victim)) return;

        Player attacker = resolveAttacker(event);
        if (attacker == null) return;

        if (!em.isParticipant(attacker.getUniqueId()) || !em.isParticipant(victim.getUniqueId())) {
            return;
        }

        if (!em.canDamage(attacker.getUniqueId(), victim.getUniqueId())) {
            event.setCancelled(true);
            Msg.send(plugin, attacker, "only-target");
        }
    }

    private Player resolveAttacker(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player) {
            return player;
        }
        if (event.getDamager() instanceof Projectile projectile) {
            ProjectileSource source = projectile.getShooter();
            if (source instanceof Player player) {
                return player;
            }
        }
        return null;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(PlayerDeathEvent event) {
        if (!em.isRunning()) return;

        Player victim = event.getEntity();
        if (!em.isParticipant(victim.getUniqueId())) return;

        event.getDrops().clear();
        event.setDroppedExp(0);
        event.setKeepLevel(true);
        event.setKeepInventory(false);

        Player killer = victim.getKiller();
        if (killer != null && em.isParticipant(killer.getUniqueId())) {
            em.onKill(killer, victim);
        } else {
            em.handleQuit(victim);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        UUID id = player.getUniqueId();

        // Выбывший во время ивента или праздника → спектатор на арене
        if (em.isSpectator(id) && (em.isRunning() || em.isEnding())) {
            Location arena = em.getArenaLocation();
            if (arena != null) {
                event.setRespawnLocation(arena);
            }
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (em.isSpectator(id) && (em.isRunning() || em.isEnding())) {
                    em.putInSpectator(player);
                }
            });
            return;
        }

        // После праздника / финала — вернуть вещи и точку
        if (em.hasSavedData(id) && !em.isRunning() && !em.isEnding()) {
            Location saved = em.peekSavedLocation(id);
            if (saved != null && saved.getWorld() != null) {
                event.setRespawnLocation(saved);
            }
            plugin.getServer().getScheduler().runTask(plugin, () -> em.restorePlayerFully(player));
        }
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        if (!em.isRunning()) return;
        if (!em.isParticipant(event.getPlayer().getUniqueId())) return;

        if (em.isHuntBook(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
            Msg.send(plugin, event.getPlayer(), "only-target");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onResurrect(EntityResurrectEvent event) {
        if (!em.isRunning()) return;
        if (!(event.getEntity() instanceof Player player)) return;
        if (!em.isParticipant(player.getUniqueId())) return;

        event.setCancelled(true);
        Msg.send(plugin, player, "totem-disabled");
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        if (!em.isRunning() || !em.isParticipant(player.getUniqueId())) {
            return;
        }

        Msg.broadcast(plugin, "logout-death", Map.of("player", player.getName()));
        Sounds.logout();

        if (player.getHealth() > 0) {
            player.setHealth(0);
        }

        if (em.isParticipant(player.getUniqueId())) {
            em.handleQuit(player);
        }
    }
}