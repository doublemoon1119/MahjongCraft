package com.doublemoon1119.mahjongcraft.example.fabric.character

import com.doublemoon1119.mahjongcraft.example.ai.ExampleAiStrategyIds
import com.doublemoon1119.mahjongcraft.example.minecraft.character.ExampleCharacters
import com.doublemoon1119.mahjongcraft.platform.fabric.api.character.FabricAiCharacterProvider
import com.doublemoon1119.mahjongcraft.platform.fabric.api.character.FabricRoomParticipant
import com.doublemoon1119.mahjongcraft.platform.fabric.api.character.FabricRoomParticipantProvider
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.character.AiCharacterOwnership
import net.minecraft.entity.Entity
import kotlin.uuid.Uuid

/** 登記時只建立能力描述；原生類型與世界都在回呼被使用時才查詢。 */
internal object ExampleActorProviders {
    /** 只辨識範例人偶，房主仍須取得放置者授權。 */
    val PARTICIPANT: FabricRoomParticipantProvider = object : FabricRoomParticipantProvider {
        /** 已載入且有放置者的人偶才提供參與資格，授權仍由 canJoin 核對。 */
        override fun describe(entity: Entity): FabricRoomParticipant? = if (entity is ExampleActorEntity && entity.isAlive && entity.ownerId != null) {
            FabricRoomParticipant(ExampleCharacters.ACTOR, ExampleAiStrategyIds.TSUMOGIRI)
        } else {
            null
        }

        /** 以原生放置者 UUID 核對房主，不以顯示名稱授權。 */
        override fun canJoin(entity: Entity, hostId: Uuid): Boolean = entity is ExampleActorEntity && entity.isAlive && ExampleActorAuthorization.canJoin(entity.ownerId, hostId)
    }

    /** 工廠不生成或移動，只借用框架指定的同一隻人偶。 */
    val BORROWED: FabricAiCharacterProvider = FabricAiCharacterProvider { context ->
        val actor = context.participant as? ExampleActorEntity ?: return@FabricAiCharacterProvider null
        if (actor.world !== context.world || !actor.isAlive) return@FabricAiCharacterProvider null
        ExampleActorHandle(actor, AiCharacterOwnership.BORROWED)
    }

    /** 自有代理不保存，由框架的生成及清理流程管理；不是一般房間的外觀選單。 */
    val OWNED: FabricAiCharacterProvider = FabricAiCharacterProvider { context ->
        if (context.participant != null) return@FabricAiCharacterProvider null
        ExampleActors.proxyType.create(context.world)?.let { ExampleActorHandle(it, AiCharacterOwnership.OWNED) }
    }
}
