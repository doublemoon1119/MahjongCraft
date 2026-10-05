package com.doublemoon1119.mahjongcraft.platform.fabric.server.achievement

import com.doublemoon1119.mahjongcraft.platform.minecraft.achievement.AchievementStatisticIds
import com.doublemoon1119.mahjongcraft.platform.minecraft.achievement.BuiltInAchievementIds
import com.doublemoon1119.mahjongcraft.platform.minecraft.rule.riichi.achievement.RiichiAchievementIds
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.test.fail

/** 驗證內建進度 JSON 與成果、統計、語系資料彼此一致，並驗證條件 JSON 的解析。 */
class MahjongAchievementResourcesTest {
    private val locales = listOf("en_us", "ja_jp", "zh_cn", "zh_tw")

    /** 內建的全部成果 ID。 */
    private val knownAchievementIds: Set<String> = setOf(
        BuiltInAchievementIds.MATCH_COMPLETED,
        BuiltInAchievementIds.FIRST_PLACE,
        BuiltInAchievementIds.LAST_PLACE,
        BuiltInAchievementIds.WIN,
        BuiltInAchievementIds.SELF_DRAW_WIN,
        BuiltInAchievementIds.DISCARD_WIN,
        BuiltInAchievementIds.DEAL_IN,
        BuiltInAchievementIds.DOUBLE_DEAL_IN,
        BuiltInAchievementIds.TRIPLE_DEAL_IN,
        BuiltInAchievementIds.EXHAUSTIVE_DRAW,
        RiichiAchievementIds.MATCH_COMPLETED,
        RiichiAchievementIds.RIICHI_WIN,
        RiichiAchievementIds.IPPATSU,
        RiichiAchievementIds.NAGASHI_MANGAN,
        RiichiAchievementIds.BUSTED,
        RiichiAchievementIds.BUSTED_FAR,
        RiichiAchievementIds.BUSTED_IN_EAST_ONE,
        RiichiAchievementIds.COUNTED_YAKUMAN_EXACT,
        RiichiAchievementIds.COUNTED_YAKUMAN_OVER,
        RiichiAchievementIds.YAKUMAN,
        RiichiAchievementIds.DOUBLE_YAKUMAN,
        RiichiAchievementIds.MULTIPLE_YAKUMAN,
    ) + (RiichiAchievementIds.yakumanTypes + RiichiAchievementIds.doubleYakumanTypes).mapNotNull(RiichiAchievementIds::yakuman)

    /** 每個進度的上一層存在、條件引用的成果與統計都存在，且每個內建成果都有對應的進度。 */
    @Test
    fun `advancements reference existing parents achievements and statistics`() {
        val advancements = loadAdvancements()
        val referenced = mutableSetOf<String>()

        advancements.forEach { (id, json) ->
            json.get("parent")?.asString?.let { parent -> assertTrue(parent in advancements, "$id has a missing parent $parent") }
            json.getAsJsonObject("criteria").entrySet().forEach { (_, criterion) ->
                val trigger = criterion.asJsonObject.get("trigger").asString
                if (trigger == MahjongAchievementCriterion.ID.toString()) {
                    val condition = MahjongAchievementCriterion.conditionFromJson(criterion.asJsonObject.getAsJsonObject("conditions"))
                    condition.achievementIds.forEach { achievementId -> assertTrue(achievementId in knownAchievementIds, "$id references unknown $achievementId") }
                    condition.statistic?.let { assertTrue(it.statisticId in AchievementStatisticIds.ALL, "$id references unknown ${it.statisticId}") }
                    referenced += condition.achievementIds
                } else {
                    assertEquals("minecraft:inventory_changed", trigger, "$id uses an unexpected trigger")
                }
            }
        }

        assertEquals(knownAchievementIds, referenced)
    }

    /** 進度標題、說明與統計名稱在四種語言都有翻譯。 */
    @Test
    fun `advancement and statistic texts have translations in every language`() {
        val requiredKeys = loadAdvancements().values.flatMap { json ->
            val display = json.getAsJsonObject("display")
            listOf("title", "description").map { display.getAsJsonObject(it).get("translate").asString }
        } + AchievementStatisticIds.ALL.map { statisticId -> "stat." + statisticId.replace(':', '.') }

        locales.forEach { locale ->
            val translations = loadTranslations(locale)
            requiredKeys.forEach { key -> assertTrue(key in translations, "$locale is missing $key") }
        }
    }

    /** 條件 JSON 可以帶限定規則與統計門檻；缺少成果清單時拒絕載入。 */
    @Test
    fun `condition json parses rule and statistic and rejects missing achievements`() {
        val condition = MahjongAchievementCriterion.conditionFromJson(
            JsonParser.parseString(
                """{"achievements": ["mahjongcraft:win"], "rule": "mahjongcraft:riichi", "statistic": "mahjongcraft:wins", "at_least": 100}""",
            ).asJsonObject,
        )

        assertEquals(setOf("mahjongcraft:win"), condition.achievementIds)
        assertEquals("mahjongcraft:riichi", condition.ruleModuleId)
        assertEquals(100, condition.statistic?.atLeast)
        assertFailsWith<RuntimeException> { MahjongAchievementCriterion.conditionFromJson(JsonObject()) }
    }

    /** 以 `mahjongcraft:<相對路徑>` 為鍵載入全部內建進度。 */
    private fun loadAdvancements(): Map<String, JsonObject> {
        val root = javaClass.classLoader.getResource(ADVANCEMENT_ROOT) ?: fail("Missing $ADVANCEMENT_ROOT")
        val directory = File(root.toURI())
        return directory.walkTopDown()
            .filter { it.isFile && it.extension == "json" }
            .associate { file ->
                val path = file.relativeTo(directory).invariantSeparatorsPath.removeSuffix(".json")
                "mahjongcraft:$path" to JsonParser.parseString(file.readText()).asJsonObject
            }
            .also { assertTrue(it.isNotEmpty(), "No advancements found") }
    }

    private fun loadTranslations(locale: String): Set<String> {
        val resource = javaClass.classLoader.getResource("assets/mahjongcraft/lang/$locale.json") ?: fail("Missing $locale language file")
        return JsonParser.parseString(resource.readText()).asJsonObject.keySet()
    }

    private companion object {
        const val ADVANCEMENT_ROOT = "data/mahjongcraft/advancements"
    }
}
