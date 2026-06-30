package com.dreiwhite.dev.hirayadrop;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class HirayaDrop extends JavaPlugin implements Listener, TabCompleter {

    private final Set<UUID> disabledPlayers = ConcurrentHashMap.newKeySet();

    private String prefix;
    private String primaryColor;
    private String secondaryColor;
    private String accentColor;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadTheme();
        getServer().getPluginManager().registerEvents(this, this);
        getCommand("hirayadrop").setTabCompleter(this);
        getLogger().info("HirayaDrop enabled.");
    }

    @Override
    public void onDisable() {
        disabledPlayers.clear();
        getLogger().info("HirayaDrop disabled.");
    }

    private void loadTheme() {
        FileConfiguration config = getConfig();
        boolean prefixEnabled = config.getBoolean("prefix.enabled", true);
        String rawPrefix = config.getString("prefix.text", "&8[&9HirayaDrop&8] ");
        primaryColor = config.getString("theme.primary", "#3B82F6");
        secondaryColor = config.getString("theme.secondary", "#FFFFFF");
        accentColor = config.getString("theme.accent", "#808080");
        prefix = prefixEnabled ? colorize(rawPrefix) : "";
    }

    private String colorize(String text) {
        if (text == null) {
            return "";
        }
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '#' && i + 6 < text.length()) {
                String hex = text.substring(i + 1, i + 7);
                if (hex.matches("[0-9a-fA-F]{6}")) {
                    result.append(hexToMinecraft(hex));
                    i += 6;
                    continue;
                }
            }
            result.append(c);
        }
        return ChatColor.translateAlternateColorCodes('&', result.toString());
    }

    private String hexToMinecraft(String hex) {
        StringBuilder sb = new StringBuilder("\u00A7x");
        for (char c : hex.toCharArray()) {
            sb.append('\u00A7').append(c);
        }
        return sb.toString();
    }

    private String message(String key, String fallback) {
        String raw = getConfig().getString("messages." + key, fallback);
        return prefix + colorize(raw)
                .replace("{primary}", colorize(primaryColor))
                .replace("{secondary}", colorize(secondaryColor))
                .replace("{accent}", colorize(accentColor));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("This command can only be used by players.");
            return true;
        }
        Player player = (Player) sender;

        if (args.length != 1 || (!args[0].equalsIgnoreCase("on") && !args[0].equalsIgnoreCase("off"))) {
            player.sendMessage(message("usage", "&7Usage: &f/hirayadrop <on|off>"));
            return true;
        }

        UUID id = player.getUniqueId();
        boolean turnOn = args[0].equalsIgnoreCase("on");

        if (turnOn) {
            if (disabledPlayers.remove(id)) {
                player.sendMessage(message("enabled", "{primary}Block drops will now appear in front of you."));
            } else {
                player.sendMessage(message("already-enabled", "{accent}Already on."));
            }
        } else {
            if (disabledPlayers.add(id)) {
                player.sendMessage(message("disabled", "{accent}Block drops will now fall normally."));
            } else {
                player.sendMessage(message("already-disabled", "{accent}Already off."));
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> options = new ArrayList<>();
            String input = args[0].toLowerCase();
            if ("on".startsWith(input)) {
                options.add("on");
            }
            if ("off".startsWith(input)) {
                options.add("off");
            }
            return options;
        }
        return java.util.Collections.emptyList();
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (disabledPlayers.contains(player.getUniqueId())) {
            return;
        }

        List<ItemStack> drops = new ArrayList<>(event.getBlock().getDrops(player.getInventory().getItemInMainHand()));
        if (drops.isEmpty()) {
            return;
        }

        event.setDropItems(false);

        World world = player.getWorld();
        Location dropLocation = player.getEyeLocation()
                .add(player.getLocation().getDirection().normalize().multiply(0.6));
        Vector velocity = new Vector(0, 0.05, 0);

        for (ItemStack drop : drops) {
            world.dropItem(dropLocation, drop).setVelocity(velocity);
        }

        int exp = event.getExpToDrop();
        if (exp > 0) {
            world.spawn(dropLocation, org.bukkit.entity.ExperienceOrb.class, orb -> orb.setExperience(exp));
            event.setExpToDrop(0);
        }
    }
}