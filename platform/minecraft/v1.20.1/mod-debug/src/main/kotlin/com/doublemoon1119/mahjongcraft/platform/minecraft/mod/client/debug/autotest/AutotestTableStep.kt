package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.debug.autotest

import com.doublemoon1119.mahjongcraft.flow.network.dto.message.RoomActionDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.NetworkJson
import com.doublemoon1119.mahjongcraft.platform.minecraft.metadata.MinecraftModMetadata
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.room.RoomScreen
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.state.ClientMahjongStateStore
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.network.MahjongChannels
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.MinecraftRoomScreenKeys
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.TableOccupancyDto
import net.minecraft.client.gui.components.AbstractButton
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
        if (!context.tourRoomSettings()) return

        MahjongChannels.roomAction.sendToServer(json, RoomActionDto.Start(venueId))
        val started = context.waitUntil(SYNC_TIMEOUT) { states.tableOccupancy(tableId)?.occupancy == TableOccupancyDto.GAME }
        if (!context.check("Started a game", started)) return
        client.setScreen(null)
        if (!context.check("The deal finishes", context.ensureGameRunning())) return
        context.tableScreenshots("dealt")
    }

    /**
     * 從房間畫面打開設定頁，按規則名稱旁的「?」打開規則目錄再返回，接著依序切換每個分類分頁截圖，最後回到成員頁。
     *
     * 只切換分頁與開啟子畫面，不改動任何設定。
     *
     * @return 全部畫面都正常切換時為 true。
     */
    private suspend fun AutotestContext.tourRoomSettings(): Boolean {
        if (!check("The room settings button is pressed", pressButton(MinecraftRoomScreenKeys.PAGE_SETTINGS))) return false
        if (!check("The room settings page shows its categories", waitUntil(SCREEN_TIMEOUT) { tabs().isNotEmpty() })) return false
        if (!check("The rule catalogue button is pressed", pressButtonShowing(RULE_CATALOGUE_LABEL))) return false
        if (!check("The rule catalogue opens from the room settings", waitForScreen("RuleCatalogueScreen"))) return false
        waitTicks(SCREEN_SETTLE_TICKS)
        screenshot("room-rule-catalogue")
        checkNotNull(client.screen).onClose()
        if (!check("The rule catalogue returns to the room settings", waitUntil(SCREEN_TIMEOUT) { client.screen is RoomScreen && tabs().isNotEmpty() })) return false
        for (index in tabs().indices) {
            // 設定頁打開時已經選在第一個分類；之後每切換一次分頁都會重建畫面上的元件，舊的分頁按鈕消失代表新的分頁已經畫好。
            if (index > 0) {
                val tab = tabs()[index]
                tab.onPress()
                if (!check("Room settings category ${index + 1} opens", waitUntil(SCREEN_TIMEOUT) { tab !in screenButtons() })) return false
            }
            waitTicks(SCREEN_SETTLE_TICKS)
            screenshot("room-settings-${index + 1}")
        }
        checkNotNull(client.screen).onClose()
        return check("The room settings return to the member page", waitUntil(SCREEN_TIMEOUT) { client.screen is RoomScreen && tabs().isEmpty() })
    }

    /**
     * 目前畫面中的分類分頁。
     *
     * @return 依畫面順序排列的分頁按鈕。
     */
    private fun AutotestContext.tabs(): List<AbstractButton> = screenButtons().filter { it.javaClass.simpleName == "TabWidget" }

    /** 房間設定頁規則名稱旁開啟規則目錄的按鈕文字。 */
    private const val RULE_CATALOGUE_LABEL = "?"

    /** 加入的 AI 數量。 */
    private const val AI_COUNT = 3

    /** 放下的麻將桌物品。 */
    private const val TABLE_ITEM = "${MinecraftModMetadata.MOD_ID}:wooden_mahjong_table"

    /** 等用戶端收到伺服器變化的最長時間。 */
    private val SYNC_TIMEOUT = 10.seconds
}
