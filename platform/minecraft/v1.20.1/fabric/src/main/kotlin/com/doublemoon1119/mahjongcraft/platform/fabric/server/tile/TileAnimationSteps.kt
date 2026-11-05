package com.doublemoon1119.mahjongcraft.platform.fabric.server.tile

import com.doublemoon1119.mahjongcraft.logic.tile.component.TilePose
import com.doublemoon1119.mahjongcraft.logic.tile.component.TileRegion
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.AnimatedMahjongEntity
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MahjongAnimationSounds
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MahjongTileEntity
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MahjongTilePose
import com.doublemoon1119.mahjongcraft.platform.minecraft.animation.AnimationStep
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongTileTableLayout
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongTileWallPlacement
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.PlannedTileMotion
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TableStepMotionPlanner
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileMotionKind

/**
 * 麻將牌各種既有動畫（開局發牌、摸牌、捨牌、鳴牌、胡牌慶祝演出的強制理牌重排／倒牌）的單張牌排程
 * 邏輯，由正式桌子的呈現（[FabricMahjongPlayerAreaPresenter] 與依步驟呈現的發布器）
 * 與 debug 動畫指令共用；不帶任何實例狀態。這些函式只吃 `tile`／位置／絕對時刻這幾個純粹的參數，
 * 不涉及桌子座標系、`TableLocation` 或任何對局概念。
 *
 * `FabricDebugCommand` 的動畫測試指令群組在不掛任何桌子／對局的臨時牌 entity 上重播同樣的動畫，
 * 讓開發者不必建房、加入、開局就能預覽演出效果；兩邊共用同一份實作，動畫調整時不會不同步。
 * `internal`：只給同模組內的呼叫端使用，不對外公開成正式 API。
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
     * [finalPlacement] 的高度：同一批兩敦牌的上下兩層高度不同，若起飛終點固定用同一個絕對高度，上下兩層
     * 會在起飛途中收斂到同一個高度，看起來像下層那張牌憑空消失；相對自己原高度往上抬固定量，兩層之間的
     * 相對高度差在起飛階段維持不變。
     *
     * 起飛播完的那一刻，隱形與瞬間重新排列到手牌列上空是同一個瞬間發生；接著額外維持
     * [MahjongTileTableLayout.DEAL_SNAP_GAP_TICKS] 的隱形，讓「重新排成一列」感覺像是刻意的一個轉場
     * 動作，不是無縫瞬移。翻牌後在觀看緩衝（[DEAL_VIEWING_BUFFER_TICKS]）播完那一刻，才傳送到
     * [postFlipPlacement]（可能與 [finalPlacement] 不同格，見 `MahjongInitialDealPresentation` KDoc）。
     *
     * @param tile 要排定發牌動畫的牌 entity。
     * @param finalPlacement 翻牌前落下的位置。
     * @param postFlipPlacement 觀看緩衝結束後的位置。
     * @param liftAbsoluteGameTime 這批牌共用的起飛絕對 tick。
     * @param flipAbsoluteGameTime 這批牌共用的翻牌絕對 tick。
     * @param playDealSound 是否為此牌播放發牌聲。
     * @param playFlipSound 是否為此牌播放翻牌聲。
     * @param endPose 翻牌動畫結束後的姿態；一般發牌為直立。
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
            buildList {
                add(AnimationStep.WaitUntil(liftAbsoluteGameTime))
                if (playDealSound) add(dealBatchSound(liftAbsoluteGameTime))
                addAll(
                    listOf(
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
                add(AnimationStep.WaitUntil(flipAbsoluteGameTime))
                if (playFlipSound) add(handTurnSound(flipAbsoluteGameTime))
                addAll(
                    listOf(
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
                        AnimationStep.Teleport(
                            postFlipPlacement.x,
                            postFlipPlacement.y,
                            postFlipPlacement.z,
                            postFlipPlacement.yaw,
                        ),
                    ),
                )
            },
        )
    }

    /**
     * 在 [startGameTime] 播放一步之中規劃好的動作；起點使用規劃的位置與姿態，不讀 entity 目前的位置，所以前面還有
     * 動畫在排隊時也會從那些動畫結束的位置接著動。
     *
     * 摸牌抬起後隱形移到新位置再落下；拋物線與弧線是一段連續可見的飛行；平移沒有弧線；翻面只轉姿態。落地時依目的區域播放
     * 對應的落桌聲：牌河為捨牌聲，副露與移出區為副露聲，從牌牆摸到立牌或摸牌位為摸牌聲。
     *
     * @param tile 要播放動作的牌 entity。
     * @param motion 規劃好的動作。
     * @param startGameTime 動作開始的絕對遊戲 tick。
     * @param playLandingSound 是否播放落地聲；同一組一起落地的牌只需要其中一張播放。
     */
    fun schedulePlannedMotion(tile: MahjongTileEntity, motion: PlannedTileMotion, startGameTime: Long, playLandingSound: Boolean = true) {
        val to = motion.to
        val toPose = motion.toPose.toTilePose()
        val from = motion.from
        val fromPose = motion.fromPose?.toTilePose() ?: toPose
        val steps = mutableListOf<AnimationStep<MahjongTilePose>>(AnimationStep.WaitUntil(startGameTime))
        val endGameTime = startGameTime + TableStepMotionPlanner.durationTicks(motion.kind)
        when (motion.kind) {
            TileMotionKind.PLACE -> {
                steps += AnimationStep.Teleport(to.x, to.y, to.z, to.yaw)
                steps += AnimationStep.Custom(toPose)
            }
            TileMotionKind.DRAW -> {
                val origin = checkNotNull(from)
                val peakY = origin.y + MahjongTileTableLayout.DRAW_LIFT_HEIGHT
                steps += AnimationStep.Teleport(origin.x, peakY, origin.z, origin.yaw)
                steps += AnimationStep.Custom(fromPose)
                steps += AnimationStep.PlayMotion(
                    durationTicks = MahjongTileTableLayout.DRAW_LIFT_DURATION_TICKS,
                    arcHeight = 0.0,
                    startOffsetX = 0.0,
                    startOffsetY = origin.y - peakY,
                    startOffsetZ = 0.0,
                    startPoseRotationDegrees = fromPose.rotationDegrees,
                    endPoseRotationDegrees = fromPose.rotationDegrees,
                )
                steps += AnimationStep.SetInvisible(true)
                steps += AnimationStep.Teleport(to.x, to.y, to.z, to.yaw)
                steps += AnimationStep.Custom(toPose)
                steps += AnimationStep.WaitUntil(
                    startGameTime + MahjongTileTableLayout.DRAW_LIFT_DURATION_TICKS + MahjongTileTableLayout.DRAW_SNAP_GAP_TICKS,
                )
                steps += AnimationStep.SetInvisible(false)
                steps += AnimationStep.PlayMotion(
                    durationTicks = MahjongTileTableLayout.DRAW_DROP_DURATION_TICKS,
                    arcHeight = 0.0,
                    startOffsetX = 0.0,
                    startOffsetY = peakY - to.y,
                    startOffsetZ = 0.0,
                    startPoseRotationDegrees = toPose.rotationDegrees,
                    endPoseRotationDegrees = toPose.rotationDegrees,
                )
            }
            TileMotionKind.FLIGHT, TileMotionKind.SLIDE, TileMotionKind.ARC -> {
                val origin = checkNotNull(from)
                steps += AnimationStep.Teleport(to.x, to.y, to.z, to.yaw)
                steps += AnimationStep.Custom(toPose)
                steps += AnimationStep.PlayMotion(
                    durationTicks = TableStepMotionPlanner.durationTicks(motion.kind),
                    arcHeight = when (motion.kind) {
                        TileMotionKind.FLIGHT -> MahjongTileTableLayout.DISCARD_ARC_HEIGHT
                        TileMotionKind.ARC -> MahjongTileTableLayout.ROW_ARC_HEIGHT
                        else -> 0.0
                    },
                    startOffsetX = origin.x - to.x,
                    startOffsetY = origin.y - to.y,
                    startOffsetZ = origin.z - to.z,
                    startPoseRotationDegrees = fromPose.rotationDegrees,
                    endPoseRotationDegrees = toPose.rotationDegrees,
                    liftCarryLower = motion.kind == TileMotionKind.ARC,
                    leanDegrees = if (motion.kind == TileMotionKind.ARC && toPose == MahjongTilePose.STANDING) {
                        MahjongTileTableLayout.ROW_ARC_LEAN_DEGREES
                    } else {
                        0.0f
                    },
                )
            }
            TileMotionKind.TURN -> {
                steps += AnimationStep.Custom(toPose)
                steps += AnimationStep.PlayMotion(
                    durationTicks = TableStepMotionPlanner.durationTicks(motion.kind),
                    arcHeight = 0.0,
                    startOffsetX = 0.0,
                    startOffsetY = 0.0,
                    startOffsetZ = 0.0,
                    startPoseRotationDegrees = fromPose.rotationDegrees,
                    endPoseRotationDegrees = toPose.rotationDegrees,
                )
            }
        }
        if (playLandingSound) landingSound(motion, endGameTime)?.let(steps::add)
        tile.enqueueAll(steps)
    }

    /**
     * 依動作與目的區域選擇落地聲；同一排內的平移、弧線、翻面與直接擺放沒有落地聲。
     *
     * @param motion 規劃好的動作。
     * @param gameTime 落地的絕對遊戲 tick。
     * @return 落地聲；不需要時為 null。
     */
    private fun landingSound(motion: PlannedTileMotion, gameTime: Long): AnimationStep.PlaySound? = when (motion.kind) {
        TileMotionKind.DRAW -> drawnTileLandingSound(gameTime)
        TileMotionKind.FLIGHT -> if (motion.toRow.region == TileRegion.DISCARD) discardLandingSound(gameTime) else meldLandingSound(gameTime)
        TileMotionKind.PLACE, TileMotionKind.SLIDE, TileMotionKind.ARC, TileMotionKind.TURN -> null
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
     * 只改位置與朝向，不改牌姿態；後續倒牌或蓋牌仍由各自的動畫步驟處理。
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
    ): List<AnimationStep<MahjongTilePose>> = listOf(
        AnimationStep.WaitUntil(startGameTime),
        AnimationStep.Teleport(finalPlacement.x, finalPlacement.y, finalPlacement.z, finalPlacement.yaw),
    )

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
     * @return 負責播放聲音的牌在清單中的位置；全部已平放時為 null。
     */
    internal fun groupLaydownSoundIndex(startPoses: List<MahjongTilePose>): Int? {
        val turning = startPoses.indices.filter { startPoses[it] != MahjongTilePose.FACE_UP }
        return turning.getOrNull(turning.size / 2)
    }

    /**
     * 從已排定的姿態建立倒牌步驟；已平放時不增加旋轉或翻牌聲。
     *
     * @param startPose 既有動畫結束後的姿態。
     * @param startGameTime 倒牌開始的絕對遊戲時間。
     * @param playGroupSound 是否播放整組翻牌聲。
     * @return 必要的倒牌步驟；已正面朝上時為空清單。
     */
    fun laydownSteps(
        startPose: MahjongTilePose,
        startGameTime: Long,
        playGroupSound: Boolean,
    ): List<AnimationStep<MahjongTilePose>> {
        if (startPose == MahjongTilePose.FACE_UP) return emptyList()
        return buildList {
            if (playGroupSound) {
                add(handTurnSound(startGameTime + MahjongTileTableLayout.WIN_LAYDOWN_DURATION_TICKS))
            }
            addAll(
                listOf(
                    AnimationStep.WaitUntil(startGameTime),
                    AnimationStep.Custom(MahjongTilePose.FACE_UP),
                    AnimationStep.PlayMotion(
                        durationTicks = MahjongTileTableLayout.WIN_LAYDOWN_DURATION_TICKS,
                        arcHeight = 0.0,
                        startOffsetX = 0.0,
                        startOffsetY = 0.0,
                        startOffsetZ = 0.0,
                        startPoseRotationDegrees = startPose.rotationDegrees,
                        endPoseRotationDegrees = MahjongTilePose.FACE_UP.rotationDegrees,
                    ),
                ),
            )
        }
    }

    /**
     * 流局時將未公開手牌由目前姿態平滑蓋成牌背朝上。姿態與動畫步驟一起寫入 entity NBT，重新載入
     * 世界後會接續尚未完成的旋轉，不會瞬間跳到終點。
     *
     * @param tile 要蓋回牌背的牌 entity。
     * @param startGameTime 同組牌共用的絕對開始 tick。
     * @param playGroupSound 是否為整組播放一次翻牌聲。
     */
    fun scheduleConceal(tile: MahjongTileEntity, startGameTime: Long, playGroupSound: Boolean = false) {
        val startPose = tile.tilePose
        tile.enqueueAll(
            buildList {
                if (playGroupSound) {
                    add(handTurnSound(startGameTime + MahjongTileTableLayout.WIN_LAYDOWN_DURATION_TICKS))
                }
                addAll(
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
            },
        )
    }

    /** 開局發牌動畫起飛階段的相對高度，理由同 `FabricMahjongTileWallPresenter.WALL_DROP_HEIGHT`——起始估算值。 */
    private const val DEAL_LIFT_HEIGHT: Double = 0.4

    /**
     * 開局發牌動畫全部播完（含翻牌）後，額外掛在每張牌佇列尾端的觀看緩衝，讓桌子在玩家真正看清楚
     * 手牌之前持續維持「還在忙」，理由見 `FabricMahjongPlayerAreaPresenter.presentInitialDeal` KDoc。
     */
    private const val DEAL_VIEWING_BUFFER_TICKS: Int = 25

    /**
     * 建立只在預定時刻附近有效的捨牌落桌聲音 step。
     *
     * @param gameTime 預定播放的絕對遊戲 tick。
     * @return 帶播放與過期時刻的捨牌音效步驟。
     */
    private fun discardLandingSound(gameTime: Long): AnimationStep.PlaySound = AnimationStep.PlaySound(
        soundId = MahjongAnimationSounds.TILE_DISCARD_LAND,
        volume = 1.05f,
        pitch = 1.0f,
        playAtGameTime = gameTime,
        expiresAtGameTime = gameTime + MahjongAnimationSounds.EVENT_GRACE_TICKS,
    )

    /**
     * 建立只在預定時刻附近有效的副露落桌聲音 step。
     *
     * @param gameTime 預定播放的絕對遊戲 tick。
     * @return 帶播放與過期時刻的副露音效步驟。
     */
    private fun meldLandingSound(gameTime: Long): AnimationStep.PlaySound = AnimationStep.PlaySound(
        soundId = MahjongAnimationSounds.TILE_MELD_LAND,
        volume = 1.1f,
        pitch = 1.0f,
        playAtGameTime = gameTime,
        expiresAtGameTime = gameTime + MahjongAnimationSounds.EVENT_GRACE_TICKS,
    )

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

    /**
     * 建立只在摸牌抵達右側摸牌位時播放的低音量聲音 step。
     *
     * @param gameTime 摸牌抵達時的絕對遊戲 tick。
     * @return 帶播放與過期時刻的摸牌音效步驟。
     */
    private fun drawnTileLandingSound(gameTime: Long): AnimationStep.PlaySound = AnimationStep.PlaySound(
        soundId = MahjongAnimationSounds.DRAW_TILE_LAND,
        volume = 0.35f,
        pitch = 1.1f,
        playAtGameTime = gameTime,
        expiresAtGameTime = gameTime + MahjongAnimationSounds.EVENT_GRACE_TICKS,
    )
}
