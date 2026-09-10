package me.getsouf.bookhunt.util;

import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

/**
 * Звуки ивента BookHunt для всех онлайн-игроков / одного игрока.
 */
public final class Sounds {

    private Sounds() {}

    public static void playAll(Sound sound, float volume, float pitch) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.playSound(p.getLocation(), sound, volume, pitch);
        }
    }

    public static void play(Player player, Sound sound, float volume, float pitch) {
        if (player != null && player.isOnline()) {
            player.playSound(player.getLocation(), sound, volume, pitch);
        }
    }

    /** Предупреждение за минуту до старта */
    public static void warning() {
        playAll(Sound.BLOCK_NOTE_BLOCK_BELL, 1.0f, 0.8f);
        playAll(Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7f, 0.5f);
    }

    /** Ивент начался */
    public static void started() {
        playAll(Sound.ENTITY_ENDER_DRAGON_GROWL, 0.6f, 1.2f);
        playAll(Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 0.8f);
    }

    /** Убийство цели */
    public static void kill(Player killer) {
        play(killer, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
        play(killer, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.5f);
        playAll(Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.4f, 1.5f);
    }

    /** Игрок выбыл */
    public static void eliminated() {
        playAll(Sound.ENTITY_WITHER_HURT, 0.5f, 0.8f);
    }

    /** Ивент окончен */
    public static void ended() {
        playAll(Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
        playAll(Sound.ENTITY_FIREWORK_ROCKET_BLAST, 0.8f, 1.0f);
    }

    /** Выход во время ивента */
    public static void logout() {
        playAll(Sound.ENTITY_VILLAGER_NO, 1.0f, 0.7f);
    }
}