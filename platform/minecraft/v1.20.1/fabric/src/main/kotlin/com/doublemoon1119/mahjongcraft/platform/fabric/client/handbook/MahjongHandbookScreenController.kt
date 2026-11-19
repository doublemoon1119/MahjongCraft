package com.doublemoon1119.mahjongcraft.platform.fabric.client.handbook

import com.doublemoon1119.mahjongcraft.platform.fabric.client.catalogue.RuleCatalogueScreenController
import com.doublemoon1119.mahjongcraft.platform.fabric.client.config.MahjongClientConfigScreenController
import com.doublemoon1119.mahjongcraft.platform.fabric.client.history.HistoryScreenController
import com.doublemoon1119.mahjongcraft.platform.fabric.registry.ModItems
import com.doublemoon1119.mahjongcraft.platform.minecraft.config.MinecraftClientConfigScreenKeys
import com.doublemoon1119.mahjongcraft.platform.minecraft.handbook.MinecraftHandbookScreenKeys
import com.doublemoon1119.mahjongcraft.platform.minecraft.ruler.MahjongRulerColor
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper
import net.fabricmc.fabric.api.event.player.UseItemCallback
import net.minecraft.client.MinecraftClient
import net.minecraft.client.option.KeyBinding
import net.minecraft.client.util.InputUtil
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import net.minecraft.util.TypedActionResult
import org.lwjgl.glfw.GLFW

/**
 * 開啟麻將手冊快捷畫面：按鍵（預設分號）、拿著麻將手冊按右鍵與指令三種入口。
 *
 * 從快捷畫面開啟的功能頁，返回時回到快捷畫面。
 *
 * @property configScreens 客戶端設定畫面的開啟入口。
 * @property historyScreens 對局歷史畫面的開啟入口。
 * @property ruleCatalogueScreens 規則一覽畫面的開啟入口。
 */
class MahjongHandbookScreenController(
    private val configScreens: MahjongClientConfigScreenController,
    private val historyScreens: HistoryScreenController,
    private val ruleCatalogueScreens: RuleCatalogueScreenController,
) {
    /** 開啟麻將手冊的按鍵，預設分號。 */
    private lateinit var openKeyBinding: KeyBinding

    /** 是否已完成事件註冊。 */
    private var registered = false

    /** 指令要求開啟、等待下一個 client tick 開啟。 */
    private var openRequested = false

    /** 註冊按鍵、client tick 與使用物品事件；只能在 client entrypoint 呼叫一次。 */
    fun register() {
        if (registered) return
        registered = true
        openKeyBinding = KeyBindingHelper.registerKeyBinding(
            KeyBinding(
                MinecraftHandbookScreenKeys.OPEN_KEY,
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_SEMICOLON,
                MinecraftClientConfigScreenKeys.KEY_CATEGORY,
            ),
        )
        ClientTickEvents.END_CLIENT_TICK.register(::tick)
        UseItemCallback.EVENT.register { player, world, hand ->
            val stack = player.getStackInHand(hand)
            if (world.isClient && stack.isOf(ModItems.MAHJONG_HANDBOOK)) {
                open(MinecraftClient.getInstance())
                TypedActionResult.success(stack)
            } else {
                TypedActionResult.pass(stack)
            }
        }
    }

    /** 從指令開啟快捷畫面；延到下一個 client tick，避免指令執行後關閉聊天畫面時一併關掉快捷畫面。 */
    fun openFromCommand() {
        openRequested = true
    }

    /**
     * 開啟指令要求的快捷畫面，並消耗按鍵事件；其他畫面開啟時按鍵不搶占使用者輸入。
     *
     * @param client 本次 tick 的客戶端。
     */
    private fun tick(client: MinecraftClient) {
        if (openRequested) {
            openRequested = false
            if (client.world != null) open(client)
        }
        while (openKeyBinding.wasPressed()) {
            if (client.currentScreen == null) open(client)
        }
    }

    /**
     * 開啟快捷畫面。
     *
     * @param client 要開啟畫面的客戶端。
     */
    private fun open(client: MinecraftClient) {
        client.setScreen(HandbookScreen(entries = entries(), openHint = ::openHint))
    }

    /** 依顯示順序排列的入口。 */
    private fun entries(): List<HandbookEntry> = listOf(
        HandbookEntry(
            icon = ItemStack(ModItems.MAHJONG_RULERS.getValue(MahjongRulerColor.CLEAR)),
            titleKey = MinecraftHandbookScreenKeys.RULE_CATALOGUE,
            descriptionKey = MinecraftHandbookScreenKeys.RULE_CATALOGUE_DESCRIPTION,
            open = ruleCatalogueScreens::openFrom,
        ),
        HandbookEntry(
            icon = ItemStack(Items.WRITABLE_BOOK),
            titleKey = MinecraftHandbookScreenKeys.HISTORY,
            descriptionKey = MinecraftHandbookScreenKeys.HISTORY_DESCRIPTION,
            open = historyScreens::openFrom,
        ),
        HandbookEntry(
            icon = ItemStack(Items.COMPARATOR),
            titleKey = MinecraftHandbookScreenKeys.CLIENT_CONFIG,
            descriptionKey = MinecraftHandbookScreenKeys.CLIENT_CONFIG_DESCRIPTION,
            open = configScreens::openFrom,
        ),
    )

    /** 依目前的按鍵綁定產生開啟方式的提示；按鍵名稱以黃色標出，不加括號，綁定符號鍵時也看得清楚。 */
    private fun openHint(): Text = if (openKeyBinding.isUnbound) {
        Text.translatable(MinecraftHandbookScreenKeys.OPEN_HINT_UNBOUND)
    } else {
        Text.translatable(MinecraftHandbookScreenKeys.OPEN_HINT, openKeyBinding.boundKeyLocalizedText.copy().formatted(Formatting.YELLOW))
    }
}
