package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.tile

import com.doublemoon1119.mahjongcraft.logic.tile.component.TilePose
import com.doublemoon1119.mahjongcraft.platform.minecraft.animation.AnimationStep
import com.doublemoon1119.mahjongcraft.platform.minecraft.animation.MahjongAnimationSounds
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.entity.AnimatedMahjongEntity
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.entity.MahjongTileEntity
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.entity.MahjongTilePose
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.HandRevealSteps
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongTileTableLayout
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongTileWallPlacement
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.PlannedTileMotion
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.PlannedTileMotionSteps

/**
 * 麻將牌各種動畫（開局發牌、摸牌、捨牌、鳴牌、胡牌慶祝演出的理牌重排與倒牌）的單張牌排程邏輯。
 *
 * - 由正式桌子的呈現（[EntityMahjongPlayerAreaPresenter] 與依步驟呈現的發布器）與 debug 動畫指令共用。
 * - 不帶任何實例狀態。函式只接收 `tile`、位置與絕對時刻，不涉及桌子座標系、`TableLocation` 或任何對局概念。
 * - `DebugCommand` 的動畫測試指令在不屬於任何桌子或對局的臨時牌 entity 上重播同樣的動畫，
 *   開發者不必建房、加入、開局就能預覽。兩邊共用同一份實作，動畫調整時不會不同步。
 * - `internal`：只給同模組內的呼叫端使用，不是正式 API。
 */
object TileAnimationSteps {
    /**
     * 開局發牌動畫：排定單張牌「起飛→隱形傳送→落下→（全部座位到齊後統一）翻牌」整段動畫。
     * 一次組好整個 [AnimationStep] 佇列，以便持久化撐過伺服器重啟，見 [AnimatedMahjongEntity] KDoc。
     *
     * - [liftAbsoluteGameTime]／[flipAbsoluteGameTime] 是呼叫端算好、同一批牌共用的絕對時刻（[AnimationStep.WaitUntil]），不是相對等待。
     *   [AnimatedMahjongEntity.enqueueAll] 疊加進去的既有佇列可能還有其他 step，用絕對時刻才能讓這批牌同時起飛、同時翻牌，
     *   見 [AnimationStep.WaitUntil] KDoc。
     * - 起飛終點高度（`peakY`）是牌目前高度（`wallY`）加上 [DEAL_LIFT_HEIGHT]，不統一對齊 [finalPlacement] 的高度。
     *   同一批兩墩牌的上下兩層高度不同，用同一個絕對高度會讓兩層在起飛途中收斂到同一高度，看起來像下層的牌消失。
     *   相對原高度往上抬固定量，兩層的高度差維持不變。
     * - 起飛播完的瞬間，牌隱形並重新排列到手牌列上空。接著維持 [MahjongTileTableLayout.DEAL_SNAP_GAP_TICKS] 的隱形，
     *   讓重新排成一列看起來是一個轉場，不是無縫瞬移。
     * - 翻牌那一刻牌背仍朝上，先瞬間換到 [postFlipPlacement] 再原地翻起。它可能與 [finalPlacement] 不同格，
     *   見 `MahjongInitialDealPresentation` KDoc。背面朝上的牌外觀相同，換位看不出來。
     * - 翻牌後保留觀看緩衝（[DEAL_VIEWING_BUFFER_TICKS]）。
     *
     * @param tile 要排定發牌動畫的牌 entity。
     * @param finalPlacement 翻牌前落下的位置。
     * @param postFlipPlacement 翻牌時與之後所在的位置。
     * @param liftAbsoluteGameTime 這批牌共用的起飛絕對 tick。
     * @param flipAbsoluteGameTime 這批牌共用的翻牌絕對 tick。
     * @param playDealSound 是否為此牌播放發牌聲。
     * @param playFlipSound 是否為此牌播放翻牌聲。
     * @param fromWall 是否從既有牌牆抬起。無牌牆時直接在手牌格位落下。
     * @param endPose 翻牌動畫結束後的姿態。一般發牌為直立。
     */
    fun scheduleDealBatch(
        tile: MahjongTileEntity,
        finalPlacement: MahjongTileWallPlacement,
        postFlipPlacement: MahjongTileWallPlacement,
        liftAbsoluteGameTime: Long,
        flipAbsoluteGameTime: Long,
        playDealSound: Boolean = false,
        playFlipSound: Boolean = false,
        endPose: MahjongTilePose = MahjongTilePose.STANDING,
        fromWall: Boolean = true,
    ) {
        val wallX = tile.x
        val wallY = tile.y
        val wallZ = tile.z
        val wallYaw = tile.yRot
        val peakY = wallY + DEAL_LIFT_HEIGHT
        val snapGapEndGameTime = liftAbsoluteGameTime + MahjongTileTableLayout.DEAL_LIFT_DURATION_TICKS +
            MahjongTileTableLayout.DEAL_SNAP_GAP_TICKS
        val viewingEndGameTime = flipAbsoluteGameTime + MahjongTileTableLayout.DEAL_FLIP_DURATION_TICKS +
            DEAL_VIEWING_BUFFER_TICKS
        tile.enqueueAll(
            steps = buildList {
                add(AnimationStep.WaitUntil(liftAbsoluteGameTime))
                if (playDealSound) add(dealBatchSound(liftAbsoluteGameTime))
                if (fromWall) {
                    addAll(
                        elements = listOf(
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
                        ),
                    )
                }
                if (!fromWall) {
                    add(AnimationStep.Teleport(finalPlacement.x, finalPlacement.y, finalPlacement.z, finalPlacement.yaw))
                    add(AnimationStep.Custom(MahjongTilePose.FACE_DOWN))
                    add(AnimationStep.SetInvisible(false))
                    add(dropMotion(MahjongTileTableLayout.DRAW_LIFT_HEIGHT, MahjongTilePose.FACE_DOWN))
                }
                add(AnimationStep.WaitUntil(flipAbsoluteGameTime))
                if (playFlipSound) add(handTurnSound(flipAbsoluteGameTime))
                addAll(
                    elements = listOf(
                        AnimationStep.Teleport(
                            x = postFlipPlacement.x,
                            y = postFlipPlacement.y,
                            z = postFlipPlacement.z,
                            yaw = postFlipPlacement.yaw,
                        ),
                        AnimationStep.Custom(endPose),
                        AnimationStep.PlayMotion(
                            durationTicks = MahjongTileTableLayout.DEAL_FLIP_DURATION_TICKS,
                            arcHeight = 0.0,
                            startOffsetX = 0.0,
                            startOffsetY = 0.0,
                            startOffsetZ = 0.0,
                            startPoseRotationDegrees = MahjongTilePose.FACE_DOWN.rotationDegrees,
                            endPoseRotationDegrees = endPose.rotationDegrees,
                        ),
                        AnimationStep.WaitUntil(viewingEndGameTime),
                    ),
                )
            },
        )
    }

    /**
     * 在 [startGameTime] 播放一步之中規劃好的動作。步驟由 [PlannedTileMotionSteps] 展開，牌譜回放的場景播放同一份步驟。
     *
     * @param tile 要播放動作的牌 entity。
     * @param motion 規劃好的動作。
     * @param startGameTime 動作開始的絕對遊戲 tick。
     * @param playLandingSound 是否播放落地聲。同一組一起落地的牌只需要其中一張播放。
     */
    fun schedulePlannedMotion(
        tile: MahjongTileEntity,
        motion: PlannedTileMotion,
        startGameTime: Long,
        playLandingSound: Boolean = true,
    ) {
        schedule(tile, PlannedTileMotionSteps.steps(motion, startGameTime, playLandingSound))
    }

    /**
     * 讓牌依序播放共用姿態的動畫步驟，例如 [PlannedTileMotionSteps.forStep] 展開的一步。
     *
     * @param tile 要播放的牌 entity。
     * @param steps 依序播放的步驟。
     */
    fun schedule(tile: MahjongTileEntity, steps: List<AnimationStep<TilePose>>) {
        tile.enqueueAll(steps.map { it.toEntityStep() })
    }

    /**
     * 把共用姿態的步驟轉成實體使用的姿態步驟。
     *
     * @return 實體的動畫步驟。
     */
    private fun AnimationStep<TilePose>.toEntityStep(): AnimationStep<MahjongTilePose> = when (this) {
        is AnimationStep.Custom -> AnimationStep.Custom(step.toTilePose())
        is AnimationStep.WaitUntil -> this
        is AnimationStep.Teleport -> this
        is AnimationStep.SetInvisible -> this
        is AnimationStep.PlaySound -> this
        is AnimationStep.PlayMotion -> this
    }

    /** 從目的地正上方落下，在既定桌面格位結束。 */
    private fun dropMotion(height: Double, pose: MahjongTilePose): AnimationStep.PlayMotion = AnimationStep.PlayMotion(
        durationTicks = MahjongTileTableLayout.DRAW_DROP_DURATION_TICKS,
        arcHeight = 0.0,
        startOffsetX = 0.0,
        startOffsetY = height,
        startOffsetZ = 0.0,
        startPoseRotationDegrees = pose.rotationDegrees,
        endPoseRotationDegrees = pose.rotationDegrees,
    )

    /** 把實體使用的姿態轉成共用的牌姿態。 */
    private fun MahjongTilePose.toSharedPose(): TilePose = when (this) {
        MahjongTilePose.STANDING -> TilePose.UPRIGHT
        MahjongTilePose.FACE_UP -> TilePose.FACE_UP
        MahjongTilePose.FACE_DOWN -> TilePose.FACE_DOWN
    }

    /** 把共用的牌姿態轉成實體使用的姿態。 */
    private fun TilePose.toTilePose(): MahjongTilePose = when (this) {
        TilePose.UPRIGHT -> MahjongTilePose.STANDING
        TilePose.FACE_UP -> MahjongTilePose.FACE_UP
        TilePose.FACE_DOWN -> MahjongTilePose.FACE_DOWN
    }

    /** 捨牌與鳴牌落地牌面提示的總顯示時間。 */
    const val ACTION_POPUP_DURATION_TICKS = 24L

    /**
     * 在攤牌展示的指定時刻，將牌瞬間放到整理後格位，不播放滑動。
     *
     * 只改位置與朝向，不改牌姿態。後續倒牌或蓋牌仍由各自的動畫步驟處理。
     *
     * @param tile 要重排的牌 entity。
     * @param finalPlacement 整理後的座標與朝向。
     * @param startGameTime 這批牌共用的絕對開始 tick。
     */
    fun scheduleInstantReorder(
        tile: MahjongTileEntity,
        finalPlacement: MahjongTileWallPlacement,
        startGameTime: Long,
    ) {
        tile.enqueueAll(instantReorderSteps(finalPlacement, startGameTime))
    }

    /**
     * 建立可持久化的攤牌排序步驟，只等待指定時刻並瞬間定位。
     *
     * @param finalPlacement 整理後的座標與朝向。
     * @param startGameTime 同組牌共用的絕對開始 tick。
     * @return 不含位移或姿態補間的排序步驟。
     */
    fun instantReorderSteps(
        finalPlacement: MahjongTileWallPlacement,
        startGameTime: Long,
    ): List<AnimationStep<MahjongTilePose>> = HandRevealSteps.reorder(finalPlacement, startGameTime).map { it.toEntityStep() }

    /**
     * 「倒牌」的單張牌動畫（胡牌慶祝演出）：姿態從佇列最後排定的姿態轉平放牌面朝上，位置完全不動，只有姿態旋轉角
     * 隨動畫進度內插——硬性限制要求所有姿態轉換都要走 [AnimationStep.Custom] + [AnimationStep.PlayMotion]，
     * 不直接賦值 [MahjongTileEntity.tilePose]。[startGameTime] 是呼叫端算好、同一批牌共用同一份的絕對
     * 開始時刻，與 [scheduleInstantReorder] 使用同一條時間軸。
     *
     * @param tile 要翻倒的牌 entity。
     * @param startGameTime 同組牌共用的絕對開始 tick。
     * @param playGroupSound 是否為整組播放一次翻牌聲。
     */
    fun scheduleLaydown(tile: MahjongTileEntity, startGameTime: Long, playGroupSound: Boolean = false) {
        tile.enqueueAll(laydownSteps(tile.queuedTilePose, startGameTime, playGroupSound))
    }

    /**
     * 為同一組牌排定倒牌，整組只播放一次翻牌聲。
     *
     * @param tiles 同組要倒下的牌，依呈現順序排列。
     * @param startGameTime 同組牌共用的絕對開始 tick。
     */
    fun scheduleGroupLaydown(tiles: List<MahjongTileEntity>, startGameTime: Long) {
        val soundIndex = groupLaydownSoundIndex(tiles.map { it.queuedTilePose })
        tiles.forEachIndexed { index, tile -> scheduleLaydown(tile, startGameTime, playGroupSound = index == soundIndex) }
    }

    /**
     * 選出負責播放整組翻牌聲的牌：在這次真的要倒下的牌中取中間那張，已平放的牌不參與。
     *
     * @param startPoses 各張牌既有動畫結束後的姿態，依呈現順序排列。
     * @return 負責播放聲音的牌在清單中的位置。全部已平放時為 null。
     */
    internal fun groupLaydownSoundIndex(startPoses: List<MahjongTilePose>): Int? = HandRevealSteps.groupSoundIndex(startPoses.map { it.toSharedPose() })

    /**
     * 從已排定的姿態建立倒牌步驟。已平放時不增加旋轉或翻牌聲。
     *
     * @param startPose 既有動畫結束後的姿態。
     * @param startGameTime 倒牌開始的絕對遊戲時間。
     * @param playGroupSound 是否播放整組翻牌聲。
     * @return 必要的倒牌步驟。已正面朝上時為空清單。
     */
    fun laydownSteps(
        startPose: MahjongTilePose,
        startGameTime: Long,
        playGroupSound: Boolean,
    ): List<AnimationStep<MahjongTilePose>> = HandRevealSteps.laydown(startPose.toSharedPose(), startGameTime, playGroupSound).map { it.toEntityStep() }

    /**
     * 流局時將未公開手牌由目前姿態平滑蓋成牌背朝上。姿態與動畫步驟一起寫入 entity NBT，重新載入
     * 世界後會接續尚未完成的旋轉，不會瞬間跳到終點。
     *
     * @param tile 要蓋回牌背的牌 entity。
     * @param startGameTime 同組牌共用的絕對開始 tick。
     * @param playGroupSound 是否為整組播放一次翻牌聲。
     */
    fun scheduleConceal(tile: MahjongTileEntity, startGameTime: Long, playGroupSound: Boolean = false) {
        tile.enqueueAll(HandRevealSteps.conceal(tile.tilePose.toSharedPose(), startGameTime, playGroupSound).map { it.toEntityStep() })
    }

    /** 開局發牌動畫起飛階段的相對高度，比照 `EntityMahjongTileWallPresenter.WALL_DROP_HEIGHT`。 */
    private const val DEAL_LIFT_HEIGHT: Double = 0.4

    /**
     * 開局發牌動畫全部播完（含翻牌）後，額外掛在每張牌佇列尾端的觀看緩衝，讓桌子在玩家真正看清楚
     * 手牌之前持續維持「還在忙」，理由見 `EntityMahjongPlayerAreaPresenter.presentInitialDeal` KDoc。
     */
    private const val DEAL_VIEWING_BUFFER_TICKS: Int = 25

    /**
     * 建立只在預定時刻附近有效的整組手牌翻轉聲音 step。
     *
     * @param gameTime 預定播放的絕對遊戲 tick。
     * @return 帶播放與過期時刻的手牌翻轉音效步驟。
     */
    private fun handTurnSound(gameTime: Long): AnimationStep.PlaySound = AnimationStep.PlaySound(
        soundId = MahjongAnimationSounds.TILE_HAND_TURN,
        volume = 1.2f,
        pitch = 1.0f,
        playAtGameTime = gameTime,
        expiresAtGameTime = gameTime + MahjongAnimationSounds.EVENT_GRACE_TICKS,
    )

    /**
     * 建立只在預定時刻附近有效的開局發牌批次聲音 step。
     *
     * @param gameTime 預定播放的絕對遊戲 tick。
     * @return 帶播放與過期時刻的發牌音效步驟。
     */
    private fun dealBatchSound(gameTime: Long): AnimationStep.PlaySound = AnimationStep.PlaySound(
        soundId = MahjongAnimationSounds.DEAL_BATCH,
        volume = 0.5f,
        pitch = 1.15f,
        playAtGameTime = gameTime,
        expiresAtGameTime = gameTime + MahjongAnimationSounds.EVENT_GRACE_TICKS,
    )
}
