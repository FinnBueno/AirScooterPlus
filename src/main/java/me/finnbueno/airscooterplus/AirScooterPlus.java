package me.finnbueno.airscooterplus;

import com.projectkorra.projectkorra.GeneralMethods;
import com.projectkorra.projectkorra.ProjectKorra;
import com.projectkorra.projectkorra.ability.AddonAbility;
import com.projectkorra.projectkorra.ability.AirAbility;
import com.projectkorra.projectkorra.attribute.Attribute;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.util.Vector;

import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Stream;

public class AirScooterPlus extends AirAbility implements AddonAbility, Listener {

    private enum RidingType {
        // normal usage
        GROUND,
        // riding alongside a wall, without ground beneath you
        WALL,
        // riding alongside a wall with ground beneath you
        // this riding type is the transition between GROUND and WALL, so you don't get charged WALL charges for just
        // riding next to a wall while on the ground
        WALL_START,
        AIR,
    }

    private static final double HEIGHT = 2.2;
    private static final String VERSION = "1.0.0";

    @Attribute(Attribute.SPEED)
    private double speed = .55;
    @Attribute(Attribute.SPEED)
    private double launchSpeed = .85;
    @Attribute(Attribute.COOLDOWN)
    private long cooldown = 3000;

    // the total size of the charge bar
    private double chargesTotal = 100;
    private double chargesLeft = 100;
    // how much it costs to use airscooter normally
    private double chargeNormalUse = 1;
    // how much it costs to go at full speed
    // how much it costs to wall ride
    private double chargeWallRide = 2;
    // how much it costs to keep going without ground
    private double chargeFly = 20;
    // how much the charge should regain uses on normal use (0 if none)
    private double rechargeOnNormalUse = 0;

    private int maxHeightFromGround = 8;

    private RidingType ridingType;
    private Block floorBlock;
    private BossBar resourceBar;

    public AirScooterPlus(Player player) {
        super(player);

        AirScooterPlus existingScooter = getAbility(player, AirScooterPlus.class);
        if (existingScooter != null) {
            existingScooter.attemptJump();
            existingScooter.remove();
            return;
        }

        if (!player.isSprinting() || !player.isSneaking()) {
            return;
        }

        if (bPlayer.isOnCooldown(this)) {
            return;
        }

        // player didn't jump
        if (isRideable(player.getLocation().getBlock().getRelative(BlockFace.DOWN))) {
            return;
        }

        // For some reason, the stock AirScooter also has a check for solid or water at the player's eye level
        // Not sure why, but if bugs arise, consider adding it as a potential fix

        this.flightHandler.createInstance(player, getName());
        player.setAllowFlight(true);
        player.setFlying(false);

        player.setSprinting(false);
        player.setSneaking(false);

        setFields();
        ridingType = RidingType.GROUND;
        resourceBar = ProjectKorra.plugin.getServer().createBossBar(getName(), BarColor.WHITE, BarStyle.SEGMENTED_10);
        resourceBar.addPlayer(player);
        start();
    }

    private void setFields() {
        this.speed = .65;
        this.launchSpeed = 1.2;
        this.cooldown = 3000;
        this.chargesTotal = 100;
        this.chargesLeft = 100;
        this.chargeNormalUse = 1;
        this.chargeWallRide = 2;
        this.chargeFly = 20;
        this.rechargeOnNormalUse = 0;
    }

    @Override
    public void progress() {
        if (!bPlayer.canBendIgnoreCooldowns(this)) {
            remove();
            return;
        }

        // try to find a floor beneath the player
        findFloor();
        double distanceToFloor;
        // check if the conditions for wall riding are met
        if (checkWallRideConditions()) {
            // check if there is no floor beneath the player
            if (this.floorBlock == null) {
                // if there is no floor, charge resourcebar accordingly
                ridingType = RidingType.WALL;
                distanceToFloor = Double.MAX_VALUE;
            } else {
                // if there is a floor, charge resourcebar as if riding on the ground
                ridingType = RidingType.WALL_START;
                distanceToFloor = player.getLocation().getY() - floorBlock.getY();
            }
        } else if (this.floorBlock != null) {
            // if there is a floor but no walls, playing is riding normally
            ridingType = RidingType.GROUND;
            distanceToFloor = player.getLocation().getY() - floorBlock.getY();
        } else {
            // player is in the air, right now the move will just cancel, but in the future this move will provide
            // a short flight duration
            ridingType = RidingType.AIR;
            distanceToFloor = Double.MAX_VALUE;
        }

        if (ridingType == RidingType.AIR) {
            remove();
            return;
        }

        if (chargesLeft <= 0) {
            remove();
            return;
        }

        handleCharges();
        displayParticles();

        // check this so the move doesn't immediately cancel itself, player needs time to gain some speed
        if (System.currentTimeMillis() > getStartTime() + 250) {
            // player has hit a wall or is otherwise being denied movement in their desired direction
            if (player.getVelocity().length() < speed * .3) {
                remove();
                return;
            }
        }

        player.sendMessage(ridingType.name());

        Vector playerDirection = null;
        switch (ridingType) {
            case GROUND:
            // case HIGH_SPEED:
                playerDirection = calculateGroundDirection(distanceToFloor);
                break;
            case WALL:
            case WALL_START:
                playerDirection = calculateWallDirection(distanceToFloor);
                break;
            case AIR:
                remove();
                return;
        }

        if (playerDirection == null) {
            remove();
            return;
        }

        player.setSprinting(false);
        player.setVelocity(playerDirection);
        if (ThreadLocalRandom.current().nextInt(4) == 0) {
            playAirbendingSound(player.getLocation().subtract(0, .5, 0));
        }
    }

    private void displayParticles() {
        Location loc = player.getLocation().subtract(0, HEIGHT / 2, 0);
        for (double theta = 0; theta < Math.PI; theta += Math.PI / 8) {
            double totalParticlesInRing = 1 + Math.cos(theta) * 9;
            for (double phi = 0; phi < Math.PI * 2; phi += (Math.PI * 2) / (totalParticlesInRing)) {
                double y = Math.cos(theta) * HEIGHT;
                double radius = Math.sin(theta) * HEIGHT;
                double x = Math.cos(phi) * radius;
                double z = Math.sin(phi) * radius;
                loc.add(x, y, z);
                playAirbendingParticles(loc, 1, 0.05, 0.05, 0.05);
                loc.subtract(x, y, z);
            }
        }
    }

    private void handleCharges() {
        double chargeDepletion = 0;
        switch (ridingType) {
            case WALL_START:
            case GROUND:
                chargeDepletion = chargeNormalUse == 0 ? -rechargeOnNormalUse : chargeNormalUse;
                break;
            case WALL:
                chargeDepletion = chargeWallRide;
                break;
            case AIR:
                chargeDepletion = chargeFly;
                break;
        }
        chargesLeft -= chargeDepletion / 20.0;
        resourceBar.setProgress(chargesLeft / chargesTotal);
    }

    private Vector calculateGroundDirection(double distanceToFloor) {
        // TODO: Limit the turn angle as an optional feature
        Vector playerDirection = getHorizontalDirection()
                .multiply(speed);

        if (distanceToFloor > HEIGHT + .75) {
            playerDirection.setY(-.25);
        } else if (distanceToFloor < HEIGHT - .2) {
            playerDirection.setY(.25);
        } else {
            playerDirection.setY(0);
        }

        // make player descend or ascend before the floor changes
        Block nextBlock = this.floorBlock.getLocation().add(playerDirection.clone().normalize().multiply(1.2)).getBlock();
        if (isRideable(nextBlock.getRelative(BlockFace.UP))) {
            // go up
            playerDirection.add(new Vector(0, -.1, 0));
        } else if (!isRideable(nextBlock)) {
            // go down
            playerDirection.add(new Vector(0, .7, 0));
        }
        return playerDirection;
    }

    private Vector calculateWallDirection(double distanceToFloor) {
        if (distanceToFloor < HEIGHT - .2) {
            // the player should not be allowed to go any lower
            return getHorizontalDirection().multiply(speed).setY(.25);
        } else {
            return player.getEyeLocation().getDirection().multiply(speed);
        }
    }

    private boolean checkWallRideConditions() {
        return Stream.of(
                GeneralMethods.getLeftSide(player.getLocation(), 1).getBlock(),
                GeneralMethods.getLeftSide(player.getLocation(), 2).getBlock(),
                GeneralMethods.getRightSide(player.getLocation(), 1).getBlock(),
                GeneralMethods.getRightSide(player.getLocation(), 2).getBlock()
        ).anyMatch(block -> GeneralMethods.isSolid(block.getType()));
    }

    public void attemptJump() {
        if (ridingType != RidingType.GROUND) return;
        player.setVelocity(player.getEyeLocation().getDirection().multiply(launchSpeed));
    }

    @Override
    public void remove() {
        super.remove();
        playAirbendingParticles(player.getLocation(), 16);
        flightHandler.removeInstance(player, getName());
        resourceBar.removeAll();
        bPlayer.addCooldown(this);
    }

    private void findFloor() {
        this.floorBlock = null;
        for (int i = 1; i < this.maxHeightFromGround; i++) {
            Block blockBelow = player.getLocation().getBlock().getRelative(BlockFace.DOWN, i);
            if (isRideable(blockBelow)) {
                this.floorBlock = blockBelow;
                return;
            }
        }
    }

    private Vector getHorizontalDirection() {
        float yaw = player.getEyeLocation().getYaw();
        return new Vector(
                -Math.sin(Math.toRadians(yaw)),
                0,
                Math.cos(Math.toRadians(yaw))
        ).normalize();
    }

    private boolean isRideable(Block block) {
        return GeneralMethods.isSolid(block) || isWater(block) || isLava(block);
    }

    @Override
    public boolean isSneakAbility() {
        return true;
    }

    @Override
    public boolean isHarmlessAbility() {
        return true;
    }

    @Override
    public long getCooldown() {
        return cooldown;
    }

    @Override
    public String getName() {
        return "AirScooter";
    }

    @Override
    public Location getLocation() {
        return player.getLocation();
    }

    @Override
    public void load() {
        ProjectKorra.plugin
                .getServer()
                .getPluginManager()
                .registerEvents(
                        new AirScooterPlusListener(),
                        ProjectKorra.plugin
                );
    }

    @Override
    public void stop() {

    }

    @Override
    public String getAuthor() {
        return "FinnBueno";
    }

    @Override
    public String getVersion() {
        return VERSION;
    }
}
