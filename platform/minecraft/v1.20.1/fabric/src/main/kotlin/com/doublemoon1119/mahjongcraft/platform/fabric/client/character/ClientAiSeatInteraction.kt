package com.doublemoon1119.mahjongcraft.platform.fabric.client.character

import com.doublemoon1119.mahjongcraft.flow.common.error.ExtensionFailurePolicy
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.NetworkJson
import com.doublemoon1119.mahjongcraft.platform.fabric.logging.mahjongCraftLogger
import com.doublemoon1119.mahjongcraft.platform.fabric.network.MahjongChannels
import com.doublemoon1119.mahjongcraft.platform.fabric.server.seating.FabricSeatPlacementRegistry
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.fabric.api.event.player.UseEntityCallback
import net.minecraft.util.ActionResult
import java.util.UUID

/** 只攔截公開接管角色與已登記座位物品的右鍵，其餘實體保持原生互動。 */
internal object ClientAiSeatInteraction {
    /** 只在客戶端主執行緒存取的公開角色名單。 */
    private val targets = mutableSetOf<UUID>()

    /** 辨識物品失敗時記錄提供者，不讓回呼破壞輸入流程。 */
    private val logger = mahjongCraftLogger(ClientAiSeatInteraction::class)

    /**
     * 登記角色標記接收、斷線清除及右鍵路由。
     *
     * @param registry 已凍結的放置能力。
     * @param failures 第三方失敗的隔離政策。
     * @param json 共用線路格式。
     */
    fun register(
        registry: FabricSeatPlacementRegistry,
        failures: ExtensionFailurePolicy,
        json: NetworkJson,
    ) {
        MahjongChannels.aiCharacterTarget.registerClientReceiver(json) { payload ->
            val id = runCatching { UUID.fromString(payload.entityId) }.getOrNull() ?: return@registerClientReceiver
            if (payload.bound) targets += id else targets -= id
        }
        ClientPlayConnectionEvents.DISCONNECT.register { _, _ -> targets.clear() }
        ClientEntityEvents.ENTITY_UNLOAD.register { entity, _ -> targets -= entity.uuid }
        UseEntityCallback.EVENT.register { player, world, hand, entity, _ ->
            if (!world.isClient || entity.uuid !in targets || player.isSneaking) return@register ActionResult.PASS
            val stack = player.getStackInHand(hand)
            if (stack.isEmpty) return@register ActionResult.PASS
            for (definition in registry.entries()) {
                try {
                    if (definition.provider.supports(stack.copy())) return@register ActionResult.SUCCESS
                } catch (cause: Throwable) {
                    if (!failures.isIsolatable(cause)) throw cause
                    logger.warn("Seat item matching failed providerId={}", definition.id, cause)
                    return@register ActionResult.FAIL
                }
            }
            ActionResult.PASS
        }
    }
}
