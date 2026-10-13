package com.doublemoon1119.mahjongcraft.platform.fabric.client.room

import com.doublemoon1119.mahjongcraft.platform.minecraft.room.RoomMemberAppearanceContext
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.RoomMemberAppearanceSource
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.RoomMemberAppearanceSourceProviderException
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.RoomMemberAppearanceSourceRegistry
import kotlinx.coroutines.CancellationException
import kotlin.uuid.Uuid

/**
 * 決定成員卡片要用哪一種外觀呈現，並收斂各種降級情況。
 *
 * 只回傳決策與需要記錄的警告，不接觸繪製；呼叫端依 [Resolution.appearance] 選擇繪製路徑，並在
 * [Resolution.warning] 非 `null` 時輸出一次記錄。
 *
 * 三種降級來源：registry 解析失敗（AI 用畫像、真人用玩家模型）、第三方宣告的 actor 預覽沒有對應工廠
 * （改用畫像）。玩家模型在玩家離線時還會再降級一次，但那需要查詢世界中的 entity，由呼叫端處理。
 *
 * @property sources 解析成員外觀來源的 registry。
 */
internal class RoomMemberAppearanceResolver(private val sources: RoomMemberAppearanceSourceRegistry) {
    /** 已警告過的 actor key，避免每一幀重複記錄。 */
    private val warnedActorKeys = mutableSetOf<String>()

    /** 已警告過的 provider ID，避免每一幀重複記錄。 */
    private val warnedProviderIds = mutableSetOf<String>()

    /**
     * 解析指定成員的外觀呈現方式，並對相同的一般失敗來源只回傳一次警告。
     * 取消例外與致命錯誤直接外傳，不改用預設外觀。
     *
     * @param playerId 成員的玩家識別碼。
     * @param isAi 成員是否由 AI 操控。
     * @return 外觀方式與本次尚未回報的降級警告。
     */
    fun resolve(playerId: Uuid, isAi: Boolean): Resolution {
        val source = try {
            sources.resolve(RoomMemberAppearanceContext(playerId, isAi))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (cause: Exception) {
            val providerId = (cause as? RoomMemberAppearanceSourceProviderException)?.providerId ?: REGISTRY_PROVIDER_ID
            val warning = Warning.ProviderFailed(providerId, cause).takeIf { warnedProviderIds.add(providerId) }
            return Resolution(if (isAi) Appearance.Portrait else Appearance.PlayerModel, warning)
        }
        return when (source) {
            RoomMemberAppearanceSource.PlayerModel -> Resolution(Appearance.PlayerModel, null)
            RoomMemberAppearanceSource.Portrait -> Resolution(Appearance.Portrait, null)
            is RoomMemberAppearanceSource.ActorPreview -> Resolution(
                Appearance.Portrait,
                Warning.MissingActorPreviewFactory(source.actorKey).takeIf { warnedActorKeys.add(source.actorKey) },
            )
        }
    }

    /**
     * 外觀解析結果。
     *
     * @property appearance 要使用的呈現方式。
     * @property warning 本次需要記錄的警告；相同來源已警告過時為 null。
     */
    data class Resolution(
        val appearance: Appearance,
        val warning: Warning?,
    )

    /** 卡片可用的呈現方式。 */
    enum class Appearance {
        /** 繪製玩家模型；玩家離線時由呼叫端再降級為畫像。 */
        PlayerModel,

        /** 繪製畫像。 */
        Portrait,
    }

    /** 需要記錄一次的降級原因。 */
    sealed interface Warning {
        /**
         * 外觀來源提供者解析失敗。
         *
         * @property providerId 失敗的提供者識別碼；無法辨識時使用 registry 的替代識別字。
         * @property cause 解析失敗的原始例外。
         */
        data class ProviderFailed(val providerId: String, val cause: Throwable) : Warning

        /**
         * 第三方宣告了 actor 預覽，但沒有對應的預覽工廠。
         *
         * @property actorKey 找不到預覽工廠的 actor 識別字。
         */
        data class MissingActorPreviewFactory(val actorKey: String) : Warning
    }

    /** 外觀解析失敗時使用的內部診斷識別字。 */
    private companion object {
        /** 失敗來自 registry 本身而非特定 provider 時使用的識別字。 */
        const val REGISTRY_PROVIDER_ID: String = "registry"
    }
}
