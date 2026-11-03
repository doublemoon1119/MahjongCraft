package com.doublemoon1119.mahjongcraft.platform.fabric.server.persistence

import com.doublemoon1119.mahjongcraft.flow.persistence.format.state.AuthoritativeStatePersistenceCodec
import com.doublemoon1119.mahjongcraft.flow.server.state.AuthoritativeStateSnapshot
import com.doublemoon1119.mahjongcraft.platform.fabric.logging.mahjongCraftLogger
import com.doublemoon1119.mahjongcraft.platform.minecraft.character.AiCharacterMetadata
import com.doublemoon1119.mahjongcraft.platform.minecraft.character.AiCharacterMetadataCodec
import com.doublemoon1119.mahjongcraft.platform.minecraft.character.AiCharacterParticipantCapture
import com.doublemoon1119.mahjongcraft.platform.minecraft.character.AiCharacterRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.character.AiCharacterSelectionResult
import com.doublemoon1119.mahjongcraft.platform.minecraft.character.AiCharacterStateSnapshot
import com.doublemoon1119.mahjongcraft.platform.minecraft.character.AiCharacterStateStore
import com.doublemoon1119.mahjongcraft.platform.minecraft.metadata.MinecraftModMetadata
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import net.minecraft.nbt.NbtCompound
import net.minecraft.nbt.NbtElement
import net.minecraft.world.PersistentState
import kotlin.coroutines.cancellation.CancellationException
import kotlin.uuid.Uuid

/**
 * 在同一份世界資料保存權威牌局與對應的角色快照。
 *
 * 寫入只讀取一次完整封套。角色區段無法解碼時保留其原始 NBT，牌局仍可更新。
 *
 * @property codec 權威牌局 codec。
 * @param initialSnapshot 已載入的牌局。
 * @param characters 已驗證的角色資料；無法讀取時為 null。
 * @param registry 已登記的角色描述。
 * @property originalCharacterData 讀取失敗時須原樣保留的區段。
 * @property characterDataFailure 角色區段的讀取失敗；不代表牌局區段無法讀取。
 */
class MahjongAuthoritativePersistentState private constructor(
    private val codec: AuthoritativeStatePersistenceCodec,
    initialSnapshot: AuthoritativeStateSnapshot,
    characters: AiCharacterMetadata?,
    registry: AiCharacterRegistry,
    private val originalCharacterData: NbtElement?,
    val characterDataFailure: Throwable?,
) : PersistentState() {
    /** 記錄保存容量與診斷的 logger。 */
    private val logger = mahjongCraftLogger(MahjongAuthoritativePersistentState::class)

    /** 不受網路序列化設定影響的角色 codec。 */
    private val characterCodec = AiCharacterMetadataCodec()

    /** 同一房間的加入、開局與刷新不共用同一份暫態擷取。 */
    private val participantCaptureScopes = ParticipantCaptureScopes()

    /** 同時保存權威與角色資料的單一來源。 */
    private val characterState = AiCharacterStateStore(registry, ::markDirty).apply { restore(initialSnapshot, characters) }

    /** 目前的權威牌局；與角色快照共用同一保存封套。 */
    val snapshot: AuthoritativeStateSnapshot get() = characterState.snapshot().authoritative

    /** 讀取一次完整保存封套，供歷史與角色同步取得同一版本。 */
    fun characterSnapshot(): AiCharacterStateSnapshot = characterState.snapshot()

    /**
     * 讓加入交易擷取預先驗證的外觀，結束後一律清除暫態資料。
     *
     * @param capture 交易外擷取的描述。
     * @param validate 取得同桌執行權後重新確認資格與描述，不得掛起或修改世界。
     * @param rejected 預檢過期時的拒絕結果。
     * @param action 會立即嘗試權威交易的加入操作。
     * @return 加入操作的結果。
     */
    internal suspend fun <T> withParticipant(
        capture: AiCharacterParticipantCapture,
        validate: () -> Boolean,
        rejected: () -> T,
        action: suspend () -> T,
    ): T = withParticipants(listOf(capture), validate, rejected, action)

    /**
     * 以同一個開局交易凍結全部已預檢的角色，結束後清除暫態描述。
     *
     * @param captures 同一次預檢的角色描述；沒有外部成員時可為空。
     * @param validate 取得同桌執行權後重新確認資格與描述，不得掛起或修改世界。
     * @param rejected 預檢過期時的拒絕結果。
     * @param action 會立即嘗試權威交易的開局操作。
     * @return 開局操作的結果。
     */
    internal suspend fun <T> withParticipants(
        captures: List<AiCharacterParticipantCapture>,
        validate: () -> Boolean,
        rejected: () -> T,
        action: suspend () -> T,
    ): T {
        if (captures.isEmpty()) return action()
        require(captures.map { it.roomInstanceId }.distinct().size == 1) { "Captures must belong to one room" }
        require(captures.map { it.playerId }.distinct().size == captures.size) { "Duplicate participant capture" }
        return participantCaptureScopes.withScope(captures.first().roomInstanceId) {
            currentCoroutineContext().ensureActive()
            if (!validate() || characterSnapshot().characters == null) return@withScope rejected()
            val prepared = mutableListOf<Uuid>()
            try {
                captures.forEach { capture ->
                    characterState.prepareParticipant(capture)
                    prepared += capture.playerId
                }
                action()
            } finally {
                prepared.forEach(characterState::releaseParticipant)
            }
        }
    }

    /**
     * 在已持有權威交易時更新等待成員的公開外觀，不改寫已開始場次。
     *
     * @param authoritative 同一交易目前的完整狀態。
     * @param capture 已在世界執行緒驗證的描述。
     * @return 已刷新時為 true；同一房間已有擷取時為 false，下輪再試。
     */
    internal fun refreshParticipant(authoritative: AuthoritativeStateSnapshot, capture: AiCharacterParticipantCapture): Boolean = participantCaptureScopes.tryWithScope(capture.roomInstanceId) {
        characterState.prepareParticipant(capture)
        try {
            characterState.update(authoritative)
            true
        } finally {
            characterState.releaseParticipant(capture.playerId)
        }
    } ?: false

    /**
     * 更新牌局並擷取新場次的角色，不呼叫世界或第三方工廠。
     *
     * @param snapshot 同一權威提交的最新狀態。
     */
    fun update(snapshot: AuthoritativeStateSnapshot) {
        characterState.update(snapshot)
    }

    /**
     * 在權威交易內變更房間角色，不允許對局中切換。
     *
     * @param authoritative 交易持有的權威狀態。
     * @param tableId 房間所在桌子。
     * @param playerId 該房間的 AI。
     * @param operatorId 發起選擇的玩家。
     * @param characterId 登記的角色 ID。
     * @return 權限與變更結果。
     */
    internal fun selectCharacter(
        authoritative: AuthoritativeStateSnapshot,
        tableId: Uuid,
        playerId: Uuid,
        operatorId: Uuid,
        characterId: String,
    ): AiCharacterSelectionResult = characterState.select(authoritative, tableId, playerId, operatorId, characterId)

    /**
     * 將同一封套內的牌局與角色一起保存；無法讀取的角色區段保持原樣。
     *
     * @param nbt 世界提供的保存容器。
     * @return 寫入後的容器。
     */
    override fun writeNbt(nbt: NbtCompound): NbtCompound {
        val current = characterState.snapshot()
        val authoritative = current.authoritative
        val encoded = codec.encode(authoritative.rooms.values, authoritative.games.values, authoritative.historyRecordingState).encodeToByteArray()
        if (encoded.size > LARGE_STATE_WARNING_BYTES) {
            logger.warn("Authoritative state is unusually large and slows world saving: bytes={}, rooms={}, games={}, pendingHistoryEvents={}", encoded.size, authoritative.rooms.size, authoritative.games.size, authoritative.historyRecordingState.pendingEvents.size)
        }
        nbt.putByteArray(NBT_KEY_STATE, encoded)
        val metadata = current.characters
        when {
            metadata != null -> nbt.putByteArray(NBT_KEY_CHARACTERS, characterCodec.encode(metadata).encodeToByteArray())
            originalCharacterData != null -> nbt.put(NBT_KEY_CHARACTERS, originalCharacterData.copy())
            else -> nbt.remove(NBT_KEY_CHARACTERS)
        }
        return nbt
    }

    /** 保存容器的固定名稱與建立方式。 */
    companion object {
        /** 世界資料檔名稱。 */
        const val STORAGE_KEY: String = "${MinecraftModMetadata.MOD_ID}_authoritative_state"

        /** 權威區段的容量警告門檻。 */
        internal const val LARGE_STATE_WARNING_BYTES: Int = 1024 * 1024

        /** 權威區段的 JSON UTF-8 位元組。 */
        internal const val NBT_KEY_STATE: String = "state"

        /** 角色區段的 JSON UTF-8 位元組。 */
        internal const val NBT_KEY_CHARACTERS: String = "characters"

        /**
         * 為沒有既有檔案的世界建立空保存資料。
         *
         * @param codec 權威牌局 codec。
         * @param registry 已登記的角色描述。
         * @return 可接受角色選擇的初始容器。
         */
        fun create(codec: AuthoritativeStatePersistenceCodec, registry: AiCharacterRegistry): MahjongAuthoritativePersistentState = MahjongAuthoritativePersistentState(
            codec = codec,
            initialSnapshot = AuthoritativeStateSnapshot(),
            characters = AiCharacterMetadata(emptyMap(), emptyMap()),
            registry = registry,
            originalCharacterData = null,
            characterDataFailure = null,
        )

        /**
         * 讀取世界資料；牌局失敗時拒絕載入，角色失敗時只隔離角色區段。
         *
         * @param nbt 已存在的世界資料。
         * @param codec 權威牌局 codec。
         * @param registry 已登記的角色描述。
         * @return 包含角色讀取狀態的保存容器。
         */
        fun fromNbt(
            nbt: NbtCompound,
            codec: AuthoritativeStatePersistenceCodec,
            registry: AiCharacterRegistry,
        ): MahjongAuthoritativePersistentState {
            require(nbt.contains(NBT_KEY_STATE, NbtElement.BYTE_ARRAY_TYPE.toInt())) { "Authoritative saved state is missing its byte array payload" }
            val decoded = codec.decode(nbt.getByteArray(NBT_KEY_STATE).decodeToString())
            val snapshot = AuthoritativeStateSnapshot(decoded.rooms, decoded.games, decoded.historyRecordingState)
            val original = nbt.get(NBT_KEY_CHARACTERS)?.copy()
            var failure: Throwable? = null
            val metadata = try {
                require(nbt.contains(NBT_KEY_CHARACTERS, NbtElement.BYTE_ARRAY_TYPE.toInt())) { "Character saved state is missing its byte array payload" }
                AiCharacterMetadataCodec().decode(nbt.getByteArray(NBT_KEY_CHARACTERS).decodeToString(throwOnInvalidSequence = true)).also { loaded ->
                    val rosters = snapshot.rooms.mapValues { (_, room) -> room.aiPlayerStrategyKeys.keys } +
                        snapshot.games.mapValues { (_, game) -> game.aiPlayerStrategyKeys.keys }
                    require(loaded.selections.mapValues { (_, players) -> players.keys } == rosters) { "Character selections do not match the saved rosters" }
                    snapshot.games.values.forEach { game ->
                        require(loaded.matches[game.matchId]?.keys == game.aiPlayerStrategyKeys.keys) { "Character opening snapshot does not match the saved match roster" }
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (cause: Exception) {
                failure = cause
                null
            }
            return MahjongAuthoritativePersistentState(
                codec = codec,
                initialSnapshot = snapshot,
                characters = metadata,
                registry = registry,
                originalCharacterData = original,
                characterDataFailure = failure,
            )
        }
    }
}
