package com.doublemoon1119.mahjongcraft.platform.fabric.client.player

import com.mojang.authlib.GameProfile
import com.mojang.authlib.minecraft.MinecraftProfileTexture
import net.minecraft.client.MinecraftClient
import net.minecraft.client.texture.PlayerSkinProvider
import net.minecraft.util.Identifier
import org.koin.core.annotation.Single
import java.util.LinkedHashMap
import java.util.LinkedHashSet
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource
import kotlin.uuid.Uuid
import kotlin.uuid.toJavaUuid

/**
 * 集中管理目前伺服器提供的玩家 profile，以及 Minecraft 原生 skin provider 的材質查詢。
 * 真人名稱不由客戶端主動猜測；正版玩家的 UUID 可交由原生服務解析，offline-mode UUID 則保留預設呈現。
 */
@Single
class ClientPlayerProfileResolver {
    /** 用來丟棄舊伺服器 session 的非同步回呼。 */
    @Volatile
    private var sessionGeneration: Long = 0

    /** 已完成解析的有限 profile 快取；最久未使用的項目會先被移除。 */
    private val resolved = object : LinkedHashMap<Uuid, GameProfile>(PROFILE_CACHE_LIMIT, 0.75f, true) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<Uuid, GameProfile>?,
        ): Boolean = size > PROFILE_CACHE_LIMIT
    }

    /** 已啟動皮膚查詢的 UUID，避免重複呼叫原生 provider。 */
    private val requested = LinkedHashSet<Uuid>()

    /** 已完成原生 skin 載入的有限材質快取。 */
    private val skins = object : LinkedHashMap<Uuid, Identifier>(SKIN_CACHE_LIMIT, 0.75f, true) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<Uuid, Identifier>?,
        ): Boolean = size > SKIN_CACHE_LIMIT
    }

    /** 已啟動原生 skin 載入的 UUID 與開始時間。 */
    private val skinRequests = LinkedHashMap<Uuid, TimeMark>()

    /** 已確認無法取得 skin 的 UUID。 */
    private val skinFailures = LinkedHashSet<Uuid>()

    /**
     * 取得伺服器同步的最後已知 profile。
     *
     * @param playerId 玩家識別碼。
     * @return 已快取的普通名稱與 UUID；尚未收到名稱時為 null。
     */
    fun resolvedProfile(playerId: Uuid): GameProfile? = synchronized(resolved) { resolved[playerId] }

    /**
     * 取得目前可用的 profile；線上資料優先於已完成的離線解析。
     *
     * @param playerId 玩家識別碼。
     * @return 線上或已快取的 profile；尚未解析時為 null。
     */
    fun knownProfile(playerId: Uuid): GameProfile? = onlineProfile(playerId) ?: resolvedProfile(playerId)

    /**
     * 記錄伺服器授權同步的最後已知普通名稱，供原生皮膚 provider 取得 UUID 與名稱 profile。
     *
     * @param playerId 玩家識別碼。
     * @param playerName 伺服器提供的普通名稱，不含文字樣式。
     */
    fun rememberServerName(playerId: Uuid, playerName: String) {
        val name = playerName.trim()
        if (name.isNotEmpty() && name.length <= MAX_NAME_LENGTH) {
            rememberProfile(playerId, GameProfile(playerId.toJavaUuid(), name))
        }
    }

    /** 清除目前伺服器 session 的名稱與查詢狀態，避免不同伺服器的 UUID 混用。 */
    fun clearSession() {
        sessionGeneration++
        synchronized(resolved) { resolved.clear() }
        synchronized(requested) { requested.clear() }
        synchronized(skins) { skins.clear() }
        synchronized(skinRequests) { skinRequests.clear() }
        synchronized(skinFailures) { skinFailures.clear() }
    }

    /**
     * 取得目前 player list 中的 profile。
     *
     * @param playerId 玩家識別碼。
     * @return 線上玩家資料；玩家離線或尚未收到資料時為 null。
     */
    fun onlineProfile(playerId: Uuid): GameProfile? = MinecraftClient.getInstance()
        .networkHandler
        ?.getPlayerListEntry(playerId.toJavaUuid())
        ?.profile

    /**
     * 取得已解析 profile 的原生皮膚材質。
     *
     * @param playerId 玩家識別碼。
     * @return 原生皮膚材質；尚未解析或材質不可用時為 null。
     */
    fun resolvedSkinTexture(playerId: Uuid): Identifier? {
        val client = MinecraftClient.getInstance()
        client.networkHandler?.getPlayerListEntry(playerId.toJavaUuid())?.let { return it.skinTexture }
        if (!supportsNativePlayerSkinLookup(playerId)) return null
        synchronized(skins) { skins[playerId] }?.let { return it }
        val profile = knownProfile(playerId) ?: return null
        requestSkin(playerId, profile)
        return null
    }

    /**
     * 對已知正版玩家 profile 啟動原生皮膚解析；名稱仍只使用伺服器提供的普通名稱。
     *
     * @param playerId 欲解析的正版玩家 UUID。
     */
    fun requestResolve(playerId: Uuid) {
        if (!supportsNativePlayerSkinLookup(playerId)) return
        knownProfile(playerId)?.let { requestSkin(playerId, it) }
    }

    /**
     * 透過 Minecraft 原生 PlayerSkinProvider 非同步載入指定 profile 的 skin。
     *
     * @param playerId 玩家 UUID。
     * @param profile 已知玩家 profile。
     */
    private fun requestSkin(playerId: Uuid, profile: GameProfile) {
        if (!supportsNativePlayerSkinLookup(playerId)) return
        synchronized(skinRequests) {
            val expired = skinRequests.filterValues { it.elapsedNow() >= SKIN_REQUEST_TIMEOUT }.keys.toList()
            expired.forEach { expiredId ->
                skinRequests.remove(expiredId)
                rememberSkinFailure(expiredId)
            }
            val startedAt = skinRequests[playerId]
            if (playerId in skinFailures) return
            if (startedAt != null) {
                if (startedAt.elapsedNow() >= SKIN_REQUEST_TIMEOUT) {
                    skinRequests.remove(playerId)
                    rememberSkinFailure(playerId)
                }
                return
            }
            if (skinRequests.size >= SKIN_REQUEST_LIMIT || !rememberRequest(playerId)) return
            skinRequests[playerId] = TimeSource.Monotonic.markNow()
        }
        val generation = sessionGeneration
        runCatching {
            MinecraftClient.getInstance().skinProvider.loadSkin(
                profile,
                PlayerSkinProvider.SkinTextureAvailableCallback { type, texture, _ ->
                    if (type != MinecraftProfileTexture.Type.SKIN) return@SkinTextureAvailableCallback
                    MinecraftClient.getInstance().execute {
                        if (generation == sessionGeneration) {
                            if (texture != null) {
                                synchronized(skins) { skins[playerId] = texture }
                            } else {
                                rememberSkinFailure(playerId)
                            }
                            synchronized(skinRequests) { skinRequests.remove(playerId) }
                        }
                    }
                },
                true,
            )
        }.onFailure {
            synchronized(skinRequests) { skinRequests.remove(playerId) }
            rememberSkinFailure(playerId)
        }
    }

    /** 將失敗 skin 請求放入有界索引。 */
    private fun rememberSkinFailure(playerId: Uuid) = synchronized(skinFailures) {
        while (skinFailures.size >= SKIN_FAILURE_LIMIT) skinFailures.remove(skinFailures.first())
        skinFailures.add(playerId)
    }

    /**
     * 將已完成解析的 profile 放入有限快取。
     *
     * @param playerId 玩家 UUID。
     * @param profile 已解析的玩家 profile。
     */
    private fun rememberProfile(playerId: Uuid, profile: GameProfile) {
        synchronized(resolved) { resolved[playerId] = profile }
    }

    /**
     * 記錄 profile 查詢並限制其記憶體使用量。
     *
     * @param playerId 欲查詢的玩家 UUID。
     * @return 本次是否新建查詢記錄。
     */
    private fun rememberRequest(playerId: Uuid): Boolean = synchronized(requested) {
        if (!requested.add(playerId)) return false
        while (requested.size > REQUEST_CACHE_LIMIT) requested.remove(requested.first())
        true
    }

    private companion object {
        /** 單一客戶端工作階段最多保留的已解析 profile 數量。 */
        const val PROFILE_CACHE_LIMIT = 256

        /** 單一客戶端工作階段最多保留的原生 skin 材質數量。 */
        const val SKIN_CACHE_LIMIT = 256

        /** 同一原生 skin 請求的最長等待時間。 */
        val SKIN_REQUEST_TIMEOUT = 30.seconds

        /** 同時保留的原生 skin 請求數量上限。 */
        const val SKIN_REQUEST_LIMIT = 256

        /** 已失敗原生 skin 請求的數量上限。 */
        const val SKIN_FAILURE_LIMIT = 256

        /** 單一客戶端工作階段最多保留的 profile 查詢數量。 */
        const val REQUEST_CACHE_LIMIT = 512

        /** Minecraft 普通玩家名稱的最大長度。 */
        const val MAX_NAME_LENGTH = 16
    }
}
