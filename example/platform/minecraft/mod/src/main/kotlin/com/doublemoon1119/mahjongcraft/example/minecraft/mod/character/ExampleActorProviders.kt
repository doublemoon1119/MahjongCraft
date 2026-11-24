package com.doublemoon1119.mahjongcraft.example.minecraft.mod.character

import com.doublemoon1119.mahjongcraft.example.ai.ExampleAiStrategyIds
import com.doublemoon1119.mahjongcraft.example.minecraft.character.ExampleActorAuthorization
import com.doublemoon1119.mahjongcraft.example.minecraft.character.ExampleCharacters
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.character.AiCharacterOwnership
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.api.character.AiCharacterProvider
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.api.character.RoomParticipant
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.api.character.RoomParticipantProvider
import net.minecraft.world.entity.Entity
import kotlin.uuid.Uuid

/** 登記時只建立能力描述；原生類型與世界都在回呼被使用時才查詢。 */
internal object ExampleActorProviders {
    /** 只辨識範例人偶，房主仍須取得放置者授權。 */
    val PARTICIPANT: RoomParticipantProvider = object : RoomParticipantProvider {
        /** 已載入且有放置者的人偶才提供參與資格，授權仍由 canJoin 核對。 */
        override fun describe(entity: Entity): RoomParticipant? = if (entity is ExampleActorEntity && entity.isAlive && entity.ownerId != null) {
            RoomParticipant(ExampleCharacters.ACTOR, ExampleAiStrategyIds.TSUMOGIRI)
        } else {
            null
        }

        /** 以原生放置者 UUID 核對房主，不以顯示名稱授權。 */
        override fun canJoin(entity: Entity, hostId: Uuid): Boolean = entity is ExampleActorEntity && entity.isAlive && ExampleActorAuthorization.canJoin(entity.ownerId, hostId)
    }

    /** 工廠不生成或移動，只借用框架指定的同一隻人偶。 */
    val BORROWED: AiCharacterProvider = AiCharacterProvider { context ->
        val actor = context.participant as? ExampleActorEntity ?: return@AiCharacterProvider null
        if (actor.level() !== context.world || !actor.isAlive) return@AiCharacterProvider null
        ExampleActorHandle(actor, AiCharacterOwnership.BORROWED)
    }

    /** 自有代理不保存，由框架的生成及清理流程管理；不是一般房間的外觀選單。 */
    val OWNED: AiCharacterProvider = AiCharacterProvider { context ->
        if (context.participant != null) return@AiCharacterProvider null
        ExampleActors.proxyType.create(context.world)?.let { ExampleActorHandle(it, AiCharacterOwnership.OWNED) }
    }
}
