package com.doublemoon1119.mahjongcraft.example.format.persistence

import kotlinx.serialization.Serializable

/** 已攤牌的保存標記；未攤牌時不保存此組件。 */
@Serializable
data object ExamplePairingHandRevealedPersistenceDto
