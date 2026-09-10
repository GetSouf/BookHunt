package me.getsouf.bookhunt.listener;

import me.getsouf.bookhunt.BookHunt;
import me.getsouf.bookhunt.manager.EventManager;
import me.getsouf.bookhunt.util.Msg;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityResurrectEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Iterator;
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
        if (!(event.getDamager() instanceof Player attacker)) return;

        if (!em.isParticipant(attacker.getUniqueId()) || !em.isParticipant(victim.getUniqueId())) {
            return;
        }

        UUID target = em.getTarget(attacker.getUniqueId());
        if (target == null || !target.equals(victim.getUniqueId())) {
            event.setCancelled(true);
            Msg.send(plugin, attacker, "only-target");
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(PlayerDeathEvent event) {
        if (!em.isRunning()) return;

        Player victim = event.getEntity();
        if (!em.isParticipant(victim.getUniqueId())) return;

        // убираем книгу из дропа
        Iterator<ItemStack> it = event.getDrops().iterator();
        while (it.hasNext()) {
            if (em.isHuntBook(it.next())) {
                it.remove();
            }
        }

        Player killer = victim.getKiller();
        if (killer != null && em.isParticipant(killer.getUniqueId())) {
            em.onKill(killer, victim);
        } else {
            // смерть без убийцы (окружение / выход) — просто выбытие
            em.handleQuit(victim);
        }
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        if (!em.isRunning()) return;
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

        // Наказание за выход: смерть
        // setHealth(0) убивает игрока — вещи выпадут как при обычной смерти
        if (player.getHealth() > 0) {
            player.setHealth(0);
        }

        Msg.broadcast(plugin, "logout-death", Map.of("player", player.getName()));

        // выбытие из ивента + передача цели охотнику
        // (handleQuit безопасен при повторном вызове из onDeath)
        em.handleQuit(player);
    }
}