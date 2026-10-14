package com.doublemoon1119.mahjongcraft.platform.minecraft.tile

import com.doublemoon1119.mahjongcraft.logic.base.TileTypeId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 驗證 Minecraft asset key registry 的註冊、查詢與凍結契約。 */
class MinecraftTileAssetRegistryImplTest {

    /** 驗證已註冊的 asset key 可由穩定 ID 查詢。 */
    @Test
    fun `registered asset key can be resolved by type id`() {
        val registry = MinecraftTileAssetRegistryImpl()
        val typeId = TileTypeId.parse("mahjongcraft:taiwan/spring")

        registry.register(MinecraftTileAssetDefinition(typeId, "flower_spring"))

        assertEquals("flower_spring", registry.find(typeId))
    }

    /** 驗證擴充不能以保留 key 覆蓋基本牌或未知牌資源。 */
    @Test
    fun `reserved built in asset keys are rejected without registration`() {
        val registry = MinecraftTileAssetRegistryImpl()
        val typeId = TileTypeId.parse("example:tile")
        val resources = MinecraftTileResources("example:textures/tile.png", "example:item/tile")
        (STANDARD_TILE_ASSET_KEYS + UNKNOWN_TILE_ASSET_KEY).forEach { key ->
            assertFailsWith<IllegalArgumentException> {
                registry.register(MinecraftTileAssetDefinition(typeId, key, resources))
            }
            assertNull(registry.find(typeId))
            assertNull(registry.findResources(key))
        }
    }

    /** 驗證同一穩定 ID 不得重複註冊。 */
    @Test
    fun `duplicate type id is rejected`() {
        val registry = MinecraftTileAssetRegistryImpl()
        val typeId = TileTypeId.parse("example:flower/spring")
        registry.register(MinecraftTileAssetDefinition(typeId, "flower_spring"))

        assertFailsWith<IllegalArgumentException> { registry.register(MinecraftTileAssetDefinition(typeId, "another_key")) }
    }

    /** 驗證不同穩定 ID 不得共用同一個 asset key，避免正規化與 Tab 補全出現無法區分的撞名。 */
    @Test
    fun `duplicate asset key across different type ids is rejected`() {
        val registry = MinecraftTileAssetRegistryImpl()
        registry.register(MinecraftTileAssetDefinition(TileTypeId.parse("example:animal/cat"), "shared_key"))

        assertFailsWith<IllegalArgumentException> {
            registry.register(MinecraftTileAssetDefinition(TileTypeId.parse("example:animal/dog"), "shared_key"))
        }
    }

    /** 驗證凍結後保持可查詢，但禁止新增映射。 */
    @Test
    fun `frozen registry remains readable and rejects registration`() {
        val registry = MinecraftTileAssetRegistryImpl()
        val registeredId = TileTypeId.parse("example:registered")
        registry.register(MinecraftTileAssetDefinition(registeredId, "registered_key"))

        registry.freeze()

        assertTrue(registry.isFrozen)
        assertEquals("registered_key", registry.find(registeredId))
        assertFailsWith<IllegalStateException> {
            registry.register(MinecraftTileAssetDefinition(TileTypeId.parse("example:late"), "late_key"))
        }
    }

    /** 驗證未註冊 ID 查詢時回傳 null，而不是拋出例外。 */
    @Test
    fun `unknown type id resolves to null`() {
        val registry = MinecraftTileAssetRegistryImpl()

        assertNull(registry.find(TileTypeId.parse("example:unknown")))
    }

    /** 驗證 [MinecraftTileAssetRegistry.isRegisteredAssetKey] 只認得實際註冊過的 asset key 值。 */
    @Test
    fun `isRegisteredAssetKey only recognizes values that were actually registered`() {
        val registry = MinecraftTileAssetRegistryImpl()
        registry.register(MinecraftTileAssetDefinition(TileTypeId.parse("example:animal/cat"), "animal_cat"))

        assertTrue(registry.isRegisteredAssetKey("animal_cat"))
        assertFalse(registry.isRegisteredAssetKey("animal_dog"))
    }

    /** 驗證省略資源描述時沿用內建 namespace 與既有路徑。 */
    @Test
    fun `default resources use the built in namespace`() {
        val registry = MinecraftTileAssetRegistryImpl()
        val definition = MinecraftTileAssetDefinition(TileTypeId.parse("example:animal/cat"), "animal_cat")

        registry.register(definition)

        assertEquals(
            expected = MinecraftTileResources(
                textureId = "mahjongcraft:textures/item/mahjong_tile/mahjong_tile_animal_cat.png",
                modelId = "mahjongcraft:item/mahjong_tile/mahjong_tile_animal_cat",
            ),
            actual = registry.findResources("animal_cat"),
        )
    }

    /** 驗證明確資源描述可使用第三方 namespace。 */
    @Test
    fun `custom resources preserve their namespace`() {
        val registry = MinecraftTileAssetRegistryImpl()
        val resources = MinecraftTileResources(
            textureId = "example:textures/item/custom_tile.png",
            modelId = "example:item/custom_tile",
        )

        registry.register(
            definition = MinecraftTileAssetDefinition(
                typeId = TileTypeId.parse("example:animal/cat"),
                assetKey = "animal_cat",
                resources = resources,
            ),
        )

        assertEquals(resources, registry.findResources("animal_cat"))
    }

    /** 驗證同一路徑可在不同 namespace 下各自使用。 */
    @Test
    fun `same resource paths in different namespaces are distinct`() {
        val registry = MinecraftTileAssetRegistryImpl()
        registry.register(
            definition = MinecraftTileAssetDefinition(
                typeId = TileTypeId.parse("example:animal/cat"),
                assetKey = "animal_cat",
                resources = MinecraftTileResources("first:tile.png", "first:tile"),
            ),
        )
        registry.register(
            definition = MinecraftTileAssetDefinition(
                typeId = TileTypeId.parse("other:animal/cat"),
                assetKey = "other_animal_cat",
                resources = MinecraftTileResources("second:tile.png", "second:tile"),
            ),
        )

        assertEquals("first:tile.png", registry.findResources("animal_cat")?.textureId)
        assertEquals("second:tile.png", registry.findResources("other_animal_cat")?.textureId)
    }

    /** 驗證完整資源 ID 必須符合 namespaced ID 格式。 */
    @Test
    fun `invalid resource ids are rejected`() {
        listOf("missing_namespace", ":tile", "example:", "Example:tile", "example:tile name", "example:tile:extra").forEach { invalid ->
            assertFailsWith<IllegalArgumentException> {
                MinecraftTileResources(invalid, "example:item/tile")
            }
            assertFailsWith<IllegalArgumentException> {
                MinecraftTileResources("example:textures/tile.png", invalid)
            }
        }
    }

    /** 驗證使用預設資源位置時，不接受會形成非法資源 ID 的素材 key。 */
    @Test
    fun `default resources reject invalid path keys`() {
        assertFailsWith<IllegalArgumentException> {
            MinecraftTileAssetDefinition(TileTypeId.parse("example:tile"), "invalid key")
        }
    }

    /** 驗證不同素材身分可以引用同一組資源，凍結後仍可查詢。 */
    @Test
    fun `distinct asset keys can share resources after freezing`() {
        val registry = MinecraftTileAssetRegistryImpl()
        val resources = MinecraftTileResources("example:textures/shared.png", "example:item/shared")
        registry.register(MinecraftTileAssetDefinition(TileTypeId.parse("example:first"), "first", resources))
        registry.register(MinecraftTileAssetDefinition(TileTypeId.parse("example:second"), "second", resources))
        registry.freeze()

        assertEquals(resources, registry.findResources("first"))
        assertEquals(resources, registry.findResources("second"))
        assertNull(registry.findResources("missing"))
    }

    /** 驗證牌組外觀與規則綁定可在同一 registry 中查詢。 */
    @Test
    fun `tile set appearances and rule bindings are resolved`() {
        val registry = MinecraftTileAssetRegistryImpl()
        val appearance = MinecraftTileSetAppearanceDefinition(
            id = "example:wood",
            coverTextureId = "example:item/mahjong_tile/cover",
            unknownFaceTextureId = "example:item/mahjong_tile/hidden",
        )
        registry.register(appearance)
        registry.register(MinecraftRuleTileAppearanceDefinition("example:rule", appearance.id))

        assertEquals(setOf(appearance.id), registry.appearanceRegistrationKeys)
        assertEquals(setOf("example:rule"), registry.ruleAppearanceBindingKeys)
        assertEquals(listOf(appearance), registry.registeredAppearances)
        assertEquals(appearance, registry.findAppearance(appearance.id))
        assertEquals(appearance, registry.findAppearanceForRule("example:rule"))
    }

    /** 驗證沒有規則綁定時會使用已登記的內建預設外觀。 */
    @Test
    fun `unbound rule uses built in default appearance`() {
        val registry = MinecraftTileAssetRegistryImpl()
        registry.register(BuiltInMinecraftTileAppearances.DEFAULT)

        assertEquals(BuiltInMinecraftTileAppearances.DEFAULT, registry.findAppearanceForRule("example:rule"))
    }

    /** 驗證凍結前會拒絕指向不存在外觀的規則綁定。 */
    @Test
    fun `freeze rejects a binding to an unknown appearance`() {
        val registry = MinecraftTileAssetRegistryImpl()
        registry.register(MinecraftRuleTileAppearanceDefinition("example:rule", "example:missing"))

        assertFailsWith<IllegalArgumentException> { registry.freeze() }
        assertFalse(registry.isFrozen)
    }

    /** 驗證外觀與綁定的註冊快照不會暴露 registry 的可變集合。 */
    @Test
    fun `appearance snapshots are detached`() {
        val registry = MinecraftTileAssetRegistryImpl()
        val appearance = MinecraftTileSetAppearanceDefinition(
            id = "example:wood",
            coverTextureId = "example:item/cover",
            unknownFaceTextureId = "example:item/hidden",
        )
        registry.register(appearance)
        val appearanceKeys = registry.appearanceRegistrationKeys
        val bindingKeys = registry.ruleAppearanceBindingKeys
        val appearances = registry.registeredAppearances

        registry.register(
            definition = MinecraftTileSetAppearanceDefinition(
                id = "example:stone",
                coverTextureId = "example:item/cover2",
                unknownFaceTextureId = "example:item/hidden2",
            ),
        )
        registry.register(MinecraftRuleTileAppearanceDefinition("example:rule", "example:stone"))

        assertEquals(setOf("example:wood"), appearanceKeys)
        assertEquals(emptySet(), bindingKeys)
        assertEquals(listOf(appearance), appearances)
    }
}
