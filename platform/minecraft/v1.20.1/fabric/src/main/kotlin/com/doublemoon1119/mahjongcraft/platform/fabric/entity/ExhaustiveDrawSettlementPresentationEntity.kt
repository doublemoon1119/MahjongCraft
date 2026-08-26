package com.doublemoon1119.mahjongcraft.platform.fabric.entity

import com.doublemoon1119.mahjongcraft.platform.fabric.registry.ModEntities
import net.minecraft.entity.Entity
import net.minecraft.entity.EntityType
import net.minecraft.entity.data.DataTracker
import net.minecraft.entity.data.TrackedData
import net.minecraft.entity.data.TrackedDataHandlerRegistry
import net.minecraft.nbt.NbtCompound
import net.minecraft.sound.SoundCategory
import net.minecraft.sound.SoundEvents
import net.minecraft.world.World
import kotlin.uuid.Uuid

/** 單一玩家在流局結算排行榜中的可持久化關鍵影格。 */
data class ExhaustiveDrawSettlementPlayerSnapshot(
    val playerId: String,
    val seatIndex: Int,
    val wind: String,
    val isAi: Boolean,
    val previousScore: Int,
    val currentScore: Int,
    val previousRank: Int,
    val currentRank: Int,
    val statusId: String?,
    val revealedHandTileIds: List<String>,
    val revealedHandAssetKeys: List<String>,
    val waitingTileAssetKeys: List<String>,
)

/**
 * 統一流局結算舞台。Entity 只同步起訖關鍵影格；client 依絕對遊戲時間重建分數與排行動畫。
 */
class ExhaustiveDrawSettlementPresentationEntity(
    type: EntityType<out ExhaustiveDrawSettlementPresentationEntity> = ModEntities.exhaustiveDrawSettlementPresentation,
    world: World,
) : Entity(type, world) {
    private var playedCoreSoundMask: Int = 0

    /** 所屬麻將桌。 */
    val managedTableId: Uuid?
        get() = dataTracker[TABLE_ID].takeIf(String::isNotBlank)?.let { runCatching { Uuid.parse(it) }.getOrNull() }

    /** 舞台開始的絕對遊戲時間。 */
    val startGameTime: Long get() = dataTracker[START_GAME_TIME]

    /** 舞台結束的絕對遊戲時間。 */
    val endGameTime: Long get() = dataTracker[END_GAME_TIME]

    /** 完整 namespaced 流局原因 ID。 */
    val reasonId: String get() = dataTracker[REASON_ID]

    /** 全部座位的排行關鍵影格。 */
    val players: List<ExhaustiveDrawSettlementPlayerSnapshot>
        get() = decodePlayers(dataTracker[PLAYERS])

    /** 是否需要先呈現實際等待牌；單純的流局宣告不占用獨立階段。 */
    val hasInformationPhase: Boolean get() = players.any { it.waitingTileAssetKeys.isNotEmpty() }

    init {
        setNoGravity(true)
    }

    /** 生成前設定完整且不可變的舞台資料。 */
    fun configure(
        tableId: Uuid,
        startGameTime: Long,
        reasonId: String,
        players: List<ExhaustiveDrawSettlementPlayerSnapshot>,
    ) {
        check(!world.isClient) { "Round settlement stage must be configured by the server" }
        require(players.isNotEmpty()) { "Round settlement must contain at least one player" }
        require(reasonId.isNotBlank()) { "Round settlement reason ID must not be blank" }
        dataTracker.set(TABLE_ID, tableId.toString())
        dataTracker.set(START_GAME_TIME, startGameTime)
        dataTracker.set(END_GAME_TIME, startGameTime + durationTicks(players))
        dataTracker.set(REASON_ID, reasonId)
        dataTracker.set(PLAYERS, encodePlayers(players))
    }

    /** 取得含 tick delta 的相對演出時間。 */
    fun elapsedTicks(tickDelta: Float): Double = world.time + tickDelta.toDouble() - startGameTime

    override fun isCollidable(): Boolean = false

    override fun isPushable(): Boolean = false

    override fun canHit(): Boolean = false

    override fun tick() {
        super.tick()
        if (world.isClient) return
        if (endGameTime - startGameTime != durationTicks(players)) {
            discard()
            return
        }
        playPendingInformationRowSounds()
        playPendingRankingRowSounds()
        playPendingRankingSettledSound()
        if (endGameTime > startGameTime && world.time >= endGameTime) discard()
    }

    /** 公開資訊逐列淡入時播放低音量提示音；若載入時已越過事件 tick，僅標記完成而不補播。 */
    private fun playPendingInformationRowSounds() {
        informationPlayers().indices.forEach { index ->
            val revealTick = informationRowRevealTick(index)
            val bit = 1 shl index
            if (playedCoreSoundMask and bit != 0) return@forEach
            val elapsed = world.time - startGameTime
            if (elapsed < revealTick) return@forEach
            playedCoreSoundMask = playedCoreSoundMask or bit
            if (elapsed <= revealTick + SettlementPresentationSoundSpec.EVENT_GRACE_TICKS) {
                world.playSound(
                    null,
                    x,
                    y,
                    z,
                    SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP,
                    SoundCategory.PLAYERS,
                    SettlementPresentationSoundSpec.DETAIL_VOLUME,
                    SettlementPresentationSoundSpec.DETAIL_BASE_PITCH + index * SettlementPresentationSoundSpec.DETAIL_PITCH_STEP,
                )
            }
        }
    }

    /** 排行榜逐列播放低音量提示音；若載入時已越過事件 tick，僅標記完成而不補播。 */
    private fun playPendingRankingRowSounds() {
        players.indices.forEach { index ->
            val revealTick = rowRevealTick(index, hasInformationPhase)
            val bit = 1 shl (RANKING_ROW_SOUND_BIT_OFFSET + index)
            if (playedCoreSoundMask and bit != 0) return@forEach
            val elapsed = world.time - startGameTime
            if (elapsed < revealTick) return@forEach
            playedCoreSoundMask = playedCoreSoundMask or bit
            if (elapsed <= revealTick + SettlementPresentationSoundSpec.EVENT_GRACE_TICKS) {
                world.playSound(
                    null,
                    x,
                    y,
                    z,
                    SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP,
                    SoundCategory.PLAYERS,
                    SettlementPresentationSoundSpec.ROW_VOLUME,
                    SettlementPresentationSoundSpec.ROW_BASE_PITCH + index * SettlementPresentationSoundSpec.ROW_PITCH_STEP,
                )
            }
        }
    }

    /** 取得第一階段實際顯示的玩家，必須與 client renderer 的公開資訊篩選條件一致。 */
    private fun informationPlayers(): List<ExhaustiveDrawSettlementPlayerSnapshot> = players.filter { player ->
        player.waitingTileAssetKeys.isNotEmpty()
    }

    /** 分數與排行抵達最終值時播放四音確認旋律；載入時若已錯過則只標記完成。 */
    private fun playPendingRankingSettledSound() {
        val elapsed = world.time - startGameTime
        val settledTick = rankingSettledSoundTick(hasInformationPhase)
        val rankingChanged = players.any { it.previousRank != it.currentRank }
        if (playedCoreSoundMask and RANKING_SETTLED_SOUND_BIT != 0 || elapsed < settledTick) return
        playedCoreSoundMask = playedCoreSoundMask or RANKING_SETTLED_SOUND_BIT
        if (rankingChanged && elapsed <= settledTick + SettlementPresentationSoundSpec.EVENT_GRACE_TICKS) {
            world.playSound(
                null,
                x,
                y,
                z,
                SoundEvents.ENTITY_PLAYER_LEVELUP,
                SoundCategory.PLAYERS,
                SettlementPresentationSoundSpec.RANKING_SETTLED_VOLUME,
                SettlementPresentationSoundSpec.RANKING_SETTLED_PITCH,
            )
        }
    }

    override fun initDataTracker() {
        dataTracker.startTracking(TABLE_ID, "")
        dataTracker.startTracking(START_GAME_TIME, 0L)
        dataTracker.startTracking(END_GAME_TIME, 0L)
        dataTracker.startTracking(REASON_ID, "")
        dataTracker.startTracking(PLAYERS, "")
    }

    override fun readCustomDataFromNbt(nbt: NbtCompound) {
        dataTracker.set(TABLE_ID, nbt.getString(NBT_TABLE_ID))
        dataTracker.set(START_GAME_TIME, nbt.getLong(NBT_START_GAME_TIME))
        dataTracker.set(END_GAME_TIME, nbt.getLong(NBT_END_GAME_TIME))
        dataTracker.set(REASON_ID, nbt.getString(NBT_REASON_ID))
        dataTracker.set(PLAYERS, nbt.getString(NBT_PLAYERS))
        playedCoreSoundMask = nbt.getInt(NBT_PLAYED_CORE_SOUND_MASK)
    }

    override fun writeCustomDataToNbt(nbt: NbtCompound) {
        nbt.putString(NBT_TABLE_ID, managedTableId?.toString().orEmpty())
        nbt.putLong(NBT_START_GAME_TIME, startGameTime)
        nbt.putLong(NBT_END_GAME_TIME, endGameTime)
        nbt.putString(NBT_REASON_ID, reasonId)
        nbt.putString(NBT_PLAYERS, dataTracker[PLAYERS])
        nbt.putInt(NBT_PLAYED_CORE_SOUND_MASK, playedCoreSoundMask)
    }

    companion object {
        /** 含等待牌時的完整雙階段展示時間，包含排行落定後 1.5 秒的額外觀看時間。 */
        const val DURATION_TICKS: Long = 240L

        /** 沒有等待牌時跳過公開資訊階段的展示時間，仍保留相同的額外觀看時間。 */
        const val RANKING_ONLY_DURATION_TICKS: Long = 180L

        /** 視錐剔除與追蹤使用的非零寬度。 */
        const val WIDTH: Float = 4.0f

        /** 視錐剔除與追蹤使用的非零高度。 */
        const val HEIGHT: Float = 3.0f

        private const val FIELD_SEPARATOR = '\u001f'
        private const val ROW_SEPARATOR = '\u001e'
        private const val LIST_SEPARATOR = '\u001d'
        private const val NBT_TABLE_ID = "ManagedTableId"
        private const val NBT_START_GAME_TIME = "StartGameTime"
        private const val NBT_END_GAME_TIME = "EndGameTime"
        private const val NBT_REASON_ID = "ReasonId"
        private const val NBT_PLAYERS = "Players"
        private const val NBT_PLAYED_CORE_SOUND_MASK = "PlayedCoreSoundMask"
        private const val INFORMATION_ROW_REVEAL_START_TICK = 40L
        private const val INFORMATION_ROW_REVEAL_INTERVAL_TICKS = 6L
        private const val ROW_REVEAL_INTERVAL_TICKS = 4L
        private const val RANKING_ROW_SOUND_BIT_OFFSET = 8
        private const val RANKING_SETTLED_SOUND_TICK = 170L
        private const val RANKING_ONLY_SETTLED_SOUND_TICK = 110L
        private const val RANKING_SETTLED_SOUND_BIT = 1 shl 24

        /** 依內容決定是否保留等待牌資訊階段。 */
        fun durationTicks(players: List<ExhaustiveDrawSettlementPlayerSnapshot>): Long = if (players.any { it.waitingTileAssetKeys.isNotEmpty() }) {
            DURATION_TICKS
        } else {
            RANKING_ONLY_DURATION_TICKS
        }

        /** 依玩家列 index 計算逐列出現時間，renderer 與 server 音效共用。 */
        fun rowRevealTick(index: Int, hasInformationPhase: Boolean): Long = (if (hasInformationPhase) 110L else 50L) +
            index.coerceAtLeast(0) * ROW_REVEAL_INTERVAL_TICKS

        /** 依是否存在等待牌資訊決定排行落定音效時間。 */
        fun rankingSettledSoundTick(hasInformationPhase: Boolean): Long = if (hasInformationPhase) {
            RANKING_SETTLED_SOUND_TICK
        } else {
            RANKING_ONLY_SETTLED_SOUND_TICK
        }

        /** 依公開資訊列 index 計算逐列出現時間。 */
        fun informationRowRevealTick(index: Int): Long = INFORMATION_ROW_REVEAL_START_TICK +
            index.coerceAtLeast(0) * INFORMATION_ROW_REVEAL_INTERVAL_TICKS

        private val TABLE_ID: TrackedData<String> = DataTracker.registerData(
            ExhaustiveDrawSettlementPresentationEntity::class.java,
            TrackedDataHandlerRegistry.STRING,
        )
        private val START_GAME_TIME: TrackedData<Long> = DataTracker.registerData(
            ExhaustiveDrawSettlementPresentationEntity::class.java,
            TrackedDataHandlerRegistry.LONG,
        )
        private val END_GAME_TIME: TrackedData<Long> = DataTracker.registerData(
            ExhaustiveDrawSettlementPresentationEntity::class.java,
            TrackedDataHandlerRegistry.LONG,
        )
        private val REASON_ID: TrackedData<String> = DataTracker.registerData(
            ExhaustiveDrawSettlementPresentationEntity::class.java,
            TrackedDataHandlerRegistry.STRING,
        )
        private val PLAYERS: TrackedData<String> = DataTracker.registerData(
            ExhaustiveDrawSettlementPresentationEntity::class.java,
            TrackedDataHandlerRegistry.STRING,
        )

        private fun encodePlayers(players: List<ExhaustiveDrawSettlementPlayerSnapshot>): String = players.joinToString(ROW_SEPARATOR.toString()) { player ->
            listOf(
                player.playerId,
                player.seatIndex,
                player.wind,
                if (player.isAi) 1 else 0,
                player.previousScore,
                player.currentScore,
                player.previousRank,
                player.currentRank,
                player.statusId.orEmpty(),
                player.revealedHandTileIds.joinToString(LIST_SEPARATOR.toString()),
                player.revealedHandAssetKeys.joinToString(LIST_SEPARATOR.toString()),
                player.waitingTileAssetKeys.joinToString(LIST_SEPARATOR.toString()),
            ).joinToString(FIELD_SEPARATOR.toString())
        }

        private fun decodePlayers(encoded: String): List<ExhaustiveDrawSettlementPlayerSnapshot> = encoded
            .split(ROW_SEPARATOR)
            .mapNotNull { row ->
                val fields = row.split(FIELD_SEPARATOR)
                if (fields.size != 12) return@mapNotNull null
                ExhaustiveDrawSettlementPlayerSnapshot(
                    playerId = fields[0],
                    seatIndex = fields[1].toIntOrNull() ?: return@mapNotNull null,
                    wind = fields[2],
                    isAi = fields[3] == "1",
                    previousScore = fields[4].toIntOrNull() ?: return@mapNotNull null,
                    currentScore = fields[5].toIntOrNull() ?: return@mapNotNull null,
                    previousRank = fields[6].toIntOrNull() ?: return@mapNotNull null,
                    currentRank = fields[7].toIntOrNull() ?: return@mapNotNull null,
                    statusId = fields[8].ifBlank { null },
                    revealedHandTileIds = fields[9].takeIf(String::isNotBlank)?.split(LIST_SEPARATOR).orEmpty(),
                    revealedHandAssetKeys = fields[10].takeIf(String::isNotBlank)?.split(LIST_SEPARATOR).orEmpty(),
                    waitingTileAssetKeys = fields[11].takeIf(String::isNotBlank)?.split(LIST_SEPARATOR).orEmpty(),
                )
            }
    }
}
