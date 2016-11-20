package com.nagisberry.astralcore

import com.google.gson.Gson
import com.nagisberry.astralcore.packet.PacketManager
import org.bukkit.Bukkit
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.plugin.java.JavaPlugin
import java.io.Reader

class AstralCore: JavaPlugin(), Listener {

    companion object {
        val gson = Gson()

        inline fun <reified T: Any> Gson.fromJson(json: Reader): T = fromJson(json, T::class.java)

        inline fun <reified T: Any> Gson.fromJson(json: String): T = fromJson(json, T::class.java)
    }

    override fun onEnable() {
        PacketManager.inject(server)
        Bukkit.getPluginManager().registerEvents(this, this)
    }
}