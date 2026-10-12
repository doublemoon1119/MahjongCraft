package com.doublemoon1119.mahjongcraft.platform.fabric.entity

import com.doublemoon1119.mahjongcraft.platform.fabric.registry.ModEntities
import com.doublemoon1119.mahjongcraft.platform.minecraft.animation.AnimationStep
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.MatchSettlementPresentationTemplate
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.MatchSettlementRevealOrder
import net.minecraft.entity.EntityType
import net.minecraft.entity.data.DataTracker
import net.minecraft.entity.data.TrackedData
import net.minecraft.entity.data.TrackedDataHandlerRegistry
import net.minecraft.nbt.NbtCompound
import net.minecraft.world.World
import kotlin.uuid.Uuid

/**
 * 終局排行榜舞台；client 只依絕對時間與權威快照重建揭曉動畫。
 *
 * @param type 終局結算實體種類。
 * @param world 實體所屬世界。
 */
class MatchSettlementPresentationEntity(
    type: EntityType<out MatchSettlementPresentationEntity> = ModEntities.matchSettlementPresentation,
    world: World,
) : SimpleAnimatedMahjongEntity(type, world),
    TableOwnedPresentationEntity {

    /** 所屬麻將桌。 */
    override val managedTableId: Uuid?
        get() = dataTracker[TABLE_ID].takeIf(String::isNotBlank)?.let { runCatching { Uuid.parse(it) }.getOrNull() }

    /** 舞台開始的絕對遊戲時間。 */
    val startGameTime: Long get() = dataTracker[START_GAME_TIME]

    /** 舞台結束的絕對遊戲時間。 */
    val endGameTime: Long get() = dataTracker[END_GAME_TIME]

    /** 使用的宣告式模板 key。 */
    val templateKey: String get() = dataTracker[TEMPLATE_KEY]

    /** 權威終局玩家資料。 */
    val players: List<MatchSettlementPlayerSnapshot> get() = decodePlayers(dataTracker[PLAYERS])

    /** 揭曉方向。 */
    val revealOrder: MatchSettlementRevealOrder
        get() = runCatching { MatchSettlementRevealOrder.valueOf(dataTracker[REVEAL_ORDER]) }
            .getOrDefault(MatchSettlementRevealOrder.LAST_TO_FIRST)

    /** 兩列揭曉起點的間隔。 */
    val rowRevealIntervalTicks: Int get() = dataTracker[ROW_REVEAL_INTERVAL]

    /** 全部揭曉後的閱讀時間。 */
    val readingTicks: Int get() = dataTracker[READING_TICKS]

    init {
        setNoGravity(true)
    }

    /**
     * 生成前設定完整且不可變的舞台資料。
     *
     * @param tableId 所屬麻將桌 UUID。
     * @param startGameTime 舞台開始的絕對遊戲時間。
     * @param players 權威終局玩家快照。
     * @param template 決定模板 key、揭曉順序與節奏的呈現模板。
     */
    fun configure(
        tableId: Uuid,
        startGameTime: Long,
        players: List<MatchSettlementPlayerSnapshot>,
        template: MatchSettlementPresentationTemplate,
    ) {
        check(!world.isClient) { "Match settlement stage must be configured by the server" }
        require(players.isNotEmpty()) { "Match settlement must contain at least one player" }
        dataTracker.set(TABLE_ID, tableId.toString())
        dataTracker.set(START_GAME_TIME, startGameTime)
        dataTracker.set(TEMPLATE_KEY, template.key)
        dataTracker.set(PLAYERS, encodePlayers(players))
        dataTracker.set(REVEAL_ORDER, template.revealOrder.name)
        dataTracker.set(ROW_REVEAL_INTERVAL, template.rowRevealIntervalTicks)
        dataTracker.set(READING_TICKS, template.readingTicks)
        dataTracker.set(END_GAME_TIME, startGameTime + durationTicks(players.size, template.rowRevealIntervalTicks, template.readingTicks))
        enqueueAll(
            revealSequence().mapIndexed { index, player ->
                val playAtGameTime = startGameTime + rowRevealTick(index, template.rowRevealIntervalTicks)
                val champion = player.finalRank == 1
                AnimationStep.PlaySound(
                    soundId = if (champion) template.championSoundId else template.rowSoundId,
                    volume = if (champion) 0.24f else 0.1f,
                    pitch = if (champion) 1.0f else 0.78f + index * 0.08f,
                    playAtGameTime = playAtGameTime,
                    expiresAtGameTime = playAtGameTime + EVENT_GRACE_TICKS,
                )
            },
        )
    }

    /**
     * 取得含 tick delta 的相對演出時間。
     *
     * @param tickDelta 本 tick 的部分經過時間。
     * @return 相對舞台開始時間經過的 tick。
     */
    fun elapsedTicks(tickDelta: Float): Double = world.time + tickDelta.toDouble() - startGameTime

    /**
     * 依模板順序取得逐列揭曉的玩家。
     *
     * @return 依 [revealOrder] 排序的玩家快照。
     */
    fun revealSequence(): List<MatchSettlementPlayerSnapshot> = when (revealOrder) {
        MatchSettlementRevealOrder.LAST_TO_FIRST -> players.sortedByDescending(MatchSettlementPlayerSnapshot::finalRank)
        MatchSettlementRevealOrder.FIRST_TO_LAST -> players.sortedBy(MatchSettlementPlayerSnapshot::finalRank)
    }

    override fun isCollidable(): Boolean = false

    override fun isPushable(): Boolean = false

    override fun canHit(): Boolean = false

    override fun tick() {
        super.tick()
        if (world.isClient) return
        if (endGameTime - startGameTime != durationTicks(players.size, rowRevealIntervalTicks, readingTicks)) {
            discard()
            return
        }
        if (endGameTime > startGameTime && world.time >= endGameTime) discard()
    }

    override fun initDataTracker() {
        dataTracker.startTracking(TABLE_ID, "")
        dataTracker.startTracking(START_GAME_TIME, 0L)
        dataTracker.startTracking(END_GAME_TIME, 0L)
        dataTracker.startTracking(TEMPLATE_KEY, "")
        dataTracker.startTracking(PLAYERS, "")
        dataTracker.startTracking(REVEAL_ORDER, MatchSettlementRevealOrder.LAST_TO_FIRST.name)
        dataTracker.startTracking(ROW_REVEAL_INTERVAL, 12)
        dataTracker.startTracking(READING_TICKS, 100)
    }

    /**
     * 從世界存檔還原舞台快照與動畫佇列。
     *
     * @param nbt entity 的持久化資料。
     */
    override fun readCustomDataFromNbt(nbt: NbtCompound) {
        dataTracker.set(TABLE_ID, nbt.getString(NBT_TABLE_ID))
        dataTracker.set(START_GAME_TIME, nbt.getLong(NBT_START_GAME_TIME))
        dataTracker.set(END_GAME_TIME, nbt.getLong(NBT_END_GAME_TIME))
        dataTracker.set(TEMPLATE_KEY, nbt.getString(NBT_TEMPLATE_KEY))
        dataTracker.set(PLAYERS, nbt.getString(NBT_PLAYERS))
        dataTracker.set(REVEAL_ORDER, nbt.getString(NBT_REVEAL_ORDER))
        dataTracker.set(ROW_REVEAL_INTERVAL, nbt.getInt(NBT_ROW_REVEAL_INTERVAL))
        dataTracker.set(READING_TICKS, nbt.getInt(NBT_READING_TICKS))
        readAnimationQueueFromNbt(nbt)
    }

    /**
     * 將舞台快照與動畫佇列寫入世界存檔。
     *
     * @param nbt 接收 entity 持久化資料的 NBT。
     */
    override fun writeCustomDataToNbt(nbt: NbtCompound) {
        nbt.putString(NBT_TABLE_ID, managedTableId?.toString().orEmpty())
        nbt.putLong(NBT_START_GAME_TIME, startGameTime)
        nbt.putLong(NBT_END_GAME_TIME, endGameTime)
        nbt.putString(NBT_TEMPLATE_KEY, templateKey)
        nbt.putString(NBT_PLAYERS, dataTracker[PLAYERS])
        nbt.putString(NBT_REVEAL_ORDER, dataTracker[REVEAL_ORDER])
        nbt.putInt(NBT_ROW_REVEAL_INTERVAL, rowRevealIntervalTicks)
        nbt.putInt(NBT_READING_TICKS, readingTicks)
        writeAnimationQueueToNbt(nbt)
    }

    companion object {
        /** 視錐剔除與追蹤使用的非零寬度。 */
        const val WIDTH = 4.0f

        /** 視錐剔除與追蹤使用的非零高度。 */
        const val HEIGHT = 3.0f

        /** 面板開始淡入的 tick。 */
        const val PANEL_FADE_IN_START_TICK = 10L

        /** 第一列開始揭曉的 tick。 */
        const val FIRST_ROW_REVEAL_TICK = 26L

        /** 每列淡入與回彈的時長。 */
        const val ROW_REVEAL_DURATION_TICKS = 10L

        /** 面板結束淡出的時長。 */
        const val FADE_OUT_TICKS = 20L

        /** 玩家欄位間使用的內部編碼分隔字元。 */
        private const val FIELD_SEPARATOR = '\u001f'

        /** 玩家列之間使用的內部編碼分隔字元。 */
        private const val ROW_SEPARATOR = '\u001e'

        /** 排定揭曉音效後仍接受播放的 tick 寬限。 */
        private const val EVENT_GRACE_TICKS = 2L

        /** 所屬桌子 UUID 的世界存檔 key。 */
        private const val NBT_TABLE_ID = "ManagedTableId"

        /** 舞台開始時間的世界存檔 key。 */
        private const val NBT_START_GAME_TIME = "StartGameTime"

        /** 舞台結束時間的世界存檔 key。 */
        private const val NBT_END_GAME_TIME = "EndGameTime"

        /** 結算模板 key 的世界存檔 key。 */
        private const val NBT_TEMPLATE_KEY = "TemplateKey"

        /** 玩家快照編碼的世界存檔 key。 */
        private const val NBT_PLAYERS = "Players"

        /** 揭曉順序名稱的世界存檔 key。 */
        private const val NBT_REVEAL_ORDER = "RevealOrder"

        /** 相鄰列揭曉間隔的世界存檔 key。 */
        private const val NBT_ROW_REVEAL_INTERVAL = "RowRevealInterval"

        /** 全部揭曉後閱讀時間的世界存檔 key。 */
        private const val NBT_READING_TICKS = "ReadingTicks"

        /**
         * 依玩家數與模板時間計算完整生命週期。
         *
         * @param playerCount 結算玩家數。
         * @param revealInterval 相鄰列揭曉起點的 tick 間隔。
         * @param readingTicks 全部揭曉後的閱讀時間。
         * @return 包含第一列揭曉、最後一列動畫、閱讀與淡出的總 tick 數。
         */
        fun durationTicks(playerCount: Int, revealInterval: Int, readingTicks: Int): Long = rowRevealTick((playerCount - 1).coerceAtLeast(0), revealInterval) +
            ROW_REVEAL_DURATION_TICKS + readingTicks + FADE_OUT_TICKS

        /**
         * 依揭曉序列 index 計算列的揭曉起點。
         *
         * @param index 揭曉序列中的列索引；負值按 0 計算。
         * @param revealInterval 相鄰列揭曉起點的 tick 間隔。
         * @return 此列相對舞台開始的揭曉 tick。
         */
        fun rowRevealTick(index: Int, revealInterval: Int): Long = FIRST_ROW_REVEAL_TICK + index.coerceAtLeast(0) * revealInterval

        /** 同步所屬麻將桌 UUID。 */
        private val TABLE_ID: TrackedData<String> = DataTracker.registerData(MatchSettlementPresentationEntity::class.java, TrackedDataHandlerRegistry.STRING)

        /** 同步舞台開始的絕對遊戲時間。 */
        private val START_GAME_TIME: TrackedData<Long> = DataTracker.registerData(MatchSettlementPresentationEntity::class.java, TrackedDataHandlerRegistry.LONG)

        /** 同步舞台結束的絕對遊戲時間。 */
        private val END_GAME_TIME: TrackedData<Long> = DataTracker.registerData(MatchSettlementPresentationEntity::class.java, TrackedDataHandlerRegistry.LONG)

        /** 同步使用的宣告式模板 key。 */
        private val TEMPLATE_KEY: TrackedData<String> = DataTracker.registerData(MatchSettlementPresentationEntity::class.java, TrackedDataHandlerRegistry.STRING)

        /** 同步編碼後的權威玩家快照。 */
        private val PLAYERS: TrackedData<String> = DataTracker.registerData(MatchSettlementPresentationEntity::class.java, TrackedDataHandlerRegistry.STRING)

        /** 同步逐列揭曉順序。 */
        private val REVEAL_ORDER: TrackedData<String> = DataTracker.registerData(MatchSettlementPresentationEntity::class.java, TrackedDataHandlerRegistry.STRING)

        /** 同步相鄰列揭曉起點的間隔。 */
        private val ROW_REVEAL_INTERVAL: TrackedData<Int> = DataTracker.registerData(MatchSettlementPresentationEntity::class.java, TrackedDataHandlerRegistry.INTEGER)

        /** 同步全部揭曉後的閱讀時間。 */
        private val READING_TICKS: TrackedData<Int> = DataTracker.registerData(MatchSettlementPresentationEntity::class.java, TrackedDataHandlerRegistry.INTEGER)

        /**
         * 將玩家快照欄位編碼為 tracker 使用的分隔字串。
         *
         * @param players 要編碼的玩家快照。
         * @return 以列與欄位分隔符連接的玩家資料。
         */
        private fun encodePlayers(players: List<MatchSettlementPlayerSnapshot>): String = players.joinToString(ROW_SEPARATOR.toString()) { player ->
            listOf(player.playerId, player.seatIndex, if (player.isAi) 1 else 0, player.initialSeatIndex, player.finalScore, player.finalRank)
                .joinToString(FIELD_SEPARATOR.toString())
        }

        /**
         * 從 tracker 分隔字串還原有效的玩家快照列。
         *
         * @param encoded 玩家快照的編碼字串。
         * @return 可解析的玩家快照；欄位數錯誤或座位索引、分數、順位無效的列會略過。
         */
        private fun decodePlayers(encoded: String): List<MatchSettlementPlayerSnapshot> = encoded.split(ROW_SEPARATOR).mapNotNull { row ->
            val fields = row.split(FIELD_SEPARATOR)
            if (fields.size != 6) return@mapNotNull null
            MatchSettlementPlayerSnapshot(
                playerId = fields[0],
                seatIndex = fields[1].toIntOrNull() ?: return@mapNotNull null,
                isAi = fields[2] == "1",
                initialSeatIndex = fields[3].toInt(),
                finalScore = fields[4].toIntOrNull() ?: return@mapNotNull null,
                finalRank = fields[5].toIntOrNull() ?: return@mapNotNull null,
            )
        }
    }
}
