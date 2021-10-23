package doublemoon.mahjongcraft.client.gui

import doublemoon.mahjongcraft.MOD_ID
import doublemoon.mahjongcraft.client.gui.widget.*
import doublemoon.mahjongcraft.game.mahjong.riichi.ScoreSettlement
import doublemoon.mahjongcraft.scheduler.client.ScoreSettleHandler
import io.github.cottonmc.cotton.gui.client.CottonClientScreen
import io.github.cottonmc.cotton.gui.client.LightweightGuiDescription
import io.github.cottonmc.cotton.gui.widget.WPlainPanel
import io.github.cottonmc.cotton.gui.widget.data.VerticalAlignment
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.minecraft.client.MinecraftClient
import net.minecraft.text.LiteralText
import net.minecraft.text.Text
import net.minecraft.text.TranslatableText
import net.minecraft.util.Formatting
import java.util.*

@Environment(EnvType.CLIENT)
class MahjongScoreSettlementScreen(description: MahjongScoreSettlementGui) : CottonClientScreen(description) {
    constructor(settlement: ScoreSettlement) : this(MahjongScoreSettlementGui(settlement))

    override fun isPauseScreen(): Boolean = false
}

@Environment(EnvType.CLIENT)
class MahjongScoreSettlementGui(
    private val settlement: ScoreSettlement
) : LightweightGuiDescription() {

    private val fontHeight = MinecraftClient.getInstance().textRenderer.fontHeight
    private val rankedScoreList = settlement.rankedScoreStringList
    private val timeText: Text
        get() {
            val time = ScoreSettleHandler.time
            val dotAmount = 3 - (time % 3)
            var text = "$time "
            repeat(dotAmount) { text += "." }
            return LiteralText(text)
        }

    init {
        rootPlainPanel(width = ROOT_WIDTH, height = ROOT_HEIGHT) {
            dynamicLabel(
                x = 0,
                y = ROOT_HEIGHT - fontHeight,
                text = { timeText.string },
                color = COLOR_RED
            )
            val title = label(
                x = 0,
                y = 0,
                text = TranslatableText(settlement.titleLang)
                    .formatted(Formatting.DARK_PURPLE)
                    .formatted(Formatting.BOLD)
            )
            val confirmButton = button(
                x = ROOT_WIDTH - BUTTON_WIDTH,
                y = ROOT_HEIGHT - BUTTON_HEIGHT,
                width = BUTTON_WIDTH,
                label = TranslatableText("$MOD_ID.gui.button.confirm"),
                onClick = { ScoreSettleHandler.closeScreen() }
            )
            scrollPanel(
                x = title.x,
                y = title.y + title.height + PADDING_NORMAL,
                width = ROOT_WIDTH,
                height = ROOT_HEIGHT - title.height - confirmButton.height - PADDING_NORMAL * 2
            ) {
                val scoreItems = mutableListOf<ScoreItem>()
                rankedScoreList.forEachIndexed { index, info ->
                    val item = ScoreItem(index + 1, info)
                    val y = if (index > 0) scoreItems[index - 1].let { it.y + it.height + 12 } else 0
                    this.add(item, 0, y)
                    scoreItems += item
                }
            }
        }
    }

    class ScoreItem(
        number: Int,
        info: List<String>
    ) : WPlainPanel() {
        private val displayName = info[0]
        private val stringUUID = info[1]
        private val isRealPlayer = info[2].toBooleanStrict()
        private val botCode = info[3].toInt()
        private val scoreTotal = info[4]
        private val scoreChange = info[5]
        private val rankFloat = info[6]

        private val textRenderer = MinecraftClient.getInstance().textRenderer
        private val maxDisplayNameLabelWidth = textRenderer.getWidth("HI IM DOUBLEMOON") //名字的長度先取最長的 16 個字元
        private val maxScoreLabelWidth = textRenderer.getWidth("1000000")

        init {
            val rank = label(
                x = 20,
                y = 0,
                height = 22,
                text = LiteralText("$number."),
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
            val displayNameLabel = label(
                x = face.x + face.width + 8,
                y = face.y,
                text = LiteralText(displayName),
                width = maxDisplayNameLabelWidth,
                verticalAlignment = VerticalAlignment.CENTER
            )
            val scoreTotalLabel = label(
                x = displayNameLabel.x + displayNameLabel.width + 8,
                y = displayNameLabel.y,
                text = LiteralText(scoreTotal),
                width = maxScoreLabelWidth,
                verticalAlignment = VerticalAlignment.CENTER
            )
            val scoreChangeTextColor = if (scoreChange == "") "" else if (scoreChange.toInt() > 0) "§a" else "§c"
            val scoreChangeLabel = label(
                x = scoreTotalLabel.x + scoreTotalLabel.width + 8,
                y = scoreTotalLabel.y,
                text = LiteralText("$scoreChangeTextColor$scoreChange"),
                width = maxScoreLabelWidth,
                verticalAlignment = VerticalAlignment.CENTER
            )
            val rankFloatTextColor = when (rankFloat) {
                "↓" -> COLOR_RED
                else -> COLOR_GREEN
            }
            label(
                x = scoreChangeLabel.x + scoreChangeLabel.width + 8,
                y = scoreChangeLabel.y,
                text = LiteralText(rankFloat),
                verticalAlignment = VerticalAlignment.CENTER,
                color = rankFloatTextColor
            )
        }
    }

    companion object {
        private const val ROOT_WIDTH = 300
        private const val ROOT_HEIGHT = 200
        private const val PADDING_NORMAL = 10
        private const val BUTTON_WIDTH = 80
        private const val BUTTON_HEIGHT = 20
        private const val COLOR_GREEN = 0x55FF55
        private const val COLOR_RED = 0xFF5555
    }
}