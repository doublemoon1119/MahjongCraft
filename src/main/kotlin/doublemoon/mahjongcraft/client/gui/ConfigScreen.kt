package doublemoon.mahjongcraft.client.gui

import doublemoon.mahjongcraft.MOD_ID
import doublemoon.mahjongcraft.MahjongCraftClient
import doublemoon.mahjongcraft.client.ModConfig
import doublemoon.mahjongcraft.client.gui.config.*
import doublemoon.mahjongcraft.game.mahjong.riichi.MahjongGameBehavior
import doublemoon.mahjongcraft.network.MahjongGamePacketHandler.sendMahjongGamePacket
import me.shedaniel.clothconfig2.api.ConfigBuilder
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.option.KeyBinding
import net.minecraft.text.LiteralText
import net.minecraft.text.TranslatableText
import java.util.*

object ConfigScreen {
    private val title = TranslatableText("config.$MOD_ID.title")
    private val displayTableLabelsText = TranslatableText("config.$MOD_ID.display_table_labels")
    private val hudBackgroundColorText = TranslatableText("config.$MOD_ID.hud.background_color")
    private val quickActionsText = TranslatableText("config.$MOD_ID.quick_actions")
    private val displayHudWhenPlayingText = TranslatableText("config.$MOD_ID.quick_actions.display_hud_when_playing")
    private val autoArrangeText = TranslatableText("config.$MOD_ID.quick_actions.auto_arrange")
    private val autoCallWinText = TranslatableText("config.$MOD_ID.quick_actions.auto_call_win")
    private val noChiiPonKanText = TranslatableText("config.$MOD_ID.quick_actions.no_chii_pon_kan")
    private val autoDrawAndDiscardText = TranslatableText("config.$MOD_ID.quick_actions.auto_draw_and_discard")
    private val autoResetTooltip = TranslatableText("config.$MOD_ID.tooltip.auto_reset")

    private val hudPositionEditorKey = MahjongCraftClient.hudPositionEditorKey
    private val hudPositionEditorTooltip
        get() = TranslatableText(
            "config.$MOD_ID.tooltip.hud_position_editor",
            hudPositionEditorKey.boundKeyLocalizedText
        )

    fun build(parent: Screen? = null): Screen = getConfigBuilder(parent).build()

    private fun getConfigBuilder(parent: Screen? = null): ConfigBuilder {
        val config = MahjongCraftClient.config
        val defaultConfig = ModConfig()
        return configBuilder(title = title, parent = parent, savingRunnable = { MahjongCraftClient.saveConfig() }) {
            category(LiteralText("general")) {
                booleanToggle(
                    text = displayTableLabelsText,
                    startValue = config.displayTableLabels,
                    defaultValue = { defaultConfig.displayTableLabels },
                ) { config.displayTableLabels = it }
                keyCodeField(
                    text = TranslatableText(hudPositionEditorKey.translationKey),
                    startKey = KeyBindingHelper.getBoundKeyOf(hudPositionEditorKey),
                    defaultKey = { hudPositionEditorKey.defaultKey },
                    tooltipSupplier = { Optional.of(arrayOf(hudPositionEditorTooltip)) },
                    saveConsumer = {
                        hudPositionEditorKey.setBoundKey(it)
                        KeyBinding.updateKeysByCode()
                        MinecraftClient.getInstance().options.write()
                    }
                )
                subCategory(text = quickActionsText) {
                    booleanToggle(
                        text = displayHudWhenPlayingText,
                        startValue = config.quickActions.displayHudWhenPlaying,
                        defaultValue = { defaultConfig.quickActions.displayHudWhenPlaying }
                    ) {
                        config.quickActions.displayHudWhenPlaying = it
                        MahjongCraftClient.hud?.refresh()
                    }
                    alphaColorField(
                        text = hudBackgroundColorText,
                        startColor = config.quickActions.hudAttribute.backgroundColor,
                        defaultColor = { defaultConfig.quickActions.hudAttribute.backgroundColor }
                    ) {
                        config.quickActions.hudAttribute.backgroundColor = it
                        MahjongCraftClient.hud?.refresh()
                    }
                    booleanToggle(
                        text = autoArrangeText,
                        startValue = config.quickActions.autoArrange,
                        defaultValue = { defaultConfig.quickActions.autoArrange }
                    ) {
                        config.quickActions.autoArrange = it
                        MinecraftClient.getInstance().player?.sendMahjongGamePacket( //每次更改 AutoArrange 的設定都發送當前的狀態過去伺服器
                            behavior = MahjongGameBehavior.AUTO_ARRANGE,
                            extraData = it.toString()
                        )
                    }
                    booleanToggle(
                        text = autoCallWinText,
                        startValue = config.quickActions.autoCallWin,
                        defaultValue = { defaultConfig.quickActions.autoCallWin },
                        tooltipSupplier = { Optional.of(arrayOf(autoResetTooltip)) }
                    ) { config.quickActions.autoCallWin = it }
                    booleanToggle(
                        text = noChiiPonKanText,
                        startValue = config.quickActions.noChiiPonKan,
                        defaultValue = { defaultConfig.quickActions.noChiiPonKan },
                        tooltipSupplier = { Optional.of(arrayOf(autoResetTooltip)) }
                    ) { config.quickActions.noChiiPonKan = it }
                    booleanToggle(
                        text = autoDrawAndDiscardText,
                        startValue = config.quickActions.autoDrawAndDiscard,
                        defaultValue = { defaultConfig.quickActions.autoDrawAndDiscard },
                        tooltipSupplier = { Optional.of(arrayOf(autoResetTooltip)) }
                    ) { config.quickActions.autoDrawAndDiscard = it }
                }
            }
        }
    }
}