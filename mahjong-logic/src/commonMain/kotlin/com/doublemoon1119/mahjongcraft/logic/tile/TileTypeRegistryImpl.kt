package com.doublemoon1119.mahjongcraft.logic.tile

import com.doublemoon1119.mahjongcraft.logic.base.TileTypeId

/**
 * [TileTypeRegistry] 的預設記憶體實作。
 *
 * 建構時不預先加入任何牌種；內建牌與第三方牌使用相同的 [register] 流程，並由外層組裝完成後呼叫
 * [freeze]。此類別不依賴 Koin，runtime single 由外層 DI module 管理。
 */
class TileTypeRegistryImpl : TileTypeRegistry {
    /** 依穩定 ID 保存定義並保留註冊順序。 */
    private val definitionsById = mutableMapOf<TileTypeId, TileTypeDefinition>()

    override var isFrozen: Boolean = false
        private set

    override fun register(definition: TileTypeDefinition) {
        check(!isFrozen) { "Tile type registry is frozen" }
        require(definition.id !in definitionsById) { "Tile type ID already registered: ${definition.id}" }
        definitionsById[definition.id] = definition
    }

    override fun freeze() {
        isFrozen = true
    }

    override fun find(id: TileTypeId): TileTypeDefinition? = definitionsById[id]

    override fun require(id: TileTypeId): TileTypeDefinition = find(id)
        ?: error("No tile type registered for ID: $id")

    override fun getAll(): List<TileTypeDefinition> = definitionsById.values.toList()
}
