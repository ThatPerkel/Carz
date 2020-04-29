package io.github.a5h73y.carz.listeners;

import io.github.a5h73y.carz.Carz;
import io.github.a5h73y.carz.enums.Permissions;
import io.github.a5h73y.carz.model.Car;
import io.github.a5h73y.carz.other.AbstractPluginReceiver;
import io.github.a5h73y.carz.other.DelayTasks;
import io.github.a5h73y.carz.utility.CarUtils;
import io.github.a5h73y.carz.utility.PermissionUtils;
import io.github.a5h73y.carz.utility.PlayerUtils;
import io.github.a5h73y.carz.utility.TranslationUtils;
import io.github.a5h73y.carz.utility.ValidationUtils;
import org.bukkit.Effect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.data.type.Slab;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.entity.Vehicle;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.vehicle.VehicleDestroyEvent;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.bukkit.event.vehicle.VehicleEntityCollisionEvent;
import org.bukkit.event.vehicle.VehicleExitEvent;
import org.bukkit.event.vehicle.VehicleUpdateEvent;
import org.bukkit.util.Vector;

import static io.github.a5h73y.carz.enums.VehicleDetailKey.VEHICLE_FUEL;
import static io.github.a5h73y.carz.enums.VehicleDetailKey.VEHICLE_OWNER;

/**
 * Vehicle related events.
 * The order of Events is in the typical lifecycle of a Car.
 */
public class VehicleListener extends AbstractPluginReceiver implements Listener {

    public VehicleListener(Carz carz) {
        super(carz);
    }

    /**
     * When the player enters a Vehicle.
     * If the user gets into an owned car that isn't theirs, it will be prevented.
     * The player is given a key if configured.
     *
     * @param event {@link VehicleEnterEvent}
     */
    @EventHandler
    public void onVehicleEnter(VehicleEnterEvent event) {
        if (!(event.getEntered() instanceof Player)) {
            return;
        }

        if (!ValidationUtils.isACarzVehicle(event.getVehicle())) {
            return;
        }

        if (carz.getConfig().getBoolean("UsePermissions")
                && !event.getEntered().hasPermission("Carz.Start")) {
            return;
        }

        Player player = (Player) event.getEntered();
        Minecart minecart = (Minecart) event.getVehicle();

        if (carz.getItemMetaUtils().has(VEHICLE_OWNER, minecart)) {
            String owner = carz.getItemMetaUtils().getValue(VEHICLE_OWNER, minecart);
            boolean isOwner = owner.equalsIgnoreCase(player.getName());

            if (!isOwner && !PermissionUtils.hasStrictPermission(player, Permissions.BYPASS_OWNER, false)) {
                player.sendMessage(TranslationUtils.getTranslation("Error.Owned")
                        .replace("%PLAYER%", owner));
                event.setCancelled(true);
                return;
            } else {
                TranslationUtils.sendTranslation("Car.CarUnlocked", player);
            }
        } else if (carz.getSettings().isOnlyOwnedCarsDrive()) {
            return;
        }

        if (carz.getFuelController().isFuelEnabled()) {
            carz.getFuelController().displayFuelLevel(player);
        }

        if (carz.getConfig().getBoolean("Key.GiveOnCarEnter")
                && !player.getInventory().contains(carz.getSettings().getKey())) {
            CarUtils.givePlayerKey(player);
        }
    }

    /**
     * When the player starts / stops the engine.
     * i.e. When a player right clicks with a key (Stick by default).
     *
     * @param event {@link PlayerInteractEvent}
     */
    @EventHandler
    public void onEngineToggle(PlayerInteractEvent event) {
        if (!event.getAction().equals(Action.RIGHT_CLICK_AIR)
                && !event.getAction().equals(Action.RIGHT_CLICK_BLOCK)) {
            return;
        }

        if (!event.getPlayer().isInsideVehicle()
                || !(event.getPlayer().getVehicle() instanceof Minecart)
                || !(event.getPlayer().getVehicle() instanceof Vehicle)) { // for some reason 1.15 needs this..?
            return;
        }

        if (!ValidationUtils.isACarzVehicle((Vehicle) event.getPlayer().getVehicle())) {
            return;
        }

        if (carz.getConfig().getBoolean("UsePermission")
                && !event.getPlayer().hasPermission("Carz.Start")) {
            return;
        }

        Vehicle vehicle = (Vehicle) event.getPlayer().getVehicle();
        Car car = carz.getCarController().getCar(vehicle.getEntityId());

        if (carz.getSettings().isOnlyOwnedCarsDrive() && !carz.getItemMetaUtils().has(VEHICLE_OWNER, vehicle)) {
            return;
        }

        if (carz.getConfig().getBoolean("Key.RequireCarzKey")
                && PlayerUtils.getMaterialInPlayersHand(event.getPlayer()) != carz.getSettings().getKey()) {
            return;
        }

        if (!DelayTasks.getInstance().delayPlayer(event.getPlayer(), 1)) {
            return;
        }

        Player player = event.getPlayer();
        Minecart minecart = (Minecart) event.getPlayer().getVehicle();

        if (carz.getCarController().isDriving(player.getName())) {
            carz.getCarController().removeDriver(player.getName());
            car.resetSpeed();
            minecart.setMaxSpeed(0D);
            TranslationUtils.sendTranslation("Car.EngineStop", player);
            carz.getItemMetaUtils().setValue(VEHICLE_FUEL, minecart, car.getCurrentFuel().toString());

        } else {
            carz.getCarController().startDriving(player.getName(), minecart);
            minecart.setMaxSpeed(1000D);
            TranslationUtils.sendTranslation("Car.EngineStart", player);
        }
    }

    /**
     * Car drive update event.
     * The Car's speed and direction is calculated.
     *
     * @param event {@link VehicleUpdateEvent}
     */
    @EventHandler
    public void onVehicleUpdate(VehicleUpdateEvent event) {
        if (!(event.getVehicle().getPassenger() instanceof Player)) {
            return;
        }

        if (!(event.getVehicle() instanceof Minecart)) {
            return;
        }

        Player player = (Player) event.getVehicle().getPassenger();

        if (!carz.getCarController().isDriving(player.getName())) {
            return;
        }

        if (event.getVehicle().getLocation().getBlock().isLiquid()
                && carz.getSettings().isDestroyInLiquid()) {
            carz.getCarController().destroyCar(event.getVehicle());
            player.playEffect(player.getLocation(), Effect.EXTINGUISH, null);
            TranslationUtils.sendTranslation("Car.LiquidDamage", player);
            return;
        }

        if (event.getVehicle().getFallDistance() > 1F && !carz.getSettings().isControlCarsWhileFalling()) {
            return;
        }

        Integer carId = event.getVehicle().getEntityId();
        Car drivingCar = carz.getCarController().getCar(carId);

        if (drivingCar.isFuelConsumed()) {
            carz.getCarController().removeDriver(player.getName());
            carz.getItemMetaUtils().setValue(VEHICLE_FUEL, event.getVehicle(), "0");
            TranslationUtils.sendTranslation("Car.FuelEmpty", player);
            return;
        }

        drivingCar.accelerate();

        Vector vehicleVelocity = event.getVehicle().getVelocity();
        Vector playerLocationVelocity = player.getLocation().getDirection();

        double carSpeed = drivingCar.getCurrentSpeed();

        vehicleVelocity.setX((playerLocationVelocity.getX() / 100.0) * carSpeed);
        vehicleVelocity.setZ((playerLocationVelocity.getZ() / 100.0) * carSpeed);

        Material materialBelow = event.getVehicle().getLocation().subtract(0.0D, 1.0D, 0.0D).getBlock().getType();

        if (carz.getSettings().containsSpeedBlock(materialBelow)) {
            Double modifier = carz.getSettings().getSpeedModifier(materialBelow);

            vehicleVelocity.setX(vehicleVelocity.getX() * modifier);
            vehicleVelocity.setZ(vehicleVelocity.getZ() * modifier);
        }

        Location playerLocation = player.getLocation().clone();
        playerLocation.setPitch(0f);

        Location twoBlocksAhead = playerLocation.add(playerLocation.getDirection().multiply(2));
        twoBlocksAhead.setY(Math.max(playerLocation.getY() + 1, twoBlocksAhead.getY()));

        // if there is a block ahead of us
        if (twoBlocksAhead.getBlock().getType() != Material.AIR
                || twoBlocksAhead.getBlock().getBlockData() instanceof Slab) {
            Location above = twoBlocksAhead.add(0, 1, 0);

            // if the block above it is AIR, allow to climb
            if (above.getBlock().getType() == Material.AIR) {
                vehicleVelocity.setY(0.25);

                vehicleVelocity.setX(playerLocationVelocity.getX() / 8.0);
                vehicleVelocity.setZ(playerLocationVelocity.getZ() / 8.0);
            }
        }

        event.getVehicle().setVelocity(vehicleVelocity);
    }

    /**
     * Car destroy event.
     *
     * @param event {@link VehicleDestroyEvent}
     */
    @EventHandler
    public void onCarDestroy(VehicleDestroyEvent event) {
        if (!(event.getAttacker() instanceof Player)) {
            return;
        }

        if (!(event.getVehicle() instanceof Minecart)) {
            return;
        }

        if (!carz.getItemMetaUtils().has(VEHICLE_OWNER, event.getVehicle())) {
            carz.getCarController().destroyCar(event.getVehicle());
            return;
        }

        event.setCancelled(true);
        String owner = carz.getItemMetaUtils().getValue(VEHICLE_OWNER, event.getVehicle());

        if (!event.getAttacker().getName().equals(owner)
                && !PermissionUtils.hasStrictPermission((Player) event.getAttacker(), Permissions.ADMIN, false)) {

            String ownedMessage = TranslationUtils.getTranslation("Error.Owned").replace("%OWNER%", owner);
            event.getAttacker().sendMessage(ownedMessage);

        } else {
            carz.getCarController().stashCar((Player) event.getAttacker(), (Minecart) event.getVehicle());
        }
    }

    /**
     * Vehicle Exit event.
     * When the Player requests to exit the car.
     *
     * @param event {@link VehicleExitEvent}
     */
    @EventHandler
    public void onVehicleExit(VehicleExitEvent event) {
        if (!(event.getExited() instanceof Player)) {
            return;
        }

        if (!(event.getVehicle() instanceof Minecart)) {
            return;
        }

        Player player = (Player) event.getExited();

        if (!carz.getCarController().isDriving(player.getName())) {
            return;
        }

        carz.getCarController().removeDriver(player.getName());
        TranslationUtils.sendTranslation("Car.EngineStop", player);

        Car car = carz.getCarController().getCar(event.getVehicle().getEntityId());
        car.resetSpeed();
        carz.getItemMetaUtils().setValue(VEHICLE_FUEL, event.getVehicle(), car.getCurrentFuel().toString());

        if (carz.getItemMetaUtils().has(VEHICLE_OWNER, event.getVehicle())
                && player.getName().equals(carz.getItemMetaUtils().getValue(VEHICLE_OWNER, event.getVehicle()))) {
            TranslationUtils.sendTranslation("Car.CarLocked", player);
        }
    }

    /**
     * Vehicle Entity Collision Event.
     * When a driver hits an entity with their car, damage amount configurable.
     *
     * @param event {@link VehicleEntityCollisionEvent}
     */
    @EventHandler
    public void onVehicleCollide(VehicleEntityCollisionEvent event) {
        if (!(event.getVehicle().getPassenger() instanceof Player)) {
            return;
        }

        if (!(event.getVehicle() instanceof Minecart)) {
            return;
        }

        Player player = (Player) event.getVehicle().getPassenger();

        if (!carz.getCarController().isDriving(player.getName())) {
            return;
        }

        if (!carz.getConfig().getBoolean("Other.DamageEntities.Enabled")) {
            return;
        }

        double damage = carz.getConfig().getDouble("Other.DamageEntities.Damage");

        if (event.getEntity() instanceof LivingEntity) {
            ((LivingEntity) event.getEntity()).damage(damage, player);
        }
    }
}