package com.bloodsoil.trueloyalty;

import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

public class TrueLoyaltyCommand implements TabExecutor {

    private final TrueLoyaltyPlugin plugin;
    private final ProtectionListener protection;

    public TrueLoyaltyCommand(TrueLoyaltyPlugin plugin, ProtectionListener protection) {
        this.plugin = plugin;
        this.protection = protection;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            return false;
        }
        switch (args[0].toLowerCase()) {
            case "give" -> {
                Player target = target(sender, args);
                if (target == null) {
                    sender.sendMessage("找不到玩家 " + (args.length > 1 ? args[1] : "自己"));
                    return true;
                }
                target.getInventory().addItem(plugin.trident());
                sender.sendMessage("已给 " + target.getName() + " 一把真·忠诚三叉戟");
                return true;
            }
            case "book" -> {
                Player target = target(sender, args);
                if (target == null) {
                    sender.sendMessage("找不到玩家 " + (args.length > 1 ? args[1] : "自己"));
                    return true;
                }
                target.getInventory().addItem(plugin.book());
                sender.sendMessage("已给 " + target.getName() + " 一本真·忠诚附魔书");
                return true;
            }
            case "test" -> {
                Player target = target(sender, args);
                if (target == null) {
                    sender.sendMessage("找不到玩家 " + (args.length > 1 ? args[1] : "自己"));
                    return true;
                }
                protection.cancelFor(target);
                new TrueLoyaltySequence(plugin, target, null, () -> {
                }).start();
                sender.sendMessage("已对 " + target.getName() + " 播放真·忠诚技能");
                return true;
            }
            case "reload" -> {
                plugin.reloadConfig();
                sender.sendMessage("配置已重载");
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    private Player target(CommandSender sender, String[] args) {
        if (args.length > 1) {
            return Bukkit.getPlayerExact(args[1]);
        }
        return sender instanceof Player player ? player : null;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            return List.of("give", "book", "test", "reload").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .toList();
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("give") || args[0].equalsIgnoreCase("book")
                || args[0].equalsIgnoreCase("test"))) {
            return Bukkit.getOnlinePlayers().stream().map(Player::getName)
                    .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
                    .toList();
        }
        return List.of();
    }
}
