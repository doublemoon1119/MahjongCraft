package com.doublemoon1119.mahjongcraft.platform.fabric.server.tile

import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MahjongTileEntity
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MahjongTilePose
import com.doublemoon1119.mahjongcraft.platform.minecraft.animation.AnimationStep
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongTileTableLayout
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongTileWallPlacement

/**
 * 麻將牌各種既有動畫（開局發牌、摸牌、捨牌、鳴牌、胡牌慶祝演出的強制理牌重排／倒牌）的單張牌排程
 * 邏輯，從各自原本所屬的正式桌子綁定 presenter（[FabricMahjongPlayerAreaPresenter]／
 * [FabricMahjongDiscardPresenter]）抽成獨立、不帶任何實例狀態的共用函式——這些函式全部只吃
 * `tile`／位置／絕對時刻這幾個純粹的參數，完全不涉及桌子座標系、`TableLocation` 或任何對局概念。
 *
 * 抽出來的理由：`FabricDebugAnimationCommand` 的動畫測試指令群組需要在完全不掛任何桌子／對局的臨時
 * 牌 entity 上重播一模一樣的動畫，讓開發者不需要真的建房/加入/開局就能預覽演出效果——重新複製一份
 * 邏輯容易在兩處實際動畫調整時忘記同步，因此改成兩邊共用同一份實作。`internal` 而非 `public`：只給
 * 同模組內的呼叫端使用，不對外公開成正式 API。
 */
internal object TileAnimationSteps {
    /**
     * 開局發牌動畫：排定單張牌「起飛→隱形傳送→落下→（全部座位都到齊後統一）翻牌」整段動畫，一次性
     * 組好整個 [AnimationStep] 佇列，理由見 [AnimatedMahjongEntity] KDoc（持久化撐過伺服器重啟）。
     *
     * [liftAbsoluteGameTime]／[flipAbsoluteGameTime] 是呼叫端算好、同一批牌共用同一份的絕對時刻
     * （[AnimationStep.WaitUntil]），不是相對等待——理由見 [AnimationStep.WaitUntil] KDoc：這批牌被
     * [tile.enqueueAll] 疊加進去的既有佇列可能還殘留其他 step，用絕對時刻才能保證這批牌真正同時起飛／
     * 同時翻牌。
     *
     * 起飛終點高度（`peakY`）是這張牌目前所在高度（`wallY`）加上 [DEAL_LIFT_HEIGHT]，不是統一對齊到
     * [finalPlacement] 的高度——同一批兩敦牌各自的上下兩層原本高度就不同，若起飛終點固定用同一個絕對
     * 高度，上下兩層會在起飛途中收斂到同一個高度，看起來像下層那張牌憑空消失；改成「相對自己原高度
     * 往上抬固定量」，兩層之間的相對高度差在起飛階段維持不變。
     *
     * 起飛播完的那一刻，隱形與瞬間重新排列到手牌列上空是同一個瞬間發生；接著額外維持
     * [MahjongTileTableLayout.DEAL_SNAP_GAP_TICKS] 的隱形，讓「重新排成一列」感覺像是刻意的一個轉場
     * 動作，不是無縫瞬移。翻牌後在觀看緩衝（[DEAL_VIEWING_BUFFER_TICKS]）播完那一刻，才傳送到
     * [postFlipPlacement]（可能與 [finalPlacement] 不同格，見 `MahjongInitialDealPresentation` KDoc）。
     */
    fun scheduleDealBatch(
        tile: MahjongTileEntity,
        finalPlacement: MahjongTileWallPlacement,
        postFlipPlacement: MahjongTileWallPlacement,
        liftAbsoluteGameTime: Long,
        flipAbsoluteGameTime: Long,
    ) {
        val wallX = tile.x
        val wallY = tile.y
        val wallZ = tile.z
        val wallYaw = tile.yaw
        val peakY = wallY + DEAL_LIFT_HEIGHT
        val snapGapEndGameTime = liftAbsoluteGameTime + MahjongTileTableLayout.DEAL_LIFT_DURATION_TICKS +
            MahjongTileTableLayout.DEAL_SNAP_GAP_TICKS
        val viewingEndGameTime = flipAbsoluteGameTime + MahjongTileTableLayout.DEAL_FLIP_DURATION_TICKS +
            DEAL_VIEWING_BUFFER_TICKS
        tile.enqueueAll(
            listOf(
                AnimationStep.WaitUntil(liftAbsoluteGameTime),
                AnimationStep.Teleport(wallX, peakY, wallZ, wallYaw),
                AnimationStep.PlayMotion(
                    durationTicks = MahjongTileTableLayout.DEAL_LIFT_DURATION_TICKS,
                    arcHeight = 0.0,
                    startOffsetX = 0.0,
                    startOffsetY = wallY - peakY,
                    startOffsetZ = 0.0,
                    startPoseRotationDegrees = MahjongTilePose.FACE_DOWN.rotationDegrees,
                    endPoseRotationDegrees = MahjongTilePose.FACE_DOWN.rotationDegrees,
                ),
                AnimationStep.SetInvisible(true),
                AnimationStep.Teleport(finalPlacement.x, finalPlacement.y, finalPlacement.z, finalPlacement.yaw),
                AnimationStep.WaitUntil(snapGapEndGameTime),
                AnimationStep.Custom(MahjongTilePose.FACE_DOWN),
                AnimationStep.SetInvisible(false),
                AnimationStep.PlayMotion(
                    durationTicks = MahjongTileTableLayout.DEAL_DROP_DURATION_TICKS,
                    arcHeight = 0.0,
                    startOffsetX = 0.0,
                    startOffsetY = peakY - finalPlacement.y,
                    startOffsetZ = 0.0,
                    startPoseRotationDegrees = MahjongTilePose.FACE_DOWN.rotationDegrees,
                    endPoseRotationDegrees = MahjongTilePose.FACE_DOWN.rotationDegrees,
                ),
                AnimationStep.WaitUntil(flipAbsoluteGameTime),
                AnimationStep.Custom(MahjongTilePose.STANDING),
                AnimationStep.PlayMotion(
                    durationTicks = MahjongTileTableLayout.DEAL_FLIP_DURATION_TICKS,
                    arcHeight = 0.0,
                    startOffsetX = 0.0,
                    startOffsetY = 0.0,
                    startOffsetZ = 0.0,
                    startPoseRotationDegrees = MahjongTilePose.FACE_DOWN.rotationDegrees,
                    endPoseRotationDegrees = MahjongTilePose.STANDING.rotationDegrees,
                ),
                AnimationStep.WaitUntil(viewingEndGameTime),
                AnimationStep.Teleport(postFlipPlacement.x, postFlipPlacement.y, postFlipPlacement.z, postFlipPlacement.yaw),
            ),
        )
    }

    /**
     * 摸牌動畫：跟開局發牌動畫共用同一套「起飛→隱形傳送→落下」節奏（手法同 [scheduleDealBatch]），
     * 完整順序是：面朝下起飛→隱形→傳送到摸牌位→（同一瞬間）姿態換成面向玩家→解除隱形→落下。翻面不是
     * 落地後另外播放的旋轉動畫，而是在隱形傳送那一刻直接切換姿態——因為切換當下牌本身是隱形的，玩家
     * 看不到姿態瞬間跳變。只有這一張牌，不需要像開局發牌那樣排定跨座位／跨批次的延遲，起飛立刻開始。
     */
    fun scheduleDrawnTile(tile: MahjongTileEntity, finalPlacement: MahjongTileWallPlacement) {
        val wallX = tile.x
        val wallY = tile.y
        val wallZ = tile.z
        val wallYaw = tile.yaw
        val peakY = wallY + MahjongTileTableLayout.DRAW_LIFT_HEIGHT
        val snapGapEndGameTime = tile.world.time + MahjongTileTableLayout.DRAW_LIFT_DURATION_TICKS +
            MahjongTileTableLayout.DRAW_SNAP_GAP_TICKS
        tile.enqueueAll(
            listOf(
                AnimationStep.Teleport(wallX, peakY, wallZ, wallYaw),
                AnimationStep.PlayMotion(
                    durationTicks = MahjongTileTableLayout.DRAW_LIFT_DURATION_TICKS,
                    arcHeight = 0.0,
                    startOffsetX = 0.0,
                    startOffsetY = wallY - peakY,
                    startOffsetZ = 0.0,
                    startPoseRotationDegrees = MahjongTilePose.FACE_DOWN.rotationDegrees,
                    endPoseRotationDegrees = MahjongTilePose.FACE_DOWN.rotationDegrees,
                ),
                AnimationStep.SetInvisible(true),
                AnimationStep.Teleport(finalPlacement.x, finalPlacement.y, finalPlacement.z, finalPlacement.yaw),
                AnimationStep.Custom(MahjongTilePose.STANDING),
                AnimationStep.WaitUntil(snapGapEndGameTime),
                AnimationStep.SetInvisible(false),
                AnimationStep.PlayMotion(
                    durationTicks = MahjongTileTableLayout.DRAW_DROP_DURATION_TICKS,
                    arcHeight = 0.0,
                    startOffsetX = 0.0,
                    startOffsetY = peakY - finalPlacement.y,
                    startOffsetZ = 0.0,
                    startPoseRotationDegrees = MahjongTilePose.STANDING.rotationDegrees,
                    endPoseRotationDegrees = MahjongTilePose.STANDING.rotationDegrees,
                ),
            ),
        )
    }

    /**
     * 捨牌動畫：一次連續可見的拋物線飛行，從手牌現在的實際位置直接飛到牌河位置，不像摸牌／發牌動畫
     * 那樣中途隱形傳送——理由見 [MahjongTileTableLayout.DISCARD_ARC_HEIGHT] KDoc。姿態
     * （[MahjongTilePose.STANDING] 轉 [MahjongTilePose.FACE_UP]）跟位移用同一段動畫連續內插。側身旋轉
     * 不連續內插——[finalPlacement] 的 yaw 本身已經是算好的最終朝向，起飛那一刻就以最終朝向飛過去。
     */
    fun scheduleDiscardFlight(tile: MahjongTileEntity, finalPlacement: MahjongTileWallPlacement) {
        val startX = tile.x
        val startY = tile.y
        val startZ = tile.z
        tile.enqueueAll(
            listOf(
                AnimationStep.Teleport(finalPlacement.x, finalPlacement.y, finalPlacement.z, finalPlacement.yaw),
                AnimationStep.Custom(MahjongTilePose.FACE_UP),
                AnimationStep.PlayMotion(
                    durationTicks = MahjongTileTableLayout.DISCARD_FLIGHT_DURATION_TICKS,
                    arcHeight = MahjongTileTableLayout.DISCARD_ARC_HEIGHT,
                    startOffsetX = startX - finalPlacement.x,
                    startOffsetY = startY - finalPlacement.y,
                    startOffsetZ = startZ - finalPlacement.z,
                    startPoseRotationDegrees = MahjongTilePose.STANDING.rotationDegrees,
                    endPoseRotationDegrees = MahjongTilePose.FACE_UP.rotationDegrees,
                ),
            ),
        )
    }

    /**
     * 鳴牌/槓牌動畫：一次連續可見的拋物線飛行，從這張牌現在的實際位置直接飛到副露區最終格位，起始
     * 姿態改讀 entity 自己目前的 [MahjongTileEntity.tilePose]，不是寫死 [MahjongTilePose.STANDING]：
     * 吃/碰/明槓被鳴取的那張、暗槓全部牌都來自手牌或牌河，姿態各不相同，這個函式同時服務所有情況，
     * 起訖姿態相同時就不會多插入一個沒有效果的 [AnimationStep.Custom]。側身旋轉（被鳴取的那張若落在
     * 側身格）跟捨牌動畫同一個既有簡化：[finalPlacement] 的 yaw 本身已經是算好的最終朝向，起飛那一刻
     * 就用最終 yaw 傳送，不連續內插旋轉角。
     */
    fun scheduleMeldClaim(tile: MahjongTileEntity, finalPlacement: MahjongTileWallPlacement, endPose: MahjongTilePose) {
        val startX = tile.x
        val startY = tile.y
        val startZ = tile.z
        val startPose = tile.tilePose
        val steps = mutableListOf<AnimationStep<MahjongTilePose>>(
            AnimationStep.Teleport(finalPlacement.x, finalPlacement.y, finalPlacement.z, finalPlacement.yaw),
        )
        if (startPose != endPose) steps += AnimationStep.Custom(endPose)
        steps += AnimationStep.PlayMotion(
            durationTicks = MahjongTileTableLayout.DISCARD_FLIGHT_DURATION_TICKS,
            arcHeight = MahjongTileTableLayout.DISCARD_ARC_HEIGHT,
            startOffsetX = startX - finalPlacement.x,
            startOffsetY = startY - finalPlacement.y,
            startOffsetZ = startZ - finalPlacement.z,
            startPoseRotationDegrees = startPose.rotationDegrees,
            endPoseRotationDegrees = endPose.rotationDegrees,
        )
        tile.enqueueAll(steps)
    }

    /**
     * 強制理牌重排的單張牌動畫（胡牌慶祝演出）：跟 [scheduleMeldClaim] 同一套「立即傳送到最終位置、
     * `PlayMotion` 只負責讓 render 端從舊位置補間過去」手法，差別是這裡全程姿態固定立牌
     * （[MahjongTilePose.STANDING]），只有位置變動，沒有姿態轉換；[startGameTime] 是呼叫端算好、這批
     * 牌共用同一份的絕對起飛時刻，不是相對等待。沒有實際移動的牌（已經在整理後該在的格位）也會照樣
     * 走一次這個 step（起訖位置相同，視覺上等同無位移），讓全部牌的動畫佇列時長一致，方便呼叫端用
     * 同一個絕對「重排播完」時刻接續排定後續步驟。
     */
    fun scheduleReorder(tile: MahjongTileEntity, finalPlacement: MahjongTileWallPlacement, startGameTime: Long) {
        val startX = tile.x
        val startY = tile.y
        val startZ = tile.z
        tile.enqueueAll(
            listOf(
                AnimationStep.WaitUntil(startGameTime),
                AnimationStep.Teleport(finalPlacement.x, finalPlacement.y, finalPlacement.z, finalPlacement.yaw),
                AnimationStep.PlayMotion(
                    durationTicks = MahjongTileTableLayout.WIN_REORDER_FLIGHT_DURATION_TICKS,
                    arcHeight = 0.0,
                    startOffsetX = startX - finalPlacement.x,
                    startOffsetY = startY - finalPlacement.y,
                    startOffsetZ = startZ - finalPlacement.z,
                    startPoseRotationDegrees = MahjongTilePose.STANDING.rotationDegrees,
                    endPoseRotationDegrees = MahjongTilePose.STANDING.rotationDegrees,
                ),
            ),
        )
    }

    /**
     * 「倒牌」的單張牌動畫（胡牌慶祝演出）：姿態從立牌轉平放牌面朝上，位置完全不動，只有姿態旋轉角
     * 隨動畫進度內插——硬性限制要求所有姿態轉換都要走 [AnimationStep.Custom] + [AnimationStep.PlayMotion]，
     * 不直接賦值 [MahjongTileEntity.tilePose]。[startGameTime] 是呼叫端算好、同一批牌共用同一份的絕對
     * 起飛時刻，理由同 [scheduleReorder]。
     */
    fun scheduleLaydown(tile: MahjongTileEntity, startGameTime: Long) {
        tile.enqueueAll(
            listOf(
                AnimationStep.WaitUntil(startGameTime),
                AnimationStep.Custom(MahjongTilePose.FACE_UP),
                AnimationStep.PlayMotion(
                    durationTicks = MahjongTileTableLayout.WIN_LAYDOWN_DURATION_TICKS,
                    arcHeight = 0.0,
                    startOffsetX = 0.0,
                    startOffsetY = 0.0,
                    startOffsetZ = 0.0,
                    startPoseRotationDegrees = MahjongTilePose.STANDING.rotationDegrees,
                    endPoseRotationDegrees = MahjongTilePose.FACE_UP.rotationDegrees,
                ),
            ),
        )
    }

    /**
     * 流局時將未公開手牌由目前姿態平滑蓋成牌背朝上。姿態與動畫步驟一起寫入 entity NBT，重新載入
     * 世界後會接續尚未完成的旋轉，不會瞬間跳到終點。
     */
    fun scheduleConceal(tile: MahjongTileEntity, startGameTime: Long) {
        val startPose = tile.tilePose
        tile.enqueueAll(
            listOf(
                AnimationStep.WaitUntil(startGameTime),
                AnimationStep.Custom(MahjongTilePose.FACE_DOWN),
                AnimationStep.PlayMotion(
                    durationTicks = MahjongTileTableLayout.WIN_LAYDOWN_DURATION_TICKS,
                    arcHeight = 0.0,
                    startOffsetX = 0.0,
                    startOffsetY = 0.0,
                    startOffsetZ = 0.0,
                    startPoseRotationDegrees = startPose.rotationDegrees,
                    endPoseRotationDegrees = MahjongTilePose.FACE_DOWN.rotationDegrees,
                    easeRotation = true,
                ),
            ),
        )
    }

    /** 開局發牌動畫起飛階段的相對高度，理由同 `FabricMahjongTileWallPresenter.WALL_DROP_HEIGHT`——起始估算值。 */
    private const val DEAL_LIFT_HEIGHT: Double = 0.4

    /**
     * 開局發牌動畫全部播完（含翻牌）後，額外掛在每張牌佇列尾端的觀看緩衝，讓桌子在玩家真正看清楚
     * 手牌之前持續維持「還在忙」，理由見 `FabricMahjongPlayerAreaPresenter.presentInitialDeal` KDoc。
     */
    private const val DEAL_VIEWING_BUFFER_TICKS: Int = 25
}
