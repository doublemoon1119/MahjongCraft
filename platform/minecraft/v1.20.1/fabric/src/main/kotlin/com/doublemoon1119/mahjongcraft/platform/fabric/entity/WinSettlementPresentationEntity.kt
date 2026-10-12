package com.doublemoon1119.mahjongcraft.platform.fabric.entity

import com.doublemoon1119.mahjongcraft.platform.fabric.registry.ModEntities
import com.doublemoon1119.mahjongcraft.platform.minecraft.animation.AnimationStep
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.PresentationLayout
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.WinSettlementPresentationTemplateRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.WinSettlementRevealTimeline
import net.minecraft.entity.EntityType
import net.minecraft.entity.data.DataTracker
import net.minecraft.entity.data.TrackedDataHandlerRegistry
import net.minecraft.nbt.NbtCompound
import net.minecraft.world.World
import org.koin.core.context.GlobalContext
import kotlin.uuid.Uuid

/**
 * 持久化胡牌詳情與最終排行的世界舞台。
 *
 * @param type 和牌結算實體種類。
 * @param world 實體所屬世界。
 */
class WinSettlementPresentationEntity(
    type: EntityType<out WinSettlementPresentationEntity> = ModEntities.winSettlementPresentation,
    world: World,
) : SimpleAnimatedMahjongEntity(type, world),
    TableOwnedPresentationEntity {
    /** 所屬麻將桌；資料未設定或損壞時為 `null`。 */
    override val managedTableId: Uuid? get() = dataTracker[TABLE_ID].takeIf(String::isNotBlank)?.let { runCatching { Uuid.parse(it) }.getOrNull() }

    /** 舞台開始的絕對遊戲時間。 */
    val startGameTime: Long get() = dataTracker[START_GAME_TIME]

    /** 舞台結束的絕對遊戲時間。 */
    val endGameTime: Long get() = dataTracker[END_GAME_TIME]

    /** 本次結算的結果識別碼。 */
    val outcomeId: String get() = dataTracker[OUTCOME_ID]

    /** 決定結算版面的模板 key。 */
    val templateKey: String get() = dataTracker[TEMPLATE_KEY]

    /** 本次和牌是否為自摸。 */
    val isTsumo: Boolean get() = dataTracker[IS_TSUMO]

    /** 贏家詳情快照；精簡中途胡牌演出可為空。 */
    val winners: List<WinSettlementWinnerSnapshot> get() = decodeWinners(dataTracker[WINNERS])

    /** 結算前後的玩家分數與排行快照。 */
    val rankings: List<WinSettlementRankingSnapshot> get() = WinSettlementRankingCodec.decode(dataTracker[RANKINGS])

    /** 模板指定的贏家詳情揭示節奏。 */
    val revealTiming: WinSettlementRevealTimingSnapshot get() = decodeRevealTiming(dataTracker[REVEAL_TIMING])

    /** 覆蓋預設贏家詳情音效的自訂 cue；空集合時使用預設 cue。 */
    val customSoundCues: List<WinSettlementSoundCueSnapshot> get() = decodeSoundCues(dataTracker[CUSTOM_SOUND_CUES])

    init {
        setNoGravity(true)
    }

    /**
     * 設定這面結算面板的完整內容。
     *
     * [winners] **允許為空**：中途胡牌（本局在胡牌後仍繼續）用的精簡收尾會跳過贏家段，面板一開場就
     * 是分數變動動畫（見 `WinSettlementPresentationRequest.includesWinnerDetails` 為 false）。此時 [rankingStartTick] 為 0，
     * client 端 renderer 的 `elapsed < rankingStart` 分支永遠不成立，贏家段的繪製不會被進入。
     * [rankings] 則一定要有內容——分數變動是這面面板唯一不可省略的部分。
     *
     * @param tableId 所屬麻將桌 UUID。
     * @param startGameTime 舞台開始的絕對遊戲時間。
     * @param outcomeId 和牌結果識別碼。
     * @param templateKey 結算呈現模板 key。
     * @param isTsumo 本次是否為自摸。
     * @param winners 贏家詳情快照；可為空。
     * @param rankings 結算前後分數及排行；不可為空。
     * @param revealTiming 贏家詳情揭示節奏。
     * @param customSoundCues 自訂音效 cue；空集合時使用內建 cue。
     */
    fun configure(
        tableId: Uuid,
        startGameTime: Long,
        outcomeId: String,
        templateKey: String,
        isTsumo: Boolean,
        winners: List<WinSettlementWinnerSnapshot>,
        rankings: List<WinSettlementRankingSnapshot>,
        revealTiming: WinSettlementRevealTimingSnapshot,
        customSoundCues: List<WinSettlementSoundCueSnapshot>,
    ) {
        check(!world.isClient)
        require(rankings.isNotEmpty()) { "A settlement panel must always show the score changes" }
        dataTracker.set(TABLE_ID, tableId.toString())
        dataTracker.set(START_GAME_TIME, startGameTime)
        dataTracker.set(REVEAL_TIMING, encodeRevealTiming(revealTiming))
        dataTracker.set(CUSTOM_SOUND_CUES, encodeSoundCues(customSoundCues))
        dataTracker.set(END_GAME_TIME, startGameTime + durationTicks(winners, revealTiming, scoreRevealDelayTicks(layoutOf(templateKey))))
        dataTracker.set(OUTCOME_ID, outcomeId)
        dataTracker.set(TEMPLATE_KEY, templateKey)
        dataTracker.set(IS_TSUMO, isTsumo)
        dataTracker.set(WINNERS, encodeWinners(winners))
        dataTracker.set(RANKINGS, WinSettlementRankingCodec.encode(rankings))
        enqueueSettlementSounds(startGameTime)
    }

    /**
     * 取得含 partial tick 的相對演出時間。
     *
     * @param tickDelta 本 tick 的部分經過時間。
     * @return 從舞台開始起算、包含部分 tick 的相對時間。
     */
    fun elapsedTicks(tickDelta: Float): Double = world.time + tickDelta.toDouble() - startGameTime

    /**
     * 取得指定贏家在目前揭示序列中的開始 tick。
     *
     * @param index 贏家序列索引。
     * @return 該贏家揭示段落開始的相對 tick。
     */
    fun winnerStartTick(index: Int): Long = winners.take(index).sumOf(::winnerDurationTicks)

    /**
     * 計算贏家詳情全部結束、排行榜開始的相對 tick。
     *
     * @return 排行榜揭示段落的開始 tick。
     */
    fun rankingStartTick(): Long = winners.sumOf(::winnerDurationTicks)

    /**
     * 依目前揭示節奏與模板版面計算贏家段長度。
     *
     * @param winner 贏家快照。
     * @return 該贏家詳情揭示段落所需的 tick 數。
     */
    fun winnerDurationTicks(winner: WinSettlementWinnerSnapshot): Long = winnerDurationTicks(winner, revealTiming, scoreRevealDelayTicks(layoutOf(templateKey)))

    /** 在結算演出到達結束時間後由伺服器移除此實體。 */
    override fun tick() {
        super.tick()
        if (world.isClient) return
        if (endGameTime > startGameTime && world.time >= endGameTime) discard()
    }

    /**
     * 將贏家詳情、自訂 cue 與排行聲音統一排入可持久化動畫佇列。
     *
     * @param startGameTime 舞台開始的絕對遊戲時間。
     */
    private fun enqueueSettlementSounds(startGameTime: Long) {
        val cues = if (customSoundCues.isNotEmpty()) customSoundCues else defaultWinnerSoundCues()
        enqueueAll((cues + rankingSoundCues()).map { cue -> cue.toAnimationStep(startGameTime) })
    }

    /**
     * 建立沒有自訂聲音模板時的既有贏家詳情聲音。
     *
     * @return 依贏家詳情與分數揭示時間產生的預設音效 cue。
     */
    private fun defaultWinnerSoundCues(): List<WinSettlementSoundCueSnapshot> = buildList {
        winners.forEachIndexed { winnerIndex, winner ->
            val winnerStart = winnerStartTick(winnerIndex)
            val entries = winner.details.filter { it.type == DETAIL_ENTRIES }.sumOf { it.values.size / ENTRY_VALUE_COUNT }
            val layout = layoutOf(templateKey)
            val afterEntriesTick = winnerStart + revealTiming.initialFadeTicks + entries * revealTiming.entryStaggerTicks
            // 役種每一條出現時響一聲；版面上排在逐條揭示之後的每一行（例如翻符）出現時也各響一聲，音高接續往上。
            val entryBeatTicks = List(entries) { entryIndex ->
                winnerStart + revealTiming.initialFadeTicks + entryIndex * revealTiming.entryStaggerTicks
            }
            val lineBeatTicks = layout
                ?.let { WinSettlementRevealTimeline.lineRevealDelaysTicks(it, winner.presentFieldIds) }
                .orEmpty()
                .map { delay -> afterEntriesTick + delay }
            (entryBeatTicks + lineBeatTicks).forEachIndexed { beatIndex, eventTick ->
                add(
                    WinSettlementSoundCueSnapshot(
                        eventTick = eventTick,
                        soundId = DETAIL_SOUND_ID,
                        volume = SettlementPresentationSoundSpec.DETAIL_VOLUME,
                        pitch = (
                            SettlementPresentationSoundSpec.DETAIL_BASE_PITCH +
                                beatIndex * SettlementPresentationSoundSpec.DETAIL_PITCH_STEP
                            ).coerceAtMost(1.55f),
                    ),
                )
            }
            val scoreTick = afterEntriesTick + scoreRevealDelayTicks(layout)
            SettlementPresentationSoundSpec.TOTAL_SCORE_MELODY_PITCHES.forEachIndexed { noteIndex, pitch ->
                add(
                    WinSettlementSoundCueSnapshot(
                        eventTick = scoreTick + noteIndex * SettlementPresentationSoundSpec.TOTAL_SCORE_MELODY_INTERVAL_TICKS,
                        soundId = DETAIL_SOUND_ID,
                        volume = SettlementPresentationSoundSpec.TOTAL_SCORE_VOLUME,
                        pitch = pitch,
                    ),
                )
            }
        }
    }

    /**
     * 建立分數排行逐列揭示及排行落定聲音。
     *
     * @return 每列揭示 cue，以及排名有變動時的落定 cue。
     */
    private fun rankingSoundCues(): List<WinSettlementSoundCueSnapshot> = buildList {
        val rankingStart = rankingStartTick()
        rankings.indices.forEach { rowIndex ->
            add(
                WinSettlementSoundCueSnapshot(
                    eventTick = rankingStart + RANKING_ROW_START_TICKS + rowIndex * RANKING_ROW_STAGGER_TICKS,
                    soundId = DETAIL_SOUND_ID,
                    volume = SettlementPresentationSoundSpec.ROW_VOLUME,
                    pitch = SettlementPresentationSoundSpec.ROW_BASE_PITCH +
                        rowIndex * SettlementPresentationSoundSpec.ROW_PITCH_STEP,
                ),
            )
        }
        if (rankings.any { it.previousRank != it.currentRank }) {
            add(
                WinSettlementSoundCueSnapshot(
                    eventTick = rankingStart + RANKING_SETTLED_SOUND_TICKS,
                    soundId = RANKING_SETTLED_SOUND_ID,
                    volume = SettlementPresentationSoundSpec.RANKING_SETTLED_VOLUME,
                    pitch = SettlementPresentationSoundSpec.RANKING_SETTLED_PITCH,
                ),
            )
        }
    }

    /**
     * 將相對 presentation 時間的聲音 cue 轉為絕對動畫 step。
     *
     * @param startGameTime 舞台開始的絕對遊戲時間。
     * @return 具有絕對播放與到期時間的音效動畫步驟。
     */
    private fun WinSettlementSoundCueSnapshot.toAnimationStep(startGameTime: Long): AnimationStep.PlaySound {
        val playAtGameTime = startGameTime + eventTick
        return AnimationStep.PlaySound(
            soundId = soundId,
            volume = volume,
            pitch = pitch,
            playAtGameTime = playAtGameTime,
            expiresAtGameTime = playAtGameTime + SettlementPresentationSoundSpec.EVENT_GRACE_TICKS,
        )
    }

    /** 結算舞台不提供碰撞。 */
    override fun isCollidable() = false

    /** 結算舞台不參與推擠。 */
    override fun isPushable() = false

    /** 結算舞台不能被選取。 */
    override fun canHit() = false

    override fun initDataTracker() {
        dataTracker.startTracking(TABLE_ID, "")
        dataTracker.startTracking(START_GAME_TIME, 0L)
        dataTracker.startTracking(END_GAME_TIME, 0L)
        dataTracker.startTracking(OUTCOME_ID, "")
        dataTracker.startTracking(TEMPLATE_KEY, "")
        dataTracker.startTracking(IS_TSUMO, false)
        dataTracker.startTracking(WINNERS, "")
        dataTracker.startTracking(RANKINGS, "")
        dataTracker.startTracking(REVEAL_TIMING, "")
        dataTracker.startTracking(CUSTOM_SOUND_CUES, "")
    }

    /**
     * 從 NBT 還原結算舞台追蹤資料與動畫佇列。
     *
     * @param nbt 儲存此實體資料的 NBT 複合標籤。
     */
    override fun readCustomDataFromNbt(nbt: NbtCompound) {
        dataTracker.set(TABLE_ID, nbt.getString("ManagedTableId"))
        dataTracker.set(START_GAME_TIME, nbt.getLong("StartGameTime"))
        dataTracker.set(END_GAME_TIME, nbt.getLong("EndGameTime"))
        dataTracker.set(OUTCOME_ID, nbt.getString("OutcomeId"))
        dataTracker.set(TEMPLATE_KEY, nbt.getString("TemplateKey"))
        dataTracker.set(IS_TSUMO, nbt.getBoolean("IsTsumo"))
        dataTracker.set(WINNERS, nbt.getString("Winners"))
        dataTracker.set(RANKINGS, nbt.getString("Rankings"))
        dataTracker.set(REVEAL_TIMING, nbt.getString("RevealTiming"))
        dataTracker.set(CUSTOM_SOUND_CUES, nbt.getString("CustomSoundCues"))
        readAnimationQueueFromNbt(nbt)
    }

    /**
     * 將結算舞台追蹤資料與動畫佇列寫入 NBT。
     *
     * @param nbt 接收此實體資料的 NBT 複合標籤。
     */
    override fun writeCustomDataToNbt(nbt: NbtCompound) {
        nbt.putString("ManagedTableId", managedTableId?.toString().orEmpty())
        nbt.putLong("StartGameTime", startGameTime)
        nbt.putLong("EndGameTime", endGameTime)
        nbt.putString("OutcomeId", outcomeId)
        nbt.putString("TemplateKey", templateKey)
        nbt.putBoolean("IsTsumo", isTsumo)
        nbt.putString("Winners", dataTracker[WINNERS])
        nbt.putString("Rankings", dataTracker[RANKINGS])
        nbt.putString("RevealTiming", dataTracker[REVEAL_TIMING])
        nbt.putString("CustomSoundCues", dataTracker[CUSTOM_SOUND_CUES])
        writeAnimationQueueToNbt(nbt)
    }

    companion object {
        /** 結算舞台的非零寬度。 */
        const val WIDTH = 6f

        /** 結算舞台的非零高度。 */
        const val HEIGHT = 4f

        /** 揭示序列開始時的淡入 tick 數。 */
        const val INITIAL_FADE_TICKS = 16L

        /** 相鄰詳情條目揭示的間隔 tick 數。 */
        const val ENTRY_STAGGER_TICKS = 8L

        /** 分數揭示動畫的 tick 數。 */
        const val SCORE_REVEAL_TICKS = 18L

        /** 分數揭示後保留畫面的 tick 數。 */
        const val READING_TICKS = 60L

        /** 贏家段落與排行榜段落間的轉場 tick 數。 */
        const val TRANSITION_TICKS = 12L

        /** 排行榜段落的固定 tick 數。 */
        const val RANKING_TICKS = 150L

        /** 舞台結束前的淡出 tick 數。 */
        const val FADE_OUT_TICKS = 18L

        /** 第一列排行揭示相對排行榜開始的延遲 tick 數。 */
        const val RANKING_ROW_START_TICKS = 14L

        /** 相鄰排行列揭示的間隔 tick 數。 */
        const val RANKING_ROW_STAGGER_TICKS = 7L

        /** 排行落定音效相對排行榜開始的延遲 tick 數。 */
        const val RANKING_SETTLED_SOUND_TICKS = 83L

        /** 詳情欄位使用文字呈現時的型別識別碼。 */
        const val DETAIL_TEXT = "T"

        /** 詳情欄位使用牌列呈現時的型別識別碼。 */
        const val DETAIL_TILES = "L"

        /** 詳情與排行揭示使用的音效事件 ID。 */
        private const val DETAIL_SOUND_ID = "minecraft:entity.experience_orb.pickup"

        /** 排名改變時的落定音效事件 ID。 */
        private const val RANKING_SETTLED_SOUND_ID = "minecraft:entity.player.levelup"

        /** 詳情欄位使用逐項條目呈現時的型別識別碼。 */
        const val DETAIL_ENTRIES = "E"

        /** 每個條目在快照中的欄位數：名稱、右側文字、右側 key、右側參數、是否強調。 */
        const val ENTRY_VALUE_COUNT = 5

        /** 條目強調欄位為真時的值。 */
        const val ENTRY_HIGHLIGHTED = "1"

        /** 結算快照一般欄位的分隔字元。 */
        private const val F = '\u001f'

        /** 贏家、音效列之間的分隔字元。 */
        private const val R = '\u001e'

        /** 同一牌列中的牌面 key 分隔字元。 */
        private const val L = '\u001d'

        /** 同一贏家中的副露或詳情項目分隔字元。 */
        private const val G = '\u001c'

        /**
         * 一位贏家的段落長度。
         *
         * @param winner 贏家詳情快照。
         * @param timing 贏家詳情揭示時間設定。
         * @param scoreRevealDelayTicks 分數在逐條揭示結束後多久出現，由模板的版面決定，見 [scoreRevealDelayTicks]。
         * @return 包含條目、分數揭示、閱讀與轉場的 tick 數。
         */
        fun winnerDurationTicks(
            winner: WinSettlementWinnerSnapshot,
            timing: WinSettlementRevealTimingSnapshot,
            scoreRevealDelayTicks: Long,
        ): Long {
            val entries = winner.details.filter { it.type == DETAIL_ENTRIES }.sumOf { it.values.size / ENTRY_VALUE_COUNT }
            return timing.initialFadeTicks + entries * timing.entryStaggerTicks +
                scoreRevealDelayTicks + timing.scoreRevealTicks + timing.readingTicks + TRANSITION_TICKS
        }

        /**
         * 計算整面面板的長度；[scoreRevealDelayTicks] 同 [winnerDurationTicks]。
         *
         * @param winners 依揭示順序排列的贏家快照；可為空。
         * @param timing 贏家詳情揭示時間設定。
         * @param scoreRevealDelayTicks 模板版面決定的分數揭示延遲 tick 數。
         * @return 所有贏家段落、排行榜與淡出所需的總 tick 數。
         */
        fun durationTicks(
            winners: List<WinSettlementWinnerSnapshot>,
            timing: WinSettlementRevealTimingSnapshot,
            scoreRevealDelayTicks: Long,
        ): Long = winners.sumOf { winnerDurationTicks(it, timing, scoreRevealDelayTicks) } + RANKING_TICKS + FADE_OUT_TICKS

        /**
         * 計算分數在逐條揭示結束後的延遲；找不到模板時視為 0，逐條揭示一結束就出現。
         *
         * @param layout 結算模板版面；為 null 時使用零 tick 延遲。
         * @return 分數揭示延遲 tick 數。
         */
        fun scoreRevealDelayTicks(layout: PresentationLayout?): Long = layout?.let { WinSettlementRevealTimeline.scoreRevealDelayTicks(it).toLong() } ?: 0L

        /**
         * 依模板識別碼取得版面設計。
         *
         * @param templateKey 結算呈現模板 key。
         * @return 註冊模板的根版面；找不到模板時為 null。
         */
        fun layoutOf(templateKey: String): PresentationLayout? = GlobalContext.get().get<WinSettlementPresentationTemplateRegistry>().findTemplate(templateKey)?.root

        /** 追蹤舞台所屬麻將桌 UUID 的資料欄位。 */
        private val TABLE_ID = DataTracker.registerData(WinSettlementPresentationEntity::class.java, TrackedDataHandlerRegistry.STRING)

        /** 追蹤舞台開始絕對遊戲時間的資料欄位。 */
        private val START_GAME_TIME = DataTracker.registerData(WinSettlementPresentationEntity::class.java, TrackedDataHandlerRegistry.LONG)

        /** 追蹤舞台結束絕對遊戲時間的資料欄位。 */
        private val END_GAME_TIME = DataTracker.registerData(WinSettlementPresentationEntity::class.java, TrackedDataHandlerRegistry.LONG)

        /** 追蹤和牌結果識別碼的資料欄位。 */
        private val OUTCOME_ID = DataTracker.registerData(WinSettlementPresentationEntity::class.java, TrackedDataHandlerRegistry.STRING)

        /** 追蹤結算呈現模板 key 的資料欄位。 */
        private val TEMPLATE_KEY = DataTracker.registerData(WinSettlementPresentationEntity::class.java, TrackedDataHandlerRegistry.STRING)

        /** 追蹤本次和牌是否自摸的資料欄位。 */
        private val IS_TSUMO = DataTracker.registerData(WinSettlementPresentationEntity::class.java, TrackedDataHandlerRegistry.BOOLEAN)

        /** 追蹤贏家詳情快照編碼字串的資料欄位。 */
        private val WINNERS = DataTracker.registerData(WinSettlementPresentationEntity::class.java, TrackedDataHandlerRegistry.STRING)

        /** 追蹤分數與排行快照編碼字串的資料欄位。 */
        private val RANKINGS = DataTracker.registerData(WinSettlementPresentationEntity::class.java, TrackedDataHandlerRegistry.STRING)

        /** 追蹤贏家詳情揭示節奏編碼字串的資料欄位。 */
        private val REVEAL_TIMING = DataTracker.registerData(WinSettlementPresentationEntity::class.java, TrackedDataHandlerRegistry.STRING)

        /** 追蹤覆蓋預設值的自訂音效 cue 編碼字串的資料欄位。 */
        private val CUSTOM_SOUND_CUES = DataTracker.registerData(WinSettlementPresentationEntity::class.java, TrackedDataHandlerRegistry.STRING)

        /**
         * 將揭示時間欄位依固定順序編成分隔字串。
         *
         * @param value 待編碼的揭示時間快照。
         * @return 包含四個時間欄位的編碼字串。
         */
        private fun encodeRevealTiming(value: WinSettlementRevealTimingSnapshot) = listOf(
            value.initialFadeTicks,
            value.entryStaggerTicks,
            value.scoreRevealTicks,
            value.readingTicks,
        ).joinToString(F.toString())

        /**
         * 解析揭示時間字串；可解析為整數的欄位數不為四時，使用內建時間預設值。
         *
         * @param encoded 待解析的揭示時間字串。
         * @return 還原的揭示時間快照或預設快照。
         */
        private fun decodeRevealTiming(encoded: String): WinSettlementRevealTimingSnapshot {
            val values = encoded.split(F).mapNotNull(String::toIntOrNull)
            return if (values.size == 4) {
                WinSettlementRevealTimingSnapshot(values[0], values[1], values[2], values[3])
            } else {
                WinSettlementRevealTimingSnapshot(INITIAL_FADE_TICKS.toInt(), ENTRY_STAGGER_TICKS.toInt(), SCORE_REVEAL_TICKS.toInt(), READING_TICKS.toInt())
            }
        }

        /**
         * 將音效 cue 快照依欄位順序編成分隔字串。
         *
         * @param values 待編碼的音效 cue 快照。
         * @return 以列分隔符串接的 cue 資料。
         */
        private fun encodeSoundCues(values: List<WinSettlementSoundCueSnapshot>) = values.joinToString(R.toString()) {
            listOf(it.eventTick, it.soundId, it.volume, it.pitch).joinToString(F.toString())
        }

        /**
         * 解析音效 cue 字串；欄位數或數值格式不符的列略過。
         *
         * @param encoded 待解析的音效 cue 字串。
         * @return 成功還原的音效 cue 清單。
         */
        private fun decodeSoundCues(encoded: String) = encoded.takeIf(String::isNotBlank)?.split(R)?.mapNotNull { row ->
            val values = row.split(F)
            if (values.size != 4) return@mapNotNull null
            WinSettlementSoundCueSnapshot(
                values[0].toLongOrNull() ?: return@mapNotNull null,
                values[1],
                values[2].toFloatOrNull() ?: return@mapNotNull null,
                values[3].toFloatOrNull() ?: return@mapNotNull null,
            )
        }.orEmpty()

        /**
         * 將贏家詳情與副露欄位依固定順序編成分隔字串。
         *
         * @param values 待編碼的贏家快照。
         * @return 以列分隔符串接的贏家資料。
         */
        private fun encodeWinners(values: List<WinSettlementWinnerSnapshot>) = values.joinToString(R.toString()) { winner ->
            listOf(
                winner.playerId,
                winner.seatIndex,
                if (winner.isAi) 1 else 0,
                winner.responsiblePlayerId.orEmpty(),
                winner.totalScore,
                winner.handAssetKeys.joinToString(L.toString()),
                winner.melds.joinToString(G.toString()) { meld ->
                    meld.assetKeys.joinToString(L.toString()) + "\u001a" + meld.faceDownIndices.joinToString(",")
                },
                winner.winningTileAssetKey,
                winner.details.joinToString(G.toString()) { detail ->
                    listOf(detail.id, detail.type, detail.values.joinToString(L.toString())).joinToString("\u001b")
                },
            ).joinToString(F.toString())
        }

        /**
         * 解析贏家快照字串；欄位數或必要數值無效的列略過，無效詳情項目不納入結果。
         *
         * @param encoded 待解析的贏家快照字串。
         * @return 成功還原的贏家快照清單。
         */
        private fun decodeWinners(encoded: String) = encoded.split(R).mapNotNull { row ->
            val f = row.split(F)
            if (f.size != 9) return@mapNotNull null
            WinSettlementWinnerSnapshot(
                f[0],
                f[1].toIntOrNull() ?: return@mapNotNull null,
                f[2] == "1",
                f[3].ifBlank { null },
                f[4].toIntOrNull() ?: return@mapNotNull null,
                f[5].splitList(),
                f[6].takeIf(String::isNotBlank)?.split(G)?.map { encodedMeld ->
                    val parts = encodedMeld.split('\u001a')
                    WinSettlementMeldSnapshot(
                        assetKeys = parts.first().splitList(),
                        faceDownIndices = parts.getOrNull(1)?.takeIf(String::isNotBlank)?.split(',')?.mapNotNull(String::toIntOrNull)?.toSet().orEmpty(),
                    )
                }.orEmpty(),
                f[7],
                f[8].takeIf(String::isNotBlank)?.split(G)?.mapNotNull { item ->
                    val parts = item.split('\u001b')
                    if (parts.size != 3) null else WinSettlementDetailSnapshot(parts[0], parts[1], parts[2].splitList())
                }.orEmpty(),
            )
        }

        /**
         * 以牌列分隔字元拆解非空欄位；空字串對應空清單。
         *
         * @return 拆解出的字串清單。
         */
        private fun String.splitList(): List<String> = takeIf(String::isNotBlank)?.split(L).orEmpty()
    }
}
