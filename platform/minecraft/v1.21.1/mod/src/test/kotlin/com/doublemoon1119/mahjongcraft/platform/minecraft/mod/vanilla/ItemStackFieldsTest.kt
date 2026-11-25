package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.vanilla

import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.MinecraftTestBootstrap
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.loader.ModRegistrar
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.loader.RegisteredEntry
import com.doublemoon1119.mahjongcraft.platform.minecraft.stick.MahjongScoringStickDenomination
import com.doublemoon1119.mahjongcraft.platform.minecraft.stick.MahjongScoringStickItemData
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.minecraft.core.Registry
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.CreativeModeTab
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import java.io.File
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

/** 物品保存欄位以 mod 登記的資料元件存讀。 */
class ItemStackFieldsTest {
    /** 登記時交出的資料元件 ID，依登記順序。 */
    private val registeredIds = mutableListOf<ResourceLocation>()

    @BeforeTest
    fun registerFields() {
        MinecraftTestBootstrap.ensure()
        registerItemStackFields(RecordingRegistrar(registeredIds))
    }

    /** 兩個欄位都登記在資料元件登記表，ID 為 `mahjongcraft:<欄位名稱>`。 */
    @Test
    fun `fields are registered as mod data components`() {
        assertEquals(listOf(resourceId("mahjongcraft:tile"), resourceId("mahjongcraft:denomination")), registeredIds)
    }

    /** 牌面名稱與每種點棒面額都能寫進物品並讀回。沒有寫入時讀到 null。 */
    @Test
    fun `values survive a round trip on an item`() {
        val tile = ItemStack(Items.STONE)
        assertNull(tile.storedTileAssetKey())
        tile.storeTileAssetKey("m5")
        assertEquals("m5", tile.storedTileAssetKey())

        MahjongScoringStickDenomination.entries.forEach { denomination ->
            val stick = ItemStack(Items.STONE)
            stick.storeScoringStickDenomination(MahjongScoringStickItemData.write(denomination))
            assertEquals(denomination, MahjongScoringStickItemData.read(stick.storedScoringStickDenomination()))
        }
    }

    /** 進度圖示引用的資料元件都是這裡登記的元件，改名時圖示不會悄悄失效。 */
    @Test
    fun `advancement icons only use registered components`() {
        val root = javaClass.classLoader.getResource(DataPackLayout.ADVANCEMENTS) ?: fail("Missing ${DataPackLayout.ADVANCEMENTS}")
        val used = File(root.toURI()).walkTopDown()
            .filter { it.isFile && it.extension == "json" }
            .mapNotNull { JsonParser.parseString(it.readText()).asJsonObject.getAsJsonObject("display") }
            .mapNotNull { display: JsonObject -> display.getAsJsonObject("icon").getAsJsonObject("components") }
            .flatMap { it.keySet() }
            .toSet()
        assertTrue(used.isNotEmpty(), "No advancement icon uses a data component")
        assertTrue(registeredIds.map { it.toString() }.containsAll(used), "Unregistered components: ${used - registeredIds.map { it.toString() }.toSet()}")
    }

    /**
     * 立即建立物件、只記下資料元件 ID 的登記方式。
     *
     * @property ids 接收登記的資料元件 ID。
     */
    private class RecordingRegistrar(private val ids: MutableList<ResourceLocation>) : ModRegistrar {
        override fun <R : Any, T : R> register(
            registry: ResourceKey<out Registry<R>>,
            id: ResourceLocation,
            factory: () -> T,
        ): RegisteredEntry<T> {
            check(registry == Registries.DATA_COMPONENT_TYPE) { "Unexpected registry $registry" }
            ids += id
            val value = factory()
            return RegisteredEntry { value }
        }

        override fun afterRegistration(action: () -> Unit) {
            action()
        }

        override fun creativeModeTabBuilder(): CreativeModeTab.Builder = error("Item fields do not create creative tabs")
    }
}
