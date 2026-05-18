package com.windpvp.admin.vanish;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.windpvp.admin.AdminTools;

public class VanishPacketListener {

    public VanishPacketListener(AdminTools plugin) {
        ProtocolLibrary.getProtocolManager().addPacketListener(
            new PacketAdapter(plugin, PacketType.Play.Server.NAMED_ENTITY_SPAWN) {
                @Override
                public void onPacketSending(PacketEvent event) {
                    PacketContainer packet = event.getPacket();
                    // Get the entity ID of the player being spawned
                    int entityId = packet.getIntegers().read(0);
                    
                    // If the entity is a player and is in our vanished list, cancel the packet
                    plugin.getServer().getOnlinePlayers().stream()
                        .filter(p -> p.getEntityId() == entityId)
                        .findFirst()
                        .ifPresent(vanishedPlayer -> {
                            if (((AdminTools) plugin).getVanishedPlayers().contains(vanishedPlayer.getUniqueId())) {
                                event.setCancelled(true);
                            }
                        });
                }
            }
        );
    }
}