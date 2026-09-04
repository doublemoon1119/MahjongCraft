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

/** 單一玩家在終局排行榜中的可持久化權威快照。 */
data class MatchSettlementPlayerSnapshot(
    val playerId: String,
    val seatIndex: Int,
    val isAi: Boolean,
    val initialSeatIndex: Int,
    val finalScore: Int,
    val finalRank: Int,
)

/** 終局排行榜舞台；client 只依絕對時間與權威快照重建揭曉動畫。 */
class MatchSettlementPresentationEntity(
    type: EntityType<out MatchSettlementPresentationEntity> = ModEntities.matchSettlementPresentation,
    world: World,
) : SimpleAnimatedMahjongEntity(type, world) {

    /** 所屬麻將桌。 */
    val managedTableId: Uuid?
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

    /** 生成前設定完整且不可變的舞台資料。 */
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
                    volume = if (champion) 0.28f else 0.1f,
                    pitch = if (champion) 1.0f else 0.78f + index * 0.08f,
                    playAtGameTime = playAtGameTime,
                    expiresAtGameTime = playAtGameTime + EVENT_GRACE_TICKS,
                )
            },
        )
    }

    /** 取得含 tick delta 的相對演出時間。 */
    fun elapsedTicks(tickDelta: Float): Double = world.time + tickDelta.toDouble() - startGameTime

    /** 依模板順序取得逐列揭曉的玩家。 */
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

        private const val FIELD_SEPARATOR = '\u001f'
        private const val ROW_SEPARATOR = '\u001e'
        private const val EVENT_GRACE_TICKS = 2L
        private const val NBT_TABLE_ID = "ManagedTableId"
        private const val NBT_START_GAME_TIME = "StartGameTime"
        private const val NBT_END_GAME_TIME = "EndGameTime"
        private const val NBT_TEMPLATE_KEY = "TemplateKey"
        private const val NBT_PLAYERS = "Players"
        private const val NBT_REVEAL_ORDER = "RevealOrder"
        private const val NBT_ROW_REVEAL_INTERVAL = "RowRevealInterval"
        private const val NBT_READING_TICKS = "ReadingTicks"

        /** 依玩家數與模板時間計算完整生命週期。 */
        fun durationTicks(playerCount: Int, revealInterval: Int, readingTicks: Int): Long = rowRevealTick((playerCount - 1).coerceAtLeast(0), revealInterval) +
            ROW_REVEAL_DURATION_TICKS + readingTicks + FADE_OUT_TICKS

        /** 依揭曉序列 index 計算列的揭曉起點。 */
        fun rowRevealTick(index: Int, revealInterval: Int): Long = FIRST_ROW_REVEAL_TICK + index.coerceAtLeast(0) * revealInterval

        private val TABLE_ID: TrackedData<String> = DataTracker.registerData(MatchSettlementPresentationEntity::class.java, TrackedDataHandlerRegistry.STRING)
        private val START_GAME_TIME: TrackedData<Long> = DataTracker.registerData(MatchSettlementPresentationEntity::class.java, TrackedDataHandlerRegistry.LONG)
        private val END_GAME_TIME: TrackedData<Long> = DataTracker.registerData(MatchSettlementPresentationEntity::class.java, TrackedDataHandlerRegistry.LONG)
        private val TEMPLATE_KEY: TrackedData<String> = DataTracker.registerData(MatchSettlementPresentationEntity::class.java, TrackedDataHandlerRegistry.STRING)
        private val PLAYERS: TrackedData<String> = DataTracker.registerData(MatchSettlementPresentationEntity::class.java, TrackedDataHandlerRegistry.STRING)
        private val REVEAL_ORDER: TrackedData<String> = DataTracker.registerData(MatchSettlementPresentationEntity::class.java, TrackedDataHandlerRegistry.STRING)
        private val ROW_REVEAL_INTERVAL: TrackedData<Int> = DataTracker.registerData(MatchSettlementPresentationEntity::class.java, TrackedDataHandlerRegistry.INTEGER)
        private val READING_TICKS: TrackedData<Int> = DataTracker.registerData(MatchSettlementPresentationEntity::class.java, TrackedDataHandlerRegistry.INTEGER)

        private fun encodePlayers(players: List<MatchSettlementPlayerSnapshot>): String = players.joinToString(ROW_SEPARATOR.toString()) { player ->
            listOf(player.playerId, player.seatIndex, if (player.isAi) 1 else 0, player.initialSeatIndex, player.finalScore, player.finalRank)
                .joinToString(FIELD_SEPARATOR.toString())
        }

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
