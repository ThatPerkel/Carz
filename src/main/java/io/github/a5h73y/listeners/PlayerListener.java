package io.github.a5h73y.listeners;

import io.github.a5h73y.Carz;
import io.github.a5h73y.enums.Permissions;
import io.github.a5h73y.other.DelayTasks;
import io.github.a5h73y.other.XMaterial;
import io.github.a5h73y.other.AbstractPluginReceiver;
import io.github.a5h73y.utility.PermissionUtils;
import io.github.a5h73y.utility.PlayerUtils;
import io.github.a5h73y.utility.TranslationUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

import static io.github.a5h73y.enums.VehicleDetailKey.VEHICLE_OWNER;
import static io.github.a5h73y.enums.VehicleDetailKey.VEHICLE_TYPE;

public class PlayerListener extends AbstractPluginReceiver implements Listener {

    public PlayerListener(Carz carz) {
        super(carz);
    }

    /**
     * When the player places a Minecart.
     * Determine if it's an owned Car.
     * @param event
     */
    @EventHandler
    public void onPlaceMinecart(PlayerInteractEvent event) {
        if (!event.getAction().equals(Action.RIGHT_CLICK_BLOCK)) {
            return;
        }

        if (PlayerUtils.getMaterialInPlayersHand(event.getPlayer()) != Material.MINECART) {
            return;
        }

        if (event.getClickedBlock().getType() == XMaterial.RAIL.parseMaterial()
                || event.getClickedBlock().getType() == XMaterial.POWERED_RAIL.parseMaterial()
                || event.getClickedBlock().getType() == XMaterial.DETECTOR_RAIL.parseMaterial()) {
            return;
        }

        Player player = event.getPlayer();

        if (!PermissionUtils.hasPermission(player, Permissions.PLACE)) {
            return;
        }

        ItemStack carInHand = PlayerUtils.getItemStackInPlayersHand(player);

        if (!carInHand.hasItemMeta() || !carz.getItemMetaUtils().has(VEHICLE_TYPE, carInHand)) {
            return;
        }

        if (!DelayTasks.getInstance().delayPlayer(player, 3)) {
            return;
        }

        if (carz.getItemMetaUtils().has(VEHICLE_OWNER, carInHand)) {
            String owner = carz.getItemMetaUtils().getValue(VEHICLE_OWNER, carInHand);

            if (!owner.equalsIgnoreCase(player.getName())) {
                TranslationUtils.sendTranslation("Error.Owned", player);
                return;
            }
        }

        Location location = event.getClickedBlock().getLocation().add(0, 1, 0);
        Minecart spawnedCar = location.getWorld().spawn(location, Minecart.class);

        carz.getItemMetaUtils().transferNamespaceKeyValues(carInHand.getItemMeta(), spawnedCar);

        String vehicleType = carz.getItemMetaUtils().getValue(VEHICLE_TYPE, spawnedCar);
        Material fillMaterial = carz.getCarController().getCarTypes().get(vehicleType).getFillMaterial();

        if (fillMaterial != null && fillMaterial != Material.AIR) {
            BlockData data = Bukkit.createBlockData(fillMaterial);
            spawnedCar.setDisplayBlockData(data);
        }

        PlayerUtils.reduceItemStackInPlayersHand(player);
    }
}
