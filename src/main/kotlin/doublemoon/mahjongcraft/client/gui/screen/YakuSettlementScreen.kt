package doublemoon.mahjongcraft.client.gui.screen

import doublemoon.mahjongcraft.MOD_ID
import doublemoon.mahjongcraft.client.gui.icon.BotFaceIcon
import doublemoon.mahjongcraft.client.gui.icon.PlayerFaceIcon
import doublemoon.mahjongcraft.client.gui.widget.*
import doublemoon.mahjongcraft.game.mahjong.riichi.model.YakuSettlement
import doublemoon.mahjongcraft.scheduler.client.YakuSettleHandler
import doublemoon.mahjongcraft.util.plus
import io.github.cottonmc.cotton.gui.client.CottonClientScreen
import io.github.cottonmc.cotton.gui.client.LibGui
import io.github.cottonmc.cotton.gui.client.LightweightGuiDescription
import io.github.cottonmc.cotton.gui.widget.WLabel
import io.github.cottonmc.cotton.gui.widget.WPlainPanel
import io.github.cottonmc.cotton.gui.widget.data.Color
import io.github.cottonmc.cotton.gui.widget.data.HorizontalAlignment
import io.github.cottonmc.cotton.gui.widget.data.VerticalAlignment
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.minecraft.client.MinecraftClient
import net.minecraft.text.MutableText
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import java.util.*

@Environment(EnvType.CLIENT)
class YakuSettlementScreen(
    settlements: List<YakuSettlement>
) : CottonClientScreen(YakuSettlementGui(settlements)) {
    override fun shouldPause(): Boolean = false
}

@Environment(EnvType.CLIENT)
class YakuSettlementGui(
    private val settlements: List<YakuSettlement>
) : LightweightGuiDescription() {

    private val client = MinecraftClient.getInstance()
    private val fontHeight = client.textRenderer.fontHeight
    private val time: Int
        get() = YakuSettleHandler.time
    private val timeText: Text
        get() {
            val dotAmount = 3 - (time % 3)
            var text = "$time "
            repeat(dotAmount) { text += "." }
            return Text.of(text)
        }
    private val darkMode: Boolean
        get() = LibGui.isDarkMode()

    init {
        rootTabPanel(width = ROOT_WIDTH, height = ROOT_HEIGHT) {
            settlements.forEach {
                val hands = it.hands
                val fuuroList = it.fuuroList
                val yakumanList = it.yakumanList
                val doubleYakumanList = it.doubleYakumanList
                val nagashiMangan = it.nagashiMangan
                val redFiveCount = it.redFiveCount
                val score = it.score
                val widget = WPlainPanel().apply {
                    dynamicLabel(
                        x = BORDER_MARGIN,
                        y = ROOT_HEIGHT + TAB_HEIGHT - fontHeight,
                        text = { timeText.string },
                        color = COLOR_RED
                    )
                    val playerInfo = plainPanel(
                        x = BORDER_MARGIN,
                        y = BORDER_MARGIN,
                        width = PLAYER_INFO_WIDTH,
                        height = PLAYER_INFO_HEIGHT
                    ) {
                        val face = if (it.isRealPlayer) {
                            playerFace(
                                x = (PLAYER_INFO_WIDTH - PLAYER_FACE_WIDTH) / 2,
                                y = 12,
                                width = PLAYER_FACE_WIDTH,
                                height = PLAYER_FACE_WIDTH,
                                uuid = UUID.fromString(it.uuid),
                                name = it.displayName
                            )
                        } else {
                            botFace(
                                x = (PLAYER_INFO_WIDTH - PLAYER_FACE_WIDTH) / 2,
                                y = 12,
                                width = PLAYER_FACE_WIDTH,
                                height = PLAYER_FACE_WIDTH,
                                code = it.botCode
                            )
                        }
                        label(
                            x = face.x,
                            y = face.y + face.height + 12,
                            width = face.width,
                            horizontalAlignment = HorizontalAlignment.CENTER,
                            text = Text.of(it.displayName)
                        )
                    }
                    val playerTiles = plainPanel(
                        x = playerInfo.x + playerInfo.width + 16,
                        y = 12,
                        width = ROOT_WIDTH - PLAYER_INFO_WIDTH - BORDER_MARGIN * 2,
                        height = TILE_HEIGHT
                    ) {
                        //很多槓的時候有機會出現 手牌 + 副露 + 槍牌 會有 16 張牌以上的情況, 會導致渲染的東西過長, 所以這裡的寬度另外做調整
                        val amount =
                            hands.size + fuuroList.sumOf { fuuro -> fuuro.second.size } + if (!nagashiMangan) 1 else 0
                        val rate = if (amount > 16) 16f / amount else 1f
                        val tileWidth = (TILE_WIDTH * rate).toInt()
                        val tileHeight = (TILE_HEIGHT * rate).toInt()
                        val tileGap = (TILE_GAP * rate).toInt()
                        var tileX = 0
                        hands.forEach { tile -> //先渲染手牌 (手牌一定至少有一張牌)
                            mahjongTile(
                                x = tileX,
                                y = 0,
                                width = tileWidth,
                                height = tileHeight,
                                mahjongTile = tile
                            )
                            tileX += (tileWidth + tileGap)
                        }
                        if (it.fuuroList.isNotEmpty()) { //副露不為空
                            tileX += tileGap * 2 //手牌跟副露中間的間隔大一點
                            it.fuuroList.forEach { (isAnkan, tiles) ->
                                tiles.forEachIndexed { index, mahjongTile ->
                                    if (isAnkan && (index == 0 || index == 3)) {
                                        colorBlock(
                                            x = tileX,
                                            y = 0,
                                            width = tileWidth,
                                            height = tileHeight,
                                            color = COLOR_TILE_BACK
                                        )
                                    } else {
                                        mahjongTile(
                                            x = tileX,
                                            y = 0,
                                            width = tileWidth,
                                            height = tileHeight,
                                            mahjongTile = mahjongTile
                                        )
                                    }
                                    tileX += (tileWidth + tileGap)
                                }
                            }
                        }
                        if (!nagashiMangan) { //如果非流局滿貫, 顯示槍牌
                            tileX += tileGap * 3
                            mahjongTile(
                                x = tileX,
                                y = 0,
                                width = tileWidth,
                                height = tileHeight,
                                mahjongTile = it.winningTile
                            )
                        }
                    }
                    val separator1 = colorBlock(
                        x = playerTiles.x,
                        y = playerTiles.y + playerTiles.height + SEPARATOR_PADDING,
                        width = playerTiles.width,
                        height = SEPARATOR_SIZE,
                        color = if (darkMode) SEPARATOR_COLOR_DARK else SEPARATOR_COLOR_LIGHT
                    )
                    val doraAndUraDoraIndicators = plainPanel(
                        x = separator1.x,
                        y = separator1.y + separator1.height + SEPARATOR_PADDING,
                        width = separator1.width,
                        height = TILE_HEIGHT
                    ) {
                        val uraDoraIndicators = if (it.riichi) it.uraDoraIndicators else listOf() //沒有立直當作沒有裏寶牌指示牌
                        var tileX = 0
                        val doraText = label(
                            x = tileX,
                            y = 0,
                            height = TILE_HEIGHT,
                            verticalAlignment = VerticalAlignment.CENTER,
                            text = Text.translatable("$MOD_ID.game.dora"),
                            color = Color.PURPLE_DYE.toRgb()
                        )
                        tileX += doraText.width + TILE_GAP * 2
                        repeat(5) { index ->
                            if (index < it.doraIndicators.size) {
                                mahjongTile(
                                    x = tileX,
                                    y = 0,
                                    width = TILE_WIDTH,
                                    height = TILE_HEIGHT,
                                    mahjongTile = it.doraIndicators[index]
                                )
                            } else {
                                colorBlock(
                                    x = tileX,
                                    y = 0,
                                    width = TILE_WIDTH,
                                    height = TILE_HEIGHT,
                                    color = COLOR_TILE_BACK
                                )
                            }
                            tileX += (TILE_WIDTH + TILE_GAP)
                        }
                        tileX += TILE_GAP * 5
                        val uraDoraText = label(
                            x = tileX,
                            y = 0,
                            height = TILE_HEIGHT,
                            verticalAlignment = VerticalAlignment.CENTER,
                            text = Text.translatable("$MOD_ID.game.ura_dora"),
                            color = Color.PURPLE_DYE.toRgb()
                        )
                        tileX += uraDoraText.width + TILE_GAP * 2
                        repeat(5) { index ->
                            if (index < uraDoraIndicators.size) {
                                mahjongTile(
                                    x = tileX,
                                    y = 0,
                                    width = TILE_WIDTH,
                                    height = TILE_HEIGHT,
                                    mahjongTile = uraDoraIndicators[index]
                                )
                            } else {
                                colorBlock(
                                    x = tileX,
                                    y = 0,
                                    width = TILE_WIDTH,
                                    height = TILE_HEIGHT,
                                    color = COLOR_TILE_BACK
                                )
                            }
                            tileX += (TILE_WIDTH + TILE_GAP)
                        }
                    }
                    val separator2 = colorBlock(
                        x = doraAndUraDoraIndicators.x,
                        y = doraAndUraDoraIndicators.y + doraAndUraDoraIndicators.height + SEPARATOR_PADDING,
                        width = doraAndUraDoraIndicators.width,
                        height = SEPARATOR_SIZE,
                        color = if (darkMode) SEPARATOR_COLOR_DARK else SEPARATOR_COLOR_LIGHT
                    )
                    val yakuList = scrollPanel(
                        x = separator2.x,
                        y = separator2.y + separator2.height + SEPARATOR_PADDING,
                        width = separator2.width,
                        height = YAKU_LIST_HEIGHT
                    ) {
                        val yakuHanMap = hashMapOf<String, Int>() //key: 役的名稱(小蛇式), value: 翻數 (負數表示不會顯示翻數)
                        when {
                            yakumanList.isNotEmpty() || doubleYakumanList.isNotEmpty() -> { //有役滿或雙倍役滿的役
                                yakumanList.forEach { yakuman -> yakuHanMap[yakuman.name.lowercase()] = -1 }
                                doubleYakumanList.forEach { doubleYakuman ->
                                    yakuHanMap[doubleYakuman.name.lowercase()] = -1
                                }
                            }
                            nagashiMangan -> yakuHanMap["nagashi_mangan"] = -1 //沒有役滿的役, 有流局滿貫
                            else -> { //沒有役滿的役, 也沒有流局滿貫
                                it.yakuList.forEach { yaku ->
                                    val key = yaku.name.lowercase()
                                    if (yakuHanMap[key] != null) yakuHanMap[key] =
                                        yakuHanMap[key]!! + yaku.han //可能有重複的役出現, ex: 寶牌 or 裏寶牌
                                    else yakuHanMap[key] = yaku.han
                                }
                                if (redFiveCount > 0) yakuHanMap["red_five"] = redFiveCount //如果有赤寶牌, 手動補上赤寶牌的役
                            }
                        }
                        val yakuLabels = mutableListOf<WLabel>()
                        yakuHanMap.forEach { (yaku, han) ->
                            if (yakuLabels.size > 0) {
                                colorBlock(
                                    x = 0,
                                    y = yakuLabels.last().let { label -> label.y + label.height + SEPARATOR_PADDING },
                                    width = doraAndUraDoraIndicators.width - SCROLL_BAR_SIZE - 4,
                                    height = SEPARATOR_SIZE,
                                    color = if (darkMode) YAKU_SEPARATOR_COLOR_DARK else YAKU_SEPARATOR_COLOR_LIGHT
                                )
                            }
                            val yakuName = label(
                                x = 0,
                                y = if (yakuLabels.size > 0) yakuLabels.last()
                                    .let { label -> label.y + label.height + SEPARATOR_PADDING * 2 + SEPARATOR_SIZE } else 0,
                                width = YAKU_LABEL_WIDTH,
                                text = Text.translatable("$MOD_ID.game.yaku.$yaku"), //先印役的名稱
                                verticalAlignment = VerticalAlignment.CENTER,
                                color = if (han >= 0) Color.BLACK.toRgb() else Color.CYAN_DYE.toRgb() //(役滿、雙倍役滿、流局滿貫皆為 -1, 顏色顯示不一樣)
                            )
                            yakuLabels += yakuName
                            if (han >= 0) {  //翻數大於 0 才會印出來, (役滿、雙倍役滿、流局滿貫皆為 -1, 一律不顯示)
                                label(
                                    x = yakuName.x + yakuName.width,
                                    y = yakuName.y,
                                    width = doraAndUraDoraIndicators.width - yakuName.width - SCROLL_BAR_SIZE - 8,
                                    height = yakuName.height,
                                    text = Text.of(han.toString()), //再印翻數
                                    color = Color.GREEN_DYE.toRgb(),
                                    verticalAlignment = VerticalAlignment.CENTER,
                                    horizontalAlignment = HorizontalAlignment.RIGHT
                                )
                            }
                        }
                    }
                    //最後是顯示 符, 翻數, 分數, 跟分數的名稱的位置
                    val scoreHeight = fontHeight + TILE_GAP * 3
                    var scoreText: MutableText = Text.literal("") //先建立空文字
                    //沒有役滿 也沒有雙倍役滿 也沒有流局滿貫, 才顯示符數跟翻數
                    if (yakumanList.isEmpty() && doubleYakumanList.isEmpty() && !nagashiMangan) {
                        val fu = Text.literal("${it.fu}").formatted(Formatting.DARK_AQUA)
                        val fuText = Text.translatable("$MOD_ID.game.fu").formatted(Formatting.DARK_PURPLE)
                        val han = Text.literal("${it.han}").formatted(Formatting.DARK_AQUA)
                        val hanText = Text.translatable("$MOD_ID.game.han").formatted(Formatting.DARK_PURPLE)
                        scoreText += (fu + " " + fuText + " " + han + " " + hanText)
                    }
                    scoreText += "  §c$score"
                    val scoreAlias: MutableText? = when { //計算出分數對應的名稱, ex: 滿貫, 倍滿, 役滿
                        nagashiMangan -> Text.translatable("$MOD_ID.game.score.mangan") //流局滿貫出現一定只有滿貫
                        yakumanList.isNotEmpty() || doubleYakumanList.isNotEmpty() -> { //有役滿的役種出現
                            val rate = (yakumanList.size * 1 + doubleYakumanList.size * 2).let { amount ->
                                if (amount > 6) 6 else amount //算出役滿倍率 (理論上最大應該是 六倍役滿 所以超過 六倍 一律當作 六倍役滿)
                            }
                            Text.translatable("$MOD_ID.game.score.yakuman_${rate}x")
                        }
                        it.han >= 13 -> Text.translatable("$MOD_ID.game.score.kazoe_yakuman") //大於等於 13 翻, 累計役滿
                        it.han >= 11 -> Text.translatable("$MOD_ID.game.score.sanbaiman") // 12, 11 翻, 三倍滿
                        it.han >= 8 -> Text.translatable("$MOD_ID.game.score.baiman") // 10, 9, 8 翻, 倍滿
                        it.han >= 6 -> Text.translatable("$MOD_ID.game.score.haneman") // 7, 6 翻, 跳滿
                        it.han >= 5 || (it.fu >= 40 && it.han == 4) || (it.fu >= 70 && it.han == 3) -> //滿貫
                            Text.translatable("$MOD_ID.game.score.mangan")
                        else -> null
                    }?.also { alias -> alias.formatted(Formatting.BOLD).formatted(Formatting.DARK_RED) }
                    if (scoreAlias != null) {
                        val decoratedStr = "§4§l!!"
                        scoreText += "  $decoratedStr"
                        scoreText += scoreAlias
                        scoreText += decoratedStr
                    }
                    plainPanel(
                        x = yakuList.x,
                        y = yakuList.y + yakuList.height + 8,
                        width = yakuList.width,
                        height = scoreHeight
                    ) {
                        label(
                            x = 0,
                            y = 0,
                            width = yakuList.width - BORDER_MARGIN,
                            height = scoreHeight,
                            text = scoreText,
                            horizontalAlignment = HorizontalAlignment.RIGHT,
                            verticalAlignment = VerticalAlignment.CENTER
                        )
                    }
                }
                //目前是沒有自動切換 tab 的功能, 可以抄 WTabPanel 寫一個出來, 但是我有點懶
                tab(
                    widget = widget,
                    icon = if (it.isRealPlayer) PlayerFaceIcon(
                        uuid = UUID.fromString(it.uuid),
                        name = it.displayName
                    ) else BotFaceIcon(code = it.botCode),
                    tooltip = listOf(Text.of(it.displayName))
                )
            }
        }
    }

    companion object {
        private const val TAB_HEIGHT = 34 //這個 34 是 WTabPanel.TAB_HEIGHT + WTabPanel.TAB_PADDING
        private const val ROOT_WIDTH = 400
        private const val ROOT_HEIGHT = 200 - TAB_HEIGHT
        private const val BORDER_MARGIN = 8
        private const val PLAYER_INFO_WIDTH = 84
        private const val PLAYER_INFO_HEIGHT = 84
        private const val PLAYER_FACE_WIDTH = 48
        private const val TILE_SCALE = 0.35f //這個值要觀察一下
        private const val TILE_WIDTH = (48 * TILE_SCALE).toInt()
        private const val TILE_HEIGHT = (64 * TILE_SCALE).toInt()
        private const val TILE_GAP = 3
        private const val YAKU_LABEL_WIDTH = 120
        private const val YAKU_LIST_HEIGHT = 135 - TAB_HEIGHT
        private const val COLOR_TILE_BACK = (0xFF_9CFF69).toInt()
        private const val COLOR_RED = (0xFF_FF5555).toInt()
        private const val SCROLL_BAR_SIZE = 8 //這個值等同 WScrollPanel.SCROLL_BAR_SIZE
        private const val SEPARATOR_COLOR_DARK = (0xFF747A80).toInt()
        private const val SEPARATOR_COLOR_LIGHT = (0xFF3C3F41).toInt()
        private const val YAKU_SEPARATOR_COLOR_DARK = (0xFF5F6467).toInt()
        private const val YAKU_SEPARATOR_COLOR_LIGHT = (0xFFAFB1B3).toInt()
        private const val SEPARATOR_PADDING = 5
        private const val SEPARATOR_SIZE = 1
    }
}