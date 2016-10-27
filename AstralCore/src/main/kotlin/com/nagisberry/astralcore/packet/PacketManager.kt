package com.nagisberry.astralcore.packet

import net.minecraft.server.v1_10_R1.Packet
import org.bukkit.craftbukkit.v1_10_R1.entity.CraftPlayer
import org.bukkit.entity.Player

object PacketManager {

    fun sendPacket(player: Player, packet: Packet<*>) {
        getPlayerConnection(player).sendPacket(packet)
    }

    fun inject(player: Player) {
        getChannelPipeline(player).addBefore("packet_handler", "AstralCraft", PlayerChannelHandler(player))
    }

    fun remove(player: Player) {
        getChannelPipeline(player).remove(PlayerChannelHandler::class.java)
    }

    private fun getChannelPipeline(player: Player) =
            getPlayerConnection(player).networkManager.channel.pipeline()

    private fun getPlayerConnection(player: Player) =
            (player as  CraftPlayer).handle.playerConnection
}