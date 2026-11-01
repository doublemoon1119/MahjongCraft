package com.doublemoon1119.mahjongcraft.platform.fabric.server.character

import com.doublemoon1119.mahjongcraft.platform.fabric.api.character.FabricAiCharacterHandle
import net.minecraft.entity.Entity
import net.minecraft.util.math.Vec3d

/**
 * 已確認固定身分的原生角色及暫態呈現狀態。
 *
 * @property entity 此次接管的固定原生實體。
 * @property handle 第三方或內建的角色能力。
 * @property capabilityId 診斷使用的完整能力 ID。
 */
internal class FabricAiCharacterActor(
    val entity: Entity,
    val handle: FabricAiCharacterHandle,
    val capabilityId: String,
) {
    /** 入座後的腳底位置，用於發現被外部移走的角色。 */
    var expectedPosition: Vec3d? = null

    /** 本次接管最後安排的載具，不代表後來由其他系統接手的載具。 */
    var assignedVehicle: Entity? = null

    /** 目前已通知的公開注視目標。 */
    var lookTarget: Vec3d? = null

    /** 本次接管中已停用的回饋種類，避免重複報錯。 */
    val failedCallbacks: MutableSet<String> = mutableSetOf()
}
