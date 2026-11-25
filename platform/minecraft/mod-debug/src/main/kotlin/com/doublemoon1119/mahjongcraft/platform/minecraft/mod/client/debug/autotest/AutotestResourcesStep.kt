package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.debug.autotest

import com.doublemoon1119.mahjongcraft.platform.minecraft.metadata.MinecraftModMetadata
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.render.SceneEntityTranslucency
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.debug.vanilla.DebugDataPackFolders
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.debug.vanilla.isBlockLootTableMissing
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.vanilla.resourceId
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.packs.resources.ResourceManager

/**
 * 檢查本 mod 的資源：物品與方塊模型、實體繪製器、配方、進度、物品標籤、方塊戰利品表、聲音與自訂著色器。
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
     * 伺服器載入了本 mod 資料包中的每個配方、進度與物品標籤，以及每個方塊的戰利品表。資料檔讀取失敗時原版只記錄錯誤並略過
     * 該檔案，所以逐一比對資料包中的檔案與伺服器載入的內容。
     *
     * @param context 步驟環境。
     */
    private fun checkServerData(context: AutotestContext) {
        val server = context.client.singleplayerServer ?: return run { context.check("Integrated server is running", false) }
        val resources = server.resourceManager
        val loadedRecipes = server.recipeManager.recipes.map { it.id }.toSet()
        checkDataFiles(context, label = "recipes", files = dataFileIds(resources, DebugDataPackFolders.RECIPES), isLoaded = loadedRecipes::contains)
        val loadedAdvancements = server.advancements.allAdvancements.map { it.id }.toSet()
        checkDataFiles(
            context = context,
            label = "advancements",
            files = dataFileIds(resources, DebugDataPackFolders.ADVANCEMENTS),
            isLoaded = loadedAdvancements::contains,
        )
        val loadedTags = BuiltInRegistries.ITEM.tagNames.toList().map { it.location }.toSet()
        checkDataFiles(context, label = "item tags", files = dataFileIds(resources, DebugDataPackFolders.ITEM_TAGS), isLoaded = loadedTags::contains)
        val blocks = BuiltInRegistries.BLOCK.filter { BuiltInRegistries.BLOCK.getKey(it).namespace == NAMESPACE }
        val withoutLoot = blocks.filter { block -> isBlockLootTableMissing(server, block) }.map { BuiltInRegistries.BLOCK.getKey(it).toString() }
        context.check("All ${blocks.size} blocks have loot tables", withoutLoot.isEmpty(), withoutLoot.joinToString().ifEmpty { null })
    }

    /**
     * 資料包中本 mod 命名空間下某類資料的檔案，換算成資料的識別碼。
     *
     * @param resources 伺服器的資源。
     * @param folder 這類資料的資料夾。
     * @return 資料的識別碼。
     */
    private fun dataFileIds(resources: ResourceManager, folder: String): List<ResourceLocation> = resources.listResources(folder) { it.namespace == NAMESPACE && it.path.endsWith(JSON_SUFFIX) }.keys
        .map { resourceId(NAMESPACE, it.path.removePrefix("$folder/").removeSuffix(JSON_SUFFIX)) }

    /**
     * 檢查某類資料的每個檔案都已載入。
     *
     * @param context 步驟環境。
     * @param label 這類資料在報告中的名稱。
     * @param files 資料包中的檔案。
     * @param isLoaded 伺服器是否載入了這筆資料。
     */
    private fun checkDataFiles(context: AutotestContext, label: String, files: List<ResourceLocation>, isLoaded: (ResourceLocation) -> Boolean) {
        val missing = files.filterNot(isLoaded).map(ResourceLocation::toString)
        context.check(
            name = "All ${files.size} $label are loaded",
            passed = files.isNotEmpty() && missing.isEmpty(),
            detail = missing.joinToString().ifEmpty { null },
        )
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

    /** 資料檔的副檔名。 */
    private const val JSON_SUFFIX: String = ".json"

    /** 讀取 `sounds.json` 的 JSON 設定。 */
    private val JSON = Json { ignoreUnknownKeys = true }
}
