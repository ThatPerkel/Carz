package io.github.a5h73y.carz.commands;

import io.github.a5h73y.carz.Carz;
import io.github.a5h73y.carz.enums.Commands;
import io.github.a5h73y.carz.enums.Permissions;
import io.github.a5h73y.carz.other.AbstractPluginReceiver;
import io.github.a5h73y.carz.utility.PermissionUtils;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

/**
 * Tab auto-completion for Carz commands.
 */
public class CarzAutoTabCompleter extends AbstractPluginReceiver implements TabCompleter {

    private static final List<String> ADD_REMOVE_LIST = Arrays.asList("climb", "speed", "launch", "placeable");

    private static final List<String> QUESTION_LIST = Arrays.asList("confirm", "cancel");

    public CarzAutoTabCompleter(final Carz carz) {
        super(carz);
    }

    /**
     * List of commands will be built based on the configuration and player permissions.
     * {@inheritDoc}
     */
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player)) {
            return null;
        }

        final Player player = (Player) sender;
        List<String> allowedCommands = new ArrayList<>();
        List<String> filteredCommands = new ArrayList<>();

        if (args.length == 1) {
            allowedCommands = populateMainCommands(player);

        } else if (args.length == 2) {
            allowedCommands = populateChildCommands(args[0].toLowerCase());
        }

        for (String allowedCommand : allowedCommands) {
            if (allowedCommand.startsWith(args[args.length - 1])) {
                filteredCommands.add(allowedCommand);
            }
        }

        return filteredCommands.isEmpty() ? allowedCommands : filteredCommands;
    }

    private List<String> populateMainCommands(Player player) {
        List<String> allowedCommands = new ArrayList<>();

        // if they have an outstanding purchase, make those the only options
        if (carz.getEconomyApi().isPurchasing(player)) {
            return QUESTION_LIST;
        }

        allowedCommands.add("cmds");
        allowedCommands.add("claim");
        allowedCommands.add("details");

        if (carz.getFuelController().isFuelEnabled()) {
            allowedCommands.add("fuel");

            if (carz.getConfig().getBoolean(Commands.REFUEL.getConfigPath())) {
                allowedCommands.add("refuel");
            }
        }

        if (PermissionUtils.hasStrictPermission(player, Permissions.PURCHASE, false)) {
            allowedCommands.add("stash");
            allowedCommands.add("cartypes");

            if (carz.getConfig().getBoolean(Commands.PURCHASE.getConfigPath())) {
                allowedCommands.add("purchase");
            }
            if (carz.getConfig().getBoolean(Commands.STORE.getConfigPath())) {
                allowedCommands.add("store");
            }
        }

        if (carz.getConfig().getBoolean(Commands.UPGRADE.getConfigPath())
                && PermissionUtils.hasStrictPermission(player, Permissions.UPGRADE, false)) {
            allowedCommands.add("upgrade");
        }

        if (PermissionUtils.hasStrictPermission(player, Permissions.ADMIN, false)) {
            allowedCommands.add("add");
            allowedCommands.add("remove");
            allowedCommands.add("createtype");
            allowedCommands.add("removetype");
            allowedCommands.add("economy");
            allowedCommands.add("reload");

            if (carz.getConfig().getBoolean(Commands.SPAWN.getConfigPath())) {
                allowedCommands.add("spawn");
            }
        }

        return allowedCommands;
    }

    private List<String> populateChildCommands(String command) {
        List<String> allowedCommands = new ArrayList<>();

        switch (command) {
            case "add":
            case "remove":
                allowedCommands = ADD_REMOVE_LIST;
                break;
            case "purchase":
            case "spawn":
                allowedCommands = new ArrayList<>(carz.getCarController().getCarTypes().keySet());
                break;
            default:
                break;
        }

        return allowedCommands;
    }
}
