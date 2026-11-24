package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.debug.autotest

import com.doublemoon1119.mahjongcraft.flow.network.dto.message.RoomActionDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.NetworkJson
import com.doublemoon1119.mahjongcraft.platform.minecraft.metadata.MinecraftModMetadata
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.room.RoomScreen
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.state.ClientMahjongStateStore
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.network.MahjongChannels
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.TableOccupancyDto
import net.minecraft.core.Direction
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.InteractionHand
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3
import org.koin.core.context.GlobalContext
import kotlin.time.Duration.Companion.seconds

/**
 * 像玩家一樣放下麻將桌、右鍵打開房間畫面，經網路送出建立房間、加入三個 AI 與開始的操作，等發牌演出結束。
 *
 * 開局後的牌桌由之後的步驟沿用；本機玩家不開自動操作，牌局停在等玩家操作，讓之後的步驟在同一局中進行。
 */
internal object AutotestTableStep : AutotestStep {
    override val id: String = "table"

    override suspend fun run(context: AutotestContext) {
        val client = context.client
        val position = AutotestTable.POSITION
        val player = checkNotNull(client.player)
        if (!context.teleport(x = 0.5, y = -60.0, z = 3.5, yaw = 0f, pitch = 30f)) return
        player.inventory.selected = 0
        if (!context.clearInventory()) return
        if (!context.runCheckedCommand("give @s $TABLE_ITEM")) return
        val holding = context.waitUntil(SYNC_TIMEOUT) { BuiltInRegistries.ITEM.getKey(player.mainHandItem.item).toString() == TABLE_ITEM }
        if (!context.check("Holding a wooden table", holding)) return
        val below = position.below()
        client.gameMode?.useItemOn(player, InteractionHand.MAIN_HAND, BlockHitResult(Vec3.atCenterOf(below).add(0.0, 0.5, 0.0), Direction.UP, below, false))
        val placed = context.waitUntil(SYNC_TIMEOUT) { BuiltInRegistries.BLOCK.getKey(client.level!!.getBlockState(position).block).toString() == TABLE_ITEM }
        if (!context.check("Placed a wooden table like a player", placed)) return
        if (!context.clearInventory()) return
        if (!context.teleport(x = 0.5, y = -60.0, z = 1.5, yaw = 0f, pitch = 35f)) return
        context.screenshot("placed")

        client.gameMode?.useItemOn(player, InteractionHand.MAIN_HAND, BlockHitResult(Vec3.atCenterOf(position), Direction.UP, position, false))
        val opened = context.waitUntil(SYNC_TIMEOUT) { client.screen is RoomScreen }
        if (!context.check("Right-clicking the table opens the room screen", opened)) return
        val tableId = (client.screen as RoomScreen).tableId
        AutotestTable.tableId = tableId
        context.screenshot("room-vacant")

        val koin = GlobalContext.get()
        val json = koin.get<NetworkJson>()
        val states = koin.get<ClientMahjongStateStore>()
        val venueId = tableId.toString()
        MahjongChannels.roomAction.sendToServer(json, RoomActionDto.Create(venueId))
        if (!context.check("Created a room", context.waitUntil(SYNC_TIMEOUT) { states.roomSnapshot(tableId)?.isInRoom == true })) return
        for (index in 1..AI_COUNT) {
            MahjongChannels.roomAction.sendToServer(json, RoomActionDto.AddAi(venueId))
            val added = context.waitUntil(SYNC_TIMEOUT) { states.roomSnapshot(tableId)?.playerIds?.size == index + 1 }
            if (!context.check("Added AI player $index", added)) return
        }
        context.screenshot("room-full")

        MahjongChannels.roomAction.sendToServer(json, RoomActionDto.Start(venueId))
        val started = context.waitUntil(SYNC_TIMEOUT) { states.tableOccupancy(tableId)?.occupancy == TableOccupancyDto.GAME }
        if (!context.check("Started a game", started)) return
        client.setScreen(null)
        if (!context.check("The deal finishes", context.ensureGameRunning())) return
        context.tableScreenshots("dealt")
    }

    /** 加入的 AI 數量。 */
    private const val AI_COUNT = 3

    /** 放下的麻將桌物品。 */
    private const val TABLE_ITEM = "${MinecraftModMetadata.MOD_ID}:wooden_mahjong_table"

    /** 等用戶端收到伺服器變化的最長時間。 */
    private val SYNC_TIMEOUT = 10.seconds
}
