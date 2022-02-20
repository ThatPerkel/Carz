package io.github.a5h73y.carz.listeners;

import static io.github.a5h73y.carz.enums.ConfigType.BLOCKS;
import static io.github.a5h73y.carz.enums.VehicleDetailKey.VEHICLE_FUEL;
import static io.github.a5h73y.carz.enums.VehicleDetailKey.VEHICLE_LOCKED;
import static io.github.a5h73y.carz.enums.VehicleDetailKey.VEHICLE_OWNER;
import static io.github.a5h73y.carz.enums.VehicleDetailKey.VEHICLE_TYPE;
import static org.bukkit.Material.AIR;

import io.github.a5h73y.carz.Carz;
import io.github.a5h73y.carz.configuration.impl.BlocksConfig;
import io.github.a5h73y.carz.controllers.CarController;
import io.github.a5h73y.carz.enums.Permissions;
import io.github.a5h73y.carz.event.EngineStartEvent;
import io.github.a5h73y.carz.event.EngineStopEvent;
import io.github.a5h73y.carz.model.Car;
import io.github.a5h73y.carz.model.CarDetails;
import io.github.a5h73y.carz.other.AbstractPluginReceiver;
import io.github.a5h73y.carz.other.DelayTasks;
import io.github.a5h73y.carz.utility.CarUtils;
import io.github.a5h73y.carz.utility.PermissionUtils;
import io.github.a5h73y.carz.utility.PlayerUtils;
import io.github.a5h73y.carz.utility.PluginUtils;
import io.github.a5h73y.carz.utility.TranslationUtils;
import io.github.a5h73y.carz.utility.ValidationUtils;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;

import org.bukkit.Bukkit;
import org.bukkit.Effect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Slab;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.entity.Vehicle;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.vehicle.VehicleDestroyEvent;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.bukkit.event.vehicle.VehicleEntityCollisionEvent;
import org.bukkit.event.vehicle.VehicleExitEvent;
import org.bukkit.event.vehicle.VehicleUpdateEvent;
import org.bukkit.util.Vector;
import org.bukkit.util.VoxelShape;

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

        if (event.getVehicle() instanceof Minecart
                && !ValidationUtils.isRail(event.getVehicle().getLocation().getBlock())
                && !carz.getCarDataPersistence().has(VEHICLE_TYPE, event.getVehicle())
                && carz.getConfig().getBoolean("Other.DriveAnyMinecart")) {
            carz.getCarDataPersistence().setValue(VEHICLE_TYPE, event.getVehicle(), CarController.DEFAULT_CAR);
        }

        if (!ValidationUtils.isACarzVehicle(event.getVehicle())) {
            return;
        }

        Player player = (Player) event.getEntered();

        if (!Carz.getDefaultConfig().isAutomaticLocking() && player.isSneaking()) {
            return;
        }

        if (!PermissionUtils.hasPermission(player, Permissions.START)) {
            return;
        }

        Minecart minecart = (Minecart) event.getVehicle();

        boolean carIsLocked = carz.getConfig().isAutomaticLocking()
                || carz.getCarDataPersistence().has(VEHICLE_LOCKED, minecart);

        if (carIsLocked && carz.getCarDataPersistence().has(VEHICLE_OWNER, minecart)) {
            String owner = carz.getCarDataPersistence().getValue(VEHICLE_OWNER, minecart);
            boolean isOwner = owner.equalsIgnoreCase(player.getName());

            if (!isOwner && !PermissionUtils.hasStrictPermission(player, Permissions.BYPASS_OWNER, false)) {
                TranslationUtils.sendValueTranslation("Error.Owned", owner, player);
                event.setCancelled(true);
                return;

            } else {
                carz.getCarDataPersistence().remove(VEHICLE_LOCKED, minecart);
                TranslationUtils.sendTranslation("Car.CarUnlocked", player);
            }
        } else if (Carz.getDefaultConfig().isOnlyOwnedCarsDrive()) {
            return;
        }

        if (carz.getFuelController().isFuelEnabled()) {
            carz.getFuelController().displayFuelLevel(player);
        }

        if (carz.getConfig().isGiveKeyOnEnter()
                && !player.getInventory().contains(Carz.getDefaultConfig().getKey())) {
            CarUtils.givePlayerKey(player);
        }

        if (carz.getConfig().getBoolean("Other.StartCarOnVehicleEnter")) {
            PlayerInteractEvent interactEvent = new PlayerInteractEvent(player,
                    Action.RIGHT_CLICK_AIR, null, null, BlockFace.SELF);
            Bukkit.getServer().getPluginManager().callEvent(interactEvent);
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
                || !(event.getPlayer().getVehicle() instanceof Vehicle)
                || !(event.getPlayer().getVehicle() instanceof Minecart)) {
            return;
        }

        Minecart vehicle = (Minecart) event.getPlayer().getVehicle();

        if (!ValidationUtils.isACarzVehicle(vehicle)) {
            return;
        }

        if (!PermissionUtils.hasPermission(event.getPlayer(), Permissions.START)) {
            return;
        }

        if (Carz.getDefaultConfig().isOnlyOwnedCarsDrive()
                && !carz.getCarDataPersistence().has(VEHICLE_OWNER, vehicle)) {
            return;
        }

        if (carz.getConfig().getBoolean("Key.RequireCarzKey")
                && PlayerUtils.getMaterialInPlayersHand(event.getPlayer()) != Carz.getDefaultConfig().getKey()) {
            return;
        }

        if (!DelayTasks.getInstance().delayPlayer(event.getPlayer(), 1)) {
            return;
        }

        Player player = event.getPlayer();
        Car car = carz.getCarController().getCar(vehicle.getEntityId());

        if (carz.getCarController().isDriving(player.getName())) {
            carz.getCarController().removeDriver(player.getName());
            vehicle.setMaxSpeed(0D);
            car.resetSpeed();
            TranslationUtils.sendTranslation("Car.EngineStop", player);
            carz.getCarDataPersistence().setValue(VEHICLE_FUEL, vehicle, car.getCurrentFuel().toString());
            Bukkit.getServer().getPluginManager().callEvent(new EngineStopEvent(player, car));

        } else {
            carz.getCarController().startDriving(player.getName(), vehicle);
            vehicle.setMaxSpeed(1000D);
            TranslationUtils.sendTranslation("Car.EngineStart", player);
            Bukkit.getServer().getPluginManager().callEvent(new EngineStartEvent(player, car));
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
        if (!(event.getVehicle() instanceof Minecart)) {
            return;
        }

        Player player = CarUtils.getPlayerDrivingVehicle(event.getVehicle());

        if (player == null) {
            return;
        }

        if (!carz.getCarController().isDriving(player.getName())) {
            return;
        }

        if (event.getVehicle().getLocation().getBlock().isLiquid()
                && Carz.getDefaultConfig().isDestroyInLiquid()) {
            carz.getCarController().destroyCar(event.getVehicle());
            player.playEffect(player.getLocation(), Effect.EXTINGUISH, null);
            TranslationUtils.sendTranslation("Car.LiquidDamage", player);
            return;
        }

        if (event.getVehicle().getFallDistance() > 1F && !Carz.getDefaultConfig().isControlCarsWhileFalling()) {
            return;
        }

        Integer carId = event.getVehicle().getEntityId();
        Car drivingCar = carz.getCarController().getCar(carId);

        if (drivingCar.isFuelConsumed()) {
            carz.getCarController().removeDriver(player.getName());
            carz.getCarDataPersistence().setValue(VEHICLE_FUEL, event.getVehicle(), "0");
            TranslationUtils.sendTranslation("Car.FuelEmpty", player);
            return;
        }

        Double acc = drivingCar.getCarDetails().getAcceleration();

        Vector vehicleVelocity = event.getVehicle().getVelocity().clone();
        // Multiply 2 to fix weird math bug (but only X & Z)
        vehicleVelocity.multiply(new Vector(2, 1, 2));

        // Dont manipulate with Y speed
        Vector playerVelocity = player.getVelocity().setY(0);

        // 0.875 to account some non-full block (e.g. grass path)
        Block blockBelow = event.getVehicle().getLocation().subtract(0.0D, 0.875D, 0.0D).getBlock();
        Material materialBelow = blockBelow.getType();
        BlocksConfig blocksConfig = (BlocksConfig) Carz.getConfig(BLOCKS);

        if (blocksConfig.containsSpeedBlock(materialBelow)) {
            Double modifier = blocksConfig.getSpeedModifier(materialBelow);
            acc *= modifier;
        }

        if (blocksConfig.containsLaunchBlock(materialBelow)) {
            Double amount = blocksConfig.getLaunchAmount(materialBelow);
            vehicleVelocity.setY(vehicleVelocity.getY() + amount);
        }

        float playerYaw = player.getLocation().getYaw();
        float vehicleYaw = event.getVehicle().getLocation().getYaw();
        
        // Get player movement input & normalize it
        Vector playerInput = playerVelocity.clone().rotateAroundY(Math.toRadians(playerYaw));

        /**
         * Vehicle acceleration
         */
        // Create new vector from Z movement and transform it
        Vector accV = new Vector(0, 0, playerInput.getZ());
        accV.rotateAroundY(Math.toRadians(-playerYaw));
        accV.multiply(acc);
        vehicleVelocity.add(accV);

        // Friction (if the Z input is close-to-zero)
        if (Math.abs(playerInput.getZ()) < 0.001) {
            Vector friction = vehicleVelocity.clone();
            friction.setY(0).multiply(-8);
            // Limit friction to certain value
            if (friction.length() > 1) {
                friction.normalize();
            }
            // Lower it
            friction.multiply(0.012);
            // Apply friction
            vehicleVelocity.add(friction);
        }

        /**
         * Process wheel steering
         */

        double steer = 5*playerInput.getX()*acc;
        vehicleYaw -= steer;

        // Apply steering
        vehicleVelocity.rotateAroundY(steer);
        event.getVehicle().setRotation(vehicleYaw, event.getVehicle().getLocation().getPitch());

        // Stop for really small values
        if (Math.abs(vehicleVelocity.getX()) < 0.01) {
            vehicleVelocity.setX(0);
        }
        if (Math.abs(vehicleVelocity.getZ()) < 0.01) {
            vehicleVelocity.setZ(0);
        }

        Location playerLocation = player.getLocation().clone();
        playerLocation.setPitch(0f);

        Location twoBlocksAhead = playerLocation.add(playerLocation.getDirection().multiply(2));
        twoBlocksAhead.setY(Math.max(playerLocation.getY() + 1, twoBlocksAhead.getY()));

        // determine if the Car should start climbing
        boolean isClimbable = calculateIsClimbable(blockBelow, twoBlocksAhead, blocksConfig);
        double climbStrength = Math.max(carz.getConfig().getClimbBlockStrength(), 0.1);

        if (isClimbable) {
            Location above = twoBlocksAhead.add(0, 1, 0);

            // if the block above it is AIR, allow to climb
            if (above.getBlock().getType() == AIR) {
                vehicleVelocity.setY(climbStrength);
            } else {
            }
        } else {
            if (blockBelow.getType() == AIR || blockBelow.getType() == Material.WATER
                    || blockBelow.getType() == Material.LAVA) {
                // Pull the vehicle down
                vehicleVelocity.setY(Math.max(vehicleVelocity.getY() - 0.1 * climbStrength, -0.5));
            } else {
                vehicleVelocity.setY(-0.01);
                // Check if not on a non-full block (not slab, but e.g. dirt path)
                double y = event.getVehicle().getLocation().getY();
                if (y - 1 < blockBelow.getY() && y - 0.875 > blockBelow.getY()) {
                    vehicleVelocity.setY(Math.abs(y - blockBelow.getY() - 1) * 4);
                }
            }
        }

        // Check max speed
        double currSpeed = vehicleVelocity.clone().setY(0).length();
        double maxSpeed = drivingCar.getMaxSpeed();
        if (currSpeed > maxSpeed) {
            vehicleVelocity.multiply(maxSpeed / currSpeed);
        }

        // Limit Y speed to climb strength
        vehicleVelocity.setY(Math.min(vehicleVelocity.getY(), climbStrength));

        event.getVehicle().setVelocity(vehicleVelocity);
        drivingCar.consumeFuel(playerVelocity.length());

        /**
         * Render Actionbar
         * conv is used to convert internal units to km/h
         * 1 unit -> ~10 blocks (m)/s -> 36 km/h
         */
        final double speed = vehicleVelocity.clone().setY(0).length();
        String guiString = String.format("%s §7| §bRychlost: §f%.1f km/h §7| §bPalivo: §f%.1f§b/§f%.0f §bl",
                drivingCar.getCarDetails().getName(),
                speed * Carz.speed_conv,
                drivingCar.getCurrentFuel(),
                carz.getFuelController().getMaxCapacity()).replace("&", "§");
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(guiString));
    }

    private boolean calculateIsClimbable(Block blockBelow, Location twoBlocksAhead, BlocksConfig blocksConfig) {
        blockBelow.getCollisionShape();
        // if the block ahead isn't solid (i.e. tall grass)
        if (blockBelow.getType() == AIR || !twoBlocksAhead.getBlock().getType().isSolid()) {
            return false;
        }

        // if there are no specified climb blocks, all solid blocks are climbable
        if (blocksConfig.getClimbBlocks().isEmpty()) {
            return true;
        }

        // are slabs climbable
        if (carz.getConfig().isAllSlabsClimb() && blockBelow.getBlockData() instanceof Slab) {
            return true;
        }

        // if there are climb blocks, make sure the material matches the whitelist
        return blocksConfig.getClimbBlocks().contains(twoBlocksAhead.getBlock().getType());
    }

    /**
     * Car destroy event.
     *
     * @param event {@link VehicleDestroyEvent}
     */
    @EventHandler
    public void onCarDestroy(VehicleDestroyEvent event) {
        if (!ValidationUtils.isACarzVehicle(event.getVehicle())) {
            return;
        }

        Minecart minecart = (Minecart) event.getVehicle();

        if (!carz.getCarDataPersistence().has(VEHICLE_OWNER, minecart)) {
            carz.getCarController().destroyCar(minecart);
            return;
        }

        event.setCancelled(true);

        if (event.getAttacker() instanceof Player) {
            String owner = carz.getCarDataPersistence().getValue(VEHICLE_OWNER, minecart);

            if (!event.getAttacker().getName().equals(owner)
                    && !PermissionUtils.hasStrictPermission((Player) event.getAttacker(), Permissions.BYPASS_OWNER,
                            false)) {
                TranslationUtils.sendValueTranslation("Error.Owned", owner, event.getAttacker());

            } else {
                carz.getCarController().stashCar((Player) event.getAttacker(), minecart);
            }
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

        if (!carz.getCarDataPersistence().has(VEHICLE_TYPE, event.getVehicle())) {
            return;
        }

        Minecart vehicle = (Minecart) event.getVehicle();

        if (carz.getCarController().isDriving(player.getName())) {
            carz.getCarController().removeDriver(player.getName());
            TranslationUtils.sendTranslation("Car.EngineStop", player);
        }

        if (Carz.getDefaultConfig().isAutomaticLocking()
                && carz.getCarDataPersistence().has(VEHICLE_OWNER, vehicle)
                && player.getName().equals(carz.getCarDataPersistence().getValue(VEHICLE_OWNER, vehicle))) {
            carz.getCarDataPersistence().setValue(VEHICLE_LOCKED, vehicle, "true");
            TranslationUtils.sendTranslation("Car.CarLocked", player);
        }

        Car car = carz.getCarController().getCar(vehicle.getEntityId());

        // car could be destroyed at this point (i.e. Water damage)
        if (car != null) {
            car.resetSpeed();
            carz.getCarDataPersistence().setValue(VEHICLE_FUEL, vehicle, car.getCurrentFuel().toString());
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
        if (!(event.getVehicle() instanceof Minecart)) {
            return;
        }

        Player player = CarUtils.getPlayerDrivingVehicle(event.getVehicle());

        if (player == null) {
            return;
        }

        if (!carz.getCarController().isDriving(player.getName())) {
            return;
        }

        if (!carz.getConfig().getBoolean("Other.DamageEntities.Enabled")) {
            return;
        }

        if (event.getEntity() instanceof LivingEntity) {
            if (event.getEntity() instanceof Player && carz.getCarController().isDriving(event.getEntity().getName())) {
                return;
            }

            double damage = carz.getConfig().getDouble("Other.DamageEntities.Damage");
            ((LivingEntity) event.getEntity()).damage(damage, player);
        }
    }

    /**
     * When the player requests to manually lock the car.
     * If the player is sneaking with a key in their hand.
     *
     * @param event {@link PlayerInteractEvent}
     */
    @EventHandler
    public void onCarLockToggle(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Vehicle)) {
            return;
        }

        if (!ValidationUtils.isACarzVehicle((Vehicle) event.getRightClicked())) {
            return;
        }

        Minecart vehicle = (Minecart) event.getRightClicked();

        if (!event.getPlayer().isSneaking() || !carz.getConfig().isSneakLockAction()) {
            return;
        }

        if (PlayerUtils.getMaterialInPlayersHand(event.getPlayer()) != Carz.getDefaultConfig().getKey()) {
            return;
        }

        if (carz.getCarDataPersistence().has(VEHICLE_OWNER, vehicle)
                && !PermissionUtils.hasStrictPermission(event.getPlayer(), Permissions.BYPASS_OWNER, false)) {
            String owner = carz.getCarDataPersistence().getValue(VEHICLE_OWNER, vehicle);

            if (!owner.equals(event.getPlayer().getName())) {
                TranslationUtils.sendValueTranslation("Error.Owned", owner, event.getPlayer());
                return;
            }
        }

        if (!DelayTasks.getInstance().delayPlayer(event.getPlayer(), 1)) {
            return;
        }

        if (carz.getCarDataPersistence().has(VEHICLE_LOCKED, vehicle)) {
            carz.getCarDataPersistence().remove(VEHICLE_LOCKED, vehicle);
            TranslationUtils.sendTranslation("Car.CarUnlocked", event.getPlayer());

        } else {
            carz.getCarDataPersistence().setValue(VEHICLE_LOCKED, vehicle, "true");
            TranslationUtils.sendTranslation("Car.CarLocked", event.getPlayer());
        }
    }
}
