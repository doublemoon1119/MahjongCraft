package com.doublemoon1119.mahjongcraft.platform.fabric.client.room

import com.doublemoon1119.mahjongcraft.flow.common.room.model.RoomSnapshot
import com.doublemoon1119.mahjongcraft.flow.common.time.MonotonicClock
import com.doublemoon1119.mahjongcraft.platform.fabric.client.fitClientText
import com.doublemoon1119.mahjongcraft.platform.fabric.client.gui.ScrollState
import com.doublemoon1119.mahjongcraft.platform.fabric.client.gui.ScrollbarLayout
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.ExcludedParticipantIdentity
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.MinecraftRoomScreenKeys
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.ConfirmScreen
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.gui.tooltip.Tooltip
import net.minecraft.client.gui.widget.ButtonWidget
import net.minecraft.client.gui.widget.TextFieldWidget
import net.minecraft.text.Text
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

/**
 * 房主管理暫時排除者的可搜尋清單；只送解除請求，更新以伺服器快照為準。
 *
 * @property parent 關閉後返回的房間畫面。
 * @property readRoom 目前已授權的房間快照。
 * @property name 既有的成員名稱解析。
 * @property face 繪製 FACE 或原有備援，不顯示模型預覽。
 * @property allow 送出個別解除請求。
 * @property allowAll 送出一次解除全部請求。
 * @property clock 與其他客戶端等待流程共用的暫停感知時鐘。
 */
internal class RoomExcludedParticipantsScreen(
    private val parent: Screen,
    private val readRoom: () -> RoomSnapshot?,
    private val name: (Uuid) -> Text,
    private val face: (DrawContext, Uuid, Int, Int, Int) -> Unit,
    private val allow: (Uuid) -> Unit,
    private val allowAll: () -> Unit,
    private val clock: MonotonicClock,
) : Screen(Text.translatable(MinecraftRoomScreenKeys.EXCLUDED_PARTICIPANTS)) {
    /** 既有清單捲動與滑塊拖曳行為。 */
    private val scroll = ScrollState()

    /** 保留重新配置畫面時的搜尋文字。 */
    private var query = ""

    /** 目前已授權的完整名單。 */
    private var entries = emptyList<Uuid>()

    /** 依目前顯示名稱篩選的名單。 */
    private var filtered = emptyList<Uuid>()

    /** 已解析的名稱；等待解除確認時保留，直到名單移除或請求逾時。 */
    private var labels = emptyMap<Uuid, String>()

    /** 名稱以外的身分資訊，不猜測未公開的實體。 */
    private var identities = emptyMap<Uuid, ExcludedParticipantIdentity>()

    /** 個別操作的等待期限；收到權威更新或逾時後才解除等待。 */
    private val pending = mutableMapOf<Uuid, Duration>()

    /** 只保留畫面可見列的解除按鈕。 */
    private val rowButtons = mutableListOf<ButtonWidget>()

    /** 保留搜尋焦點，清單刷新不重建整個畫面。 */
    private lateinit var search: TextFieldWidget

    /** 等待中不能重複送出全部解除。 */
    private lateinit var clearAll: ButtonWidget

    /** 清單寬度不隨大螢幕無限伸展。 */
    private val listWidth: Int get() = minOf(320, width - 36).coerceAtLeast(80)

    /** 清單左界。 */
    private val left: Int get() = (width - listWidth) / 2

    /** 清單上界，位於搜尋欄下方。 */
    private val top: Int get() = 66

    /** 清單下界，保留底部操作空間。 */
    private val bottom: Int get() = (height - 42).coerceAtLeast(top)

    /** 只建立能完整放進可用高度的列。 */
    private val rows: Int get() = (bottom - top) / ROW_HEIGHT

    /** 捲動輸入與繪製共用同一份幾何。 */
    private val scrollbar: ScrollbarLayout get() = ScrollbarLayout(top, bottom, filtered.size, rows, scroll.index, minOf(12, (bottom - top).coerceAtLeast(0)))

    override fun init() {
        search = TextFieldWidget(textRenderer, left, 38, listWidth, 20, Text.translatable(MinecraftRoomScreenKeys.EXCLUDED_SEARCH))
        search.setMaxLength(128)
        search.text = query
        search.setPlaceholder(Text.translatable(MinecraftRoomScreenKeys.EXCLUDED_SEARCH))
        search.setChangedListener {
            query = it
            scroll.reset()
            refreshRows()
        }
        addDrawableChild(search)
        clearAll = addDrawableChild(
            ButtonWidget.builder(Text.translatable(MinecraftRoomScreenKeys.ALLOW_ALL_REJOINING)) { confirmClearAll() }
                .dimensions(left, height - 30, (listWidth - 8) / 2, 20).build(),
        )
        addDrawableChild(
            ButtonWidget.builder(Text.translatable(MinecraftRoomScreenKeys.DONE)) { close() }
                .dimensions(left + (listWidth + 8) / 2, height - 30, (listWidth - 8) / 2, 20).build(),
        )
        refreshRows()
    }

    /** 依權威名單、顯示名稱或 UUID 篩選，只重建可見列，保留搜尋焦點與游標。 */
    private fun refreshRows() {
        rowButtons.forEach(::remove)
        rowButtons.clear()
        val room = readRoom()?.takeIf { it.isHost }
        entries = room?.excludedPlayerIds.orEmpty()
        pending.keys.retainAll(entries.toSet())
        identities = if (room == null) {
            emptyMap()
        } else {
            entries.mapNotNull { id -> ExcludedParticipantIdentity.resolve(room, id)?.let { id to it } }.toMap()
        }
        val previousLabels = labels
        labels = entries.associateWith { id ->
            if (id in pending) previousLabels[id] ?: name(id).string else name(id).string
        }
        filtered = entries.filter { id ->
            identities[id]?.matches(labels[id].orEmpty(), query) == true
        }
        scroll.clamp((filtered.size - rows).coerceAtLeast(0))
        filtered.drop(scroll.index).take(rows).forEachIndexed { index, id ->
            val button = ButtonWidget.builder(Text.literal("X")) {
                pending[id] = clock.now() + CONFIRMATION_TIMEOUT
                allow(id)
                refreshRows()
            }.dimensions(left + listWidth - 24, top + index * ROW_HEIGHT + 8, 20, 20)
                .narrationSupplier { Text.translatable(MinecraftRoomScreenKeys.ALLOW_REJOINING).append(": ").append(Text.literal(labels[id].orEmpty())) }
                .build().also {
                    it.tooltip = Tooltip.of(Text.translatable(MinecraftRoomScreenKeys.ALLOW_REJOINING))
                    it.active = id !in pending
                }
            rowButtons += addDrawableChild(button)
        }
        if (::clearAll.isInitialized) clearAll.active = entries.isNotEmpty() && pending.isEmpty()
    }

    /** 全部解除不受搜尋文字限制；確認後仍由伺服器檢查權限與最新名單。 */
    private fun confirmClearAll() {
        val confirmation = object : ConfirmScreen(
            { accepted ->
                if (accepted && readRoom()?.isHost == true) {
                    readRoom()?.excludedPlayerIds.orEmpty().forEach { pending[it] = clock.now() + CONFIRMATION_TIMEOUT }
                    allowAll()
                }
                client?.setScreen(this@RoomExcludedParticipantsScreen)
            },
            Text.translatable(MinecraftRoomScreenKeys.ALLOW_ALL_REJOINING),
            Text.translatable(MinecraftRoomScreenKeys.ALLOW_ALL_CONFIRMATION),
        ) {
            override fun shouldPause(): Boolean = false
        }
        client?.setScreen(confirmation)
    }

    override fun tick() {
        search.tick()
        val room = readRoom()
        if (room == null || !room.isHost) {
            close()
            return
        }
        val expired = pending.entries.removeAll { (id, deadline) -> id !in room.excludedPlayerIds || clock.now() >= deadline }
        if (room.excludedPlayerIds != entries || expired || entries.any { (it !in pending && labels[it] != name(it).string) || identities[it] != ExcludedParticipantIdentity.resolve(room, it) }) refreshRows()
    }

    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        val room = readRoom()
        if (room == null || !room.isHost) {
            close()
            return
        }
        // 網路快照可能在兩次 tick 之間更新，繪製前先移除已不在名單上的卡片。
        if (room.excludedPlayerIds != entries) refreshRows()
        renderBackground(context)
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 16, 0xFFFFFF)
        filtered.drop(scroll.index).take(rows).forEachIndexed { index, id ->
            val y = top + index * ROW_HEIGHT
            val hovered = mouseX in left until left + listWidth && mouseY in y until y + ROW_HEIGHT - 4
            context.fill(left, y, left + listWidth, y + ROW_HEIGHT - 4, if (hovered) 0xA0404040.toInt() else 0x90202020.toInt())
            face(context, id, left + 4, y + 6, 24)
            context.drawTextWithShadow(textRenderer, fitClientText(textRenderer, Text.literal(labels[id].orEmpty()), listWidth - 70), left + 36, y + 5, 0xFFFFFF)
            identities[id]?.let { identity ->
                val value = Text.translatable(MinecraftRoomScreenKeys.EXCLUDED_UUID, identity.id.toString())
                context.drawTextWithShadow(
                    textRenderer,
                    fitClientText(textRenderer, value, listWidth - 70),
                    left + 36,
                    y + 19,
                    0xA0A0A0,
                )
            }
        }
        if (filtered.isEmpty()) {
            val key = if (entries.isEmpty()) MinecraftRoomScreenKeys.EXCLUDED_EMPTY else MinecraftRoomScreenKeys.EXCLUDED_NO_RESULTS
            context.drawCenteredTextWithShadow(textRenderer, Text.translatable(key), width / 2, top + 16, 0xA0A0A0)
        }
        val bar = scrollbar
        if (bar.maximumScroll > 0 && rows > 0) {
            context.fill(left + listWidth + 4, bar.trackTop, left + listWidth + 10, bar.trackBottom, 0x80505050.toInt())
            context.fill(left + listWidth + 4, bar.thumbTop, left + listWidth + 10, bar.thumbTop + bar.thumbHeight, 0xFFD0D0D0.toInt())
        }
        super.render(context, mouseX, mouseY, delta)
        filtered.drop(scroll.index).take(rows).forEachIndexed { index, id ->
            val y = top + index * ROW_HEIGHT
            if (mouseX in (left + 36) until (left + listWidth - 28) && mouseY in (y + 17) until (y + 31)) {
                identities[id]?.let {
                    context.drawTooltip(
                        textRenderer,
                        Text.translatable(MinecraftRoomScreenKeys.EXCLUDED_UUID, it.id.toString()),
                        mouseX,
                        mouseY,
                    )
                }
            }
        }
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, amount: Double): Boolean {
        if (mouseY >= top && mouseY < bottom && scroll.scrollBy(amount, scrollbar.maximumScroll)) {
            refreshRows()
            return true
        }
        return super.mouseScrolled(mouseX, mouseY, amount)
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (button == 0 && rows > 0 && scrollbar.maximumScroll > 0 && mouseX >= left + listWidth + 4 && mouseX < left + listWidth + 10 && mouseY >= top && mouseY < bottom) {
            scroll.beginDrag(mouseY, scrollbar)
            refreshRows()
            return true
        }
        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun mouseDragged(mouseX: Double, mouseY: Double, button: Int, deltaX: Double, deltaY: Double): Boolean {
        if (button == 0 && scroll.dragging) {
            if (scroll.dragTo(mouseY, scrollbar)) refreshRows()
            return true
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)
    }

    override fun mouseReleased(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (button == 0) scroll.endDrag()
        return super.mouseReleased(mouseX, mouseY, button)
    }

    override fun close() {
        client?.setScreen(parent)
    }

    override fun shouldPause(): Boolean = false

    /** 清單的固定列高與既有回覆等待上限。 */
    private companion object {
        /** 頭像與角落按鈕不與下一列重疊。 */
        const val ROW_HEIGHT: Int = 40

        /** 只解除本地等待，不假裝伺服器已成功。 */
        val CONFIRMATION_TIMEOUT: Duration = 10.seconds
    }
}
