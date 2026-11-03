package com.doublemoon1119.mahjongcraft.example.fabric

import com.doublemoon1119.mahjongcraft.example.fabric.furniture.block.ExampleBenchGeometry
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import net.minecraft.util.math.Box
import net.minecraft.util.math.Direction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** 實際長凳模型、連接狀態與碰撞範圍必須一致。 */
class ExampleBenchResourcesTest {
    /** 四種端點組合使用與實際 JSON 相同的範圍，沒有只在模型上消失的扶手。 */
    @Test
    fun `bench model elements match the actual collision geometry for every connection state`() {
        for (left in listOf(false, true)) {
            for (right in listOf(false, true)) {
                val models = buildList {
                    add("bench")
                    if (!left) add("bench_left")
                    if (!right) add("bench_right")
                }
                val boxes = models.flatMap { name ->
                    read("models/block/$name.json").getValue("elements").jsonArray.map { element ->
                        val from = element.jsonObject.getValue("from").jsonArray.map { it.jsonPrimitive.double }
                        val to = element.jsonObject.getValue("to").jsonArray.map { it.jsonPrimitive.double }
                        Box(
                            from[0],
                            from[1],
                            from[2],
                            to[0],
                            to[1],
                            to[2],
                        )
                    }
                }
                assertEquals(
                    ExampleBenchGeometry.boxes(
                        Direction.NORTH,
                        left,
                        right,
                    ).toSet(),
                    boxes.toSet(),
                )
            }
        }
    }

    /** 模型旋轉對應碰撞前方，物品採完整單格而不是缺少扶手的中段。 */
    @Test
    fun `bench resource rotations and inventory model preserve facing and both ends`() {
        val parts = read("blockstates/bench.json").getValue("multipart").jsonArray
        val rotations = mapOf(
            "north" to 0,
            "east" to 90,
            "south" to 180,
            "west" to 270,
        )
        parts.forEach { part ->
            val face = part.jsonObject.getValue("when").jsonObject.getValue("facing").jsonPrimitive.content
            assertEquals(rotations.getValue(face), part.jsonObject.getValue("apply").jsonObject.getValue("y").jsonPrimitive.int)
        }
        assertEquals(12, parts.size)
        assertEquals("mahjongcraft_example:block/bench_single", read("models/item/bench.json").getValue("parent").jsonPrimitive.content)
        assertEquals(8, read("models/block/bench_single.json").getValue("elements").jsonArray.size)
    }

    /** 超過一格的椅背不能讓預設 UV 讀到相鄰 atlas 貼圖。 */
    @Test
    fun `above block elements use explicit UV coordinates inside their own sprite`() {
        for (name in listOf(
            "bench",
            "bench_single",
            "bench_left",
            "bench_right",
        )) {
            val elements = read("models/block/$name.json").getValue("elements").jsonArray
            elements.filter { element -> element.jsonObject.getValue("to").jsonArray[1].jsonPrimitive.double > 16.0 }.forEach { element ->
                element.jsonObject.getValue("faces").jsonObject.values.forEach { face ->
                    val uv = assertNotNull(face.jsonObject["uv"], "Above-block faces need explicit UVs").jsonArray
                    assertEquals(4, uv.size)
                    assertTrue(uv.all { it.jsonPrimitive.double in 0.0..16.0 }, "UVs must stay inside the texture sprite")
                }
            }
        }
    }

    /** 單格物品與所有端點組合的外表面不可重疊，避免轉動視角時閃爍。 */
    @Test
    fun `bench armrests and backrest have no overlapping coplanar outward faces`() {
        assertNoOverlappingFaces(listOf("bench_single"))
        for (left in listOf(false, true)) {
            for (right in listOf(false, true)) {
                assertNoOverlappingFaces(
                    buildList {
                        add("bench")
                        if (!left) add("bench_left")
                        if (!right) add("bench_right")
                    },
                )
            }
        }
    }

    /**
     * 檢查實際模型每對同朝向的面，只有共邊或共點可以位於同一平面。
     *
     * @param models 一次繪製的模型組合。
     */
    private fun assertNoOverlappingFaces(models: List<String>) {
        val faces = models.flatMap { model ->
            read("models/block/$model.json").getValue("elements").jsonArray.flatMap { element ->
                val from = element.jsonObject.getValue("from").jsonArray.map { it.jsonPrimitive.double }
                val to = element.jsonObject.getValue("to").jsonArray.map { it.jsonPrimitive.double }
                val box = Box(
                    from[0],
                    from[1],
                    from[2],
                    to[0],
                    to[1],
                    to[2],
                )
                element.jsonObject.getValue("faces").jsonObject.keys.map { face -> assertNotNull(Direction.byName(face)) to box }
            }
        }
        for ((index, face) in faces.withIndex()) {
            val (direction, first) = face
            for ((otherDirection, second) in faces.drop(index + 1)) {
                if (direction != otherDirection) continue
                val samePlane = when (direction) {
                    Direction.DOWN -> first.minY == second.minY
                    Direction.UP -> first.maxY == second.maxY
                    Direction.NORTH -> first.minZ == second.minZ
                    Direction.SOUTH -> first.maxZ == second.maxZ
                    Direction.WEST -> first.minX == second.minX
                    Direction.EAST -> first.maxX == second.maxX
                }
                val overlappingArea = when (direction.axis) {
                    Direction.Axis.X -> first.minY < second.maxY && second.minY < first.maxY && first.minZ < second.maxZ && second.minZ < first.maxZ
                    Direction.Axis.Y -> first.minX < second.maxX && second.minX < first.maxX && first.minZ < second.maxZ && second.minZ < first.maxZ
                    Direction.Axis.Z -> first.minX < second.maxX && second.minX < first.maxX && first.minY < second.maxY && second.minY < first.maxY
                }
                assertFalse(samePlane && overlappingArea, "Coplanar $direction faces overlap in $models: $first / $second")
            }
        }
    }

    /**
     * 從真正會打包的共用資源讀取模型。
     *
     * @param path 相對範例資源根目錄的路徑。
     * @return 已解析的 JSON 物件。
     */
    private fun read(path: String) = assertNotNull(javaClass.classLoader.getResourceAsStream("assets/mahjongcraft_example/$path"), path)
        .bufferedReader().use { Json.parseToJsonElement(it.readText()).jsonObject }
}
