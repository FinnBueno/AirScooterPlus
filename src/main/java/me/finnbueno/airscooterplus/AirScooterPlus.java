package me.finnbueno.airscooterplus;

import com.projectkorra.projectkorra.GeneralMethods;
import com.projectkorra.projectkorra.ProjectKorra;
import com.projectkorra.projectkorra.ability.AddonAbility;
import com.projectkorra.projectkorra.ability.AirAbility;
import com.projectkorra.projectkorra.ability.ElementalAbility;
import com.projectkorra.projectkorra.attribute.Attribute;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.util.Vector;

public class AirScooterPlus extends AirAbility implements AddonAbility, Listener {

    private static final String VERSION = "1.0.0";

    @Attribute(Attribute.SPEED)
    private double speed = 1;
    @Attribute(Attribute.COOLDOWN)
    private long cooldown = 3000;
    private int maxHeightFromGround = 3;

    private Block floorBlock;

    public AirScooterPlus(Player player) {
        super(player);

        AirScooterPlus existingScooter = getAbility(player, AirScooterPlus.class);
        if (existingScooter != null) {
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
        if (GeneralMethods.isSolid(player.getLocation().getBlock().getRelative(BlockFace.DOWN))) {
            return;
        }

        // For some reason, the stock AirScooter also has a check for solid or water at the player's eye level
        // Not sure why, but if bugs arise, consider adding it as a potential fix

        this.flightHandler.createInstance(player, getName());
        player.setAllowFlight(true);
        player.setFlying(false);

        player.setSprinting(false);
        player.setSneaking(false);

        start();
    }

    @Override
    public void progress() {
        if (!bPlayer.canBendIgnoreCooldowns(this)) {
            remove();
            return;
        }

        findFloor();
        if (this.floorBlock == null) {
            remove();
            return;
        }

        // TODO: Limit the turn angle as an optional feature
        Vector playerDirection = player.getEyeLocation().getDirection().multiply(speed);
        // check this so the move doesn't immediately cancel itself, player needs time to gain some speed
        if (System.currentTimeMillis() > getStartTime() + 250) {
            // player has hit a wall or is otherwise being denied movement in their desired direction
            if (player.getVelocity().length() < speed * .3) {
                remove();
                return;
            }
        }

        double distanceToFloor = player.getLocation().getY() - floorBlock.getY();
        if (distanceToFloor > 2.75) {
            playerDirection.setY(-.25);
        } else if (distanceToFloor < 2) {
            playerDirection.setY(.25);
        } else {
            playerDirection.setY(0);
        }
        Location l = player.getEyeLocation();
        player.sendMessage(Math.cos(Math.toRadians(l.getYaw())) + " - " + Math.sin(Math.toRadians(l.getPitch())));
    }

    private void findFloor() {
        this.floorBlock = null;
        for (int i = 1; i < this.maxHeightFromGround; i++) {
            Block blockBelow = player.getLocation().getBlock().getRelative(BlockFace.DOWN, i);
            if (
                    GeneralMethods.isSolid(blockBelow) ||
                    ElementalAbility.isWater(blockBelow) ||
                    ElementalAbility.isLava(blockBelow)
            ) {
                this.floorBlock = blockBelow;
                return;
            }
        }
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
        return "AirScooterPlus";
    }

    @Override
    public Location getLocation() {
        return player.getLocation();
    }

    @Override
    public void load() {
        System.out.println("Load");
        ProjectKorra.plugin.getServer().getPluginManager().registerEvents(new AirScooterPlusListener(), ProjectKorra.plugin);
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
