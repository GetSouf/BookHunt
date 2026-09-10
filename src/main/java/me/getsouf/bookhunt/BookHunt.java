package me.getsouf.bookhunt;

import me.getsouf.bookhunt.command.BookHuntCommand;
import me.getsouf.bookhunt.listener.EventListener;
import me.getsouf.bookhunt.manager.AutoStartManager;
import me.getsouf.bookhunt.manager.EventManager;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

public class BookHunt extends JavaPlugin {

    private static BookHunt instance;
    private Economy economy;
    private EventManager eventManager;
    private AutoStartManager autoStartManager;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        if (!setupEconomy()) {
            getLogger().severe("Vault economy не найдена!");
            getLogger().severe("Убедись, что Vault и ExcellentEconomy (или другой economy) включены.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        eventManager = new EventManager(this);
        autoStartManager = new AutoStartManager(this);

        BookHuntCommand cmd = new BookHuntCommand(this);
        getCommand("bookhunt").setExecutor(cmd);
        getCommand("bookhunt").setTabCompleter(cmd);

        getServer().getPluginManager().registerEvents(new EventListener(this), this);

        autoStartManager.start();

        getLogger().info("BookHunt включён!");
        getLogger().info("Награда за убийство: $" + getConfig().getDouble("reward-per-kill", 50.0));
    }

    @Override
    public void onDisable() {
        if (eventManager != null && (eventManager.isRunning() || eventManager.isWarningPhase())) {
            eventManager.forceEnd("Плагин выключен");
        }
        if (autoStartManager != null) {
            autoStartManager.stop();
        }
        getLogger().info("BookHunt выключен");
    }

    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            return false;
        }
        RegisteredServiceProvider<Economy> provider =
                getServer().getServicesManager().getRegistration(Economy.class);
        if (provider == null) {
            return false;
        }
        economy = provider.getProvider();
        return economy != null;
    }

    public static BookHunt getInstance() {
        return instance;
    }

    public Economy getEconomy() {
        return economy;
    }

    public EventManager getEventManager() {
        return eventManager;
    }

    public AutoStartManager getAutoStartManager() {
        return autoStartManager;
    }
}