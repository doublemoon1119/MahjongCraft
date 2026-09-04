package com.doublemoon1119.mahjongcraft.platform.fabric.entity

import com.doublemoon1119.mahjongcraft.platform.fabric.registry.ModEntities
import com.doublemoon1119.mahjongcraft.platform.minecraft.animation.AnimationStep
import net.minecraft.block.Blocks
import net.minecraft.entity.EntityType
import net.minecraft.entity.data.DataTracker
import net.minecraft.entity.data.TrackedData
import net.minecraft.entity.data.TrackedDataHandlerRegistry
import net.minecraft.nbt.NbtCompound
import net.minecraft.registry.Registries
import net.minecraft.world.World
import kotlin.uuid.Uuid

/** 單張 showcase 視覺牌的同步快照。 */
data class ShowcaseCardSnapshot(
    val wingIndex: Int,
    val order: Int,
    val assetKey: String,
    val startOffsetX: Double,
    val startOffsetY: Double,
    val startOffsetZ: Double,
    val startYaw: Float,
)

/** 共享權威胡牌張的牌面與起飛前世界相對位置快照。 */
data class ShowcaseWinningTileSnapshot(
    val assetKey: String,
    val startOffsetX: Double,
    val startOffsetY: Double,
    val startOffsetZ: Double,
    val startYaw: Float,
)

/** 單一贏家展示翼的同步資料。 */
data class ShowcaseWingSnapshot(val seatIndex: Int, val cueKey: String, val cards: List<ShowcaseCardSnapshot>)

/** 可持久化的第三方 showcase 額外音效快照。 */
data class ShowcaseSoundSnapshot(val soundId: String, val tickOffset: Int, val volume: Float, val pitch: Float)

/**
 * 持久化共享胡牌 showcase 舞台；所有視覺皆由 client 依絕對時間與 [animationSeed] 決定。
 */
class WinCelebrationShowcaseEntity(
    type: EntityType<out WinCelebrationShowcaseEntity> = ModEntities.winCelebrationShowcase,
    world: World,
) : SimpleAnimatedMahjongEntity(type, world) {
    /** 所屬麻將桌。 */
    val managedTableId: Uuid?
        get() = dataTracker[TABLE_ID].takeIf(String::isNotBlank)?.let { runCatching { Uuid.parse(it) }.getOrNull() }

    /** 整段演出的開始時間。 */
    val startGameTime: Long get() = dataTracker[START_GAME_TIME]

    /** 整段演出的結束時間。 */
    val endGameTime: Long get() = dataTracker[END_GAME_TIME]

    /** 跨重載保持一致的動畫 seed。 */
    val animationSeed: Long get() = dataTracker[ANIMATION_SEED]

    /** 共享胡牌張牌面 asset key。 */
    val winningTileAssetKey: String get() = dataTracker[WINNING_TILE_ASSET_KEY]

    /** 胡牌張完整起飛所需快照；舊存檔沒有資料時為 null。 */
    val winningTileSnapshot: ShowcaseWinningTileSnapshot?
        get() = decodeWinningTile(dataTracker[WINNING_TILE_SNAPSHOT])

    /** 全部贏家翼資料。 */
    val wings: List<ShowcaseWingSnapshot>
        get() = decodeWings(dataTracker[WINGS])

    /** 正式展示階段的額外宣告式音效。 */
    val extraSounds: List<ShowcaseSoundSnapshot>
        get() = decodeSounds(dataTracker[EXTRA_SOUNDS])

    init {
        setNoGravity(true)
    }

    /** 生成前一次設定完整舞台資料。 */
    fun configure(
        tableId: Uuid,
        startGameTime: Long,
        endGameTime: Long,
        animationSeed: Long,
        winningTile: ShowcaseWinningTileSnapshot,
        wings: List<ShowcaseWingSnapshot>,
        extraSounds: List<ShowcaseSoundSnapshot> = emptyList(),
    ) {
        check(!world.isClient) { "Showcase stage must be configured by the server" }
        require(endGameTime > startGameTime) { "Showcase end time must be after start time" }
        require(wings.size in 1..3) { "Showcase must contain one to three winner wings" }
        dataTracker.set(TABLE_ID, tableId.toString())
        dataTracker.set(START_GAME_TIME, startGameTime)
        dataTracker.set(END_GAME_TIME, endGameTime)
        dataTracker.set(ANIMATION_SEED, animationSeed)
        dataTracker.set(WINNING_TILE_ASSET_KEY, winningTile.assetKey)
        dataTracker.set(WINNING_TILE_SNAPSHOT, encodeWinningTile(winningTile))
        dataTracker.set(WINGS, encodeWings(wings))
        dataTracker.set(EXTRA_SOUNDS, encodeSounds(extraSounds))
        enqueueShowcaseSounds(startGameTime)
    }

    /** 取得帶 tickDelta 的演出經過 tick。 */
    fun elapsedTicks(tickDelta: Float): Double = world.time.toDouble() + tickDelta - startGameTime

    /** 純視覺舞台不提供碰撞。 */
    override fun isCollidable(): Boolean = false

    /** 純視覺舞台不參與推擠。 */
    override fun isPushable(): Boolean = false

    /** 純視覺舞台不能被選取。 */
    override fun canHit(): Boolean = false

    /** 驗證時間線並在整段演出結束後自行移除。 */
    override fun tick() {
        super.tick()
        if (!world.isClient && endGameTime - startGameTime !in MINIMUM_VALID_DURATION_TICKS..MAXIMUM_VALID_DURATION_TICKS) {
            discard()
            return
        }
        if (endGameTime > startGameTime && world.time >= endGameTime) discard()
    }

    /** 將核心演出與額外宣告式聲音全部排入可持久化動畫佇列。 */
    private fun enqueueShowcaseSounds(startGameTime: Long) {
        val tntPlaceSoundId = Registries.SOUND_EVENT.getId(Blocks.TNT.defaultState.soundGroup.placeSound).toString()
        val cues = buildList {
            wings.indices.forEach { index ->
                add(soundStep(startGameTime, WinCelebrationCinematicTimeline.FIREWORK_LAUNCH_TICK, FIREWORK_SOUND_ID, 0.75f, 0.96f + index * 0.05f))
            }
            add(soundStep(startGameTime, WinCelebrationCinematicTimeline.TNT_PLACEMENT_SOUND_TICK, tntPlaceSoundId, 0.8f, 1.0f))
            add(soundStep(startGameTime, WinCelebrationCinematicTimeline.IGNITION_SOUND_TICK, IGNITION_SOUND_ID, 0.8f, 1.0f))
            add(soundStep(startGameTime, WinCelebrationCinematicTimeline.IGNITION_SOUND_TICK, TNT_PRIMED_SOUND_ID, 0.75f, 1.08f))
            add(soundStep(startGameTime, WinCelebrationCinematicTimeline.EXPLOSION_SOUND_TICK, EXPLOSION_SOUND_ID, 0.95f, 0.82f))
            add(soundStep(startGameTime, WinCelebrationCinematicTimeline.TITLE_REVEAL_SOUND_TICK, TITLE_SOUND_ID, 0.35f, 0.9f))
            extraSounds.forEach { sound ->
                add(soundStep(startGameTime, SHOWCASE_CONTENT_START_TICK + sound.tickOffset, sound.soundId, sound.volume, sound.pitch))
            }
        }
        enqueueAll(cues)
    }

    /** 建立只在預定時刻後一個 tick 內有效的 showcase 聲音 cue。 */
    private fun soundStep(
        startGameTime: Long,
        tickOffset: Long,
        soundId: String,
        volume: Float,
        pitch: Float,
    ): AnimationStep.PlaySound {
        val playAtGameTime = startGameTime + tickOffset
        return AnimationStep.PlaySound(soundId, volume, pitch, playAtGameTime, playAtGameTime + SOUND_GRACE_TICKS)
    }

    override fun initDataTracker() {
        dataTracker.startTracking(TABLE_ID, "")
        dataTracker.startTracking(START_GAME_TIME, 0L)
        dataTracker.startTracking(END_GAME_TIME, 0L)
        dataTracker.startTracking(ANIMATION_SEED, 0L)
        dataTracker.startTracking(WINNING_TILE_ASSET_KEY, "")
        dataTracker.startTracking(WINNING_TILE_SNAPSHOT, "")
        dataTracker.startTracking(WINGS, "")
        dataTracker.startTracking(EXTRA_SOUNDS, "")
    }

    override fun readCustomDataFromNbt(nbt: NbtCompound) {
        dataTracker.set(TABLE_ID, nbt.getString(NBT_KEY_TABLE_ID))
        dataTracker.set(START_GAME_TIME, nbt.getLong(NBT_KEY_START_GAME_TIME))
        dataTracker.set(END_GAME_TIME, nbt.getLong(NBT_KEY_END_GAME_TIME))
        dataTracker.set(ANIMATION_SEED, nbt.getLong(NBT_KEY_ANIMATION_SEED))
        dataTracker.set(WINNING_TILE_ASSET_KEY, nbt.getString(NBT_KEY_WINNING_TILE_ASSET_KEY))
        dataTracker.set(WINNING_TILE_SNAPSHOT, nbt.getString(NBT_KEY_WINNING_TILE_SNAPSHOT))
        dataTracker.set(WINGS, nbt.getString(NBT_KEY_WINGS))
        dataTracker.set(EXTRA_SOUNDS, nbt.getString(NBT_KEY_EXTRA_SOUNDS))
        readAnimationQueueFromNbt(nbt)
    }

    override fun writeCustomDataToNbt(nbt: NbtCompound) {
        nbt.putString(NBT_KEY_TABLE_ID, managedTableId?.toString().orEmpty())
        nbt.putLong(NBT_KEY_START_GAME_TIME, startGameTime)
        nbt.putLong(NBT_KEY_END_GAME_TIME, endGameTime)
        nbt.putLong(NBT_KEY_ANIMATION_SEED, animationSeed)
        nbt.putString(NBT_KEY_WINNING_TILE_ASSET_KEY, winningTileAssetKey)
        nbt.putString(NBT_KEY_WINNING_TILE_SNAPSHOT, dataTracker[WINNING_TILE_SNAPSHOT])
        nbt.putString(NBT_KEY_WINGS, dataTracker[WINGS])
        nbt.putString(NBT_KEY_EXTRA_SOUNDS, dataTracker[EXTRA_SOUNDS])
        writeAnimationQueueToNbt(nbt)
    }

    companion object {
        /** 正式展示開始前的固定前導與 billboard 轉場長度。 */
        const val SHOWCASE_CONTENT_START_TICK: Long = 300L

        /** 正式展示後的固定收束淡出長度。 */
        const val FADE_OUT_TICKS: Long = 18L

        /** 依 definition 的正式展示時長計算整段演出長度。 */
        fun totalDurationTicks(showcaseDurationTicks: Int): Long = WinCelebrationCinematicTimeline.totalDurationTicks(showcaseDurationTicks)

        /** 煙火推進正式發生的相對 tick。 */
        const val FIREWORK_LAUNCH_TICK: Long = WinCelebrationCinematicTimeline.FIREWORK_LAUNCH_TICK

        private const val MINIMUM_VALID_DURATION_TICKS = SHOWCASE_CONTENT_START_TICK + 80L + FADE_OUT_TICKS
        private const val MAXIMUM_VALID_DURATION_TICKS = SHOWCASE_CONTENT_START_TICK + 240L + FADE_OUT_TICKS

        /** 舞台追蹤範圍使用的非零寬度。 */
        const val WIDTH: Float = 8.0f

        /** 舞台追蹤範圍使用的非零高度。 */
        const val HEIGHT: Float = 4.0f

        private const val NBT_KEY_TABLE_ID = "ManagedTableId"
        private const val NBT_KEY_START_GAME_TIME = "StartGameTime"
        private const val NBT_KEY_END_GAME_TIME = "EndGameTime"
        private const val NBT_KEY_ANIMATION_SEED = "AnimationSeed"
        private const val NBT_KEY_WINNING_TILE_ASSET_KEY = "WinningTileAssetKey"
        private const val NBT_KEY_WINNING_TILE_SNAPSHOT = "WinningTileSnapshot"
        private const val NBT_KEY_WINGS = "Wings"
        private const val NBT_KEY_EXTRA_SOUNDS = "ExtraSounds"
        private const val SOUND_GRACE_TICKS = 1L
        private const val FIREWORK_SOUND_ID = "minecraft:entity.firework_rocket.launch"
        private const val IGNITION_SOUND_ID = "minecraft:item.flintandsteel.use"
        private const val TNT_PRIMED_SOUND_ID = "minecraft:entity.tnt.primed"
        private const val EXPLOSION_SOUND_ID = "minecraft:entity.generic.explode"
        private const val TITLE_SOUND_ID = "minecraft:ui.toast.challenge_complete"

        private val TABLE_ID: TrackedData<String> = DataTracker.registerData(WinCelebrationShowcaseEntity::class.java, TrackedDataHandlerRegistry.STRING)
        private val START_GAME_TIME: TrackedData<Long> = DataTracker.registerData(WinCelebrationShowcaseEntity::class.java, TrackedDataHandlerRegistry.LONG)
        private val END_GAME_TIME: TrackedData<Long> = DataTracker.registerData(WinCelebrationShowcaseEntity::class.java, TrackedDataHandlerRegistry.LONG)
        private val ANIMATION_SEED: TrackedData<Long> = DataTracker.registerData(WinCelebrationShowcaseEntity::class.java, TrackedDataHandlerRegistry.LONG)
        private val WINNING_TILE_ASSET_KEY: TrackedData<String> = DataTracker.registerData(WinCelebrationShowcaseEntity::class.java, TrackedDataHandlerRegistry.STRING)
        private val WINNING_TILE_SNAPSHOT: TrackedData<String> = DataTracker.registerData(WinCelebrationShowcaseEntity::class.java, TrackedDataHandlerRegistry.STRING)
        private val WINGS: TrackedData<String> = DataTracker.registerData(WinCelebrationShowcaseEntity::class.java, TrackedDataHandlerRegistry.STRING)
        private val EXTRA_SOUNDS: TrackedData<String> = DataTracker.registerData(WinCelebrationShowcaseEntity::class.java, TrackedDataHandlerRegistry.STRING)

        private fun encodeWinningTile(tile: ShowcaseWinningTileSnapshot): String = listOf(
            tile.assetKey,
            tile.startOffsetX,
            tile.startOffsetY,
            tile.startOffsetZ,
            tile.startYaw,
        ).joinToString("\u001f")

        private fun decodeWinningTile(encoded: String): ShowcaseWinningTileSnapshot? {
            val parts = encoded.takeIf(String::isNotBlank)?.split("\u001f", limit = 5) ?: return null
            return ShowcaseWinningTileSnapshot(
                assetKey = parts.getOrNull(0)?.takeIf(String::isNotBlank) ?: return null,
                startOffsetX = parts.getOrNull(1)?.toDoubleOrNull() ?: return null,
                startOffsetY = parts.getOrNull(2)?.toDoubleOrNull() ?: return null,
                startOffsetZ = parts.getOrNull(3)?.toDoubleOrNull() ?: return null,
                startYaw = parts.getOrNull(4)?.toFloatOrNull() ?: return null,
            )
        }

        /** 使用不會出現在 Identifier／asset key 的控制字元編碼同步資料。 */
        private fun encodeWings(wings: List<ShowcaseWingSnapshot>): String = wings.joinToString("\u001e") { wing ->
            listOf(
                wing.seatIndex.toString(),
                wing.cueKey,
                wing.cards.joinToString("\u001d") {
                    listOf(it.order, it.assetKey, it.startOffsetX, it.startOffsetY, it.startOffsetZ, it.startYaw).joinToString("\u001c")
                },
            ).joinToString("\u001f")
        }

        /** 還原 [encodeWings] 產生的同步字串，損壞項目直接略過。 */
        private fun decodeWings(encoded: String): List<ShowcaseWingSnapshot> = encoded.takeIf(String::isNotBlank)
            ?.split("\u001e")
            ?.mapIndexedNotNull { wingIndex, value ->
                val parts = value.split("\u001f", limit = 3)
                val seat = parts.getOrNull(0)?.toIntOrNull() ?: return@mapIndexedNotNull null
                val cue = parts.getOrNull(1).orEmpty()
                val cards = parts.getOrNull(2).orEmpty().takeIf(String::isNotBlank)?.split("\u001d")?.mapNotNull { card ->
                    val cardParts = card.split("\u001c", limit = 6)
                    val order = cardParts.getOrNull(0)?.toIntOrNull() ?: return@mapNotNull null
                    ShowcaseCardSnapshot(
                        wingIndex = wingIndex,
                        order = order,
                        assetKey = cardParts.getOrNull(1).orEmpty(),
                        startOffsetX = cardParts.getOrNull(2)?.toDoubleOrNull() ?: 0.0,
                        startOffsetY = cardParts.getOrNull(3)?.toDoubleOrNull() ?: 0.0,
                        startOffsetZ = cardParts.getOrNull(4)?.toDoubleOrNull() ?: 0.0,
                        startYaw = cardParts.getOrNull(5)?.toFloatOrNull() ?: 0.0f,
                    )
                }.orEmpty()
                ShowcaseWingSnapshot(seat, cue, cards)
            }.orEmpty()

        /** 將額外音效編碼成 DataTracker 可同步的緊湊字串。 */
        private fun encodeSounds(sounds: List<ShowcaseSoundSnapshot>): String = sounds.joinToString("\u001e") { sound ->
            listOf(sound.soundId, sound.tickOffset, sound.volume, sound.pitch).joinToString("\u001f")
        }

        /** 還原 [encodeSounds] 的額外音效快照，損壞項目直接略過。 */
        private fun decodeSounds(encoded: String): List<ShowcaseSoundSnapshot> = encoded.takeIf(String::isNotBlank)
            ?.split("\u001e")
            ?.mapNotNull { value ->
                val parts = value.split("\u001f", limit = 4)
                ShowcaseSoundSnapshot(
                    soundId = parts.getOrNull(0)?.takeIf(String::isNotBlank) ?: return@mapNotNull null,
                    tickOffset = parts.getOrNull(1)?.toIntOrNull() ?: return@mapNotNull null,
                    volume = parts.getOrNull(2)?.toFloatOrNull() ?: return@mapNotNull null,
                    pitch = parts.getOrNull(3)?.toFloatOrNull() ?: return@mapNotNull null,
                )
            }.orEmpty()
    }
}
