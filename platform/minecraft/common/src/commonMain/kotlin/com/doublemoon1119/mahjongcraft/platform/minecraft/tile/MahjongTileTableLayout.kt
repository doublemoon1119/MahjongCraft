package com.doublemoon1119.mahjongcraft.platform.minecraft.tile

import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallPosition
import com.doublemoon1119.mahjongcraft.platform.minecraft.dice.MahjongTableFacing
import com.doublemoon1119.mahjongcraft.platform.minecraft.dice.MahjongTableSide
import com.doublemoon1119.mahjongcraft.platform.minecraft.dice.seatIndexToTableSide

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
 * 兩段式旋轉合成與 [com.doublemoon1119.mahjongcraft.platform.minecraft.dice.MahjongDiceTableLayout] 完全
 * 同一套慣例：先算出「以局部南側為基準」的向量，再依序旋轉到目標側面、旋轉到桌子世界朝向。
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
     * [com.doublemoon1119.mahjongcraft.logic.table.layout.FourSidedWallLayoutSupport] 的墩位排列
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
     * 依 controller 座標、桌子世界朝向與座位 index，算出該玩家手牌中第 [tileIndex] 張牌的世界座標。
     *
     * 跟 [wallPlacement] 不同，手牌位置直接用玩家自己在 `TableState.players` 的固定座位 index 算局部
     * 側面（`seatIndexToTableSide(seatIndex)`），不經過莊家相對的 [advance] 旋轉——座位 index 整場對局
     * 固定不變（只有自風跟著轉），跟 [com.doublemoon1119.mahjongcraft.platform.minecraft.seating.MahjongSeatingTableLayout]
     * 的既有慣例一致；牌牆才需要莊家相對旋轉，因為 `TileWallPosition.side` 的意義每局隨莊家改變。
     *
     * @param handSize 這位玩家目前的手牌張數，用來把整排手牌對稱置中於局部側面。
     * @param tileIndex 這張牌在手牌裡的零基底索引（`0` 在最大 X、遞增時往負向移動，跟牌牆 `stack` 的
     * 排列方向一致）。
     */
    fun handPlacement(
        controllerX: Int,
        controllerY: Int,
        controllerZ: Int,
        tableFacing: MahjongTableFacing,
        seatIndex: Int,
        handSize: Int,
        tileIndex: Int,
    ): MahjongTileWallPlacement {
        require(handSize > 0) { "Hand size must be positive" }
        require(tileIndex in 0 until handSize) { "Tile index $tileIndex out of range for hand size $handSize" }

        val physicalSide = seatIndexToTableSide(seatIndex)
        val local = localHandVector(handSize, tileIndex)
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
     * [MahjongTileDimensions.TILE_WIDTH]，跟牌牆同一套墩間距常數對稱置中。垂直於側面的距離
     * （`HAND_EDGE_OFFSET`）是初始估算值，只要求比牌牆的 `halfSpan` 更靠近桌緣（手牌在牌牆跟桌緣
     * 之間，貼近玩家自己），實際數值待遊戲內比對後可能還要再微調，比照牌牆當初的調校方式。
     */
    private fun localHandVector(handSize: Int, tileIndex: Int): TileTableVector {
        val stackStep = MahjongTileDimensions.TILE_WIDTH + MahjongTileDimensions.TILE_SMALL_PADDING
        val alongSide = ((handSize - 1) / 2.0 - tileIndex) * stackStep
        return TileTableVector(x = alongSide, y = 0.0, z = HAND_EDGE_OFFSET)
    }

    /**
     * 依 controller 座標、桌子世界朝向與座位 index，算出這位玩家目前摸到、尚未併入立牌或打出的那張牌
     * 的世界座標——緊鄰立牌列尾端外一段看得出來的縫隙，不是重新置中整排立牌（真實麻將：摸到的牌先擺
     * 在手牌一側，不會立刻插入、重新排列整排立牌）。跟 [handPlacement] 同樣直接用座位 index 算局部
     * 側面，不經過莊家相對旋轉。
     *
     * @param standingTileCount 這位玩家目前立牌張數（不含這張剛摸到的牌）。
     */
    fun drawnTilePlacement(
        controllerX: Int,
        controllerY: Int,
        controllerZ: Int,
        tableFacing: MahjongTableFacing,
        seatIndex: Int,
        standingTileCount: Int,
    ): MahjongTileWallPlacement {
        val physicalSide = seatIndexToTableSide(seatIndex)
        val local = localDrawnTileVector(standingTileCount)
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
     * 看得出來的間隔。直立擺放，`z` 跟 [localHandVector] 共用 [HAND_EDGE_OFFSET]。
     */
    private fun localDrawnTileVector(standingTileCount: Int): TileTableVector {
        val stackStep = MahjongTileDimensions.TILE_WIDTH + MahjongTileDimensions.TILE_SMALL_PADDING
        val handRowEdge = (standingTileCount - 1) / 2.0 * stackStep
        val gap = MahjongTileDimensions.TILE_WIDTH * DRAWN_TILE_GAP_RATIO
        val alongSide = handRowEdge + stackStep + gap
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
     * [SIDEWAYS_YAW_OFFSET] 度，位置本身不變。已知簡化：不因側身而加寬該格間距，跟真實麻將側身牌會
     * 占用略多橫向空間不同；先進遊戲內用截圖比對，如果側身牌跟旁邊牌重疊太明顯，再考慮個別調整間距。
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
        wallRemaining: Boolean,
    ): MahjongTileWallPlacement {
        require(discardIndex >= 0) { "Discard index must not be negative" }

        val physicalSide = seatIndexToTableSide(seatIndex)
        val local = localDiscardVector(discardIndex, wallRemaining)
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
     */
    private fun localDiscardVector(discardIndex: Int, wallRemaining: Boolean): TileTableVector {
        val lastSafeRow = DISCARD_SAFE_ROWS - 1
        val lastSafeRowStartIndex = lastSafeRow * DISCARD_TILES_PER_ROW
        val (row, column) = if (!wallRemaining || discardIndex < lastSafeRowStartIndex) {
            discardIndex / DISCARD_TILES_PER_ROW to discardIndex % DISCARD_TILES_PER_ROW
        } else {
            lastSafeRow to (discardIndex - lastSafeRowStartIndex)
        }
        val stackStep = MahjongTileDimensions.TILE_WIDTH + MahjongTileDimensions.TILE_SMALL_PADDING
        val alongSide = (column - (DISCARD_TILES_PER_ROW - 1) / 2.0) * stackStep
        val rowStep = MahjongTileDimensions.TILE_HEIGHT + MahjongTileDimensions.TILE_SMALL_PADDING
        val perpendicular = DISCARD_ROW_BASE_OFFSET + row * rowStep
        return TileTableVector(x = alongSide, y = 0.0, z = perpendicular)
    }

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
     * 牌牆角落貼齊處縫隙相對 [MahjongTileDimensions.TILE_WIDTH] 的比例，遊戲內驗證後調整的觀感參數。
     * `internal` 而非 `private`：讓同模組的 [MahjongTileTableLayoutTest][com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongTileTableLayoutTest]
     * 能直接引用同一個數值驗證預期位移量，不需要在測試裡另外複製一份可能忘記同步的常數。
     */
    internal const val CORNER_GAP_RATIO: Double = 0.25

    /** 王牌區沿排列方向滑向開門缺口的距離相對 [MahjongTileDimensions.TILE_WIDTH] 的比例，使用者指定的觀感參數；`internal` 理由同 [CORNER_GAP_RATIO]。 */
    internal const val DEAD_WALL_GAP_RATIO: Double = 0.25
    private const val FULL_YAW_DEGREES: Float = 360.0f

    /** [HAND_EDGE_OFFSET] 額外扣除的桌緣留白，遊戲內驗證後調整的觀感參數（使用者回報手牌離桌緣太近）。 */
    internal const val HAND_EDGE_MARGIN: Double = 0.15

    /**
     * 手牌垂直於側面、離桌子中心的距離：46×46 模型單位可用內框半寬（換算 1.4375 block）扣除立牌厚度
     * 的一半，再扣掉 [HAND_EDGE_MARGIN] 讓手牌跟桌緣之間留一點空隙——初始版本沒扣這段 margin，
     * 遊戲內驗證時手牌幾乎貼到桌緣，因此往桌子中心方向再退一點；仍比牌牆（`halfSpan` 約 0.98 block，
     * 17 墩時）更靠外，維持在牌牆與桌緣之間。
     */
    internal const val HAND_EDGE_OFFSET: Double = 46.0 / 16.0 / 2.0 - MahjongTileDimensions.TILE_DEPTH / 2.0 - HAND_EDGE_MARGIN

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

    /** 側身標記的牌額外旋轉角度。 */
    internal const val SIDEWAYS_YAW_OFFSET: Float = 90.0f

    /** 南→西→北→東的固定順序，跟 [seatIndexToTableSide] 與 `TileWallPosition.side` 同一套慣例。 */
    private val SIDE_ORDER = listOf(MahjongTableSide.SOUTH, MahjongTableSide.WEST, MahjongTableSide.NORTH, MahjongTableSide.EAST)
}
