package com.doublemoon1119.mahjongcraft.platform.fabric.server.persistence

import com.doublemoon1119.mahjongcraft.platform.minecraft.character.AiCharacterRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.character.AiCharacterRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.character.BuiltInAiCharacters

/** 建立只含內建角色的獨立測試登記表，不使用全域 runtime。 */
internal fun testCharacterRegistry(): AiCharacterRegistry = AiCharacterRegistryImpl().apply {
    register(BuiltInAiCharacters.TILE_MASCOT)
    freeze()
}
