package com.doublemoon1119.mahjongcraft.platform.minecraft.tile

import com.doublemoon1119.mahjongcraft.platform.minecraft.dice.DiceAnimationVector
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** 驗證私人手牌布局的理牌、動畫銜接與共享牌張交接。 */
class PrivateHandLayoutTest {
    /** 第一張固定測試牌的識別碼。 */
    private val firstId = Uuid.parse("00000000-0000-0000-0000-000000000001")

    /** 第二張固定測試牌的識別碼。 */
    private val secondId = Uuid.parse("00000000-0000-0000-0000-000000000002")

    /** 第三張固定測試牌的識別碼。 */
    private val thirdId = Uuid.parse("00000000-0000-0000-0000-000000000003")

    /** 靜止牌張依私人牌序占用固定格位，並保留獨立摸牌位。 */
    @Test
    fun `static hand uses sorted private slots and drawn slot`() {
        val layout = PrivateHandLayout()
        val inputs = transforms(
            firstId to transform(0.0),
            secondId to transform(1.0),
            thirdId to transform(2.0),
        )

        val frame = layout.update(
            time = 0.0,
            standingOrder = listOf(secondId, firstId),
            drawnId = thirdId,
            inputs = inputs,
            releaseHand = false,
        )

        assertEquals(transform(0.0), frame.getValue(secondId))
        assertEquals(transform(1.0), frame.getValue(firstId))
        assertEquals(transform(2.0), frame.getValue(thirdId))
    }

    /** 理牌中途改變終點時會從當下位置接續，不會在同一時間點跳到新終點。 */
    @Test
    fun `reorder at the same time starts from the current position`() {
        val layout = PrivateHandLayout()
        val inputs = transforms(firstId to transform(0.0), secondId to transform(1.0))

        val before = layout.update(
            time = 0.0,
            standingOrder = listOf(firstId, secondId),
            drawnId = null,
            inputs = inputs,
            releaseHand = false,
        )
        val after = layout.update(
            time = 0.0,
            standingOrder = listOf(secondId, firstId),
            drawnId = null,
            inputs = inputs,
            releaseHand = false,
        )

        assertEquals(before.getValue(firstId), after.getValue(firstId))
        assertEquals(before.getValue(secondId), after.getValue(secondId))
    }

    /** 理牌過渡完成後會抵達新的格位終點。 */
    @Test
    fun `reorder reaches the new endpoint`() {
        val layout = PrivateHandLayout()
        val inputs = transforms(firstId to transform(0.0), secondId to transform(1.0))

        layout.update(
            time = 0.0,
            standingOrder = listOf(firstId, secondId),
            drawnId = null,
            inputs = inputs,
            releaseHand = false,
        )
        layout.update(
            time = 0.0,
            standingOrder = listOf(secondId, firstId),
            drawnId = null,
            inputs = inputs,
            releaseHand = false,
        )
        val endpoint = layout.update(
            time = 5.0,
            standingOrder = listOf(secondId, firstId),
            drawnId = null,
            inputs = inputs,
            releaseHand = false,
        )

        assertEquals(transform(1.0), endpoint.getValue(firstId))
        assertEquals(transform(0.0), endpoint.getValue(secondId))
    }

    /** 立牌數量變少但原生資料尚未更新時，沿用原生舊中心，不自行壓縮牌列。 */
    @Test
    fun `shortened hand keeps the old native row positions`() {
        val layout = PrivateHandLayout()
        val native = transforms(
            firstId to transform(-1.0),
            secondId to transform(0.0),
            thirdId to transform(1.0),
        )

        layout.update(
            time = 0.0,
            standingOrder = listOf(firstId, secondId, thirdId),
            drawnId = null,
            inputs = native,
            releaseHand = false,
        )
        val shortened = layout.update(
            time = 1.0,
            standingOrder = listOf(firstId, secondId),
            drawnId = null,
            inputs = native,
            releaseHand = false,
        )

        assertEquals(transform(-1.0), shortened.getValue(firstId))
        assertEquals(transform(0.0), shortened.getValue(secondId))
    }

    /** 尚未發出的蓋牌不應在取得立牌資料前被私人布局接管。 */
    @Test
    fun `face down native tile is not claimed before it is dealt`() {
        val layout = PrivateHandLayout()
        val faceDown = transform(0.0).copy(poseDegrees = 180.0f)
        val frame = layout.update(
            time = 0.0,
            standingOrder = listOf(firstId, secondId),
            drawnId = null,
            inputs = displayInputs(firstId to input(faceDown), secondId to input(transform(1.0))),
            releaseHand = false,
        )

        assertFalse(frame.containsKey(firstId))
        assertEquals(transform(1.0), frame.getValue(secondId))
    }

    /** 摸牌轉為立牌後會納入新的立牌格位，而不是停留在獨立摸牌位。 */
    @Test
    fun `drawn tile joins the standing row when it becomes standing`() {
        val layout = PrivateHandLayout()
        val native = transforms(firstId to transform(0.0), secondId to transform(1.0))

        layout.update(
            time = 0.0,
            standingOrder = listOf(firstId),
            drawnId = secondId,
            inputs = native,
            releaseHand = false,
        )
        val standing = layout.update(
            time = 1.0,
            standingOrder = listOf(firstId, secondId),
            drawnId = null,
            inputs = native,
            releaseHand = false,
        )

        assertEquals(transform(1.0), standing.getValue(secondId))
    }

    /** 理牌尚未完成時收到新終點，會從目前位置重新銜接而非跳回舊起點。 */
    @Test
    fun `new target during reorder resumes from current position`() {
        val layout = PrivateHandLayout()
        val initial = transforms(firstId to transform(0.0), secondId to transform(1.0))
        layout.update(
            time = 0.0,
            standingOrder = listOf(firstId, secondId),
            drawnId = null,
            inputs = initial,
            releaseHand = false,
        )
        layout.update(
            time = 0.0,
            standingOrder = listOf(secondId, firstId),
            drawnId = null,
            inputs = initial,
            releaseHand = false,
        )

        val changed = transforms(firstId to transform(10.0), secondId to transform(11.0))
        val resumed = layout.update(
            time = 2.0,
            standingOrder = listOf(secondId, firstId),
            drawnId = null,
            inputs = changed,
            releaseHand = false,
        )
        val endpoint = layout.update(
            time = 7.0,
            standingOrder = listOf(secondId, firstId),
            drawnId = null,
            inputs = changed,
            releaseHand = false,
        )

        assertTrue(resumed.getValue(firstId).position.x < transform(11.0).position.x)
        assertEquals(transform(11.0).position.x, endpoint.getValue(firstId).position.x, 0.000001)
    }

    /** 原生資料缺少一張牌時保留其他牌的格位，不將後面的牌壓縮。 */
    @Test
    fun `missing input does not compress remaining slots`() {
        val layout = PrivateHandLayout()
        val frame = layout.update(
            time = 0.0,
            standingOrder = listOf(firstId, secondId, thirdId),
            drawnId = null,
            inputs = transforms(firstId to transform(0.0), thirdId to transform(2.0)),
            releaseHand = false,
        )

        assertFalse(frame.containsKey(secondId))
        assertEquals(transform(0.0), frame.getValue(firstId))
        assertEquals(transform(2.0), frame.getValue(thirdId))
    }

    /** 多張牌各自有原生動畫時，私人布局仍會同時追蹤，不會取消整副手牌。 */
    @Test
    fun `multiple native animations remain independently tracked`() {
        val layout = PrivateHandLayout()
        val animated = displayInputs(
            firstId to input(transform(0.0), motion = motion(transform(-1.0), transform(0.0))),
            secondId to input(transform(1.0), motion = motion(transform(-1.0), transform(1.0))),
        )

        val frame = layout.update(
            time = 1.0,
            standingOrder = listOf(firstId, secondId),
            drawnId = null,
            inputs = animated,
            releaseHand = false,
        )

        assertEquals(setOf(firstId, secondId), frame.keys)
    }

    /** 和牌或完整攤牌時釋放私人布局，牌張交回共享位置並最終移除接管狀態。 */
    @Test
    fun `release hand hands tiles back to shared layout`() {
        val layout = PrivateHandLayout()
        val initial = transforms(firstId to transform(0.0))
        layout.update(
            time = 0.0,
            standingOrder = listOf(firstId),
            drawnId = null,
            inputs = initial,
            releaseHand = false,
        )

        val shared = transforms(firstId to transform(10.0))
        val duringHandoff = layout.update(
            time = 1.0,
            standingOrder = listOf(firstId),
            drawnId = null,
            inputs = shared,
            releaseHand = true,
        )
        val finished = layout.update(
            time = 5.0,
            standingOrder = listOf(firstId),
            drawnId = null,
            inputs = shared,
            releaseHand = true,
        )
        val released = layout.update(
            time = 6.0,
            standingOrder = listOf(firstId),
            drawnId = null,
            inputs = shared,
            releaseHand = true,
        )

        assertTrue(duringHandoff.containsKey(firstId))
        assertTrue(finished.getValue(firstId).position.x > 0.0)
        assertTrue(finished.getValue(firstId).position.x < 10.0)
        assertTrue(released.isEmpty())
    }

    /** 共享牌張仍有原生動畫時，釋放私人布局會銜接該動畫並在完成後移除。 */
    @Test
    fun `release hand follows an active shared animation`() {
        val layout = PrivateHandLayout()
        layout.update(
            time = 0.0,
            standingOrder = listOf(firstId),
            drawnId = null,
            inputs = transforms(firstId to transform(0.0)),
            releaseHand = false,
        )

        val shared = displayInputs(
            firstId to input(
                target = transform(10.0),
                motion = motion(transform(0.0), transform(10.0)),
            ),
        )
        val duringHandoff = layout.update(
            time = 1.0,
            standingOrder = listOf(firstId),
            drawnId = null,
            inputs = shared,
            releaseHand = true,
        )
        val moving = layout.update(
            time = 3.0,
            standingOrder = listOf(firstId),
            drawnId = null,
            inputs = shared,
            releaseHand = true,
        )
        val released = layout.update(
            time = 5.0,
            standingOrder = listOf(firstId),
            drawnId = null,
            inputs = shared,
            releaseHand = true,
        )

        assertEquals(transform(0.0), duringHandoff.getValue(firstId))
        assertTrue(moving.getValue(firstId).position.x > transform(0.0).position.x)
        assertTrue(moving.getValue(firstId).position.x < transform(10.0).position.x)
        assertTrue(released.isEmpty())
    }

    /** 原生資料短暫缺席時保留交接中的牌，超過寬限時間後才移除。 */
    @Test
    fun `missing input uses a short handoff grace period`() {
        val layout = PrivateHandLayout()
        val inputs = transforms(firstId to transform(0.0))
        layout.update(
            time = 0.0,
            standingOrder = listOf(firstId),
            drawnId = null,
            inputs = inputs,
            releaseHand = false,
        )

        val grace = layout.update(
            time = 1.0,
            standingOrder = listOf(firstId),
            drawnId = null,
            inputs = emptyMap(),
            releaseHand = false,
        )
        val expired = layout.update(
            time = 4.0,
            standingOrder = listOf(firstId),
            drawnId = null,
            inputs = emptyMap(),
            releaseHand = false,
        )

        assertTrue(grace.containsKey(firstId))
        assertTrue(expired.isEmpty())
    }

    /** 清除會移除全部私人布局並重設時間，避免換桌後沿用舊牌張狀態。 */
    @Test
    fun `clear removes all private entries`() {
        val layout = PrivateHandLayout()
        val inputs = transforms(firstId to transform(0.0))
        layout.update(
            time = 10.0,
            standingOrder = listOf(firstId),
            drawnId = null,
            inputs = inputs,
            releaseHand = false,
        )

        layout.clear()

        assertTrue(
            layout.update(
                time = 0.0,
                standingOrder = emptyList(),
                drawnId = null,
                inputs = inputs,
                releaseHand = false,
            ).isEmpty(),
        )
    }

    /** 時間回退時仍以最後時間取樣，動畫不會倒退。 */
    @Test
    fun `time going backwards does not rewind animation`() {
        val layout = PrivateHandLayout()
        val inputs = displayInputs(
            firstId to input(transform(0.0), motion = motion(transform(-1.0), transform(0.0))),
        )

        val later = layout.update(
            time = 3.0,
            standingOrder = listOf(firstId),
            drawnId = null,
            inputs = inputs,
            releaseHand = false,
        ).getValue(firstId)
        val earlier = layout.update(
            time = 1.0,
            standingOrder = listOf(firstId),
            drawnId = null,
            inputs = inputs,
            releaseHand = false,
        ).getValue(firstId)

        assertEquals(later, earlier)
    }

    /** 四個座位方向皆以相同牌距理牌，不假設牌列沿世界 X 軸。 */
    @Test
    fun `all seat directions retain their own row geometry`() {
        listOf(0.0f, 90.0f, 180.0f, 270.0f).forEach { yaw ->
            val step = MahjongTileDimensions.TILE_WIDTH + MahjongTileDimensions.TILE_SMALL_PADDING
            val angle = yaw * PI / 180.0
            val first = transform(0.0).copy(yawDegrees = yaw)
            val second = TileDisplayTransform(
                position = DiceAnimationVector(cos(angle) * step, 0.0, sin(angle) * step),
                yawDegrees = yaw,
                poseDegrees = 0.0f,
            )
            val frame = PrivateHandLayout().update(
                time = 0.0,
                standingOrder = listOf(secondId, firstId),
                drawnId = null,
                inputs = transforms(firstId to first, secondId to second),
                releaseHand = false,
            )
            assertEquals(second.position.x, frame.getValue(firstId).position.x, 0.000001)
            assertEquals(second.position.z, frame.getValue(firstId).position.z, 0.000001)
            assertEquals(first.position.x, frame.getValue(secondId).position.x, 0.000001)
            assertEquals(first.position.z, frame.getValue(secondId).position.z, 0.000001)
        }
    }

    /** 摸牌併入立牌時，先從獨立摸牌位出發，等原生位置到齊後仍抵達新格位。 */
    @Test
    fun `drawn merge remains continuous until new native positions arrive`() {
        val layout = PrivateHandLayout()
        val old = transforms(firstId to transform(0.0), secondId to transform(1.5))
        layout.update(
            time = 0.0,
            standingOrder = listOf(firstId),
            drawnId = secondId,
            inputs = old,
            releaseHand = false,
        )
        val started = layout.update(
            time = 1.0,
            standingOrder = listOf(firstId, secondId),
            drawnId = null,
            inputs = old,
            releaseHand = false,
        )
        assertEquals(transform(1.5), started.getValue(secondId))
        val aligned = transforms(firstId to transform(-0.5), secondId to transform(0.5))
        layout.update(
            time = 2.0,
            standingOrder = listOf(firstId, secondId),
            drawnId = null,
            inputs = aligned,
            releaseHand = false,
        )
        val finished = layout.update(
            time = 6.0,
            standingOrder = listOf(firstId, secondId),
            drawnId = null,
            inputs = aligned,
            releaseHand = false,
        )
        assertEquals(transform(0.5).position.x, finished.getValue(secondId).position.x, 0.000001)
        assertEquals(transform(-0.5).position.x, finished.getValue(firstId).position.x, 0.000001)
    }

    /** 舊幀交給 renderer 後，下一次布局更新不會回頭修改舊幀內容。 */
    @Test
    fun `published display frame is not mutated by the next update`() {
        val layout = PrivateHandLayout()
        val inputs = transforms(firstId to transform(0.0), secondId to transform(1.0))
        val firstFrame = layout.update(
            time = 0.0,
            standingOrder = listOf(firstId, secondId),
            drawnId = null,
            inputs = inputs,
            releaseHand = false,
        )
        val saved = firstFrame.toMap()
        layout.update(
            time = 1.0,
            standingOrder = listOf(secondId, firstId),
            drawnId = null,
            inputs = inputs,
            releaseHand = false,
        )
        assertEquals(saved, firstFrame)
    }

    /** 一張牌離手時，只交接該張牌，其他立牌仍從私人位置補位。 */
    @Test
    fun `departing tile does not reset the rest of the private hand`() {
        val layout = PrivateHandLayout()
        val old = transforms(firstId to transform(-1.0), secondId to transform(0.0), thirdId to transform(1.0))
        val before = layout.update(
            time = 0.0,
            standingOrder = listOf(thirdId, secondId, firstId),
            drawnId = null,
            inputs = old,
            releaseHand = false,
        )
        val discarded = transform(8.0).copy(poseDegrees = 90.0f)
        val next = transforms(firstId to discarded, secondId to transform(-0.5), thirdId to transform(0.5))
        val start = layout.update(
            time = 1.0,
            standingOrder = listOf(thirdId, secondId),
            drawnId = null,
            inputs = next,
            releaseHand = false,
        )
        assertEquals(before.getValue(thirdId), start.getValue(thirdId))
        assertEquals(before.getValue(firstId), start.getValue(firstId))
        val finished = layout.update(
            time = 6.0,
            standingOrder = listOf(thirdId, secondId),
            drawnId = null,
            inputs = next,
            releaseHand = false,
        )
        assertFalse(firstId in finished)
        assertEquals(transform(-0.5).position.x, finished.getValue(thirdId).position.x, 0.000001)
        assertEquals(transform(0.5).position.x, finished.getValue(secondId).position.x, 0.000001)
    }

    /** 原生交接的旋轉緩出設定必須保留，不能只有位置沿用原有曲線。 */
    @Test
    fun `handoff retains shared rotation easing`() {
        val layout = PrivateHandLayout()
        val initial = transform(0.0)
        layout.update(
            time = 0.0,
            standingOrder = listOf(firstId),
            drawnId = null,
            inputs = transforms(firstId to initial),
            releaseHand = false,
        )
        val target = transform(4.0).copy(poseDegrees = 90.0f)
        val moving = input(
            target = target,
            motion = motion(initial, target).copy(
                spec = TileMotionAnimationSpec(
                    startPoseRotationDegrees = 0.0f,
                    endPoseRotationDegrees = 90.0f,
                    easeRotation = true,
                ),
            ),
        )
        layout.update(
            time = 0.0,
            standingOrder = emptyList(),
            drawnId = null,
            inputs = displayInputs(firstId to moving),
            releaseHand = false,
        )
        val frame = layout.update(
            time = 2.0,
            standingOrder = emptyList(),
            drawnId = null,
            inputs = displayInputs(firstId to moving),
            releaseHand = false,
        )
        assertEquals(moving.sample(2.0).poseDegrees, frame.getValue(firstId).poseDegrees)
    }

    /** 位置封包延後超過寬限時間時，也不能把減少張數前的座標誤認成新中心。 */
    @Test
    fun `delayed native positions never shift the cached row center`() {
        val layout = PrivateHandLayout()
        val old = transforms(firstId to transform(-1.0), secondId to transform(0.0), thirdId to transform(1.0))
        layout.update(
            time = 0.0,
            standingOrder = listOf(firstId, secondId, thirdId),
            drawnId = null,
            inputs = old,
            releaseHand = false,
        )
        layout.update(
            time = 1.0,
            standingOrder = listOf(firstId, secondId),
            drawnId = null,
            inputs = old,
            releaseHand = false,
        )
        val settled = layout.update(
            time = 20.0,
            standingOrder = listOf(firstId, secondId),
            drawnId = null,
            inputs = old,
            releaseHand = false,
        )
        assertEquals(transform(-0.5).position.x, settled.getValue(firstId).position.x, 0.000001)
        assertEquals(transform(0.5).position.x, settled.getValue(secondId).position.x, 0.000001)
    }

    /** 摸牌先收到合併位置、快照稍後才到時，牌排中心仍維持原位。 */
    @Test
    fun `native merge arriving before snapshot does not move the row center`() {
        val layout = PrivateHandLayout()
        layout.update(
            time = 0.0,
            standingOrder = listOf(firstId),
            drawnId = secondId,
            inputs = transforms(firstId to transform(0.0), secondId to transform(1.5)),
            releaseHand = false,
        )
        val aligned = transforms(firstId to transform(-0.5), secondId to transform(0.5))
        layout.update(
            time = 1.0,
            standingOrder = listOf(firstId),
            drawnId = secondId,
            inputs = aligned,
            releaseHand = false,
        )
        val waiting = layout.update(
            time = 10.0,
            standingOrder = listOf(firstId),
            drawnId = secondId,
            inputs = aligned,
            releaseHand = false,
        )
        assertEquals(transform(0.0).position.x, waiting.getValue(firstId).position.x, 0.000001)
        layout.update(
            time = 11.0,
            standingOrder = listOf(firstId, secondId),
            drawnId = null,
            inputs = aligned,
            releaseHand = false,
        )
        val finished = layout.update(
            time = 16.0,
            standingOrder = listOf(firstId, secondId),
            drawnId = null,
            inputs = aligned,
            releaseHand = false,
        )
        assertEquals(transform(-0.5).position.x, finished.getValue(firstId).position.x, 0.000001)
        assertEquals(transform(0.5).position.x, finished.getValue(secondId).position.x, 0.000001)
    }

    /** 身分重複是壞資料，不得以 map 覆蓋方式偷偷產生另一份選牌對照。 */
    @Test
    fun `duplicate hand identity is not used for private mapping`() {
        val frame = PrivateHandLayout().update(
            time = 0.0,
            standingOrder = listOf(firstId, firstId),
            drawnId = null,
            inputs = transforms(firstId to transform(0.0)),
            releaseHand = false,
        )
        assertTrue(frame.isEmpty())
    }

    /** 將牌張終點快速組成原生輸入資料。 */
    private fun transforms(vararg values: Pair<Uuid, TileDisplayTransform>): Map<Uuid, TileDisplayInput> = values.associate { (id, target) ->
        id to input(target)
    }

    /** 將完整原生輸入資料組成布局更新所需的索引。 */
    private fun displayInputs(vararg values: Pair<Uuid, TileDisplayInput>): Map<Uuid, TileDisplayInput> = values.toMap()

    /** 建立單張可見牌的原生輸入。 */
    private fun input(target: TileDisplayTransform, motion: TileDisplayMotion? = null): TileDisplayInput = TileDisplayInput(
        target = target,
        motion = motion,
        visible = true,
    )

    /** 建立從指定起點前往終點的測試動畫。 */
    private fun motion(from: TileDisplayTransform, to: TileDisplayTransform): TileDisplayMotion = TileDisplayMotion(
        startedAt = 0L,
        spec = TileMotionAnimationSpec(
            startPoseRotationDegrees = from.poseDegrees,
            endPoseRotationDegrees = to.poseDegrees,
        ),
        startOffset = DiceAnimationVector(
            from.position.x - to.position.x,
            from.position.y - to.position.y,
            from.position.z - to.position.z,
        ),
        startYawOffset = from.yawDegrees - to.yawDegrees,
    )

    /** 建立指定格位的直立牌，牌距採用正式牌寬加微小間距。 */
    private fun transform(slot: Double): TileDisplayTransform = TileDisplayTransform(
        position = DiceAnimationVector(
            slot * (MahjongTileDimensions.TILE_WIDTH + MahjongTileDimensions.TILE_SMALL_PADDING),
            0.0,
            0.0,
        ),
        yawDegrees = 0.0f,
        poseDegrees = 0.0f,
    )
}
