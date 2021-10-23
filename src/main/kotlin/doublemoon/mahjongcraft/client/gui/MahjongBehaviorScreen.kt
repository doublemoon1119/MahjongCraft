package doublemoon.mahjongcraft.client.gui

import doublemoon.mahjongcraft.MOD_ID
import doublemoon.mahjongcraft.client.gui.widget.*
import doublemoon.mahjongcraft.game.mahjong.riichi.ClaimTarget
import doublemoon.mahjongcraft.game.mahjong.riichi.MahjongGameBehavior
import doublemoon.mahjongcraft.game.mahjong.riichi.MahjongRule
import doublemoon.mahjongcraft.game.mahjong.riichi.MahjongTile
import doublemoon.mahjongcraft.network.MahjongGamePacketHandler.sendMahjongGamePacket
import doublemoon.mahjongcraft.scheduler.client.ClientCountdownTimeHandler
import io.github.cottonmc.cotton.gui.client.CottonClientScreen
import io.github.cottonmc.cotton.gui.client.LightweightGuiDescription
import io.github.cottonmc.cotton.gui.widget.WPlainPanel
import io.github.cottonmc.cotton.gui.widget.data.Color
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.minecraft.client.MinecraftClient
import net.minecraft.text.LiteralText
import net.minecraft.text.Text
import net.minecraft.text.TranslatableText
import net.minecraft.util.Formatting
import org.mahjong4j.tile.Tile

@Environment(EnvType.CLIENT)
class MahjongBehaviorScreen(description: MahjongBehaviorGui) : CottonClientScreen(description) {
    constructor(
        behavior: MahjongGameBehavior,
        hands: List<MahjongTile>,
        target: ClaimTarget,
        data: String
    ) : this(MahjongBehaviorGui(behavior, hands, target, data))

    override fun isPauseScreen(): Boolean = false
}

@Environment(EnvType.CLIENT)
class MahjongBehaviorGui(
    private val behavior: MahjongGameBehavior,
    private val hands: List<MahjongTile>,
    private val target: ClaimTarget,
    private val data: String
) : LightweightGuiDescription() {

    //渲染時間用
    private val basicTime: Int
        get() = ClientCountdownTimeHandler.basicAndExtraTime.first ?: 0
    private val extraTime: Int
        get() = ClientCountdownTimeHandler.basicAndExtraTime.second ?: 0
    private val basicTimeText: String
        get() = if (basicTime > 0) "§a$basicTime" else ""
    private val plusTimeText: String
        get() = if (basicTime > 0 && extraTime > 0) "§e + " else ""
    private val extraTimeText: String
        get() = if (extraTime > 0) "§c$extraTime" else ""
    private val timeText: LiteralText
        get() = LiteralText("$basicTimeText$plusTimeText$extraTimeText")

    //渲染麻將牌用
    private val hasTakenTile = when (behavior) { //在發送這行為過來時是否有拿牌, 目前只有下面 3 個情況有拿牌
        MahjongGameBehavior.TSUMO -> true
        MahjongGameBehavior.KYUUSHU_KYUUHAI -> true
        MahjongGameBehavior.ANKAN_OR_KAKAN -> true
        else -> false
    }
    private val handsWidth =
        TILE_WIDTH * hands.size + TILE_GAP * (hands.size - 1) + if (hasTakenTile) TAKEN_TILE_GAP else 0
    private var claimingTile: MahjongTile? = null

    /**
     * 根據 [behavior] 和 [data] 產生的選項的牌,
     * 用來渲染在畫面中央, 每個牌列表的最後一個都是被鳴的牌 (加槓除外, 加槓是倒數第 2 個是原本碰被鳴的牌, 最後一張是加槓的牌)
     * [Pair] 的格式為 Pair<Pair<行為,這個行為的對象>,牌的列表> (行為對象只有在 暗槓或加槓 的時候才會有特殊處理,否則都是 [target],加槓的對象是原本碰的對像)
     * */
    private val tilesForOptions =
        mutableListOf<Pair<Pair<MahjongGameBehavior, ClaimTarget>, MutableList<MahjongTile>>>().apply {
            when (behavior) {
                MahjongGameBehavior.CHII -> {
                    val dataList = Json.decodeFromString<MutableList<String>>(data)
                    claimingTile = Json.decodeFromString<MahjongTile>(dataList[0])
                    val tilePairsForChii =
                        Json.decodeFromString<MutableList<Pair<MahjongTile, MahjongTile>>>(dataList[1])
                    tilePairsForChii.forEach {
                        val tiles = it.toList().toMutableList().apply { this += claimingTile!! }
                        this += behavior to target to tiles
                    }
                }
                MahjongGameBehavior.PON_OR_CHII -> {
                    val dataList = Json.decodeFromString<MutableList<String>>(data)
                    claimingTile = Json.decodeFromString<MahjongTile>(dataList[0])
                    val tilePairsForChii =
                        Json.decodeFromString<MutableList<Pair<MahjongTile, MahjongTile>>>(dataList[1])
                    val tilePairForPon = Json.decodeFromString<Pair<MahjongTile, MahjongTile>>(dataList[2])
                    this += MahjongGameBehavior.PON to target to tilePairForPon.toList().toMutableList()
                        .apply { this += claimingTile!! }
                    tilePairsForChii.forEach {
                        this += MahjongGameBehavior.CHII to target to it.toList().toMutableList()
                            .apply { this += claimingTile!! }
                    }
                }
                MahjongGameBehavior.PON -> {
                    val dataList = Json.decodeFromString<MutableList<String>>(data)
                    claimingTile = Json.decodeFromString<MahjongTile>(dataList[0])
                    val tilePairForPon = Json.decodeFromString<Pair<MahjongTile, MahjongTile>>(dataList[1])
                    val tiles = tilePairForPon.toList().toMutableList().apply { this += claimingTile!! }
                    this += MahjongGameBehavior.PON to target to tiles
                }
                MahjongGameBehavior.ANKAN_OR_KAKAN -> {
                    val dataList = Json.decodeFromString<MutableList<String>>(data)
                    val canAnkanTiles = Json.decodeFromString<MutableSet<MahjongTile>>(dataList[0])
                    val canKakanTiles = Json.decodeFromString<MutableSet<Pair<MahjongTile, ClaimTarget>>>(dataList[1])
                    val rule = MahjongRule.fromJsonString(dataList[2])
                    val redFiveQuantity = rule.redFive.quantity
                    claimingTile = hands.last() //加槓才會用到
                    canAnkanTiles.forEach { tile -> //添加暗槓顯示的牌
                        val isFiveTile = //是否是 5 的牌
                            tile.mahjong4jTile == MahjongTile.S5.mahjong4jTile || tile.mahjong4jTile == MahjongTile.P5.mahjong4jTile || tile.mahjong4jTile == MahjongTile.M5.mahjong4jTile
                        if (redFiveQuantity == 0 || !isFiveTile) { //沒有赤牌 或 不是 5 的牌
                            this += MahjongGameBehavior.ANKAN to target to MutableList(4) { tile }
                        } else { //有赤牌,且是 5 的牌
                            val tiles = mutableListOf<MahjongTile>()
                            val redFiveTile = when (tile) {
                                MahjongTile.M5, MahjongTile.M5_RED -> MahjongTile.M5_RED
                                MahjongTile.S5, MahjongTile.S5_RED -> MahjongTile.S5_RED
                                MahjongTile.P5, MahjongTile.P5_RED -> MahjongTile.P5_RED
                                else -> null
                            }!!
                            val notRedFiveTile = MahjongTile.values()[redFiveTile.mahjong4jTile.code]
                            repeat(4) { times ->
                                tiles += if (times < redFiveQuantity) redFiveTile else notRedFiveTile
                            }
                            this += MahjongGameBehavior.ANKAN to target to tiles
                        }
                    }
                    canKakanTiles.forEach { //添加加槓顯示的牌
                        val tile = it.first
                        val oTarget = it.second
                        val isFiveTile = //是否是 5 的牌
                            tile.mahjong4jTile == MahjongTile.S5.mahjong4jTile || tile.mahjong4jTile == MahjongTile.P5.mahjong4jTile || tile.mahjong4jTile == MahjongTile.M5.mahjong4jTile
                        if (redFiveQuantity == 0 || !isFiveTile) { //沒有赤牌 或 不是 5 的牌
                            this += MahjongGameBehavior.KAKAN to oTarget to MutableList(4) { tile }
                        } else { //有赤牌,且是 5 的牌
                            val tiles = mutableListOf<MahjongTile>()
                            val redFiveAmount = redFiveQuantity - if (claimingTile!!.isRed) 1 else 0
                            val redFiveTile = getRedFiveTile(tile)
                                ?: throw IllegalStateException("Cannot get red-five tile from $tile")
                            val notRedFiveTile = MahjongTile.values()[redFiveTile.mahjong4jTile.code]
                            repeat(3) { times ->
                                tiles += if (times < redFiveAmount) redFiveTile else notRedFiveTile
                            }
                            tiles += claimingTile!! //最後一張是加槓牌
                            this += MahjongGameBehavior.KAKAN to oTarget to tiles
                        }
                    }
                }
                MahjongGameBehavior.MINKAN -> { //能出現明槓一定是同時能槓跟碰 (手上至少有 3 張能與 claimingTile 組成刻子的牌)
                    val dataList = Json.decodeFromString<MutableList<String>>(data)
                    claimingTile = Json.decodeFromString<MahjongTile>(dataList[0])
                    val rule = MahjongRule.fromJsonString(dataList[1])
                    val redFiveQuantity = rule.redFive.quantity
                    val isFiveTile = //是否是 5 的牌
                        claimingTile!!.mahjong4jTile == MahjongTile.S5.mahjong4jTile || claimingTile!!.mahjong4jTile == MahjongTile.P5.mahjong4jTile || claimingTile!!.mahjong4jTile == MahjongTile.M5.mahjong4jTile
                    if (redFiveQuantity == 0 || !isFiveTile) { //沒有赤牌 或 不是 5 的牌
                        this += MahjongGameBehavior.MINKAN to target to MutableList(4) { claimingTile!! }
                        this += MahjongGameBehavior.PON to target to MutableList(3) { claimingTile!! }
                    } else { //有赤牌,且是 5 的牌
                        val tilesForPon = mutableListOf<MahjongTile>()
                        val redFiveAmount = redFiveQuantity - if (claimingTile!!.isRed) 1 else 0
                        val redFiveTile = getRedFiveTile(claimingTile!!)
                            ?: throw IllegalStateException("Cannot get red-five tile from $claimingTile")
                        val notRedFiveTile = MahjongTile.values()[redFiveTile.mahjong4jTile.code]
                        repeat(2) { times -> tilesForPon += if (times < redFiveAmount) redFiveTile else notRedFiveTile }
                        val tilesForKan = tilesForPon.toMutableList()
                        tilesForKan += notRedFiveTile
                        tilesForPon += claimingTile!! //最後一張是加槓牌
                        tilesForKan += claimingTile!!
                        this += MahjongGameBehavior.MINKAN to target to tilesForKan
                        this += MahjongGameBehavior.PON to target to tilesForPon
                    }
                }
                MahjongGameBehavior.RIICHI -> { //立直會顯示聽的牌
//                    val machi = Json.decodeFromString<MutableList<MahjongTile>>(data)
                    val tilePairsForRiichi =
                        Json.decodeFromString<MutableList<Pair<MahjongTile, MutableList<MahjongTile>>>>(data)
                    tilePairsForRiichi.forEach { //只會顯示可以丟的牌, 並且不會顯示無役跟待取以及待取剩幾張
                        val tile = it.first
                        this += MahjongGameBehavior.RIICHI to target to mutableListOf(tile)
                    }
                }
                MahjongGameBehavior.KYUUSHU_KYUUHAI -> { //九種九牌會顯示手牌中所有么九牌
                    val allYaochu = hands.filter { it.mahjong4jTile.isYaochu }.toMutableList()
                    this += behavior to target to allYaochu
                }
                MahjongGameBehavior.RON -> {
                    claimingTile = Json.decodeFromString<MahjongTile>(data)
                    this += behavior to target to mutableListOf(claimingTile!!)
                }
                MahjongGameBehavior.TSUMO -> { //自摸為手牌最後一張
                    this += behavior to target to mutableListOf(hands.last())
                }
                else -> { //剩下的結果都不會顯示牌
                }
            }
        }

    /**
     * 根據 [tile] 取得對應的 赤牌
     * */
    private fun getRedFiveTile(tile: MahjongTile): MahjongTile? = when (tile) {
        MahjongTile.M5, MahjongTile.M5_RED -> MahjongTile.M5_RED
        MahjongTile.S5, MahjongTile.S5_RED -> MahjongTile.S5_RED
        MahjongTile.P5, MahjongTile.P5_RED -> MahjongTile.P5_RED
        else -> null
    }

    /**
     * 拿來顯示手牌提示用, 僅使用 [Tile] 來比對是否為同張牌
     * */
    private val handsHintTiles: MutableList<Tile> = mutableListOf<Tile>().apply {
        tilesForOptions.forEach {
            val behavior = it.first.first
            val tiles = it.second.toMutableList()
            when (behavior) {
                MahjongGameBehavior.ANKAN,
                MahjongGameBehavior.KAKAN,
                MahjongGameBehavior.MINKAN,
                MahjongGameBehavior.RIICHI,
                MahjongGameBehavior.TSUMO,
                MahjongGameBehavior.RON -> this += tiles.last().mahjong4jTile //只傳入最後一張牌
                else -> { //剩下的情況傳入除了最後一張牌以外的牌
                    tiles.removeLast()
                    tiles.forEach { tile -> this += tile.mahjong4jTile }
                }
            }
        }
    }

    init {
        rootPlainPanel(width = ROOT_WIDTH, height = ROOT_HEIGHT) {
            val timer = dynamicLabel(x = 0, y = 0, text = { timeText.rawString }) //顯示剩餘時間用
            button(
                x = ROOT_WIDTH - BUTTON_WIDTH,
                y = 0,
                width = BUTTON_WIDTH,
                label = TranslatableText("$MOD_ID.game.behavior.skip"),
                onClick = {
                    MinecraftClient.getInstance().player?.sendMahjongGamePacket(
                        behavior = MahjongGameBehavior.SKIP
                    )
                }
            )
            claimingTileWidget()
            val handsWidgetHeight = TILE_HEIGHT + TILE_GAP + HINT_HEIGHT
            val handsWidget = handsWidget(
                x = 0,
                y = ROOT_HEIGHT - handsWidgetHeight,
                width = ROOT_WIDTH,
                height = handsWidgetHeight
            )
            optionsWidget(
                x = 0,
                y = HINT_HEIGHT * 2 + TILE_GAP * 2 + TILE_HEIGHT + TILE_GAP * 6,
                width = ROOT_WIDTH,
                height = ROOT_HEIGHT - timer.height - handsWidget.height
            )
        }
    }

    private fun WPlainPanel.claimingTileWidget(): WMahjongTile? {
        claimingTile?.let { //正中間上方被鳴的牌
            val mtX = (ROOT_WIDTH - TILE_WIDTH) / 2
            val tileWidget =
                mahjongTile(
                    x = mtX,
                    y = HINT_HEIGHT + TILE_GAP,
                    width = TILE_WIDTH,
                    height = TILE_HEIGHT,
                    mahjongTile = it
                )
            val target = if (behavior == MahjongGameBehavior.KAKAN) ClaimTarget.SELF else target
            val hWidth = if (target == ClaimTarget.LEFT || target == ClaimTarget.RIGHT) HINT_HEIGHT else TILE_WIDTH
            val hHeight =
                if (target == ClaimTarget.LEFT || target == ClaimTarget.RIGHT) TILE_HEIGHT else HINT_HEIGHT
            colorBlock(
                x = when (target) {
                    ClaimTarget.LEFT -> tileWidget.x - TILE_GAP - hWidth
                    ClaimTarget.RIGHT -> tileWidget.x + TILE_WIDTH + TILE_GAP
                    else -> mtX
                },
                y = when (target) {
                    ClaimTarget.ACROSS -> tileWidget.y - TILE_GAP - hHeight
                    ClaimTarget.SELF -> tileWidget.y + tileWidget.height + TILE_GAP
                    else -> HINT_HEIGHT + TILE_GAP
                },
                width = hWidth,
                height = hHeight,
                color = Color.GREEN
            )
            return tileWidget
        } ?: return null
    }

    private fun WPlainPanel.handsWidget(x: Int, y: Int, width: Int, height: Int) =
        plainPanel(x, y, width, height) {
            hands.forEachIndexed { index, tile ->
                val tileX = ((width - handsWidth) / 2
                        + (TILE_WIDTH + TILE_GAP) * index
                        + if (hasTakenTile && index == hands.lastIndex) TAKEN_TILE_GAP else 0)
                if (tile.mahjong4jTile in handsHintTiles) {
                    colorBlock(
                        x = tileX,
                        y = TILE_HEIGHT + TILE_GAP,
                        width = TILE_WIDTH,
                        height = HINT_HEIGHT,
                        color = Color.RED_DYE
                    )
                }
                mahjongTile(x = tileX, y = 0, width = TILE_WIDTH, height = TILE_HEIGHT, mahjongTile = tile)
            }
        }


    private fun WPlainPanel.optionsWidget(x: Int, y: Int, width: Int, height: Int) =
        scrollPanel(x, y, width, height) { //算出第一個 x 的偏移, 讓選項置中
            val totalWidth = tilesForOptions.size * OptionItem.WIDTH + (tilesForOptions.size - 1) * OPTION_GAP
            val offsetX = (width - totalWidth) / 2
            val x0 = if (offsetX > 0) offsetX else 0
            tilesForOptions.forEachIndexed { index, tilesForOption ->
                val option = OptionItem(tilesForOption, hands, data)
                val optionX = x0 + (OptionItem.WIDTH + OPTION_GAP) * index
                this.add(option, optionX, 0, OptionItem.WIDTH, OptionItem.HEIGHT)
            }
        }

    /**
     * @param tilesForOption 請參考 [tilesForOptions]
     * @param data 就是 [MahjongBehaviorGui.data], 單純在立直的時候產生 tooltip 用
     * */
    class OptionItem(
        tilesForOption: Pair<Pair<MahjongGameBehavior, ClaimTarget>, MutableList<MahjongTile>>,
        hands: List<MahjongTile>,
        data: String
    ) : WPlainPanel() {
        private val player = MinecraftClient.getInstance().player!!
        private val behaviorAndTarget = tilesForOption.first
        private val behavior = behaviorAndTarget.first
        private val target = behaviorAndTarget.second
        private val tiles = tilesForOption.second

        init {
            setSize(WIDTH, HEIGHT)
            initTiles()
            val tooltip: Array<Text> = when (behavior) {
                MahjongGameBehavior.RIICHI -> {
                    val texts = mutableListOf<Text>()
                    texts += TranslatableText("$MOD_ID.game.machi").formatted(Formatting.RED).formatted(Formatting.BOLD)
                    val tilePairsForRiichi =
                        Json.decodeFromString<MutableList<Pair<MahjongTile, MutableList<MahjongTile>>>>(data)
                    val pair = tilePairsForRiichi.find { it.first == tiles[0] }
                    val machi = pair!!.second
                        .distinctBy { tile -> tile.displayNameString } //去掉重複的選項
                        .sortedBy { tile -> tile.sortOrder } //按照順序排好
                    texts += machi.map { LiteralText("§3 - §e${it.displayNameString}") }
                    texts.toTypedArray()
                }
                MahjongGameBehavior.EXHAUSTIVE_DRAW -> arrayOf(
                    TranslatableText(behavior.lang).formatted(Formatting.RED).formatted(Formatting.BOLD)
                )
                else -> arrayOf() //目前剩下的暫時都沒有 tooltip
            }
            tooltipButton(
                x = BUTTON_X,
                y = BUTTON_Y,
                width = BUTTON_WIDTH,
                tooltip = tooltip,
                label = when (behavior) {
                    MahjongGameBehavior.MINKAN,
                    MahjongGameBehavior.ANKAN,
                    MahjongGameBehavior.KAKAN -> TranslatableText(MahjongGameBehavior.KAN.lang)
                    MahjongGameBehavior.KYUUSHU_KYUUHAI -> TranslatableText(MahjongGameBehavior.EXHAUSTIVE_DRAW.lang)
                    else -> TranslatableText(behavior.lang)
                },
                onClick = { //這裡 onClick 之後並不會馬上關閉 GUI, 會等到 [ClientCountdownTimeHandler] 收到清除時間的數據後, 會自己關
                    val claimingTile =
                        if (behavior != MahjongGameBehavior.TSUMO) hands.last() else tiles.last() //自摸的話是手牌最後一張
                    when (behavior) {
                        MahjongGameBehavior.CHII -> {
                            val pair = tiles[0] to tiles[1]
                            player.sendMahjongGamePacket(
                                behavior = behavior,
                                extraData = Json.encodeToString(pair)
                            )
                        }
                        MahjongGameBehavior.KAKAN, MahjongGameBehavior.ANKAN -> {
                            player.sendMahjongGamePacket(
                                behavior = MahjongGameBehavior.ANKAN_OR_KAKAN,
                                extraData = Json.encodeToString(claimingTile)
                            )
                        }
                        MahjongGameBehavior.PON, MahjongGameBehavior.MINKAN,
                        MahjongGameBehavior.TSUMO, MahjongGameBehavior.RON,
                        MahjongGameBehavior.KYUUSHU_KYUUHAI -> {
                            player.sendMahjongGamePacket(
                                behavior = behavior
                            )
                        }
                        MahjongGameBehavior.RIICHI -> {
                            player.sendMahjongGamePacket(
                                behavior = MahjongGameBehavior.RIICHI,
                                extraData = Json.encodeToString(tiles.first())
                            )
                        }
                        else -> {
                        }
                    }
                }
            )
        }

        private fun initTiles() {
            if (target == ClaimTarget.SELF || behavior == MahjongGameBehavior.RON) { //一定垂直擺放的牌->鳴牌對象是自己, 或者榮和
                val totalWidth = tiles.size * TILE_WIDTH + (tiles.size - 1) * TILE_GAP
                val x0 = (WIDTH - totalWidth) / 2 //計算這個值是為了讓牌能置中
                tiles.forEachIndexed { index, mahjongTile ->
                    val x = x0 + (TILE_WIDTH + TILE_GAP) * index
                    mahjongTile(
                        x = x,
                        y = NORMAL_TILE_Y,
                        width = TILE_WIDTH,
                        height = TILE_HEIGHT,
                        mahjongTile = mahjongTile
                    )
                }
            } else { //如果鳴牌對象不是自己 或者 榮和,一定至少有一張牌是水平
                val atLeastOneHorizontalTiles = tiles.toMutableList()
                val kakanTile = if (behavior == MahjongGameBehavior.KAKAN) { //加槓牌
                    atLeastOneHorizontalTiles.removeLast() //加槓的最後一張牌不加入寬度計算 (因為加槓的牌是往上排, 不是往右排)
                } else null
                val claimingTile = atLeastOneHorizontalTiles.removeLast() //最後一張牌為被鳴的牌, 暫時先移出列表
                atLeastOneHorizontalTiles.sortBy { it.sortOrder } //將牌順序整理一下
                val claimingTileIndex = when (target) { //計算被鳴的牌應該在的位置
                    ClaimTarget.RIGHT -> 2
                    ClaimTarget.ACROSS -> 1
                    ClaimTarget.LEFT -> 0
                    else -> return // else 情況不可能出現, 出現一定是錯誤
                }
                val claimingTileDirection = when (target) {
                    ClaimTarget.RIGHT, ClaimTarget.ACROSS -> WMahjongTile.TileDirection.RIGHT
                    ClaimTarget.LEFT -> WMahjongTile.TileDirection.LEFT
                    else -> WMahjongTile.TileDirection.NORMAL
                }
                atLeastOneHorizontalTiles.add(claimingTileIndex, claimingTile) //將被鳴的牌放入應該在的位置
                val totalWidth = ((atLeastOneHorizontalTiles.size - 1) * TILE_WIDTH //直的牌
                        + TILE_HEIGHT // 1 張橫的牌
                        + (atLeastOneHorizontalTiles.size - 1) * TILE_GAP)
                val x0 = (WIDTH - totalWidth) / 2 //計算這個值是為了讓牌能置中
                val widgets = mutableListOf<WMahjongTile>()
                atLeastOneHorizontalTiles.forEachIndexed { index, tile ->
                    val x = if (index > 0) widgets[index - 1].let { it.x + it.width + TILE_GAP } else x0
                    if (index == claimingTileIndex) { //不能用牌比對, 要用 index 比對, 因為碰跟槓的牌會有重複, 會全都一起水平或一起垂直
                        if (kakanTile != null) { //將加槓牌(即 tiles 最後一張)橫放補到被鳴的牌上面
                            mahjongTile(
                                x = x,
                                y = KAKAN_TILE_Y,
                                width = TILE_WIDTH,
                                height = TILE_HEIGHT,
                                mahjongTile = kakanTile,
                                direction = claimingTileDirection
                            )
                        }
                        widgets += mahjongTile(
                            x = x,
                            y = if (claimingTileDirection == WMahjongTile.TileDirection.NORMAL) NORMAL_TILE_Y
                            else HORIZONTAL_TILE_Y,
                            width = TILE_WIDTH,
                            height = TILE_HEIGHT,
                            mahjongTile = tile,
                            direction = claimingTileDirection
                        )
                    } else {
                        widgets += mahjongTile(
                            x = x,
                            y = NORMAL_TILE_Y,
                            width = TILE_WIDTH,
                            height = TILE_HEIGHT,
                            mahjongTile = tile
                        )
                    }
                }
            }
        }

        companion object {
            private const val KAKAN_TILE_Y = 0
            private const val HORIZONTAL_TILE_Y = KAKAN_TILE_Y + TILE_WIDTH + TILE_GAP
            private const val NORMAL_TILE_Y = HORIZONTAL_TILE_Y + TILE_WIDTH - TILE_HEIGHT
            const val WIDTH = TILE_WIDTH * 4 + TILE_GAP * 3 //寬度是使用 4 張直立牌的寬度
            const val HEIGHT = TILE_WIDTH * 2 + TILE_GAP * 2 + BUTTON_HEIGHT // 2 張橫擺的牌 + 按紐的高度
            private const val BUTTON_X = (WIDTH - BUTTON_WIDTH) / 2
            private const val BUTTON_Y = NORMAL_TILE_Y + TILE_HEIGHT + TILE_GAP
        }
    }

    companion object {
        //背景圖片的大小
        private const val ROOT_WIDTH = 400
        private const val ROOT_HEIGHT = 200

        private const val BUTTON_WIDTH = 80
        private const val BUTTON_HEIGHT = 20

        private const val TILE_SCALE = 0.5f //這個值要觀察一下
        private const val TILE_WIDTH = (48 * TILE_SCALE).toInt()
        private const val TILE_HEIGHT = (64 * TILE_SCALE).toInt()
        private const val TILE_GAP = TILE_WIDTH / 12
        private const val TAKEN_TILE_GAP = TILE_GAP * 3
        private const val OPTION_GAP = TILE_GAP * 12
        private const val HINT_HEIGHT = TILE_GAP * 2
    }

}
