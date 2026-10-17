package com.doublemoon1119.mahjongcraft.platform.fabric.client.tile

import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MahjongTileEntity
import net.minecraft.client.MinecraftClient
import net.minecraft.util.hit.EntityHitResult
import net.minecraft.util.hit.HitResult

/** GameRenderer mixin 使用的靜態橋接；實際狀態仍由 Koin 管理的 controller 持有。 */
object ClientHandTileLayoutBridge {
    /** 目前 client instance 的 controller；連線切換只清除內容，不替換 instance。 */
    @Volatile
    private var controller: ClientHandTileLayoutController? = null

    /**
     * 啟用客戶端共用的本地手牌布局元件。
     *
     * @param value 由客戶端依賴組裝建立的布局元件。
     */
    @JvmStatic
    fun activate(value: ClientHandTileLayoutController) {
        controller = value
    }

    /** 清空連線內容，保留下一次連線仍會使用的 controller。 */
    @JvmStatic
    fun clear() {
        controller?.clear()
    }

    /**
     * 在渲染幀開始時建立一次本人布局，供後續選取與渲染共用。
     *
     * @param client 目前客戶端。
     * @param tickDelta 本幀部分 tick。
     */
    @JvmStatic
    fun beginFrame(client: MinecraftClient, tickDelta: Float) {
        controller?.beginFrame(client, tickDelta)
    }

    /**
     * 依本地顯示位置修正手牌命中；沒有位移時保留原版結果。
     *
     * @param client 目前客戶端。
     * @param original 原版視線選取結果。
     * @param tickDelta 本幀部分 tick。
     * @return 以本地手牌位置修正後的選取結果。
     */
    @JvmStatic
    fun adjustTarget(
        client: MinecraftClient,
        original: HitResult?,
        tickDelta: Float,
    ): HitResult? {
        val active = controller ?: return original
        if (!active.hasOverrides) return original
        val world = client.world ?: return original
        val cameraEntity = client.cameraEntity ?: return original
        val interaction = client.interactionManager ?: return original
        val reachDistance = if (interaction.hasExtendedReach()) 6.0 else 3.0
        val start = cameraEntity.getCameraPosVec(tickDelta)
        // 沿用原版的視線起點與固定 1.0 朝向插值，第三人稱也不另換成攝影機位置。
        val end = start.add(cameraEntity.getRotationVec(1.0f).multiply(reachDistance))
        val blockTarget = cameraEntity.raycast(interaction.reachDistance.toDouble(), tickDelta, false)
        val blockDistance = if (blockTarget.type == HitResult.Type.BLOCK) blockTarget.pos.squaredDistanceTo(start) else Double.POSITIVE_INFINITY
        val originalEntity = (original as? EntityHitResult)?.entity
        val originalMoved = originalEntity is MahjongTileEntity && active.display(originalEntity) != null
        // 非手牌的原版命中仍是基準；舊格位上的手牌則必須重做射線，不能在沒有新命中時退回舊格位。
        val fallback = if (originalMoved) blockTarget else original
        val fallbackDistance = fallback?.takeIf { it.type != HitResult.Type.MISS }?.pos?.squaredDistanceTo(start)
            ?: Double.POSITIVE_INFINITY
        val candidate = findNearestHandTileTarget(
            start = start,
            end = end,
            candidates = world.entities.filter { entity ->
                entity !== cameraEntity && entity.rootVehicle !== cameraEntity.rootVehicle && !entity.isRemoved && entity.canHit() && !entity.isSpectator
            }.map { entity ->
                val box = if (entity is MahjongTileEntity) active.adjustedBox(entity) else entity.boundingBox
                entity to box
            },
            occlusionDistanceSquared = blockDistance,
            maximumDistanceSquared = fallbackDistance,
        ) ?: return fallback
        return EntityHitResult(candidate.payload, candidate.position)
    }
}
