package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.debug.autotest

import com.doublemoon1119.mahjongcraft.flow.network.dto.message.RoomActionDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.NetworkJson
import com.doublemoon1119.mahjongcraft.platform.minecraft.metadata.MinecraftModMetadata
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.room.RoomScreen
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.state.ClientMahjongStateStore
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.network.MahjongChannels
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.GameConfigEditorSpec
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.GameConfigPresentationRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.MinecraftRoomScreenKeys
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.TableOccupancyDto
import com.doublemoon1119.mahjongcraft.platform.minecraft.rule.RuleModuleDisplayNameRegistry
import net.minecraft.client.gui.components.AbstractButton
import net.minecraft.core.Direction
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.InteractionHand
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3
import org.koin.core.context.GlobalContext
import org.lwjgl.glfw.GLFW
import kotlin.math.abs
import kotlin.time.Duration.Companion.seconds

/**
 * 像玩家一樣開一場牌局。
 *
 * - 放下麻將桌後不等待就右鍵，確認剛放下的桌子也能打開房間畫面。
 * - 再右鍵打開房間畫面，經網路送出建立房間、加入三個 AI 與開始的操作，等發牌演出結束。
 * - 開局後的牌桌由之後的步驟沿用。
 * - 本機玩家不開自動操作，牌局停在等玩家操作，讓之後的步驟在同一局中進行。
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
        // 放下後不等待就右鍵，伺服器通常在同一個 tick 處理兩者：桌子還沒等到延後的位置登記，也要能開啟房間畫面。
        client.gameMode?.useItemOn(player, InteractionHand.MAIN_HAND, BlockHitResult(Vec3.atCenterOf(position), Direction.UP, position, false))
        val placed = context.waitUntil(SYNC_TIMEOUT) { BuiltInRegistries.BLOCK.getKey(client.level!!.getBlockState(position).block).toString() == TABLE_ITEM }
        if (!context.check("Placed a wooden table like a player", placed)) return
        val openedAtOnce = context.waitUntil(SYNC_TIMEOUT) { client.screen is RoomScreen }
        if (!context.check("Right-clicking the table right after placing it opens the room screen", openedAtOnce)) return
        client.setScreen(null)
        if (!context.clearInventory()) return
        if (!context.teleport(x = 0.5, y = -60.0, z = 1.5, yaw = 0f, pitch = 35f)) return
        context.screenshot("placed")

        client.gameMode?.useItemOn(player, InteractionHand.MAIN_HAND, BlockHitResult(Vec3.atCenterOf(position), Direction.UP, position, false))
        val opened = context.waitUntil(SYNC_TIMEOUT) { client.screen is RoomScreen }
        if (!context.check("Right-clicking the table opens the room screen", opened)) return
        val tableId = (client.screen as? RoomScreen)?.tableId ?: error("The room screen closed before its table ID was read")
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
     * 逐一查看房間的設定頁。
     *
     * - 從房間畫面打開設定頁，按規則名稱旁的「?」打開規則目錄再返回。
     * - 接著依序切換每個分類分頁截圖，最後回到成員頁。
     * - 只切換分頁與開啟子畫面，不改動任何設定。
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
        if (!tourMultiChoice()) return false
        checkNotNull(client.screen).onClose()
        return check("The room settings return to the member page", waitUntil(SCREEN_TIMEOUT) { client.screen is RoomScreen && tabs().isEmpty() })
    }

    /**
     * 查看多選設定畫面。
     *
     * - 在設定頁把規則改選成有多選設定的規則，打開多選畫面截圖後取消，再以「復原」放棄改選，回到原本的規則。
     * - 改選規則只改變設定頁的草稿，沒有按套用就不會送到伺服器，房間的規則全程不變。
     * - 沒有任何規則提供多選設定時（例如沒有載入範例擴充），記錄略過並視為通過。
     *
     * @return 全部畫面都正常切換，或沒有規則可以測試時為 true。
     */
    private suspend fun AutotestContext.tourMultiChoice(): Boolean {
        val koin = GlobalContext.get()
        val presentations = koin.get<GameConfigPresentationRegistry>()
        val ruleNames = koin.get<RuleModuleDisplayNameRegistry>()
        // 與設定頁的規則選單相同的選項順序。
        val candidates = presentations.ruleModuleIds.mapNotNull(presentations::find)
        val nameKeys = candidates.mapNotNull { ruleNames.find(it.ruleModuleId) }.toSet()
        val selector = screenButtons().firstOrNull { button -> nameKeys.any { button.message.usesKey(it) } }
        if (!check("The room settings show the rule selector", selector != null)) return false
        val currentKey = nameKeys.first { checkNotNull(selector).message.usesKey(it) }
        val targetIndex = candidates.indexOfFirst { candidate ->
            candidate.selectable &&
                ruleNames.find(candidate.ruleModuleId) != currentKey &&
                candidate.fields.any { it.editor is GameConfigEditorSpec.MultiChoice }
        }
        if (targetIndex < 0) return check("No rule offers a multi-choice setting, so the multi-choice screen is skipped", true)
        val targetKey = checkNotNull(ruleNames.find(candidates[targetIndex].ruleModuleId))

        // 與玩家用鍵盤操作相同：打開規則選單，從目前的規則移到目標規則再按 Enter。
        checkNotNull(selector).onPress()
        val currentIndex = candidates.indexOfFirst { ruleNames.find(it.ruleModuleId) == currentKey }
        val screen = checkNotNull(client.screen)
        val key = if (targetIndex > currentIndex) GLFW.GLFW_KEY_DOWN else GLFW.GLFW_KEY_UP
        repeat(abs(targetIndex - currentIndex)) { screen.keyPressed(key, 0, 0) }
        screen.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0)
        val switched = waitUntil(SCREEN_TIMEOUT) { screenButtons().any { it.message.usesKey(targetKey) } }
        if (!check("The settings page switches to a rule with a multi-choice setting", switched)) return false

        // 改選規則後顯示第一個分類；多選欄位不在這個分類時依序切換分頁直到出現。
        for (tab in tabs()) {
            if (screenButtons().any { it.message.usesKey(MinecraftRoomScreenKeys.SELECTED_COUNT) }) break
            tab.onPress()
            waitUntil(SCREEN_TIMEOUT) { tab !in screenButtons() }
        }
        if (!check("The multi-choice setting button is pressed", pressButton(MinecraftRoomScreenKeys.SELECTED_COUNT))) return false
        if (!check("The multi-choice screen opens", waitForScreen("MultiChoiceScreen"))) return false
        waitTicks(SCREEN_SETTLE_TICKS)
        screenshot("room-multi-choice")
        checkNotNull(client.screen).onClose()
        if (!check("Cancelling the multi-choice screen returns to the room settings", waitUntil(SCREEN_TIMEOUT) { client.screen is RoomScreen && tabs().isNotEmpty() })) return false

        if (!check("The undo button is pressed", pressButton(MinecraftRoomScreenKeys.UNDO))) return false
        val restored = waitUntil(SCREEN_TIMEOUT) { screenButtons().any { it.message.usesKey(currentKey) } }
        return check("Undo returns the settings page to the room's rule", restored)
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
