package com.doublemoon1119.mahjongcraft.platform.minecraft.character

import com.doublemoon1119.mahjongcraft.flow.common.error.ExtensionFailurePolicy
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.character.AiCharacterOwnership
import kotlin.coroutines.cancellation.CancellationException
import kotlin.uuid.Uuid

/**
 * 管理角色的唯一綁定與釋放；由平台在其主執行緒呼叫。
 *
 * @param R 平台角色型別。
 * @property failurePolicy 第三方失敗的隔離政策。
 * @property report 可隔離失敗的診斷，不包含遊戲私有資料。
 */
class AiCharacterLeaseCoordinator<R>(
    private val failurePolicy: ExtensionFailurePolicy,
    private val report: (AiCharacterBindingKey, Throwable) -> Unit,
) {
    /** 目前有效的角色。 */
    private val leases = mutableMapOf<AiCharacterBindingKey, AiCharacterLease<R>>()

    /** 防止同一個世界角色同時被兩名 AI 接管。 */
    private val claimed = mutableMapOf<Uuid, AiCharacterBindingKey>()

    /** 釋放尚未完成的身分，避免恢復舊行為時又被重新接管。 */
    private val retiring = mutableSetOf<Uuid>()

    /** 呼叫外部工廠之前先登記，防止重入。 */
    private val pending = mutableMapOf<AiCharacterBindingKey, Long>()

    /** 每次 session 重設都遞增，舊回呼不能安裝到新 session。 */
    private var generation = 0L

    /** 是否接受新的角色要求。 */
    private var active = false

    /** 開始新 session；前一份角色先安全釋放。 */
    fun startSession() {
        stopSession()
        active = true
    }

    /** 停止新工作並釋放全部角色，外部清理期間不保留可重入的舊登記。 */
    fun stopSession() {
        active = false
        generation++
        pending.clear()
        val old = leases.toMap()
        leases.clear()
        claimed.clear()
        retiring += old.values.map { it.entityId }
        var failure: Throwable? = null
        old.forEach { (key, lease) ->
            try {
                release(key, lease)
            } catch (cause: Throwable) {
                if (failure == null) failure = cause else failure.addSuppressed(cause)
            }
        }
        failure?.let { throw it }
    }

    /**
     * 取得目前已啟用的身分快照，不執行外部回呼。
     *
     * @return 已啟用角色的身分集合。
     */
    fun keys(): Set<AiCharacterBindingKey> = leases.keys.toSet()

    /**
     * 由已認領的角色身分查詢仍有效的接管，不掃描其他角色。
     *
     * @param entityId 提供者回報的固定身分。
     * @return 目前有效的接管，否則為 null。
     */
    fun keyFor(entityId: Uuid): AiCharacterBindingKey? = claimed[entityId]?.takeIf { find(it) != null }

    /**
     * 找回仍有效的角色；失效時解除綁定。
     *
     * @param key 要查詢的場次與 AI。
     * @return 仍有效的角色，否則為 null。
     */
    fun find(key: AiCharacterBindingKey): R? {
        val lease = leases[key] ?: return null
        return try {
            if (lease.isValid()) {
                lease.rider
            } else {
                remove(key)
                null
            }
        } catch (cause: Throwable) {
            remove(key)
            handleFailure(key, cause)
            null
        }
    }

    /**
     * 準備並啟用一份角色；外部回呼之後都重新確認 session。
     *
     * @param key 場次與 AI 身分。
     * @param acquire 只取得角色，不暫停行為、不加入世界。
     * @param activate 執行安全定位、自有角色生成及必要驗證。
     * @return 有效角色；無能力、失敗、重入或 session 已改變時為 null。
     */
    fun ensure(
        key: AiCharacterBindingKey,
        acquire: () -> AiCharacterLease<R>?,
        activate: (AiCharacterLease<R>) -> Boolean,
    ): R? {
        if (!active) return null
        find(key)?.let { return it }
        if (key in pending) return null
        val started = generation
        pending[key] = started
        var candidate: AiCharacterLease<R>? = null
        var prepared = false
        var installed = false
        try {
            candidate = acquire() ?: return null
            if (!active || generation != started || (candidate.entityId in claimed || candidate.entityId in retiring)) return null
            claimed[candidate.entityId] = key
            prepared = true
            if (!candidate.prepare() || !active || generation != started) return null
            if (!activate(candidate) || !active || generation != started || !candidate.isValid()) return null
            leases[key] = candidate
            installed = true
            return candidate.rider
        } catch (cause: Throwable) {
            handleFailure(key, cause)
            return null
        } finally {
            if (pending[key] == started) pending.remove(key)
            if (!installed && candidate != null) {
                if (claimed[candidate.entityId] == key && generation == started) claimed.remove(candidate.entityId)
                if (prepared) {
                    release(key, candidate)
                } else if (candidate.ownership == AiCharacterOwnership.OWNED && candidate.entityId !in claimed && candidate.entityId !in retiring) {
                    try {
                        candidate.discardOwned()
                    } catch (cause: Throwable) {
                        handleFailure(key, cause)
                    }
                }
            }
        }
    }

    /**
     * 清除已不屬於目前權威對局的角色。
     *
     * @param expected 仍應保留的角色身分。
     */
    fun retain(expected: Set<AiCharacterBindingKey>) {
        (leases.keys - expected).toList().forEach(::remove)
    }

    /**
     * 清除一份角色；刪除登記後才執行外部回呼。
     *
     * @param key 要解除的角色。
     */
    fun remove(key: AiCharacterBindingKey) {
        val lease = leases.remove(key) ?: return
        if (claimed[lease.entityId] == key) claimed.remove(lease.entityId)
        release(key, lease)
    }

    /**
     * 不論原生恢復是否成功，自有角色都要移除；借用角色絕不移除。
     *
     * @param key 診斷身分。
     * @param lease 需要釋放的租用。
     */
    private fun release(key: AiCharacterBindingKey, lease: AiCharacterLease<R>) {
        retiring += lease.entityId
        try {
            try {
                lease.release()
            } catch (cause: Throwable) {
                handleFailure(key, cause)
            } finally {
                if (lease.ownership == AiCharacterOwnership.OWNED) {
                    try {
                        lease.discardOwned()
                    } catch (cause: Throwable) {
                        handleFailure(key, cause)
                    }
                }
            }
        } finally {
            retiring -= lease.entityId
        }
    }

    /**
     * 隔離一般擴充失敗，取消與致命錯誤向外傳。
     *
     * @param key 診斷身分。
     * @param cause 外部回呼失敗。
     */
    private fun handleFailure(key: AiCharacterBindingKey, cause: Throwable) {
        if (cause is CancellationException || !failurePolicy.isIsolatable(cause)) throw cause
        report(key, cause)
    }
}
