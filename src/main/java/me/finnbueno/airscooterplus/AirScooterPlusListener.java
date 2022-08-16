package me.finnbueno.airscooterplus;

import com.projectkorra.projectkorra.BendingPlayer;
import com.projectkorra.projectkorra.ability.CoreAbility;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerInteractEvent;

public class AirScooterPlusListener implements Listener {

    @EventHandler
    public void onClick(PlayerInteractEvent event) {
        if (event.getAction() != Action.LEFT_CLICK_AIR && event.getAction() != Action.LEFT_CLICK_BLOCK) {
            return;
        }
        BendingPlayer bendingPlayer = BendingPlayer.getBendingPlayer(event.getPlayer());
        if (bendingPlayer.canBend(CoreAbility.getAbility(AirScooterPlus.class))) {
            new AirScooterPlus(event.getPlayer());
        }
    }

}
