package doublemoon.mahjongcraft.client.gui

import doublemoon.mahjongcraft.MOD_ID
import doublemoon.mahjongcraft.client.gui.widget.*
import doublemoon.mahjongcraft.game.mahjong.riichi.RankedScoreItem
import doublemoon.mahjongcraft.game.mahjong.riichi.ScoreSettlement
import doublemoon.mahjongcraft.scheduler.client.ScoreSettleHandler
import io.github.cottonmc.cotton.gui.client.CottonClientScreen
import io.github.cottonmc.cotton.gui.client.LightweightGuiDescription
import io.github.cottonmc.cotton.gui.widget.WLabel
import io.github.cottonmc.cotton.gui.widget.WPlainPanel
import io.github.cottonmc.cotton.gui.widget.data.Color
import io.github.cottonmc.cotton.gui.widget.data.VerticalAlignment
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.minecraft.client.MinecraftClient
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import java.util.*

@Environment(EnvType.CLIENT)
class ScoreSettlementScreen(
    settlement: ScoreSettlement
) : CottonClientScreen(ScoreSettlementGui(settlement)) {
    override fun shouldPause(): Boolean = false
}

@Environment(EnvType.CLIENT)
class ScoreSettlementGui(
    private val settlement: ScoreSettlement
) : LightweightGuiDescription() {
    private val fontHeight = MinecraftClient.getInstance().textRenderer.fontHeight
    private val rankedScoreList = settlement.rankedScoreList
    private val time: Text
        get() {
            val time = ScoreSettleHandler.time
            val dotAmount = 3 - (time % 3)
            var text = "$time "
            repeat(dotAmount) { text += "." }
            return Text.of(text)
        }

    init {
        rootPlainPanel(width = ROOT_WIDTH, height = ROOT_HEIGHT) {
            dynamicLabel(
                x = BORDER_MARGIN,
                y = BORDER_MARGIN + ROOT_HEIGHT - fontHeight,
                text = { time.string },
                color = COLOR_RED
            )
            val title = label(
                x = BORDER_MARGIN,
                y = BORDER_MARGIN,
                text = Text.translatable(settlement.titleTranslateKey)
                    .formatted(Formatting.DARK_PURPLE)
                    .formatted(Formatting.BOLD)
            )
            val confirmButton = button(
                x = ROOT_WIDTH - BUTTON_WIDTH - BORDER_MARGIN,
                y = ROOT_HEIGHT - BUTTON_HEIGHT - BORDER_MARGIN,
                width = BUTTON_WIDTH,
                label = Text.translatable("$MOD_ID.gui.button.confirm"),
                onClick = { ScoreSettleHandler.closeScreen() }
            )
            scrollPanel(
                x = title.x,
                y = title.y + title.height + PADDING_NORMAL,
                width = ROOT_WIDTH,
                height = ROOT_HEIGHT - title.height - confirmButton.height - PADDING_NORMAL * 2
            ) {
                val scoreItems = mutableListOf<ScoreItem>()
                rankedScoreList.forEachIndexed { index, rankedScoreItem ->
                    val item = ScoreItem(index + 1, rankedScoreItem)
                    val y = if (index > 0) scoreItems[index - 1].let { it.y + it.height + 12 } else 0
                    this.add(item, 0, y)
                    scoreItems += item
                }
            }
        }
    }

    class ScoreItem(
        number: Int,
        rankedScoreItem: RankedScoreItem
    ) : WPlainPanel() {
        private val displayName = rankedScoreItem.scoreItem.displayName
        private val stringUUID = rankedScoreItem.scoreItem.stringUUID
        private val isRealPlayer = rankedScoreItem.scoreItem.isRealPlayer
        private val botCode = rankedScoreItem.scoreItem.botCode
        private val scoreTotal = rankedScoreItem.scoreTotal
        private val scoreChange = rankedScoreItem.scoreChangeText
        private val rankFloat = rankedScoreItem.rankFloatText

        private val textRenderer = MinecraftClient.getInstance().textRenderer
        private val maxDisplayNameLabelWidth = textRenderer.getWidth("HI IM DOUBLEMOON") //名字的長度先取最長的 16 個字元
        private val maxScoreLabelWidth = textRenderer.getWidth("1000000")

        init {
            val rank = label(
                x = BORDER_MARGIN + 20,
                y = BORDER_MARGIN,
                height = 22,
                text = Text.of("$number."),
                verticalAlignment = VerticalAlignment.CENTER
            )
            val face = if (isRealPlayer) {
                playerFace(
                    x = rank.x + rank.width + 8,
                    y = rank.y,
                    width = 22,
                    height = 22,
                    uuid = UUID.fromString(stringUUID),
                    name = displayName
                )
            } else {
                botFace(
                    x = rank.x + rank.width + 8,
                    y = rank.y,
                    width = 22,
                    height = 22,
                    code = botCode
                )
            }
            val displayNameText =
                if (isRealPlayer) Text.of(displayName) else Text.translatable("entity.$MOD_ID.mahjong_bot")
            val displayNameLabel = label(
                x = face.x + face.width + 8,
                y = face.y,
                text = displayNameText,
                width = maxDisplayNameLabelWidth,
                verticalAlignment = VerticalAlignment.CENTER
            )
            val scoreTotalLabel = label(
                x = displayNameLabel.x + displayNameLabel.width + 8,
                y = displayNameLabel.y,
                text = Text.of("$scoreTotal"),
                width = maxScoreLabelWidth,
                verticalAlignment = VerticalAlignment.CENTER
            )
            val scoreChangeTextColor =
                if (scoreChange.string == "") WLabel.DEFAULT_TEXT_COLOR else if (scoreChange.string.toInt() > 0) Color.GREEN.toRgb() else Color.RED.toRgb()
            val scoreChangeLabel = label(
                x = scoreTotalLabel.x + scoreTotalLabel.width + 8,
                y = scoreTotalLabel.y,
                text = scoreChange,
                width = maxScoreLabelWidth,
                verticalAlignment = VerticalAlignment.CENTER,
                color = scoreChangeTextColor
            )
            val rankFloatTextColor = when (rankFloat.string) {
                "↓" -> COLOR_RED
                else -> COLOR_GREEN
            }
            label(
                x = scoreChangeLabel.x + scoreChangeLabel.width + 8,
                y = scoreChangeLabel.y,
                text = rankFloat,
                verticalAlignment = VerticalAlignment.CENTER,
                color = rankFloatTextColor
            )
        }
    }

    companion object {
        private const val ROOT_WIDTH = 300
        private const val ROOT_HEIGHT = 200
        private const val BORDER_MARGIN = 8
        private const val PADDING_NORMAL = 10
        private const val BUTTON_WIDTH = 80
        private const val BUTTON_HEIGHT = 20
        private const val COLOR_GREEN = 0x55FF55
        private const val COLOR_RED = 0xFF5555
    }
}