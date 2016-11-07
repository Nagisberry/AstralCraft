package com.nagisberry.astralitems

import com.google.common.base.Optional
import com.nagisberry.astralcore.AstralCore
import com.nagisberry.astralcore.AstralCore.Companion.fromJson
import com.nagisberry.astralcore.command.CommandManager
import com.nagisberry.astralcore.event.PacketWriteEvent
import com.nagisberry.astralcore.packet.PacketManager
import net.minecraft.server.v1_10_R1.*
import net.minecraft.server.v1_10_R1.ItemStack as NMSItemStack
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.craftbukkit.v1_10_R1.inventory.CraftItemStack
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerGameModeChangeEvent
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin
import java.nio.file.Files
import kotlin.concurrent.thread

class AstralItems: JavaPlugin(), Listener {

    companion object {
        val hideFlags = arrayOf(
                ItemFlag.HIDE_ATTRIBUTES,
                ItemFlag.HIDE_DESTROYS,
                ItemFlag.HIDE_ENCHANTS,
                ItemFlag.HIDE_PLACED_ON,
                ItemFlag.HIDE_UNBREAKABLE,
                ItemFlag.HIDE_POTION_EFFECTS
        )
    }

    override fun onEnable() {
        CommandManager.registerCommand("items", this)
        Bukkit.getPluginManager().registerEvents(this, this)
        if (!dataFolder.exists()) { Files.createDirectory(dataFolder.toPath()) }
        ItemManager.loadFiles(dataFolder.toPath())
    }

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<String>): Boolean {
        if (command.name == "items") {
            if (sender.isOp) {
                if (args.size == 0) {
                    sender.sendMessage("/$label [reload|get]")
                } else if (args[0].equals("reload", true)) {
                    ItemManager.reloadFiles(dataFolder.toPath())
                    sender.sendMessage("[AstralItems] リロードが完了しました")
                } else if (args[0].equals("get", true)) {
                    if (sender !is Player) {
                        sender.sendMessage("[AstralItems] Playerのみ実行可能なコマンドです")
                    } else if (args.size == 1) {
                        sender.sendMessage("/$label ${args[0]} <ItemID> (<amount>)")
                    } else {
                        ItemManager[args[1]]?.createItemStack(
                                if (args.size > 2) { args[2].toInt() } else { 1 },
                                if (args.size > 3) {
                                    AstralCore.gson.fromJson<Map<String, Map<String, Any>>>(
                                            args.copyOfRange(3, args.size).joinToString(" ")
                                    )
                                } else { emptyMap() }
                        )?.let {
                            sender.inventory.addItem(it)
                        } ?: sender.sendMessage("[AstralItems] 存在しないItemIDです")
                    }
                }
                return true
            }
        }
        return false
    }

    @EventHandler
    fun onPacketWrite(evt: PacketWriteEvent) {
        val message = evt.message
        if (evt.player.gameMode != GameMode.CREATIVE) {
            if (message is PacketPlayOutWindowItems) {
                message.javaClass.getDeclaredField("b").apply { isAccessible = true }.let {
                    it[message] = it[message].let { it as Array<*> }
                            .map { it as NMSItemStack? }
                            .map(CraftItemStack::asBukkitCopy)
                            .map(ItemStack::toDisplayItem)
                            .map(CraftItemStack::asNMSCopy).toTypedArray()
                }
            } else if (message is PacketPlayOutSetSlot) {
                message.javaClass.getDeclaredField("c").apply { isAccessible = true }.let {
                    it[message] = it[message]
                            ?.let { it as NMSItemStack? }
                            ?.let(CraftItemStack::asBukkitCopy)
                            ?.let {
                                if (it.itemMeta?.hasItemFlag(ItemFlag.HIDE_PLACED_ON) ?: false) {
                                    it
                                } else { it.toDisplayItem() }
                            }?.let(CraftItemStack::asNMSCopy)
                }
            }
        }
        if (message is PacketPlayOutEntityMetadata) {
            val field = DataWatcher.Item::class.java.getDeclaredField("b").apply { isAccessible = true }
            message.javaClass.getDeclaredField("b").apply { isAccessible = true }.let { f ->
                (f[message] as List<*>).map { it as DataWatcher.Item<*> }.let {
                    val itemStack = it.filter { it.a().a() == 6 }.map {
                        (it.b() as? Optional<*>)?.orNull() as? NMSItemStack?
                    }.filterNotNull().getOrNull(0)?.let(CraftItemStack::asBukkitCopy)?.let {
                        if (it.itemMeta.hasItemFlag(ItemFlag.HIDE_PLACED_ON)) { null } else {
                            it.toDisplayItem()
                        }
                    } ?: null
                    if (itemStack != null) {
                        f[message] = mutableListOf(*it.toTypedArray()).map { item -> when (item.a().a()) {
                            2 -> item.apply { field[item] = itemStack.itemMeta.displayName }
                            3 -> item.apply { field[item] = true }
                            6 -> itemStack.let(CraftItemStack::asNMSCopy).let { Optional.of(it) }.let {
                                DataWatcher.Item<Optional<NMSItemStack>>(
                                        item.a() as DataWatcherObject<Optional<NMSItemStack>>, it
                                )
                            }
                            else -> item
                        } }
                    }
                }
            }
        } else if (message is PacketPlayOutEntityEquipment) {
            message.javaClass.getDeclaredField("b").apply { isAccessible = true }?.let {
                if((it[message] as EnumItemSlot).a() == EnumItemSlot.Function.HAND) {
                    message.javaClass.getDeclaredField("c").apply { isAccessible = true }?.let {
                        it[message] = (it[message] as NMSItemStack?)
                                ?.let(CraftItemStack::asBukkitCopy)
                                ?.let(ItemStack::toDisplayItem)
                                ?.let(CraftItemStack::asNMSCopy)
                    }
                }
            }
        }
    }

    @EventHandler
    fun onGameModeChange(evt: PlayerGameModeChangeEvent) {
        val player = evt.player
        if (evt.newGameMode == GameMode.CREATIVE) {
            player.inventory.contents.forEachIndexed { slot, stack ->
                PacketPlayOutSetSlot(
                        0, getNMSSlotNumber(slot),
                        stack?.let(CraftItemStack::asNMSCopy)
                ).let { thread {
                    Thread.sleep(10)
                    PacketManager.sendPacket(player, it)
                } }
            }
        } else if (player.gameMode == GameMode.CREATIVE) {
            player.inventory.contents.forEachIndexed { slot, stack ->
                PacketPlayOutSetSlot(
                        0, getNMSSlotNumber(slot),
                        stack?.toDisplayItem()?.apply { itemMeta = itemMeta.apply {
                            addItemFlags(*hideFlags)
                        } }?.let(CraftItemStack::asNMSCopy)
                ).let { PacketManager.sendPacket(player, it) }
            }
        }
    }

    private fun getNMSSlotNumber(bukkitSlotNumber: Int) = when (bukkitSlotNumber) {
        in 0..8 -> bukkitSlotNumber + 36
        in 9..35 -> bukkitSlotNumber
        in 36..39 -> (bukkitSlotNumber - 44) * -1
        40 -> 45
        else -> -1
    }
}