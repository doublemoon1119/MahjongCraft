package com.doublemoon1119.mahjongcraft.platform.fabric.entity

import com.doublemoon1119.mahjongcraft.platform.fabric.registry.ModEntities
import net.minecraft.entity.Entity
import net.minecraft.entity.EntityType
import net.minecraft.entity.data.DataTracker
import net.minecraft.entity.data.TrackedDataHandlerRegistry
import net.minecraft.nbt.NbtCompound
import net.minecraft.registry.Registries
import net.minecraft.sound.SoundCategory
import net.minecraft.sound.SoundEvents
import net.minecraft.util.Identifier
import net.minecraft.world.World
import kotlin.uuid.Uuid

data class WinSettlementDetailSnapshot(val id: String, val type: String, val values: List<String>)

data class WinSettlementMeldSnapshot(
    val assetKeys: List<String>,
    val faceDownIndices: Set<Int>,
)

data class WinSettlementWinnerSnapshot(
    val playerId: String,
    val seatIndex: Int,
    val isAi: Boolean,
    val responsiblePlayerId: String?,
    val totalScore: Int,
    val handAssetKeys: List<String>,
    val melds: List<WinSettlementMeldSnapshot>,
    val winningTileAssetKey: String,
    val details: List<WinSettlementDetailSnapshot>,
)

data class WinSettlementRankingSnapshot(
    val playerId: String,
    val seatIndex: Int,
    val isAi: Boolean,
    val previousScore: Int,
    val currentScore: Int,
    val previousRank: Int,
    val currentRank: Int,
)

data class WinSettlementRevealTimingSnapshot(
    val initialFadeTicks: Int,
    val entryStaggerTicks: Int,
    val scoreRevealTicks: Int,
    val readingTicks: Int,
)

data class WinSettlementSoundCueSnapshot(
    val eventTick: Long,
    val soundId: String,
    val volume: Float,
    val pitch: Float,
)

/** 持久化胡牌詳情與最終排行的世界舞台。 */
class WinSettlementPresentationEntity(
    type: EntityType<out WinSettlementPresentationEntity> = ModEntities.winSettlementPresentation,
    world: World,
) : Entity(type, world) {
    private val playedSoundIndexes = mutableSetOf<Int>()
    val managedTableId: Uuid? get() = dataTracker[TABLE_ID].takeIf(String::isNotBlank)?.let { runCatching { Uuid.parse(it) }.getOrNull() }
    val startGameTime: Long get() = dataTracker[START_GAME_TIME]
    val endGameTime: Long get() = dataTracker[END_GAME_TIME]
    val outcomeId: String get() = dataTracker[OUTCOME_ID]
    val templateKey: String get() = dataTracker[TEMPLATE_KEY]
    val isTsumo: Boolean get() = dataTracker[IS_TSUMO]
    val winners: List<WinSettlementWinnerSnapshot> get() = decodeWinners(dataTracker[WINNERS])
    val rankings: List<WinSettlementRankingSnapshot> get() = decodeRankings(dataTracker[RANKINGS])
    val revealTiming: WinSettlementRevealTimingSnapshot get() = decodeRevealTiming(dataTracker[REVEAL_TIMING])
    val customSoundCues: List<WinSettlementSoundCueSnapshot> get() = decodeSoundCues(dataTracker[CUSTOM_SOUND_CUES])

    init {
        setNoGravity(true)
    }

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
        require(winners.isNotEmpty() && rankings.isNotEmpty())
        dataTracker.set(TABLE_ID, tableId.toString())
        dataTracker.set(START_GAME_TIME, startGameTime)
        dataTracker.set(REVEAL_TIMING, encodeRevealTiming(revealTiming))
        dataTracker.set(CUSTOM_SOUND_CUES, encodeSoundCues(customSoundCues))
        dataTracker.set(END_GAME_TIME, startGameTime + durationTicks(winners, revealTiming))
        dataTracker.set(OUTCOME_ID, outcomeId)
        dataTracker.set(TEMPLATE_KEY, templateKey)
        dataTracker.set(IS_TSUMO, isTsumo)
        dataTracker.set(WINNERS, encodeWinners(winners))
        dataTracker.set(RANKINGS, encodeRankings(rankings))
    }

    fun elapsedTicks(tickDelta: Float): Double = world.time + tickDelta.toDouble() - startGameTime
    fun winnerStartTick(index: Int): Long = winners.take(index).sumOf { winnerDurationTicks(it, revealTiming) }
    fun rankingStartTick(): Long = winners.sumOf { winnerDurationTicks(it, revealTiming) }
    fun winnerDurationTicks(winner: WinSettlementWinnerSnapshot): Long = winnerDurationTicks(winner, revealTiming)

    override fun tick() {
        super.tick()
        if (world.isClient) return
        val elapsed = world.time - startGameTime
        if (elapsed >= 0) playPendingSounds(elapsed)
        if (endGameTime > startGameTime && world.time >= endGameTime) discard()
    }

    private fun playPendingSounds(elapsed: Long) {
        if (customSoundCues.isNotEmpty()) {
            playCustomSounds(elapsed)
            playRankingSounds(elapsed, customSoundCues.size)
            return
        }
        var eventIndex = 0
        winners.forEachIndexed { winnerIndex, winner ->
            val start = winnerStartTick(winnerIndex)
            val entries = winner.details.filter { it.type == DETAIL_ENTRIES }.sumOf { it.values.size / ENTRY_VALUE_COUNT }
            repeat(entries) { entryIndex ->
                playOnce(
                    eventIndex++,
                    start + revealTiming.initialFadeTicks + entryIndex * revealTiming.entryStaggerTicks,
                    elapsed,
                    SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP,
                    SettlementPresentationSoundSpec.DETAIL_VOLUME,
                    (
                        SettlementPresentationSoundSpec.DETAIL_BASE_PITCH +
                            entryIndex * SettlementPresentationSoundSpec.DETAIL_PITCH_STEP
                        ).coerceAtMost(1.55f),
                )
            }
            if (winner.hasPostEntrySummary) {
                playOnce(
                    eventIndex++,
                    start + revealTiming.initialFadeTicks + entries * revealTiming.entryStaggerTicks,
                    elapsed,
                    SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP,
                    SettlementPresentationSoundSpec.DETAIL_VOLUME,
                    (
                        SettlementPresentationSoundSpec.DETAIL_BASE_PITCH +
                            entries * SettlementPresentationSoundSpec.DETAIL_PITCH_STEP
                        ).coerceAtMost(1.55f),
                )
            }
            val scoreTick = start + revealTiming.initialFadeTicks + (entries + if (winner.hasPostEntrySummary) 1 else 0) * revealTiming.entryStaggerTicks
            SettlementPresentationSoundSpec.TOTAL_SCORE_MELODY_PITCHES.forEachIndexed { noteIndex, pitch ->
                playOnce(
                    eventIndex++,
                    scoreTick + noteIndex * SettlementPresentationSoundSpec.TOTAL_SCORE_MELODY_INTERVAL_TICKS,
                    elapsed,
                    SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP,
                    SettlementPresentationSoundSpec.TOTAL_SCORE_VOLUME,
                    pitch,
                )
            }
        }
        playRankingSounds(elapsed, eventIndex)
    }

    private fun playCustomSounds(elapsed: Long) {
        customSoundCues.forEachIndexed { index, cue ->
            val identifier = Identifier.tryParse(cue.soundId) ?: return@forEachIndexed
            if (!Registries.SOUND_EVENT.containsId(identifier)) return@forEachIndexed
            val sound = Registries.SOUND_EVENT.get(identifier) ?: return@forEachIndexed
            playOnce(index, cue.eventTick, elapsed, sound, cue.volume, cue.pitch)
        }
    }

    private fun playRankingSounds(elapsed: Long, firstEventIndex: Int) {
        var eventIndex = firstEventIndex
        val rankingStart = rankingStartTick()
        rankings.indices.forEach { rowIndex ->
            playOnce(
                eventIndex++,
                rankingStart + RANKING_ROW_START_TICKS + rowIndex * RANKING_ROW_STAGGER_TICKS,
                elapsed,
                SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP,
                SettlementPresentationSoundSpec.ROW_VOLUME,
                SettlementPresentationSoundSpec.ROW_BASE_PITCH + rowIndex * SettlementPresentationSoundSpec.ROW_PITCH_STEP,
            )
        }
        val rankingChanged = rankings.any { it.previousRank != it.currentRank }
        if (rankingChanged) {
            playOnce(
                eventIndex,
                rankingStart + RANKING_SETTLED_SOUND_TICKS,
                elapsed,
                SoundEvents.ENTITY_PLAYER_LEVELUP,
                SettlementPresentationSoundSpec.RANKING_SETTLED_VOLUME,
                SettlementPresentationSoundSpec.RANKING_SETTLED_PITCH,
            )
        }
    }

    private fun playOnce(index: Int, eventTick: Long, elapsed: Long, sound: net.minecraft.sound.SoundEvent, volume: Float, pitch: Float) {
        if (!playedSoundIndexes.add(index) || elapsed < eventTick) {
            if (elapsed < eventTick) playedSoundIndexes.remove(index)
            return
        }
        if (elapsed > eventTick + SettlementPresentationSoundSpec.EVENT_GRACE_TICKS) return
        world.playSound(
            null,
            x,
            y,
            z,
            sound,
            SoundCategory.PLAYERS,
            volume,
            pitch,
        )
    }

    override fun isCollidable() = false
    override fun isPushable() = false
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
        playedSoundIndexes += nbt.getIntArray("PlayedSoundIndexes").toSet()
    }

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
        nbt.putIntArray("PlayedSoundIndexes", playedSoundIndexes.toIntArray())
    }

    companion object {
        const val WIDTH = 6f
        const val HEIGHT = 4f
        const val INITIAL_FADE_TICKS = 16L
        const val ENTRY_STAGGER_TICKS = 8L
        const val SCORE_REVEAL_TICKS = 18L
        const val HAN_FU_REVEAL_TICKS = 8L
        const val READING_TICKS = 60L
        const val TRANSITION_TICKS = 12L
        const val RANKING_TICKS = 150L
        const val FADE_OUT_TICKS = 18L
        const val RANKING_ROW_START_TICKS = 14L
        const val RANKING_ROW_STAGGER_TICKS = 7L
        const val RANKING_SETTLED_SOUND_TICKS = 83L
        const val DETAIL_TEXT = "T"
        const val DETAIL_TILES = "L"
        const val DETAIL_ENTRIES = "E"
        const val ENTRY_VALUE_COUNT = 4
        private const val F = '\u001f'
        private const val R = '\u001e'
        private const val L = '\u001d'
        private const val G = '\u001c'

        fun winnerDurationTicks(winner: WinSettlementWinnerSnapshot, timing: WinSettlementRevealTimingSnapshot): Long {
            val entries = winner.details.filter { it.type == DETAIL_ENTRIES }.sumOf { it.values.size / ENTRY_VALUE_COUNT }
            return timing.initialFadeTicks + entries * timing.entryStaggerTicks +
                (if (winner.hasPostEntrySummary) HAN_FU_REVEAL_TICKS else 0L) + timing.scoreRevealTicks + timing.readingTicks + TRANSITION_TICKS
        }

        private val WinSettlementWinnerSnapshot.hasPostEntrySummary: Boolean
            get() = details.any { it.id.endsWith(":riichi_han_fu") || it.id.endsWith(":riichi_yakuman_total") }

        fun durationTicks(winners: List<WinSettlementWinnerSnapshot>, timing: WinSettlementRevealTimingSnapshot): Long = winners
            .sumOf { winnerDurationTicks(it, timing) } + RANKING_TICKS + FADE_OUT_TICKS

        private val TABLE_ID = DataTracker.registerData(WinSettlementPresentationEntity::class.java, TrackedDataHandlerRegistry.STRING)
        private val START_GAME_TIME = DataTracker.registerData(WinSettlementPresentationEntity::class.java, TrackedDataHandlerRegistry.LONG)
        private val END_GAME_TIME = DataTracker.registerData(WinSettlementPresentationEntity::class.java, TrackedDataHandlerRegistry.LONG)
        private val OUTCOME_ID = DataTracker.registerData(WinSettlementPresentationEntity::class.java, TrackedDataHandlerRegistry.STRING)
        private val TEMPLATE_KEY = DataTracker.registerData(WinSettlementPresentationEntity::class.java, TrackedDataHandlerRegistry.STRING)
        private val IS_TSUMO = DataTracker.registerData(WinSettlementPresentationEntity::class.java, TrackedDataHandlerRegistry.BOOLEAN)
        private val WINNERS = DataTracker.registerData(WinSettlementPresentationEntity::class.java, TrackedDataHandlerRegistry.STRING)
        private val RANKINGS = DataTracker.registerData(WinSettlementPresentationEntity::class.java, TrackedDataHandlerRegistry.STRING)
        private val REVEAL_TIMING = DataTracker.registerData(WinSettlementPresentationEntity::class.java, TrackedDataHandlerRegistry.STRING)
        private val CUSTOM_SOUND_CUES = DataTracker.registerData(WinSettlementPresentationEntity::class.java, TrackedDataHandlerRegistry.STRING)

        private fun encodeRevealTiming(value: WinSettlementRevealTimingSnapshot) = listOf(
            value.initialFadeTicks,
            value.entryStaggerTicks,
            value.scoreRevealTicks,
            value.readingTicks,
        ).joinToString(F.toString())

        private fun decodeRevealTiming(encoded: String): WinSettlementRevealTimingSnapshot {
            val values = encoded.split(F).mapNotNull(String::toIntOrNull)
            return if (values.size == 4) {
                WinSettlementRevealTimingSnapshot(values[0], values[1], values[2], values[3])
            } else {
                WinSettlementRevealTimingSnapshot(INITIAL_FADE_TICKS.toInt(), ENTRY_STAGGER_TICKS.toInt(), SCORE_REVEAL_TICKS.toInt(), READING_TICKS.toInt())
            }
        }

        private fun encodeSoundCues(values: List<WinSettlementSoundCueSnapshot>) = values.joinToString(R.toString()) {
            listOf(it.eventTick, it.soundId, it.volume, it.pitch).joinToString(F.toString())
        }

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

        private fun encodeRankings(values: List<WinSettlementRankingSnapshot>) = values.joinToString(R.toString()) {
            listOf(it.playerId, it.seatIndex, if (it.isAi) 1 else 0, it.previousScore, it.currentScore, it.previousRank, it.currentRank).joinToString(F.toString())
        }

        private fun decodeRankings(encoded: String) = encoded.split(R).mapNotNull { row ->
            val f = row.split(F)
            if (f.size != 7) return@mapNotNull null
            WinSettlementRankingSnapshot(f[0], f[1].toIntOrNull() ?: return@mapNotNull null, f[2] == "1", f[3].toIntOrNull() ?: return@mapNotNull null, f[4].toIntOrNull() ?: return@mapNotNull null, f[5].toIntOrNull() ?: return@mapNotNull null, f[6].toIntOrNull() ?: return@mapNotNull null)
        }

        private fun String.splitList(): List<String> = takeIf(String::isNotBlank)?.split(L).orEmpty()
    }
}
