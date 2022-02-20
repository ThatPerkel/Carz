package io.github.a5h73y.carz.commands;

import static io.github.a5h73y.carz.controllers.CarController.DEFAULT_CAR;

import io.github.a5h73y.carz.Carz;
import io.github.a5h73y.carz.conversation.CreateCarTypeConversation;
import io.github.a5h73y.carz.enums.Commands;
import io.github.a5h73y.carz.other.AbstractPluginReceiver;
import io.github.a5h73y.carz.utility.CarUtils;
import io.github.a5h73y.carz.utility.PluginUtils;
import io.github.a5h73y.carz.utility.TranslationUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;

/**
 * Console related Carz commands handling.
 */
public class CarzConsoleCommands extends AbstractPluginReceiver implements CommandExecutor {

    public CarzConsoleCommands(final Carz carz) {
        super(carz);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender instanceof Player || !(sender instanceof ConsoleCommandSender)) {
            sender.sendMessage(Carz.getPrefix() + "Use '/carz' for player commands.");
            return false;
        }

        if (args.length < 1) {
            sender.sendMessage(Carz.getPrefix() + "proudly created by " + ChatColor.AQUA + "A5H73Y&Perkel");
            TranslationUtils.sendTranslation("Carz.ConsoleCommands", sender);
            return false;
        }

        switch (args[0].toLowerCase()) {
            case "spawn":
                if (!PluginUtils.isCommandEnabled(sender, Commands.SPAWN)) {
                    return false;
                }

                if (args.length < 2) {
                    TranslationUtils.sendTranslation("Error.SpecifyPlayer", sender);
                    return false;
                }

                Player player = Bukkit.getPlayer(args[1]);

                if (player == null) {
                    TranslationUtils.sendTranslation("Error.UnknownPlayer", sender);
                    return false;
                }

                CarUtils.givePlayerCar(player, args.length > 2 ? args[2] : DEFAULT_CAR);
                TranslationUtils.sendTranslation("Car.Spawned", sender, player);
                break;

            case "add":
                if (!PluginUtils.validateArgs(sender, args, 3, 4)) {
                    return false;
                }

                PluginUtils.addBlockType(sender, args);
                break;

            case "remove":
                PluginUtils.removeBlockType(sender, args);
                break;

            case "createtype":
                new CreateCarTypeConversation((ConsoleCommandSender) sender).begin();
                break;

            case "removetype":
                if (!PluginUtils.validateArgs(sender, args, 2)) {
                    return false;
                }

                PluginUtils.removeCarType(sender, args[1]);
                break;

            case "cartypes":
                TranslationUtils.sendHeading(TranslationUtils.getTranslation("CarType.Heading", false), sender);
                carz.getCarController().getCarTypes().keySet().forEach(sender::sendMessage);
                break;

            case "economy":
                carz.getEconomyApi().sendEconomyInformation(sender);
                break;

            case "destroyall":
                CarUtils.destroyAllCars();
                TranslationUtils.sendTranslation("Carz.CarsDestroyed", sender);
                break;

            case "reload":
                Carz.getInstance().getConfigManager().reloadConfigs();
                TranslationUtils.sendTranslation("Carz.ConfigReloaded", sender);
                break;

            case "cmds":
                sender.sendMessage("/carzc spawn (player)");
                sender.sendMessage("/carzc add (type) (material) [amount]");
                sender.sendMessage("/carzc remove (type) (material)");
                sender.sendMessage("/carzc createtype");
                sender.sendMessage("/carzc removetype");
                sender.sendMessage("/carzc cartypes");
                sender.sendMessage("/carzc economy");
                sender.sendMessage("/carzc destroyall");
                sender.sendMessage("/carzc reload");
                break;

            default:
                TranslationUtils.sendTranslation("Error.UnknownCommand", sender);
                TranslationUtils.sendTranslation("Carz.ConsoleCommands", sender);
        }

        return true;
    }
}
