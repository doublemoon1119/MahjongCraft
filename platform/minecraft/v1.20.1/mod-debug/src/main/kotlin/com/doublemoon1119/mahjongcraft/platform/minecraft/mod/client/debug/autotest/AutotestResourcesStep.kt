package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.debug.autotest

import com.doublemoon1119.mahjongcraft.platform.minecraft.metadata.MinecraftModMetadata
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.render.SceneEntityTranslucency
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.vanilla.resourceId
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.tags.TagKey
import net.minecraft.world.level.storage.loot.BuiltInLootTables
import net.minecraft.world.level.storage.loot.LootDataId
import net.minecraft.world.level.storage.loot.LootDataType
import net.minecraft.world.level.storage.loot.LootTable

/**
 * 檢查本 mod 的資源：物品與方塊模型、實體繪製器、配方、進度、方塊戰利品表、物品標籤、聲音與自訂著色器。
 *
 * 語言檔的鍵由建置時的檢查負責，這裡不重複。
 */
internal object AutotestResourcesStep : AutotestStep {
    override val id: String = "resources"

    override suspend fun run(context: AutotestContext) {
        checkModels(context)
        checkEntityRenderers(context)
        checkServerData(context)
        checkSounds(context)
        context.check("Translucent figure shader is loaded", SceneEntityTranslucency.isShaderLoaded)
    }

    /**
     * 每個物品與方塊狀態都有自己的模型，不是缺少模型時的紫黑方塊。
     *
     * @param context 步驟環境。
     */
    private fun checkModels(context: AutotestContext) {
        val client = context.client
        val missing = client.modelManager.missingModel
        val items = BuiltInRegistries.ITEM.filter { BuiltInRegistries.ITEM.getKey(it).namespace == NAMESPACE }
        val itemsWithoutModel = items.filter { client.itemRenderer.itemModelShaper.getItemModel(it) === missing }
            .map { BuiltInRegistries.ITEM.getKey(it).toString() }
        context.check("All ${items.size} items have models", itemsWithoutModel.isEmpty(), itemsWithoutModel.joinToString().ifEmpty { null })
        val states = BuiltInRegistries.BLOCK.filter { BuiltInRegistries.BLOCK.getKey(it).namespace == NAMESPACE }
            .flatMap { it.stateDefinition.possibleStates }
        val statesWithoutModel = states.filter { client.blockRenderer.blockModelShaper.getBlockModel(it) === missing }.map(Any::toString)
        context.check("All ${states.size} block states have models", statesWithoutModel.isEmpty(), statesWithoutModel.joinToString().ifEmpty { null })
    }

    /**
     * 每個實體類型都有繪製器。
     *
     * @param context 步驟環境。
     */
    private fun checkEntityRenderers(context: AutotestContext) {
        val client = context.client
        val level = client.level ?: return
        val types = BuiltInRegistries.ENTITY_TYPE.filter { BuiltInRegistries.ENTITY_TYPE.getKey(it).namespace == NAMESPACE }
        val withoutRenderer = types.filter { type ->
            val entity = type.create(level) ?: return@filter false
            try {
                runCatching { client.entityRenderDispatcher.getRenderer(entity) }.getOrNull() == null
            } finally {
                entity.discard()
            }
        }.map { BuiltInRegistries.ENTITY_TYPE.getKey(it).toString() }
        context.check("All ${types.size} entity types have renderers", withoutRenderer.isEmpty(), withoutRenderer.joinToString().ifEmpty { null })
    }

    /**
     * 伺服器載入了本 mod 的配方、進度、方塊戰利品表與物品標籤。
     *
     * @param context 步驟環境。
     */
    private fun checkServerData(context: AutotestContext) {
        val server = context.client.singleplayerServer ?: return run { context.check("Integrated server is running", false) }
        val recipes = server.recipeManager.recipes.filter { it.id.namespace == NAMESPACE }
        context.check("Recipes are loaded", recipes.isNotEmpty(), "${recipes.size} recipes")
        val advancements = server.advancements.allAdvancements.filter { it.id.namespace == NAMESPACE }
        context.check("Advancements are loaded", advancements.isNotEmpty(), "${advancements.size} advancements")
        val blocks = BuiltInRegistries.BLOCK.filter { BuiltInRegistries.BLOCK.getKey(it).namespace == NAMESPACE }
        val withoutLoot = blocks.filter { block ->
            val table = block.lootTable
            table != BuiltInLootTables.EMPTY && server.lootData.getElement(LootDataId(LootDataType.TABLE, table)).let { it == null || it === LootTable.EMPTY }
        }.map { BuiltInRegistries.BLOCK.getKey(it).toString() }
        context.check("All ${blocks.size} blocks have loot tables", withoutLoot.isEmpty(), withoutLoot.joinToString().ifEmpty { null })
        val dyes = BuiltInRegistries.ITEM.getTag(TagKey.create(Registries.ITEM, resourceId(NAMESPACE, "dyes")))
        val dyeCount = dyes.map { it.size() }.orElse(0)
        context.check("Item tag $NAMESPACE:dyes has items", dyeCount > 0, "$dyeCount items")
    }

    /**
     * `sounds.json` 指到的音檔都存在，遊戲也找得到每個聲音事件。
     *
     * @param context 步驟環境。
     */
    private fun checkSounds(context: AutotestContext) {
        val client = context.client
        val resources = client.resourceManager
        val events = resources.getResourceStack(resourceId(NAMESPACE, "sounds.json"))
            .flatMap { resource -> resource.openAsReader().use { JSON.parseToJsonElement(it.readText()).jsonObject.entries } }
        val unknownEvents = events.map { it.key }.filter { client.soundManager.getSoundEvent(resourceId(NAMESPACE, it)) == null }
        context.check("All ${events.size} sound events are known", unknownEvents.isEmpty(), unknownEvents.joinToString().ifEmpty { null })
        val files = events.flatMap { (_, definition) ->
            definition.jsonObject["sounds"]?.jsonArray.orEmpty().map { sound ->
                val name = if (sound is JsonPrimitive) sound.content else (sound as JsonObject).getValue("name").jsonPrimitive.content
                val isEvent = (sound as? JsonObject)?.get("type")?.jsonPrimitive?.content == "event"
                name to isEvent
            }
        }.filterNot { it.second }.map { it.first }.distinct()
        val missingFiles = files.filter { name ->
            val location = resourceId(if (':' in name) name.substringBefore(':') else NAMESPACE, "sounds/${name.substringAfter(':')}.ogg")
            resources.getResource(location).isEmpty
        }
        context.check("All ${files.size} sound files exist", missingFiles.isEmpty(), missingFiles.joinToString().ifEmpty { null })
    }

    /** 本 mod 的命名空間。 */
    private const val NAMESPACE: String = MinecraftModMetadata.MOD_ID

    /** 讀取 `sounds.json` 的 JSON 設定。 */
    private val JSON = Json { ignoreUnknownKeys = true }
}
