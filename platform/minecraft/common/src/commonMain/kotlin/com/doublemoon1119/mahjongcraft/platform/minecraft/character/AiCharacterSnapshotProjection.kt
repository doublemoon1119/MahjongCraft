package com.doublemoon1119.mahjongcraft.platform.minecraft.character

import com.doublemoon1119.mahjongcraft.platform.minecraft.api.character.AiCharacterDefinition
import kotlin.uuid.Uuid

/**
 * 只向房主提供等待房間的排除者名稱與 FACE，不公開來源綁定或模型能力。
 *
 * @param tableId 同一份權威封套內的房間。
 * @param observerId 已通過場地交付授權的收件者。
 * @return 房主可讀的精簡描述；其他觀察者或對局一律為空。
 */
fun AiCharacterStateSnapshot.excludedAppearancesFor(tableId: Uuid, observerId: Uuid): Map<Uuid, AiCharacterSnapshot> {
    val room = authoritative.rooms[tableId] ?: return emptyMap()
    if (room.hostId != observerId) return emptyMap()
    val metadata = characters ?: return emptyMap()
    return room.admission.excludedParticipants.values.mapNotNull { id ->
        metadata.participant(id)?.let { snapshot ->
            val definition = snapshot.definition?.let { AiCharacterDefinition(it.id, it.nameKey, it.face, null, null) }
            id to snapshot.copy(definition = definition)
        }
    }.toMap()
}
