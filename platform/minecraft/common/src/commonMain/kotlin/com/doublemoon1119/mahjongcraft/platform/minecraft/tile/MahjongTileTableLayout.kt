package com.doublemoon1119.mahjongcraft.platform.minecraft.tile

import com.doublemoon1119.mahjongcraft.logic.base.MeldType
import com.doublemoon1119.mahjongcraft.logic.base.RelativeDirection
import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallPosition
import com.doublemoon1119.mahjongcraft.platform.minecraft.dice.MahjongDiceTableLayout
import com.doublemoon1119.mahjongcraft.platform.minecraft.dice.MahjongTableFacing
import com.doublemoon1119.mahjongcraft.platform.minecraft.dice.MahjongTableSide
import com.doublemoon1119.mahjongcraft.platform.minecraft.dice.seatIndexToTableSide
import com.doublemoon1119.mahjongcraft.platform.minecraft.seating.MahjongSeatingTableLayout
import com.doublemoon1119.mahjongcraft.platform.minecraft.stick.MahjongScoringStickDimensions
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongTileTableLayout.advance
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongTileTableLayout.localDiscardVector
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongTileTableLayout.localHandVector
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongTileTableLayout.localWallVector
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongTileTableLayout.rotateForFacing
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongTileTableLayout.rotateForSide

/** 局部水平向量；沿用 [MahjongTableSide]／[MahjongTableFacing] 兩段式旋轉合成，Y 軸不受水平旋轉影響。 */
private data class TileTableVector(val x: Double, val y: Double, val z: Double)

/**
 * 一張牌牆用牌的版本無關世界呈現位置。
 *
 * @property x 世界 X 座標，牌底面中心。
 * @property y 世界 Y 座標，牌底面中心。
 * @property z 世界 Z 座標，牌底面中心。
 * @property yaw 世界水平朝向角度。
 */
data class MahjongTileWallPlacement(
    val x: Double,
    val y: Double,
    val z: Double,
    val yaw: Float,
)

/**
 * 把 [TileWallPosition] 這種與 Minecraft 座標無關的抽象牌牆結構位置，轉換成真實世界座標。
 *
 * 兩段式旋轉合成與 [MahjongDiceTableLayout] 完全同一套慣例：先算出「以局部南側為基準」的向量，再依序旋轉到目標側面、旋轉到桌子世界朝向。
 * [TileWallPosition.side] 以莊家自身面為 0、依這裡的 `SIDE_ORDER`（南→西→北→東，逆時針）遞增，只需要
 * 把 [seatIndexToTableSide] 算出的莊家局部側面當成起點，依 [TileWallPosition.side] 再用同一個
 * `SIDE_ORDER` 旋轉相應步數，就能得到這張牌實際所在的局部側面——`SIDE_ORDER` 只是牌牆環狀結構自己的
 * 固定組裝順序，起點在哪裡（[seatIndexToTableSide] 挑哪個方向對應座位 0）不影響這個環本身密不密合，
 * 兩者刻意各自獨立，見 [seatIndexToTableSide] KDoc 的完整說明。
 */
object MahjongTileTableLayout {
    /**
     * 依 controller 座標、桌子世界朝向、莊家座位與牌牆總墩數，算出單一 [TileWallPosition] 的世界座標。
     *
     * @param stacksPerSide 這副牌牆每面的總墩數（例如四人日麻固定 17），用來將牌墩對稱置中於側面。
     * @param isDeadWall 這張牌是否屬於王牌區——為 `true` 時額外把整條線沿排列方向、往開門缺口的方向
     * 滑動一點（見 [localWallVector]），跟活牌保持一點視覺距離。牌牆剛生成時所有牌（含王牌）都應該以
     * `false` 呼叫，維持一圈完整無縫的牌牆；等到骰子動畫播完、要把王牌區「移出」開門時，才對王牌區的
     * 牌改用 `true` 重新算一次座標並移動過去——這是刻意分兩階段呼叫的設計，不是可以合併成一次的參數。
     */
    fun wallPlacement(
        controllerX: Int,
        controllerY: Int,
        controllerZ: Int,
        tableFacing: MahjongTableFacing,
        dealerSeatIndex: Int,
        stacksPerSide: Int,
        position: TileWallPosition,
        isDeadWall: Boolean = false,
    ): MahjongTileWallPlacement {
        require(stacksPerSide > 0) { "Stacks per side must be positive" }
        require(position.stack in 0 until stacksPerSide) {
            "Stack ${position.stack} out of range for $stacksPerSide stacks per side"
        }
        require(position.layer in 0..1) { "Layer ${position.layer} must be 0 or 1" }

        val physicalSide = advance(seatIndexToTableSide(dealerSeatIndex), position.side)
        val local = localWallVector(stacksPerSide, position.stack, position.layer, isDeadWall)
        val worldOffset = rotateForFacing(rotateForSide(local, physicalSide), tableFacing)
        return MahjongTileWallPlacement(
            x = controllerX + BLOCK_CENTER + worldOffset.x,
            y = controllerY + TABLETOP_HEIGHT + worldOffset.y,
            z = controllerZ + BLOCK_CENTER + worldOffset.z,
            yaw = (yawForSide(physicalSide) + yawForFacing(tableFacing)).mod(FULL_YAW_DEGREES),
        )
    }

    /**
     * 依牌牆每面墩數，算出牌牆生成掉落動畫（波浪感）從觸發到全部落地所需的總 tick 數——每面牌牆
     * `stack` 越大（越靠玩家左手邊，見 [wallPlacement] KDoc）越晚開始掉落，`stack = stacksPerSide - 1`
     * 是最後開始掉落的一墩，該墩開始掉落後還要再等一次完整的 [TileMotionAnimationSpec.DEFAULT_DURATION_TICKS]
     * 才落地；跟 [MahjongDiceTableLayout.totalAnimationTicks]（「最大 stagger + 單次動畫時長」）同一套
     * 算法模式，供呼叫端標記桌子忙碌時長使用。
     */
    fun wallDropAnimationTicks(stacksPerSide: Int): Int = wallDropStartDelayTicks((stacksPerSide - 1).coerceAtLeast(0)) +
        TileMotionAnimationSpec.DEFAULT_DURATION_TICKS

    /**
     * 依 [TileWallPosition.stack] 算出牌牆生成掉落動畫該延遲多久才開始，供
     * `FabricMahjongTileWallPresenter` 排定每墩延遲使用；跟 [wallDropAnimationTicks] 共用同一個
     * [WAVE_STEP_TICKS]，避免呼叫端各自寫一份相同的乘法。
     */
    fun wallDropStartDelayTicks(stack: Int): Int = stack * WAVE_STEP_TICKS

    /**
     * 以局部南側玩家為基準的單張牌牆用牌位置：[TileWallPosition.layer] 沿 Y 軸以牌深堆疊，牌面朝下
     * 平放——躺平後，沿墩排列方向（局部 X 軸）的外觀寬度是 [MahjongTileDimensions.TILE_WIDTH]，垂直
     * 於側面方向（局部 Z 軸）的外觀寬度是 [MahjongTileDimensions.TILE_HEIGHT]（原本直立時的高度，
     * 躺平後變成水平方向的寬度）。
     *
     * 垂直於側面的偏移（`halfSpan`）刻意跟沿側面方向的半跨距使用同一個值——四面牌牆各自是一條長度
     * `stacksPerSide * stackStep` 的直線，若垂直偏移不等於這條線自己的半跨距，四面牌牆的直線就搆不到
     * 彼此，四個角會留下沒用到的空格（實際遊戲內驗證過的現象）。
     *
     * 但只讓兩個方向共用 `halfSpan` 還不夠：這樣四面牌牆的角落端點座標會完全重合，實際上牌本身有寬度
     * （`TILE_HEIGHT`），端點重合代表兩面牌牆在角落互相穿插重疊（同樣是實際遊戲內驗證過的現象）。
     * 因此再把整條線沿局部 X 軸整體平移 `TILE_HEIGHT / 2`——這個位移量與旋轉合成（[rotateForSide]／
     * [rotateForFacing]）自動套用到全部四面，讓四面牌牆形成像實體麻將桌那樣的風車形接角：每個角落都是
     * 一面牌牆的端邊貼齊另一面牌牆的側邊，不重疊。額外再加上 [MahjongTileDimensions.TILE_WIDTH] 的
     * [CORNER_GAP_RATIO] 倍，讓角落貼齊處留一點看得出來的縫隙，不是完全零間隙的貼合（遊戲內驗證後的
     * 觀感調整，不影響上面推導出的不重疊關係，只是把「剛好貼齊」再往外推一點；比例本身沒有幾何推導
     * 依據，純粹是視覺調校參數，用牌本身尺寸的比例表示比直接疊加 [MahjongTileDimensions.TILE_SMALL_PADDING]
     * 倍數更好預期調整後的視覺效果）。
     *
     * [isDeadWall] 為 `true` 時，額外把 `alongSide`（沿墩排列方向的位置）往「`stack` 遞增」的方向多推
     * [MahjongTileDimensions.TILE_WIDTH] 的 [DEAD_WALL_GAP_RATIO] 倍——`stack` 遞增的方向正是王牌區
     * 緊鄰開門缺口、之後會被摸走分配給玩家手牌的活牌墩所在方向（見
     * `com.doublemoon1119.mahjongcraft.logic.table.layout.FourSidedWallLayoutSupport` 的墩位排列
     * 邏輯：王牌從缺口本身往 `stack` 遞減方向連續佔用，活牌則從缺口另一側往 `stack` 遞增方向連續
     * 佔用，兩者在缺口處以遞增/遞減方向相鄰）。這是刻意選這個方向的位移，不是垂直推向桌子中心——
     * 活牌之後會被摸走清空這塊空間，王牌滑過去暫時會跟還沒摸走的活牌墩重疊一點點，等手牌分配這個
     * 切片完成後就會自然錯開，這次不需要另外處理這個過渡期的重疊。
     *
     * `stack = 0` 在該面玩家自己右手邊（`alongSide` 較大）、`stack` 遞增往左手邊移動，真實麻將
     * 「牌山數墩：從該牌山最右端往左邊數」就是這個方向。這裡的方向跟 [seatIndexToTableSide]／
     * `SIDE_ORDER` 是耦合校準出來的一組關係，不要單獨改——理由與踩過的坑見 [seatIndexToTableSide] KDoc。
     */
    private fun localWallVector(stacksPerSide: Int, stack: Int, layer: Int, isDeadWall: Boolean): TileTableVector {
        val stackStep = MahjongTileDimensions.TILE_WIDTH + MahjongTileDimensions.TILE_SMALL_PADDING
        val halfSpan = stacksPerSide / 2.0 * stackStep
        val cornerInterlockShift = MahjongTileDimensions.TILE_HEIGHT / 2.0 +
            MahjongTileDimensions.TILE_WIDTH * CORNER_GAP_RATIO
        val deadWallOpeningShift = if (isDeadWall) MahjongTileDimensions.TILE_WIDTH * DEAD_WALL_GAP_RATIO else 0.0
        val alongSide = ((stacksPerSide - 1) / 2.0 - stack) * stackStep + cornerInterlockShift - deadWallOpeningShift
        val layerHeight = layer * MahjongTileDimensions.TILE_DEPTH +
            if (layer > 0) MahjongTileDimensions.TILE_SMALL_PADDING else 0.0
        return TileTableVector(x = alongSide, y = layerHeight, z = halfSpan)
    }

    /**
     * 依全域抓取次序（`0` 起算，涵蓋所有座位、所有輪次攤平成一個序列——莊家抓第一批、換下一家抓、
     * 輪完一圈才回到莊家抓下一批，見 `FabricMahjongPlayerAreaPresenter.presentInitialDeal` KDoc）算出
     * 開局發牌動畫這一次該延遲多久才開始——不像 [wallDropStartDelayTicks] 那樣所有墩其實還是各自
     * 獨立落下，這裡是刻意讓連續抓取大幅重疊：[DEAL_TURN_STAGGER_TICKS] 小於
     * [DEAL_LIFT_DURATION_TICKS]，下一次抓取甚至會在上一次的起飛動畫播到一半時就先開始，不需要等
     * 上一次的起飛播完、更不需要等牌真的落地放好，讓連續抓取的節奏更快、更連貫；供 [dealAnimationTicks]
     * 與呼叫端排定每次抓取延遲使用。
     */
    fun dealBatchStartDelayTicks(turnIndex: Int): Int = turnIndex * DEAL_TURN_STAGGER_TICKS

    /**
     * 依總抓取次數（輪數 × 座位數，見 [dealBatchStartDelayTicks] KDoc）算出翻牌動畫該延遲多久才開始
     * ——所有座位的最後一次抓取（落下階段）完整播完後，經過 [DEAL_FLIP_GAP_TICKS] 這一小段停頓，
     * 全部座位的牌才同時原地翻起（位置不變，只有姿態旋轉角內插）；跟
     * [dealAnimationTicks] 共用「最後一次抓取的延遲 + 該次本身時長」這段算法，避免兩處各自重複計算。
     */
    fun dealFlipStartDelayTicks(totalTurnCount: Int): Int = dealBatchStartDelayTicks((totalTurnCount - 1).coerceAtLeast(0)) +
        DEAL_LIFT_DURATION_TICKS + DEAL_SNAP_GAP_TICKS + DEAL_DROP_DURATION_TICKS + DEAL_FLIP_GAP_TICKS

    /**
     * 依總抓取次數（輪數 × 座位數，見 [dealBatchStartDelayTicks] KDoc）算出開局發牌動畫（含最後統一
     * 翻牌）從觸發到全部播完所需的總 tick 數，供呼叫端標記桌子忙碌時長使用；等於 [dealFlipStartDelayTicks]
     * 加上翻牌動畫本身的時長（[DEAL_FLIP_DURATION_TICKS]）。
     */
    fun dealAnimationTicks(totalTurnCount: Int): Int = dealFlipStartDelayTicks(totalTurnCount) + DEAL_FLIP_DURATION_TICKS

    /**
     * 依 controller 座標、桌子世界朝向與座位 index，算出該玩家手牌中第 [tileIndex] 張牌的世界座標。
     *
     * 跟 [wallPlacement] 不同，手牌位置直接用玩家自己在 `TableState.players` 的固定座位 index 算局部
     * 側面（`seatIndexToTableSide(seatIndex)`），不經過莊家相對的 [advance] 旋轉——座位 index 整場對局
     * 固定不變（只有自風跟著轉），跟 [MahjongSeatingTableLayout]的既有慣例一致；
     * 牌牆才需要莊家相對旋轉，因為 `TileWallPosition.side` 的意義每局隨莊家改變。
     *
     * @param handSize 這位玩家目前的手牌張數，用來把整排手牌對稱置中於局部側面。
     * @param tileIndex 這張牌在手牌裡的零基底索引（`0` 在最大 X、遞增時往負向移動，跟牌牆 `stack` 的
     * 排列方向一致）。
     * @param cornerYieldShift 整排手牌（含摸牌位，見 [drawnTilePlacement]）需要往玩家自己方向（局部
     * X 軸負向）額外平移的距離，`0.0`（預設值）代表不需要讓開。由呼叫端依
     * [handCornerYieldShift] 算好傳入——這裡只負責套用，不重新判斷是否需要讓開；整排牌一起平移，牌與
     * 牌之間的間距（`stackStep`）不受影響，是刻意的設計決定。
     */
    fun handPlacement(
        controllerX: Int,
        controllerY: Int,
        controllerZ: Int,
        tableFacing: MahjongTableFacing,
        seatIndex: Int,
        handSize: Int,
        tileIndex: Int,
        cornerYieldShift: Double = 0.0,
    ): MahjongTileWallPlacement {
        require(handSize > 0) { "Hand size must be positive" }
        require(tileIndex in 0 until handSize) { "Tile index $tileIndex out of range for hand size $handSize" }
        require(cornerYieldShift >= 0.0) { "Corner yield shift must not be negative" }

        val physicalSide = seatIndexToTableSide(seatIndex)
        val local = localHandVector(handSize, tileIndex, cornerYieldShift)
        val worldOffset = rotateForFacing(rotateForSide(local, physicalSide), tableFacing)
        return MahjongTileWallPlacement(
            x = controllerX + BLOCK_CENTER + worldOffset.x,
            y = controllerY + TABLETOP_HEIGHT + worldOffset.y,
            z = controllerZ + BLOCK_CENTER + worldOffset.z,
            yaw = (yawForSide(physicalSide) + yawForFacing(tableFacing)).mod(FULL_YAW_DEGREES),
        )
    }

    /**
     * 以局部南側玩家為基準的單張手牌位置：牌直立擺放，沿排列方向（局部 X 軸）的外觀寬度是
     * [MahjongTileDimensions.TILE_WIDTH]，跟牌牆同一套墩間距常數對稱置中，再扣掉 [cornerYieldShift]
     * 整排一起往負向平移。垂直於側面的距離（`HAND_EDGE_OFFSET`）是初始估算值，只要求比牌牆的
     * `halfSpan` 更靠近桌緣（手牌在牌牆跟桌緣之間，貼近玩家自己），實際數值待遊戲內比對後可能還要
     * 再微調，比照牌牆當初的調校方式。
     */
    private fun localHandVector(handSize: Int, tileIndex: Int, cornerYieldShift: Double): TileTableVector {
        val stackStep = MahjongTileDimensions.TILE_WIDTH + MahjongTileDimensions.TILE_SMALL_PADDING
        val alongSide = ((handSize - 1) / 2.0 - tileIndex) * stackStep - cornerYieldShift
        return TileTableVector(x = alongSide, y = 0.0, z = HAND_EDGE_OFFSET)
    }

    /**
     * 依 controller 座標、桌子世界朝向與座位 index，算出這位玩家目前摸到、尚未併入立牌或打出的那張牌
     * 的世界座標——緊鄰立牌列尾端外一段看得出來的縫隙，不是重新置中整排立牌（真實麻將：摸到的牌先擺
     * 在手牌一側，不會立刻插入、重新排列整排立牌）。跟 [handPlacement] 同樣直接用座位 index 算局部
     * 側面，不經過莊家相對旋轉。
     *
     * @param standingTileCount 這位玩家目前立牌張數（不含這張剛摸到的牌）。
     * @param cornerYieldShift 跟 [handPlacement] 的同名參數共用同一個值——摸牌位跟著整排立牌一起
     * 平移，才不會平移後反而超出立牌列。`0.0`（預設值）代表不需要讓開。
     */
    fun drawnTilePlacement(
        controllerX: Int,
        controllerY: Int,
        controllerZ: Int,
        tableFacing: MahjongTableFacing,
        seatIndex: Int,
        standingTileCount: Int,
        cornerYieldShift: Double = 0.0,
    ): MahjongTileWallPlacement {
        require(cornerYieldShift >= 0.0) { "Corner yield shift must not be negative" }

        val physicalSide = seatIndexToTableSide(seatIndex)
        val local = localDrawnTileVector(standingTileCount, cornerYieldShift)
        val worldOffset = rotateForFacing(rotateForSide(local, physicalSide), tableFacing)
        return MahjongTileWallPlacement(
            x = controllerX + BLOCK_CENTER + worldOffset.x,
            y = controllerY + TABLETOP_HEIGHT + worldOffset.y,
            z = controllerZ + BLOCK_CENTER + worldOffset.z,
            yaw = (yawForSide(physicalSide) + yawForFacing(tableFacing)).mod(FULL_YAW_DEGREES),
        )
    }

    /**
     * 以局部南側玩家為基準的摸牌位位置：延續 [localHandVector] 立牌列尾端（`tileIndex = 0` 那張的
     * 位置，即玩家右手邊——遊戲內驗證過摸牌位放在 `tileIndex = handSize - 1` 那側時方向是反的，摸到
     * 的牌實際上該出現在玩家右手邊，不是左手邊）再往外一個 `stackStep`，額外加上
     * [DRAWN_TILE_GAP_RATIO] 倍 [MahjongTileDimensions.TILE_WIDTH] 的縫隙，讓摸到的牌跟立牌列之間有
     * 看得出來的間隔，再扣掉 [cornerYieldShift]。直立擺放，`z` 跟 [localHandVector] 共用
     * [HAND_EDGE_OFFSET]。
     */
    private fun localDrawnTileVector(standingTileCount: Int, cornerYieldShift: Double): TileTableVector {
        val stackStep = MahjongTileDimensions.TILE_WIDTH + MahjongTileDimensions.TILE_SMALL_PADDING
        val handRowEdge = (standingTileCount - 1) / 2.0 * stackStep
        val gap = MahjongTileDimensions.TILE_WIDTH * DRAWN_TILE_GAP_RATIO
        val alongSide = handRowEdge + stackStep + gap - cornerYieldShift
        return TileTableVector(x = alongSide, y = 0.0, z = HAND_EDGE_OFFSET)
    }

    /**
     * 依 controller 座標、桌子世界朝向、座位 index 與這位玩家目前牌河總張數，算出牌河中第
     * [discardIndex] 張牌（依捨牌順序，0-based）的世界座標——固定 [DISCARD_TILES_PER_ROW] 張一排，
     * 超過時往桌緣方向堆疊下一排；牌躺平、牌面朝上（真實麻將：牌河攤開顯示花色，跟 [wallPlacement]
     * 牌面朝下相反），整體比牌牆更靠近桌子中心（真實麻將：牌河位於四面牌牆圍成的桌面中央區域）。
     * 跟 [handPlacement] 同樣直接用座位 index 算局部側面，不經過莊家相對旋轉——牌河屬於捨牌者自己，
     * 跟座位一樣整場對局固定不變。
     *
     * @param isSidewaysMarked 這張牌是否要側身呈現（例如立直宣告牌）——為 `true` 時額外把 yaw 轉
     * [SIDEWAYS_YAW_OFFSET] 度。側身牌沿排列方向（局部 X 軸）實際佔用的寬度是
     * [MahjongTileDimensions.TILE_HEIGHT] 而非直立牌的 [MahjongTileDimensions.TILE_WIDTH]，
     * [sidewaysMarkedDiscardIndex] 讓 [localDiscardVector] 知道該格該用哪一個寬度計算跟左右鄰居的
     * 間距，避免側身牌跟旁邊的牌重疊——手法比照 [meldPlacement] 依牌實際朝向換算寬度的既有慣例。
     * @param sidewaysMarkedDiscardIndex 這位玩家牌河裡側身標記那張牌自己的 [discardIndex]（`null`
     * 代表這位玩家目前沒有任何側身標記牌）；同一位玩家牌河裡最多只會有一張側身標記牌。
     * @param wallRemaining 牌山是否還有剩餘牌——用來判斷第 [DISCARD_SAFE_ROWS] 排放滿後，第四排要不要
     * 真的往桌緣方向新增，理由見 [localDiscardVector] KDoc。
     */
    fun discardPlacement(
        controllerX: Int,
        controllerY: Int,
        controllerZ: Int,
        tableFacing: MahjongTableFacing,
        seatIndex: Int,
        discardIndex: Int,
        isSidewaysMarked: Boolean,
        sidewaysMarkedDiscardIndex: Int?,
        wallRemaining: Boolean,
    ): MahjongTileWallPlacement {
        require(discardIndex >= 0) { "Discard index must not be negative" }

        val physicalSide = seatIndexToTableSide(seatIndex)
        val local = localDiscardVector(discardIndex, wallRemaining, sidewaysMarkedDiscardIndex)
        val worldOffset = rotateForFacing(rotateForSide(local, physicalSide), tableFacing)
        val baseYaw = (yawForSide(physicalSide) + yawForFacing(tableFacing)).mod(FULL_YAW_DEGREES)
        return MahjongTileWallPlacement(
            x = controllerX + BLOCK_CENTER + worldOffset.x,
            y = controllerY + TABLETOP_HEIGHT + worldOffset.y,
            z = controllerZ + BLOCK_CENTER + worldOffset.z,
            yaw = if (isSidewaysMarked) (baseYaw + SIDEWAYS_YAW_OFFSET).mod(FULL_YAW_DEGREES) else baseYaw,
        )
    }

    /**
     * 以局部南側玩家為基準的單張牌河牌位置：前 [DISCARD_SAFE_ROWS] 排（`row` 0 起算到
     * `DISCARD_SAFE_ROWS - 1`）依 [DISCARD_TILES_PER_ROW] 張正常換行，列沿排列方向（局部 X 軸）
     * 對稱置中——`column` 遞增時往玩家右手邊移動（遊戲內驗證過原本由右往左排列的方向是反的，真實
     * 麻將是從玩家左手邊往右手邊依序擺放捨牌），行往桌緣方向（局部 Z 軸正向）堆疊，起始距離
     * [DISCARD_ROW_BASE_OFFSET] 為比 [wallPlacement] 的 `halfSpan` 更靠近桌子中心的估算值。
     *
     * 超過 [DISCARD_SAFE_ROWS] 排（第四排以後）要不要真的往桌緣方向新增一排，取決於 [wallRemaining]：
     * 牌山還有剩餘牌時，代表牌牆理論上仍可能佔用第四排的位置（遊戲內驗證過真的會撞進牌牆），因此固定
     * 停在最後一個安全排（`row = DISCARD_SAFE_ROWS - 1`）、沿著同一排的排列方向繼續往外延伸，`column`
     * 不回繞；牌山已經摸完（`wallRemaining = false`，此時牌牆本身視覺上也應該已經清空）就不需要再
     * 避讓，正常繼續往桌緣方向新增第四、第五……排。這裡沒有精確查詢「捨牌者自己那一面牆是否還有牌」
     * ——那需要額外把莊家座位、`TileWallPosition.side` 分配、目前牌山消耗進度都串起來查，對這種很少
     * 見的極端長牌河邊界情況而言成本過高；用「牌山整體是否還有牌」當簡化版的替代判斷，兩者只有在牌山
     * 快摸完但捨牌者自己那面牆已經先被摸空的極端情況下才會不一致，可接受。
     *
     * 每排第一格（`column = 0`）固定用同一套「假設每格都是直立牌固定寬度」的置中公式算出的位置，
     * 不因為某一排剛好有側身標記牌就整排重新置中——所有排的第一格因此永遠對齊在同一條軸線上。同一排
     * 內若有側身標記牌（[sidewaysMarkedDiscardIndex] 落在同一排），只有從那一格開始（含）才往右
     * 額外挪動 [MahjongTileDimensions.TILE_HEIGHT] 與 [MahjongTileDimensions.TILE_WIDTH] 的差距（側身牌
     * 實際佔用的橫向空間比直立牌寬，見 [meldPlacement] 依牌實際朝向換算寬度的既有慣例），側身格本身
     * 只挪動一半（讓它的左緣仍對齊直立牌該有的位置，右緣才多出來），之後的格子挪動全額，維持跟其他
     * 直立牌之間原本的固定間距——這是遊戲內實際驗證過的問題：先前改成整排依實際寬度置中，會導致有
     * 側身牌那一排的第一格跟其他排對不齊，看起來像整排在跳動。
     */
    private fun localDiscardVector(discardIndex: Int, wallRemaining: Boolean, sidewaysMarkedDiscardIndex: Int?): TileTableVector {
        val lastSafeRow = DISCARD_SAFE_ROWS - 1
        val lastSafeRowStartIndex = lastSafeRow * DISCARD_TILES_PER_ROW

        fun rowColumnOf(index: Int): Pair<Int, Int> = if (!wallRemaining || index < lastSafeRowStartIndex) {
            index / DISCARD_TILES_PER_ROW to index % DISCARD_TILES_PER_ROW
        } else {
            lastSafeRow to (index - lastSafeRowStartIndex)
        }

        val (row, column) = rowColumnOf(discardIndex)
        val sidewaysColumn = sidewaysMarkedDiscardIndex
            ?.let(::rowColumnOf)
            ?.takeIf { (sidewaysRow, _) -> sidewaysRow == row }
            ?.second

        val stackStep = MahjongTileDimensions.TILE_WIDTH + MahjongTileDimensions.TILE_SMALL_PADDING
        val baseAlongSide = (column - (DISCARD_TILES_PER_ROW - 1) / 2.0) * stackStep
        val sidewaysExtraWidth = MahjongTileDimensions.TILE_HEIGHT - MahjongTileDimensions.TILE_WIDTH
        val alongSide = when {
            sidewaysColumn == null || column < sidewaysColumn -> baseAlongSide
            column == sidewaysColumn -> baseAlongSide + sidewaysExtraWidth / 2.0
            else -> baseAlongSide + sidewaysExtraWidth
        }

        val rowStep = MahjongTileDimensions.TILE_HEIGHT + MahjongTileDimensions.TILE_SMALL_PADDING
        val perpendicular = DISCARD_ROW_BASE_OFFSET + row * rowStep
        return TileTableVector(x = alongSide, y = 0.0, z = perpendicular)
    }

    /**
     * 依 controller 座標、桌子世界朝向與座位 index，算出這位玩家副露區中偏離桌角錨點
     * [alongOffsetFromCorner] 距離的那張牌的世界座標——副露區錨定在桌子實際邊界的固定角落（玩家自己
     * 右手邊），不是相對手牌列／摸牌位算出來的浮動錨點（理由見 [MELD_AREA_CORNER_OFFSET] KDoc）。
     * 第一組（最早宣告）副露的最右側那張牌貼齊錨點本身，後續每組依序往玩家自己手牌方向（局部 X 軸
     * 負向）排開；單一組內鳴取牌該落在哪個格位，由呼叫端依鳴取來源方位（`RelativeDirection`）換算。
     *
     * 躺平的牌側身（[isSidewaysTile]）與直立兩種朝向的實際外觀寬高不同——側身牌沿排列方向的寬度是
     * [MahjongTileDimensions.TILE_HEIGHT]，直立牌是 [MahjongTileDimensions.TILE_WIDTH]（原本立牌的
     * 寬／高互換，見 [wallPlacement] 同一套躺平慣例）——因此 [alongOffsetFromCorner] 不是單純的格數
     * 乘上固定間距，而是呼叫端依每張牌實際朝向的寬度累加出來的距離，確保側身牌不會因為只占用一般
     * 格寬而跟隔壁重疊；垂直於排列方向的位置（`z`）也依 [isSidewaysTile] 內部換算，讓兩種朝向的牌
     * 靠近桌緣那一側的外緣對齊在同一條線上（[MELD_NEAR_EDGE_LINE]），不會因為朝向不同而其中一種
     * 牌外緣突出或內縮。
     *
     * 跟 [handPlacement]／[discardPlacement] 同樣直接用座位 index 算局部側面，不經過莊家相對旋轉——
     * 副露屬於宣告玩家自己，跟座位一樣整場對局固定不變。
     *
     * @param alongOffsetFromCorner 這張牌中心點沿排列方向距離桌角錨點（[MELD_AREA_CORNER_OFFSET]，
     * 往玩家自己手牌方向為正）的實際世界距離；呼叫端需自行沿排列方向累加每張已放置牌的半寬，確保這個
     * 值不會大到讓副露延伸進手牌列範圍，這裡不做邊界檢查。
     * @param isSidewaysTile 這張牌是否為鳴取自他家、需側身呈現的牌——為 `true` 時除了額外把 yaw 轉
     * [SIDEWAYS_YAW_OFFSET] 度，也依側身牌的實際外觀寬度換算 `x`／`z`，見本函式 KDoc。
     * @param depthOffsetFromEdge 這張牌垂直於排列方向（局部 Z 軸）額外往桌子中心方向（遠離桌緣）推
     * 的距離，`0.0`（預設值）代表跟同排其他牌一樣貼齊 [MELD_NEAR_EDGE_LINE]。加槓（`MeldType.ADDED_KAN`）
     * 補上的第 4 張牌用這個參數疊在原碰側身牌「靠近桌子中心」那一側（不是沿排列方向的旁邊），刻意
     * 的設計決定；其餘所有牌固定 `0.0`。
     */
    fun meldPlacement(
        controllerX: Int,
        controllerY: Int,
        controllerZ: Int,
        tableFacing: MahjongTableFacing,
        seatIndex: Int,
        alongOffsetFromCorner: Double,
        isSidewaysTile: Boolean,
        depthOffsetFromEdge: Double = 0.0,
    ): MahjongTileWallPlacement {
        require(alongOffsetFromCorner >= 0.0) { "Along offset from corner must not be negative" }
        require(depthOffsetFromEdge >= 0.0) { "Depth offset from edge must not be negative" }

        val physicalSide = seatIndexToTableSide(seatIndex)
        val local = localMeldVector(alongOffsetFromCorner, isSidewaysTile, depthOffsetFromEdge)
        val worldOffset = rotateForFacing(rotateForSide(local, physicalSide), tableFacing)
        val baseYaw = (yawForSide(physicalSide) + yawForFacing(tableFacing)).mod(FULL_YAW_DEGREES)
        return MahjongTileWallPlacement(
            x = controllerX + BLOCK_CENTER + worldOffset.x,
            y = controllerY + TABLETOP_HEIGHT + worldOffset.y,
            z = controllerZ + BLOCK_CENTER + worldOffset.z,
            yaw = if (isSidewaysTile) (baseYaw + SIDEWAYS_YAW_OFFSET).mod(FULL_YAW_DEGREES) else baseYaw,
        )
    }

    /**
     * 以局部南側玩家為基準的單張副露牌位置：沿排列方向（局部 X 軸）從 [MELD_AREA_CORNER_OFFSET] 桌角
     * 錨點往負向（玩家自己手牌方向）位移 [alongOffsetFromCorner]；垂直於排列方向（局部 Z 軸）依
     * [isSidewaysTile] 換算讓外緣對齊 [MELD_NEAR_EDGE_LINE]（副露區自己緊貼桌子實際邊界的外緣線，
     * 不是手牌那條退縮過的線，見該常數 KDoc），不是固定值——直立牌沿排列方向的寬度是
     * [MahjongTileDimensions.TILE_WIDTH]，垂直方向是 [MahjongTileDimensions.TILE_HEIGHT]；側身牌兩者
     * 互換。[depthOffsetFromEdge] 大於 `0.0` 時再額外往桌子中心方向（局部 Z 軸負向）推這麼多距離。
     */
    private fun localMeldVector(alongOffsetFromCorner: Double, isSidewaysTile: Boolean, depthOffsetFromEdge: Double): TileTableVector {
        val alongSide = MELD_AREA_CORNER_OFFSET - alongOffsetFromCorner
        val halfPerpendicularFootprint =
            if (isSidewaysTile) MahjongTileDimensions.TILE_WIDTH / 2.0 else MahjongTileDimensions.TILE_HEIGHT / 2.0
        val perpendicular = MELD_NEAR_EDGE_LINE - halfPerpendicularFootprint - depthOffsetFromEdge
        return TileTableVector(x = alongSide, y = 0.0, z = perpendicular)
    }

    /**
     * 依鳴取來源方位，換算鳴取牌在副露組內（`0` 為組內最左，靠近副露區桌角錨點方向）該落在哪個格位——
     * 上家（[RelativeDirection.Left]，吃唯一合法來源）固定最左；對家（[RelativeDirection.Across]）
     * 固定格位 `1`，三張的碰跟四張的槓皆同（四張時是「偏左第二張」，不是幾何正中央）；下家
     * （[RelativeDirection.Right]）固定最右；暗槓（[RelativeDirection.Self]，沒有鳴牌來源）回傳
     * `null`，呼叫端據此判斷不重排、四張全部直立呈現。公開成員：[meldAreaWidth]（算總寬度）與
     * `FabricMahjongMeldPresenter`（逐格擺放）需要共用同一套判斷，避免兩處各自實作後互相漂移。
     */
    fun sidewaysSlotIndex(direction: RelativeDirection, tileCount: Int): Int? = when (direction) {
        RelativeDirection.Left -> SIDEWAYS_SLOT_LEFT
        RelativeDirection.Across -> SIDEWAYS_SLOT_ACROSS
        RelativeDirection.Right -> tileCount - 1
        RelativeDirection.Self -> null
    }

    /**
     * 副露區沿排列方向（局部 X 軸）總共消耗的世界寬度（不含桌角本身的偏移，純粹是「這些副露疊起來
     * 有多寬」），供手牌／摸牌位（[handCornerYieldShift]）判斷是否需要讓開。
     *
     * 逐格累加的公式跟 `FabricMahjongMeldPresenter.present()` 實際逐格擺放時的游標算法完全一致
     * （側身牌用 [MahjongTileDimensions.TILE_HEIGHT]、直立牌用 [MahjongTileDimensions.TILE_WIDTH]，
     * 每張牌後面都跟一層 [MahjongTileDimensions.TILE_SMALL_PADDING] 縫隙，組間再額外跳過
     * [MELD_GROUP_GAP]）——兩處算的是同一件事的兩種用途（一個要算出每張牌的實際座標、一個只要算出
     * 總寬度），必須共用同一套公式，否則手牌讓開的偏移量會跟副露實際佔用的空間對不上。
     * [MeldType.ADDED_KAN] 補上的第 4 張牌疊在原碰側身牌旁邊（見 [meldPlacement] 的
     * `depthOffsetFromEdge`），不佔用額外的橫向格位，因此不計入寬度。
     */
    fun meldAreaWidth(melds: List<MahjongMeldTileGroup>): Double {
        var width = 0.0
        melds.forEachIndexed { index, meld ->
            if (index > 0) width += MELD_GROUP_GAP
            val isAddedKan = meld.type == MeldType.ADDED_KAN
            val baseTileIds = if (isAddedKan) meld.tileIds.dropLast(1) else meld.tileIds
            val slotCount = baseTileIds.size
            val sidewaysSlot = meld.calledTileId?.let { sidewaysSlotIndex(meld.sourceDirection, slotCount) }
            for (slot in 0 until slotCount) {
                val isSideways = slot == sidewaysSlot
                val tileWidth = if (isSideways) MahjongTileDimensions.TILE_HEIGHT else MahjongTileDimensions.TILE_WIDTH
                width += tileWidth + MahjongTileDimensions.TILE_SMALL_PADDING
            }
        }
        return width
    }

    /**
     * 積棒區沿排列方向（局部 X 軸）總共消耗的世界寬度——同一排最多 [STICKS_PER_ROW] 支，超過的部分
     * 往局部 Y 軸疊下一層（見 [stickPlacement]），不會增加寬度，所以寬度只會隨 [stickCount] 成長到
     * [STICKS_PER_ROW] 支就不再變化。
     */
    fun stickAreaWidth(stickCount: Int): Double {
        if (stickCount <= 0) return 0.0
        val columns = stickCount.coerceAtMost(STICKS_PER_ROW)
        return columns * (MahjongScoringStickDimensions.STICK_DEPTH + MahjongTileDimensions.TILE_SMALL_PADDING)
    }

    /**
     * 手牌整列（含摸牌位）需要往玩家自己方向（局部 X 軸負向）平移多少距離，才不會跟副露＋積棒區
     * （[reservedCornerWidth]，即 [stickAreaWidth] 加 [meldAreaWidth] 的總和）重疊——`0.0` 代表目前
     * 手牌長度不足以碰到副露／積棒區，不需要讓開。
     *
     * 手牌本來就以桌子中心對稱置中（見 [localHandVector]），最靠近桌角那一側的外緣（`tileIndex = 0`
     * 那張牌的外緣）天生會隨手牌張數增加往桌角方向逼近；[reservedCornerWidth] 越大，[MELD_AREA_CORNER_OFFSET]
     * 桌角錨點往手牌方向退讓的邊界就越靠內。只要手牌外緣還沒超過這條退讓後的邊界（扣掉
     * [HAND_CORNER_GAP]，讓兩者之間留一點看得出來的縫隙，不是貼死），回傳 `0.0`；超過時回傳剛好
     * 讓外緣貼齊這條退讓後邊界所需的平移量。
     *
     * [hasDrawnTile] 為 `true` 時，實際最靠近桌角的外緣不是立牌列本身，而是
     * [localDrawnTileVector]（緊接立牌列尾端外一個 `stackStep` 加 [DRAWN_TILE_GAP_RATIO] 縫隙）——
     * 摸牌位跟著整排立牌共用同一個 `cornerYieldShift`（見 [drawnTilePlacement] KDoc），若這裡只用
     * 立牌列自己的外緣計算，摸牌位會在讓開後仍然超出邊界、撞進副露區，這是遊戲內實際驗證過的問題。
     */
    fun handCornerYieldShift(handSize: Int, reservedCornerWidth: Double, hasDrawnTile: Boolean = false): Double {
        if (handSize <= 0 || reservedCornerWidth <= 0.0) return 0.0
        val stackStep = MahjongTileDimensions.TILE_WIDTH + MahjongTileDimensions.TILE_SMALL_PADDING
        val handRightEdge = (handSize - 1) / 2.0 * stackStep + MahjongTileDimensions.TILE_WIDTH / 2.0
        val drawnTileGap = MahjongTileDimensions.TILE_WIDTH * DRAWN_TILE_GAP_RATIO
        val rightEdge = if (hasDrawnTile) handRightEdge + stackStep + drawnTileGap else handRightEdge
        val availableCornerBoundary = MELD_AREA_CORNER_OFFSET - reservedCornerWidth - HAND_CORNER_GAP
        return (rightEdge - availableCornerBoundary).coerceAtLeast(0.0)
    }

    /**
     * 依 controller 座標、桌子世界朝向與座位 index，算出這位玩家積棒區中第 [stickIndex]
     * （`0` 起算，依生成順序排列）支積棒的世界座標——積棒直接佔用 [MELD_AREA_CORNER_OFFSET] 桌角
     * 錨點本身（副露從積棒外緣往手牌方向接續排開，見 [meldPlacement] 呼叫端如何把
     * [stickAreaWidth] 當成副露游標起始值），刻意的設計決定。
     *
     * 短邊（[MahjongScoringStickDimensions.STICK_DEPTH]）朝向玩家自己、沿排列方向（局部 X 軸）決定
     * 同一排能塞幾支；長邊（[MahjongScoringStickDimensions.STICK_WIDTH]）往桌子中心延伸（垂直排列
     * 方向，局部 Z 軸）。同一排最多 [STICKS_PER_ROW] 支，超過往局部 Y 軸疊下一層，同樣從第一支排到
     * 第 [STICKS_PER_ROW] 支，Y 軸層數無上限——同樣是刻意的設計決定。
     */
    fun stickPlacement(
        controllerX: Int,
        controllerY: Int,
        controllerZ: Int,
        tableFacing: MahjongTableFacing,
        seatIndex: Int,
        stickIndex: Int,
    ): MahjongTileWallPlacement {
        require(stickIndex >= 0) { "Stick index must not be negative" }

        val physicalSide = seatIndexToTableSide(seatIndex)
        val local = localStickVector(stickIndex)
        val worldOffset = rotateForFacing(rotateForSide(local, physicalSide), tableFacing)
        val baseYaw = (yawForSide(physicalSide) + yawForFacing(tableFacing)).mod(FULL_YAW_DEGREES)
        return MahjongTileWallPlacement(
            x = controllerX + BLOCK_CENTER + worldOffset.x,
            y = controllerY + TABLETOP_HEIGHT + worldOffset.y,
            z = controllerZ + BLOCK_CENTER + worldOffset.z,
            yaw = (baseYaw + SIDEWAYS_YAW_OFFSET).mod(FULL_YAW_DEGREES),
        )
    }

    /**
     * 以局部南側玩家為基準的單支積棒位置：沿排列方向（局部 X 軸）從 [MELD_AREA_CORNER_OFFSET] 桌角
     * 錨點往負向排開，每排（[STICKS_PER_ROW] 支）用一個 [MahjongScoringStickDimensions.STICK_DEPTH]
     * 加 [MahjongTileDimensions.TILE_SMALL_PADDING] 的步距；垂直排列方向（局部 Z 軸）固定貼齊
     * [MELD_NEAR_EDGE_LINE] 往桌子中心扣除半個 [MahjongScoringStickDimensions.STICK_WIDTH]（長邊朝
     * 桌子中心延伸）；局部 Y 軸依層數（`stickIndex / STICKS_PER_ROW`）疊高，層高為
     * [MahjongScoringStickDimensions.STICK_HEIGHT] 加一層 [MahjongTileDimensions.TILE_SMALL_PADDING]。
     */
    private fun localStickVector(stickIndex: Int): TileTableVector {
        val column = stickIndex % STICKS_PER_ROW
        val layer = stickIndex / STICKS_PER_ROW
        val stepAlong = MahjongScoringStickDimensions.STICK_DEPTH + MahjongTileDimensions.TILE_SMALL_PADDING
        val alongSide = MELD_AREA_CORNER_OFFSET - (column + 0.5) * stepAlong
        val perpendicular = MELD_NEAR_EDGE_LINE - MahjongScoringStickDimensions.STICK_WIDTH / 2.0
        val layerHeight = layer * (MahjongScoringStickDimensions.STICK_HEIGHT + MahjongTileDimensions.TILE_SMALL_PADDING)
        return TileTableVector(x = alongSide, y = layerHeight, z = perpendicular)
    }

    /**
     * 依 controller 座標、桌子世界朝向與座位 index，算出這位玩家立直棒該擺放的世界座標——立直棒放在
     * 這位玩家牌河（[DISCARD_ROW_BASE_OFFSET]）更靠近桌子中心那一側、緊鄰牌河第一排的位置、沿排列
     * 方向（局部 X 軸）置中，代表「立直棒放在桌子中央附近、緊鄰自己牌河」的實際擺法——遊戲內比對過
     * 截圖後確認：不是放在手牌與牌河之間（那個位置太靠近玩家自己、而非桌子中央）。跟 [discardPlacement]／
     * [stickPlacement] 同樣直接用座位 index 算局部側面，不經過莊家相對旋轉——立直棒屬於宣告立直的
     * 玩家自己，跟座位一樣整場對局固定不變。
     */
    fun riichiStickPlacement(
        controllerX: Int,
        controllerY: Int,
        controllerZ: Int,
        tableFacing: MahjongTableFacing,
        seatIndex: Int,
    ): MahjongTileWallPlacement {
        val physicalSide = seatIndexToTableSide(seatIndex)
        val local = localRiichiStickVector()
        val worldOffset = rotateForFacing(rotateForSide(local, physicalSide), tableFacing)
        val baseYaw = (yawForSide(physicalSide) + yawForFacing(tableFacing)).mod(FULL_YAW_DEGREES)
        return MahjongTileWallPlacement(
            x = controllerX + BLOCK_CENTER + worldOffset.x,
            y = controllerY + TABLETOP_HEIGHT + worldOffset.y,
            z = controllerZ + BLOCK_CENTER + worldOffset.z,
            yaw = baseYaw,
        )
    }

    /**
     * 以局部南側玩家為基準的立直棒位置：沿排列方向（局部 X 軸）置中（`alongSide = 0`）；垂直於排列
     * 方向（局部 Z 軸）從 [DISCARD_ROW_BASE_OFFSET]（牌河第一排「牌中心點」，不是牌的近緣）往桌子
     * 中心方向退開：先扣掉牌河第一排那張牌自己一半的 [MahjongTileDimensions.TILE_HEIGHT]（牌本身
     * 佔用的範圍，[DISCARD_ROW_BASE_OFFSET] 本身只是牌中心點座標，直接拿來當立直棒邊界會讓立直棒疊進
     * 牌河第一排——這是遊戲內實際驗證過的問題），再扣掉立直棒自己一半的
     * [MahjongScoringStickDimensions.STICK_DEPTH]，最後扣掉 [RIICHI_STICK_CLEARANCE_GAP] 讓兩者之間
     * 保留一點肉眼可辨的縫隙，不是只夠避免 Z-fighting 的極小留白。
     */
    private fun localRiichiStickVector(): TileTableVector {
        val clearance = MahjongTileDimensions.TILE_HEIGHT / 2.0 +
            MahjongScoringStickDimensions.STICK_DEPTH / 2.0 +
            RIICHI_STICK_CLEARANCE_GAP
        val perpendicular = DISCARD_ROW_BASE_OFFSET - clearance
        return TileTableVector(x = 0.0, y = 0.0, z = perpendicular)
    }

    /**
     * 依 controller 座標，算出桌面中央局況顯示（`MahjongRoundInfoEntity`）該擺放的世界座標——單一
     * entity 置中於桌面正中央，不需要依座位旋轉（billboard 顯示永遠面向鏡頭，見該 entity 的
     * renderer），高度刻意明顯高於牌牆／骰子等其他桌面機關的最高點（[ROUND_INFO_HEIGHT_ABOVE_TABLE]），
     * 避免文字被桌面模型或牌牆擋住——實際數值待進遊戲用不同鏡頭角度比對調整。回傳值的 `yaw` 未使用
     * （billboard 不需要固定朝向），固定為 `0.0f`。
     */
    fun roundInfoDisplayPlacement(controllerX: Int, controllerY: Int, controllerZ: Int): MahjongTileWallPlacement = MahjongTileWallPlacement(
        x = controllerX + BLOCK_CENTER,
        y = controllerY + TABLETOP_HEIGHT + ROUND_INFO_HEIGHT_ABOVE_TABLE,
        z = controllerZ + BLOCK_CENTER,
        yaw = 0.0f,
    )

    /**
     * 取得胡牌 showcase 的固定世界錨點：水平位置是 controller 所在桌面的幾何中心，高度落在桌面上緣。
     * 正式對局與 debug 虛擬桌共用這個入口，避免 stage 再以胡牌張或呼叫者座標作為中心而產生偏移。
     */
    fun showcaseStagePlacement(controllerX: Int, controllerY: Int, controllerZ: Int): MahjongTileWallPlacement = MahjongTileWallPlacement(
        x = controllerX + BLOCK_CENTER,
        y = controllerY + TABLETOP_HEIGHT,
        z = controllerZ + BLOCK_CENTER,
        yaw = 0.0f,
    )

    /** 依南→西→北→東的固定順序（跟 [seatIndexToTableSide] 同一套方向），把 [side] 往同方向推進 [steps] 步。 */
    private fun advance(side: MahjongTableSide, steps: Int): MahjongTableSide = SIDE_ORDER[(SIDE_ORDER.indexOf(side) + steps).mod(SIDE_ORDER.size)]

    /** 將局部南側基準旋轉至指定側面。 */
    private fun rotateForSide(vector: TileTableVector, side: MahjongTableSide): TileTableVector = when (side) {
        MahjongTableSide.SOUTH -> vector
        MahjongTableSide.WEST -> TileTableVector(-vector.z, vector.y, vector.x)
        MahjongTableSide.NORTH -> TileTableVector(-vector.x, vector.y, -vector.z)
        MahjongTableSide.EAST -> TileTableVector(vector.z, vector.y, -vector.x)
    }

    /** 將朝北擺放桌子的局部向量旋轉至指定世界朝向。 */
    private fun rotateForFacing(vector: TileTableVector, facing: MahjongTableFacing): TileTableVector = when (facing) {
        MahjongTableFacing.NORTH -> vector
        MahjongTableFacing.EAST -> TileTableVector(-vector.z, vector.y, vector.x)
        MahjongTableFacing.SOUTH -> TileTableVector(-vector.x, vector.y, -vector.z)
        MahjongTableFacing.WEST -> TileTableVector(vector.z, vector.y, -vector.x)
    }

    /** 局部側面對應的基準 yaw，旋轉方向與 [rotateForSide] 的向量旋轉一致。 */
    private fun yawForSide(side: MahjongTableSide): Float = when (side) {
        MahjongTableSide.SOUTH -> 0.0f
        MahjongTableSide.WEST -> 90.0f
        MahjongTableSide.NORTH -> 180.0f
        MahjongTableSide.EAST -> 270.0f
    }

    /** 世界朝向對應的基準 yaw，旋轉方向與 [rotateForFacing] 的向量旋轉一致。 */
    private fun yawForFacing(facing: MahjongTableFacing): Float = when (facing) {
        MahjongTableFacing.NORTH -> 0.0f
        MahjongTableFacing.EAST -> 90.0f
        MahjongTableFacing.SOUTH -> 180.0f
        MahjongTableFacing.WEST -> 270.0f
    }

    /** 固定桌面幾何常數。 */
    private const val BLOCK_CENTER: Double = 0.5
    private const val TABLETOP_HEIGHT: Double = 1.0

    /**
     * 桌面中央局況顯示（[roundInfoDisplayPlacement]）懸浮高度，明顯高於牌牆／骰子等其他桌面機關的
     * 最高點，避免文字被擋住，起始估算值，預期進遊戲後調整。
     */
    private const val ROUND_INFO_HEIGHT_ABOVE_TABLE: Double = 2.25

    /**
     * 牌牆生成掉落動畫中，同一面牌牆相鄰兩墩（`stack` 差 1）開始掉落的時間差，供 [wallDropAnimationTicks]
     * 與呼叫端排定每墩延遲使用；遊戲內比對後整體再調快，從原本 2 調到現在的值。
     */
    internal const val WAVE_STEP_TICKS: Int = 1

    /**
     * 開局發牌動畫中，牌從牌山位置小幅起飛的動畫時長，供 [dealBatchStartDelayTicks]／[dealAnimationTicks]
     * 使用；公開（非 `internal`）讓版本層的動畫實作能直接引用同一個值當作
     * `MahjongTileEntity.startMotionAnimation` 的 `durationTicks` 參數，不需要另外複製一份可能忘記
     * 同步的數字。
     */
    const val DEAL_LIFT_DURATION_TICKS: Int = 4

    /** 開局發牌動畫中，牌從起飛頂點瞬間重新排列到手牌列上空後，落下到最終手牌位置的動畫時長；公開理由同 [DEAL_LIFT_DURATION_TICKS]。 */
    const val DEAL_DROP_DURATION_TICKS: Int = 5

    /**
     * 開局發牌動畫中，起飛完成、瞬間重新排列到手牌列上空那一刻起，到真正解除隱形開始落下之間的短暫
     * 間隔——這段期間牌是隱形的（見 `FabricMahjongPlayerAreaPresenter.scheduleDealBatchAnimation`），
     * 讓「重新排成一列」感覺像是刻意的一個轉場動作，不是無縫瞬移；公開理由同 [DEAL_LIFT_DURATION_TICKS]。
     */
    const val DEAL_SNAP_GAP_TICKS: Int = 2

    /**
     * 開局發牌動畫中，連續兩次抓取「開始」之間的間隔——刻意小於 [DEAL_LIFT_DURATION_TICKS]，讓下一次
     * 抓取在上一次起飛動畫「播到一半」就先開始，不必等上一次的起飛都播完，兩次抓取的起飛動作會有一小
     * 段同時進行，讓連續抓取的節奏更快、更連貫；重新排列＋落下就更不用等了，理由見
     * [dealBatchStartDelayTicks]；`internal` 理由同 [CORNER_GAP_RATIO]。
     */
    internal const val DEAL_TURN_STAGGER_TICKS: Int = 3

    /**
     * 開局發牌動畫中，所有座位的最後一次抓取都落地後，到全部座位的牌同時開始原地翻起之間的短暫停頓，
     * 供 [dealFlipStartDelayTicks] 使用——讓「全部落地」跟「一起翻起」感覺是兩個分開的動作，不是無縫
     * 接續；遊戲內驗證後從 3 調高，原本的量級太短，最後一手剛摸進來就緊接著翻牌，感覺不夠自然。公開
     * 理由同 [DEAL_LIFT_DURATION_TICKS]。
     */
    const val DEAL_FLIP_GAP_TICKS: Int = 15

    /**
     * 開局發牌動畫中，翻牌動畫（姿態從蓋牌轉直立，位置不變）的動畫時長，供
     * [dealFlipStartDelayTicks]／[dealAnimationTicks] 使用；公開理由同
     * [DEAL_LIFT_DURATION_TICKS]。
     */
    const val DEAL_FLIP_DURATION_TICKS: Int = 4

    /**
     * 摸牌動畫起飛階段的相對高度，跟開局發牌動畫共用同一套「起飛→隱形傳送→落下」節奏（見
     * `FabricMahjongPlayerAreaPresenter.scheduleDrawnTileAnimation`），差別只在摸牌全程姿態固定直立、
     * 不需要額外的翻牌步驟——摸牌是單張、高頻的動作，直接面向玩家出現比蓋牌後再翻更符合直覺，見
     * `GamePresentationPublisher.publishPlayerAreaUpdated` 的 `animateDrawnTile` 參數 KDoc。起始估算值，
     * 預期進遊戲後調整。
     */
    const val DRAW_LIFT_HEIGHT: Double = 0.4

    /** 摸牌動畫起飛階段的動畫時長，供 [MahjongTileTableLayout] 以外的呼叫端引用，理由同 [DEAL_LIFT_DURATION_TICKS]。 */
    const val DRAW_LIFT_DURATION_TICKS: Int = 4

    /** 摸牌動畫起飛完成、隱形傳送到摸牌位上空後，到解除隱形開始落下之間的短暫間隔，理由同 [DEAL_SNAP_GAP_TICKS]。 */
    const val DRAW_SNAP_GAP_TICKS: Int = 2

    /** 摸牌動畫落下階段的動畫時長，理由同 [DEAL_DROP_DURATION_TICKS]。 */
    const val DRAW_DROP_DURATION_TICKS: Int = 5

    /**
     * 捨牌動畫是一次連續可見的拋物線飛行（不隱形、不傳送），從手牌位置直接飛到牌河位置，途中姿態從
     * 面向玩家的直立牌連續轉成攤平面朝上——跟摸牌／發牌刻意不同：丟牌是玩家自己主動觸發、全場最高頻
     * 的即時操作，玩家丟牌當下早就知道牌面內容，不需要摸牌／發牌那種「隱形揭曉」的儀式感，反而應該
     * 像現實丟牌一樣是一個連續、看得見、俐落的動作，見
     * `GamePresentationPublisher.publishDiscardPileUpdated` 的 `newlyDiscardedTileId` 參數 KDoc。
     * 側身旋轉（立直宣告牌）不連續內插，直接在動畫一開始就用最終 yaw 起飛——理由見
     * `FabricMahjongDiscardPresenter.scheduleDiscardTileAnimation` KDoc。
     *
     * 這個拋物線頂點額外高度，起始估算值，預期進遊戲後調整。
     */
    const val DISCARD_ARC_HEIGHT: Double = 0.3

    /** 捨牌動畫的動畫總時長，理由同 [DISCARD_ARC_HEIGHT]。 */
    const val DISCARD_FLIGHT_DURATION_TICKS: Int = 5

    /**
     * 胡牌慶祝演出中，強制理牌重排時每張牌從目前位置飛到整理後格位的動畫時長——手法比照鳴牌動畫
     * （[DISCARD_FLIGHT_DURATION_TICKS]），是一次連續可見的短程飛行，不是瞬間傳送；沒有移動的牌
     * （已經在整理後該在的格位）也會照樣播放這段動畫（起訖位置相同，視覺上等同無位移），維持所有牌
     * 共用同一個絕對收斂時刻的既有慣例，理由見 `FabricMahjongPlayerAreaPresenter.scheduleDealBatchAnimation`
     * KDoc。起始估算值，預期進遊戲後調整。
     */
    const val WIN_REORDER_FLIGHT_DURATION_TICKS: Int = 5

    /**
     * 胡牌慶祝演出中，牌姿態從立牌轉平放（牌面朝上）「倒下」的動畫時長，供自摸牌單獨倒下與其餘手牌
     * 一起倒下這兩個步驟共用；位置不變，只有姿態旋轉角內插，手法比照 [DEAL_FLIP_DURATION_TICKS]。
     * 起始估算值，預期進遊戲後調整。
     */
    const val WIN_LAYDOWN_DURATION_TICKS: Int = 4

    /**
     * 胡牌慶祝演出（自摸）中，自摸牌單獨倒下播完，到其餘手牌一起倒下開始之間的等待，約 0.8 秒，估算值，
     * 實作成具名常數方便進遊戲測試後微調；榮和／搶槓情境省略自摸牌倒下這一步，這個常數改成「強制理牌
     * 重排播完到手牌一起倒下之間」的等待。
     */
    const val WIN_PRE_HAND_LAYDOWN_DELAY_TICKS: Int = 16

    /** 胡牌慶祝演出中，手牌一起倒下播完到引雷三叉戟開始落下之間的等待，約 0.8 秒。 */
    const val WIN_PRE_EFFECT_DELAY_TICKS: Int = 16

    /** 引雷三叉戟從牌面上方落到胡牌張的時長。 */
    const val WIN_TRIDENT_FALL_DURATION_TICKS: Int = 6

    /** 三叉戟插中胡牌張後顫動並等待閃電落下的時長。 */
    const val WIN_TRIDENT_SETTLE_DURATION_TICKS: Int = 8

    /** 胡牌閃電相對整段降臨特效開始的 tick。 */
    const val WIN_LIGHTNING_START_TICK: Int = WIN_TRIDENT_FALL_DURATION_TICKS + WIN_TRIDENT_SETTLE_DURATION_TICKS

    /** 三叉戟引雷前搖與後續閃電、環形電弧的完整播放時長。 */
    const val WIN_EFFECT_DURATION_TICKS: Int = WIN_LIGHTNING_START_TICK + 30

    /**
     * 牌牆角落貼齊處縫隙相對 [MahjongTileDimensions.TILE_WIDTH] 的比例，遊戲內驗證後調整的觀感參數。
     * `internal` 而非 `private`：讓同模組的 `com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongTileTableLayoutTest`
     * 能直接引用同一個數值驗證預期位移量，不需要在測試裡另外複製一份可能忘記同步的常數。
     */
    internal const val CORNER_GAP_RATIO: Double = 0.25

    /** 王牌區沿排列方向滑向開門缺口的距離相對 [MahjongTileDimensions.TILE_WIDTH] 的比例，觀感調校參數；`internal` 理由同 [CORNER_GAP_RATIO]。 */
    internal const val DEAD_WALL_GAP_RATIO: Double = 0.25
    private const val FULL_YAW_DEGREES: Float = 360.0f

    /** [HAND_EDGE_OFFSET] 額外扣除的桌緣留白，遊戲內驗證後調整的觀感參數（初版手牌幾乎貼到桌緣）。 */
    internal const val HAND_EDGE_MARGIN: Double = 0.15

    /**
     * 手牌垂直於側面、離桌子中心的距離：46×46 模型單位可用內框半寬（換算 1.4375 block）扣除立牌厚度
     * 的一半，再扣掉 [HAND_EDGE_MARGIN] 讓手牌跟桌緣之間留一點空隙——初始版本沒扣這段 margin，
     * 遊戲內驗證時手牌幾乎貼到桌緣，因此往桌子中心方向再退一點；仍比牌牆（`halfSpan` 約 0.98 block，
     * 17 墩時）更靠外，維持在牌牆與桌緣之間。
     */
    internal const val HAND_EDGE_OFFSET: Double =
        46.0 / 16.0 / 2.0 - MahjongTileDimensions.TILE_DEPTH / 2.0 - HAND_EDGE_MARGIN

    /** 摸牌位跟立牌列尾端之間的縫隙相對 [MahjongTileDimensions.TILE_WIDTH] 的比例，起始估算值，預期進遊戲後用截圖比對調整。 */
    internal const val DRAWN_TILE_GAP_RATIO: Double = 0.5

    /** 牌河固定每排張數，超過往下一排堆疊。 */
    internal const val DISCARD_TILES_PER_ROW: Int = 6

    /**
     * `discardPlacement` 沒有像 [wallPlacement] 一樣收到 `stacksPerSide`，這裡假設標準日麻 17
     * 墩／面來估算牌牆 `halfSpan`，換算 [DISCARD_ROW_BASE_OFFSET]／[DISCARD_WALL_NEAR_EDGE] 用。
     */
    private const val ASSUMED_WALL_STACKS_PER_SIDE: Int = 17

    /**
     * 牌河實際會換行的排數——第三排（`row` 從 0 起算為 2）用完後，[localDiscardVector] 不再往桌緣
     * 方向新增第四排，而是固定停在這一排、沿排列方向繼續延伸，理由見 [localDiscardVector] KDoc。
     */
    internal const val DISCARD_SAFE_ROWS: Int = 3

    /** [DISCARD_WALL_NEAR_EDGE] 再往內縮的小留白，避免牌河貼到牌牆邊緣完全不留縫隙。 */
    internal const val DISCARD_WALL_CLEARANCE_MARGIN: Double = 0.05

    /**
     * 牌牆內緣（最靠近桌子中心那一側）的估算位置，扣掉 [DISCARD_WALL_CLEARANCE_MARGIN] 留白，供
     * [DISCARD_ROW_BASE_OFFSET] 換算用。
     */
    internal const val DISCARD_WALL_NEAR_EDGE: Double =
        ASSUMED_WALL_STACKS_PER_SIDE / 2.0 * (MahjongTileDimensions.TILE_WIDTH + MahjongTileDimensions.TILE_SMALL_PADDING) -
            DISCARD_WALL_CLEARANCE_MARGIN

    /**
     * 牌河第一排垂直於側面、離桌子中心的起始距離——用「[DISCARD_WALL_NEAR_EDGE] 扣掉 [DISCARD_SAFE_ROWS]
     * 排乘每排間距」算出來的：可以離桌子中心最遠、又保證 [DISCARD_SAFE_ROWS] 排都不會撞進牌牆的偏移
     * 值。遊戲內先後驗證過「太靠近中心」（初版估算值 0.3）、「太靠近牌牆」（手動加大到 0.6）、「第四排
     * 撞進牌牆」（拿全部排數去扣，沒有預留餘裕）三種錯誤，這個公式與 [localDiscardVector] 的「超過
     * [DISCARD_SAFE_ROWS] 排就停在最後一排延伸」設計一起解決，之後如果 [DISCARD_SAFE_ROWS] 或牌牆
     * 墩數估計改變，這裡會自動跟著調整，不需要再手動猜一次。
     */
    internal const val DISCARD_ROW_BASE_OFFSET: Double =
        DISCARD_WALL_NEAR_EDGE - DISCARD_SAFE_ROWS * (MahjongTileDimensions.TILE_HEIGHT + MahjongTileDimensions.TILE_SMALL_PADDING)

    /** 立直棒跟牌河第一排之間，肉眼可辨的留白距離，起始估算值，預期進遊戲後用截圖比對調整。 */
    internal const val RIICHI_STICK_CLEARANCE_GAP: Double = 0.05

    /** 側身標記的牌額外旋轉角度。 */
    internal const val SIDEWAYS_YAW_OFFSET: Float = 90.0f

    /** [MELD_AREA_CORNER_OFFSET] 額外扣除的桌緣留白，理由同 [HAND_EDGE_MARGIN]。 */
    internal const val MELD_AREA_CORNER_MARGIN: Double = 0.05

    /**
     * 副露區沿排列方向（局部 X 軸）的固定桌角邊界線——是第一組（最早宣告）副露最右側那張牌「外緣」
     * 該貼齊的線，不是那張牌的中心點；那張牌實際朝向（直立或側身）寬度不同，中心點座標由呼叫端依
     * [meldPlacement] 的 `alongOffsetFromCorner` 換算，這裡只固定邊界本身。
     *
     * 邊界固定在桌子實際邊界的角落，不是相對目前手牌張數／副露組數算出來的浮動起點——手牌張數會隨
     * 副露成立而縮小，若邊界跟著目前手牌張數或副露組數計算，邊界會不斷往外退，超過兩組副露就可能
     * 撐出桌面邊界，甚至遠到超出 `FabricMahjongMeldPresenter` 清除既有管理中麻將牌用的搜尋半徑，
     * 遊戲結束後留下沒被回收的孤兒 entity——這是實際遊戲內驗證過的問題，不是假設。改用跟
     * [HAND_EDGE_OFFSET] 同一組「46×46 模型單位可用內框半寬」換算固定角落位置，扣除 [MELD_AREA_CORNER_MARGIN]
     * 留白；後續每組副露都往這條線的左側（玩家自己手牌方向）排開，只會往桌子內側延伸，不會再超出邊界。
     */
    internal const val MELD_AREA_CORNER_OFFSET: Double =
        46.0 / 16.0 / 2.0 - MELD_AREA_CORNER_MARGIN

    /**
     * 副露區垂直於排列方向（局部 Z 軸）、靠近桌緣那一側的固定外緣邊界線——直接沿用跟
     * [MELD_AREA_CORNER_OFFSET] 相同的「46×46 模型單位可用內框半寬扣 [MELD_AREA_CORNER_MARGIN]」桌子
     * 實際邊界公式（兩個方向的留白刻意用同一個較小的邊界留白，不是 [HAND_EDGE_MARGIN] 那個手牌專用、
     * 為了視覺退縮特地調大的留白值），讓錨點那張牌在角落的兩側外緣（沿排列方向與垂直排列方向）都貼齊
     * 桌子實際邊界，不是跟手牌列共用同一條線——手牌是立牌、副露是躺平的牌，兩者外觀尺寸與貼齊基準本來
     * 就不同，共用同一條線是先前設計的誤用，也是副露會跟手牌列外觀體積重疊的原因之一。直立與側身兩種
     * 朝向的牌，外觀垂直於排列方向的寬度不同（分別是 [MahjongTileDimensions.TILE_HEIGHT] 與
     * [MahjongTileDimensions.TILE_WIDTH]），[meldPlacement] 依朝向各自從這條邊界線往桌子中心扣除半寬
     * 換算中心點，確保兩種朝向的牌外緣都對齊同一條線，不會其中一種突出或內縮。
     */
    internal const val MELD_NEAR_EDGE_LINE: Double = 46.0 / 16.0 / 2.0 - MELD_AREA_CORNER_MARGIN

    /**
     * 相鄰兩組副露之間額外跳過的世界距離相對 [MahjongTileDimensions.TILE_WIDTH] 的比例——只需要
     * 看得出分組的小縫隙，不是一整張牌的寬度（初版直接用一整張牌寬，遊戲內驗證後回報間距過大）。
     * 原本是 `FabricMahjongMeldPresenter` 私有的調校常數，搬進這裡是因為 [meldAreaWidth] 需要跟
     * 逐格擺放共用同一個數值，不能各自維護一份。
     */
    const val MELD_GROUP_GAP_RATIO: Double = 0.3

    /** 相鄰兩組副露之間額外跳過的世界距離，見 [MELD_GROUP_GAP_RATIO]。 */
    const val MELD_GROUP_GAP: Double = MahjongTileDimensions.TILE_WIDTH * MELD_GROUP_GAP_RATIO

    /**
     * 加槓補上的第 4 張牌，垂直於排列方向（往桌子中心）額外推的距離——剛好是側身牌自己的外觀寬度
     * （[MahjongTileDimensions.TILE_WIDTH]，側身後這個方向的寬度）加一層
     * [MahjongTileDimensions.TILE_SMALL_PADDING] 縫隙，讓兩張側身牌前後相鄰、不重疊。搬移理由同
     * [MELD_GROUP_GAP]。
     */
    const val ADDED_KAN_DEPTH_OFFSET: Double = MahjongTileDimensions.TILE_WIDTH + MahjongTileDimensions.TILE_SMALL_PADDING

    /** 上家鳴取（吃唯一合法來源）的側身牌組內格位：固定最左。搬移理由同 [MELD_GROUP_GAP]。 */
    const val SIDEWAYS_SLOT_LEFT: Int = 0

    /**
     * 對家鳴取的側身牌組內格位：固定 `1`（碰／槓皆同，四張時是偏左第二張，不是幾何正中央）。搬移
     * 理由同 [MELD_GROUP_GAP]。
     */
    const val SIDEWAYS_SLOT_ACROSS: Int = 1

    /** 積棒同一排最多排放的支數，超過往局部 Y 軸疊下一層——刻意的設計決定。 */
    const val STICKS_PER_ROW: Int = 5

    /**
     * 手牌／摸牌位讓開副露＋積棒區時，額外多留的縫隙——避免 [handCornerYieldShift] 算出來的偏移
     * 只是讓兩者剛好貼齊、外觀上完全不留空隙。使用起始估算值，跟 [MELD_GROUP_GAP] 同一數量級但
     * 不共用同一個值，因為這裡是手牌區跟副露／積棒區兩個不同子系統之間的縫隙，不是同一副露內部的
     * 組間縫隙，兩者觀感上不必然要一致；預期進遊戲後用截圖比對調整。
     */
    internal const val HAND_CORNER_GAP: Double = MahjongTileDimensions.TILE_WIDTH * 0.2

    /** 南→西→北→東的固定順序，跟 [seatIndexToTableSide] 與 `TileWallPosition.side` 同一套慣例。 */
    private val SIDE_ORDER =
        listOf(MahjongTableSide.SOUTH, MahjongTableSide.WEST, MahjongTableSide.NORTH, MahjongTableSide.EAST)
}
