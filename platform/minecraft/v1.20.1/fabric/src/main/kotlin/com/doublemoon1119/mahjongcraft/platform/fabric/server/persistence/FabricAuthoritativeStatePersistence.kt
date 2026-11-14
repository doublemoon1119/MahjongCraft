package com.doublemoon1119.mahjongcraft.platform.fabric.server.persistence

import com.doublemoon1119.mahjongcraft.flow.common.room.model.Room
import com.doublemoon1119.mahjongcraft.flow.persistence.format.state.AuthoritativeStatePersistenceCodec
import com.doublemoon1119.mahjongcraft.flow.server.lifecycle.ServerSessionStateCleaner
import com.doublemoon1119.mahjongcraft.flow.server.lifecycle.ServerSessionStateRestorer
import com.doublemoon1119.mahjongcraft.flow.server.state.AuthoritativeStateBusyException
import com.doublemoon1119.mahjongcraft.flow.server.state.AuthoritativeStateStore
import com.doublemoon1119.mahjongcraft.flow.server.state.AuthoritativeStateUpdate
import com.doublemoon1119.mahjongcraft.platform.fabric.logging.mahjongCraftLogger
import com.doublemoon1119.mahjongcraft.platform.minecraft.character.AiCharacterParticipantCapture
import com.doublemoon1119.mahjongcraft.platform.minecraft.character.AiCharacterRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.character.AiCharacterStateSnapshot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import net.minecraft.server.MinecraftServer
import kotlin.uuid.Uuid

/**
 * 將單次 Minecraft server session 與其 overworld [MahjongAuthoritativePersistentState] 接起來。
 *
 * @property codec 編碼與解碼權威狀態的持久化 codec。
 * @property store 目前的權威狀態儲存庫。
 * @property stateRestorer 將持久化快照還原到領域儲存庫。
 * @property stateCleaner 初始化失敗時清除本次 session 的衍生與權威記憶體，不寫回存檔。
 * @property characterRegistry 已凍結的角色描述登記表。
 * @property dataStatus 回報牌局或角色區段的讀取失敗。
 */
class FabricAuthoritativeStatePersistence(
    private val codec: AuthoritativeStatePersistenceCodec,
    private val store: AuthoritativeStateStore,
    private val stateRestorer: ServerSessionStateRestorer,
    private val stateCleaner: ServerSessionStateCleaner,
    private val characterRegistry: AiCharacterRegistry,
    private val dataStatus: FabricMahjongDataStatus,
) {
    private val logger = mahjongCraftLogger(FabricAuthoritativeStatePersistence::class)

    /** 目前 server session 使用的 Minecraft persistent state。 */
    private var persistentState: MahjongAuthoritativePersistentState? = null

    /**
     * 載入 [server] 所屬世界的權威狀態，並開始轉送後續 dirty snapshot。
     *
     * @param server 要載入權威狀態的 Minecraft 伺服器。
     */
    suspend fun attach(server: MinecraftServer) {
        check(persistentState == null) { "Authoritative persistence is already attached to a server session" }
        try {
            val state = attachPersistenceState(
                load = {
                    loadPersistentStateStrict(
                        server = server,
                        key = MahjongAuthoritativePersistentState.STORAGE_KEY,
                        decode = { nbt -> MahjongAuthoritativePersistentState.fromNbt(nbt, codec, characterRegistry) },
                        create = { MahjongAuthoritativePersistentState.create(codec, characterRegistry) },
                    )
                },
                initialize = { state ->
                    store.load(state.snapshot)
                    val restoreResult = stateRestorer.restore(state.snapshot)
                    restoreResult.membershipConflicts.forEach { conflict ->
                        logger.warn(
                            "Skipped restoring membership for player {} because saved state references multiple tables: {}",
                            conflict.playerId,
                            conflict.venueIds,
                        )
                    }
                    restoreResult.snapshotFailures.forEach { failure ->
                        logger.warn(
                            "Restored conservative snapshots after rule projection failed gameId={} rule={}; authoritative data was not changed",
                            failure.gameId,
                            failure.ruleModuleId,
                            failure.cause,
                        )
                    }
                    store.setDirtyListener(state::update)
                    logger.debug(
                        "Attached authoritative persistence with {} room(s), {} game(s), and {} membership conflict(s)",
                        state.snapshot.rooms.size,
                        state.snapshot.games.size,
                        restoreResult.membershipConflicts.size,
                    )
                    // 完整還原與診斷成功後才將合法的還原調整交給世界保存容器。
                    if (store.isDirty()) state.update(store.snapshot())
                },
                cleanupFailedAttach = {
                    withContext(NonCancellable) {
                        store.setDirtyListener {}
                        stateCleaner.clear()
                    }
                },
            )
            persistentState = state
            state.characterDataFailure?.let { cause ->
                logger.warn("AI character data could not be read; its original section is preserved and character changes are disabled", cause)
                dataStatus.report(MahjongDataProblem(MahjongDataSource.AI_CHARACTERS, persistentStateDisplayPath(MahjongAuthoritativePersistentState.STORAGE_KEY), classifyDataLoadFailure(cause)))
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (exception: Exception) {
            logger.error("Failed to attach authoritative persistence for the current server session", exception)
            throw exception
        }
    }

    /** 取得同一版本的牌局與角色快照；沒有有效 session 時為 null。 */
    fun characterSnapshot(): AiCharacterStateSnapshot? = persistentState?.characterSnapshot()

    /**
     * 在目前 session 的保存封套內提交外部實體成員。
     *
     * @param capture 預檢已擷取的描述。
     * @param validate 取得執行權後立即重新驗證來源、席位與公開描述。
     * @param rejected 預檢失效的結果。
     * @param action 不等待權威鎖的加入操作。
     * @return 加入操作的結果；沒有保存 session 時拒絕。
     */
    internal suspend fun <T> withParticipant(
        capture: AiCharacterParticipantCapture,
        validate: () -> Boolean,
        rejected: () -> T,
        action: suspend () -> T,
    ): T {
        val saved = persistentState ?: return rejected()
        return saved.withParticipant(capture, { persistentState === saved && validate() }, rejected, action)
    }

    /**
     * 讓開局交易使用同一次預檢的全部角色，不在交易內呼叫原生工廠。
     *
     * @param captures 本次開局的外觀。
     * @param validate 取得執行權後立即重新驗證來源、席位與公開描述。
     * @param rejected 預檢失效的結果。
     * @param action 不等待權威鎖的開局操作。
     * @return 開局操作的結果。
     */
    internal suspend fun <T> withParticipants(
        captures: List<AiCharacterParticipantCapture>,
        validate: () -> Boolean,
        rejected: () -> T,
        action: suspend () -> T,
    ): T {
        if (captures.isEmpty()) return action()
        val saved = persistentState ?: return rejected()
        return saved.withParticipants(captures, { persistentState === saved && validate() }, rejected, action)
    }

    /**
     * 只在原 session 與房間仍相同時刷新等待角色，不讓世界回呼進入鎖內。
     *
     * @param expected 等待房間的預檢狀態。
     * @param sessionId 預檢所屬的保存 session。
     * @param capture 已脫離原生物件的外觀。
     * @return 完成更新或本來即相同時為 true。
     */
    internal suspend fun refreshParticipant(expected: Room, sessionId: Uuid, capture: AiCharacterParticipantCapture): Boolean = try {
        store.update(waitForLock = false) { current ->
            val saved = persistentState
            val metadata = saved?.characterSnapshot()
            val accepted = current.rooms[expected.id] == expected &&
                metadata?.sessionId == sessionId &&
                expected.admission.instanceId == capture.roomInstanceId &&
                (expected.admission.memberIdentities[capture.playerId] == capture.identity || expected.admission.excludedParticipants[capture.identity] == capture.playerId) &&
                metadata.characters != null
            if (!accepted) return@update AuthoritativeStateUpdate(current, false)
            val refreshed = metadata.characters?.participant(capture.playerId) == capture.appearance || saved.refreshParticipant(current, capture)
            AuthoritativeStateUpdate(current, refreshed)
        }
    } catch (_: AuthoritativeStateBusyException) {
        false
    }

    /** 停止 dirty 轉送；Minecraft 仍持有 state，會在既有世界保存流程中寫入磁碟。 */
    suspend fun detach() {
        store.setDirtyListener {}
        persistentState = null
        logger.debug("Detached authoritative persistence from the current server session")
    }

    /**
     * 載入權威狀態；無法讀取時不中斷伺服器啟動，改為停用本次 session 的麻將功能。
     *
     * @param server 要載入權威狀態的 Minecraft 伺服器。
     * @return 成功接上時為 true；無法讀取、已停用麻將功能時為 false。
     */
    suspend fun attachOrDisable(server: MinecraftServer): Boolean = attachAuthoritativeStateOrDisable(
        attach = { attach(server) },
        markUnavailable = store::markUnavailable,
        report = { problem ->
            logger.warn("Mahjong features are disabled for this session; {} was left unchanged", problem.file)
            dataStatus.report(problem)
        },
    )
}

/**
 * 接上權威狀態；無法讀取時拒絕之後的所有寫入並回報，讓世界照常進入、只停用麻將功能。
 *
 * 失敗時 [attach] 已清除本次建立的記憶體並保留原檔；之後的寫入若建立在清空的狀態上，會與原始資料衝突，
 * 因此由 [markUnavailable] 一律拒絕。取消與致命錯誤照常往外傳。
 *
 * @param attach 載入並接上權威狀態。
 * @param markUnavailable 拒絕之後所有權威狀態寫入。
 * @param report 回報無法讀取的資料。
 * @return 成功接上時為 true。
 */
internal suspend fun attachAuthoritativeStateOrDisable(
    attach: suspend () -> Unit,
    markUnavailable: suspend () -> Unit,
    report: (MahjongDataProblem) -> Unit,
): Boolean = try {
    attach()
    true
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (exception: Exception) {
    markUnavailable()
    report(
        MahjongDataProblem(
            source = MahjongDataSource.AUTHORITATIVE_STATE,
            file = persistentStateDisplayPath(MahjongAuthoritativePersistentState.STORAGE_KEY),
            cause = classifyDataLoadFailure(exception),
        ),
    )
    false
}
