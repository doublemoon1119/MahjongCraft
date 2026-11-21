package com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.character

import com.doublemoon1119.mahjongcraft.ai.RandomAiStrategy
import com.doublemoon1119.mahjongcraft.platform.fabric.api.character.FabricAiCharacterHandle
import com.doublemoon1119.mahjongcraft.platform.fabric.api.character.FabricAiCharacterProvider
import com.doublemoon1119.mahjongcraft.platform.fabric.api.character.FabricRoomParticipant
import com.doublemoon1119.mahjongcraft.platform.fabric.api.character.FabricRoomParticipantProvider
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.character.AiCharacterDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.character.AiCharacterOwnership
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.character.AiCharacterPreview
import com.doublemoon1119.mahjongcraft.platform.minecraft.character.BuiltInAiCharacters
import net.minecraft.entity.Entity
import net.minecraft.entity.decoration.ArmorStandEntity
import kotlin.uuid.Uuid

/** 只在開發環境登記的最小借用 adapter，以已入座護甲座驗證正式公開接點。 */
internal object DebugSeatedParticipantAdapter {
    /** 登記描述、世界能力與預覽能力共用的完整 ID。 */
    const val ID: String = "mahjongcraft:debug/seated_participant"

    /** 只辨識明確由測試建立的護甲座。 */
    const val TAG: String = "mahjongcraft_debug_participant"

    /** 將測試工作授權綁到指定房主，而非任意附近房間。 */
    const val OWNER_TAG_PREFIX: String = "mahjongcraft_debug_owner_"

    /** 不依賴現場實體的公開外觀描述，頭像驗證模型備援。 */
    val CHARACTER: AiCharacterDefinition = AiCharacterDefinition(ID, BuiltInAiCharacters.TILE_MASCOT_NAME_KEY, null, ID, AiCharacterPreview(ID, emptyMap()))

    /** 只描述與驗證，不在辨識時接管或改動世界。 */
    val PARTICIPANT: FabricRoomParticipantProvider = object : FabricRoomParticipantProvider {
        override fun describe(entity: Entity): FabricRoomParticipant? = if (entity is ArmorStandEntity && TAG in entity.commandTags) FabricRoomParticipant(CHARACTER, RandomAiStrategy.KEY) else null

        override fun canJoin(entity: Entity, hostId: Uuid): Boolean = authorized(entity.commandTags, hostId)
    }

    /** 護甲座沒有尋路 AI，借用期間無須改動原生行為或保存旗標。 */
    val WORLD: FabricAiCharacterProvider = FabricAiCharacterProvider { context ->
        val actor = context.participant as? ArmorStandEntity ?: return@FabricAiCharacterProvider null
        object : FabricAiCharacterHandle {
            override val entity: Entity = actor

            override val ownership: AiCharacterOwnership = AiCharacterOwnership.BORROWED

            override fun prepare(): Boolean = actor.isAlive

            override fun release() = Unit
        }
    }

    /**
     * 只接受建立測試實體的房主，不將一般座位辨識當成工作授權。
     *
     * @param tags 實體已保存的測試標記。
     * @param hostId 房主。
     * @return 該實體已明確允許加入此房主的房間時為 true。
     */
    fun authorized(tags: Set<String>, hostId: Uuid): Boolean = TAG in tags && "$OWNER_TAG_PREFIX$hostId" in tags
}
