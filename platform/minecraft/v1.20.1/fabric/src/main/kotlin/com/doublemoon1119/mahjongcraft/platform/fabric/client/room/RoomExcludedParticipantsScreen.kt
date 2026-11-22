package com.doublemoon1119.mahjongcraft.platform.fabric.client.room

import com.doublemoon1119.mahjongcraft.flow.common.room.model.RoomSnapshot
import com.doublemoon1119.mahjongcraft.flow.common.time.MonotonicClock
import com.doublemoon1119.mahjongcraft.platform.fabric.client.gui.RestartableMarqueeButtonWidget
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.fitClientText
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.gui.FormScreenLayout
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.gui.PixelIcon
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.gui.ScreenRendering
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.gui.ScrollState
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.gui.ScrollbarLayout
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.gui.VerticalScrollbar
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.ExcludedParticipantIdentity
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.MinecraftRoomScreenKeys
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.client.gui.screens.ConfirmScreen
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
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
    private val name: (Uuid) -> Component,
    private val face: (GuiGraphics, Uuid, Int, Int, Int) -> Unit,
    private val allow: (Uuid) -> Unit,
    private val allowAll: () -> Unit,
    private val clock: MonotonicClock,
) : Screen(Component.translatable(MinecraftRoomScreenKeys.EXCLUDED_PARTICIPANTS)) {
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
    private val rowButtons = mutableListOf<Button>()

    /** 保留搜尋焦點，清單刷新不重建整個畫面。 */
    private lateinit var search: EditBox

    /** 等待中不能重複送出全部解除。 */
    private lateinit var clearAll: Button

    /** 內容寬度、底部按鈕列與捲軸位置沿用設定頁版面。 */
    private val frame: FormScreenLayout
        get() = FormScreenLayout(windowWidth = width, windowHeight = height, hasPageSwitch = false, hasRuleRow = false, hasTabs = false)

    /** 清單寬度，與設定頁內容同寬。 */
    private val listWidth: Int get() = frame.contentWidth

    /** 清單左界。 */
    private val left: Int get() = frame.contentLeft

    /** 清單上界，位於搜尋欄下方。 */
    private val top: Int get() = SEARCH_Y + FormScreenLayout.BUTTON_HEIGHT + SEARCH_GAP

    /** 清單下界，在底部按鈕列上方。 */
    private val bottom: Int get() = frame.fieldsBottom.coerceAtLeast(top)

    /** 只建立能完整放進可用高度的列。 */
    private val rows: Int get() = (bottom - top) / ROW_HEIGHT

    /** 捲動輸入與繪製共用同一份幾何。 */
    private val scrollbar: ScrollbarLayout get() = ScrollbarLayout(top, bottom, filtered.size, rows, scroll.index, minOf(12, (bottom - top).coerceAtLeast(0)))

    override fun init() {
        search = EditBox(font, left + 1, SEARCH_Y + 1, listWidth - 2, FormScreenLayout.BUTTON_HEIGHT - 2, Component.translatable(MinecraftRoomScreenKeys.EXCLUDED_SEARCH))
        search.setMaxLength(128)
        search.setValue(query)
        search.setHint(Component.translatable(MinecraftRoomScreenKeys.EXCLUDED_SEARCH))
        search.setResponder {
            query = it
            scroll.reset()
            refreshRows()
        }
        addRenderableWidget(search)
        val footer = frame.footer(buttonCount = FOOTER_SLOTS)
        clearAll = addRenderableWidget(
            RestartableMarqueeButtonWidget.builder(Component.translatable(MinecraftRoomScreenKeys.ALLOW_ALL_REJOINING)) { confirmClearAll() }
                .dimensions(footer.buttonX(0), frame.footerY, footer.buttonWidth(0), FormScreenLayout.BUTTON_HEIGHT).build(),
        )
        addRenderableWidget(
            RestartableMarqueeButtonWidget.builder(Component.translatable(MinecraftRoomScreenKeys.DONE)) { onClose() }
                .dimensions(footer.buttonX(FOOTER_SLOTS - 1), frame.footerY, footer.buttonWidth(FOOTER_SLOTS - 1), FormScreenLayout.BUTTON_HEIGHT).build(),
        )
        refreshRows()
    }

    /** 依權威名單、顯示名稱或 UUID 篩選，只重建可見列，保留搜尋焦點與游標。 */
    private fun refreshRows() {
        rowButtons.forEach(::removeWidget)
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
            val button = RestartableMarqueeButtonWidget.builder(Component.translatable(MinecraftRoomScreenKeys.ALLOW_REJOINING).append(": ").append(Component.literal(labels[id].orEmpty()))) {
                pending[id] = clock.now() + CONFIRMATION_TIMEOUT
                allow(id)
                refreshRows()
            }.dimensions(left + listWidth - FormScreenLayout.BUTTON_HEIGHT - ROW_BUTTON_INSET, top + index * ROW_HEIGHT + 8, FormScreenLayout.BUTTON_HEIGHT, FormScreenLayout.BUTTON_HEIGHT)
                .build().also {
                    it.icon = PixelIcon.CROSS
                    it.tooltip = Tooltip.create(Component.translatable(MinecraftRoomScreenKeys.ALLOW_REJOINING))
                    it.active = id !in pending
                }
            rowButtons += addRenderableWidget(button)
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
                minecraft?.setScreen(this@RoomExcludedParticipantsScreen)
            },
            Component.translatable(MinecraftRoomScreenKeys.ALLOW_ALL_REJOINING),
            Component.translatable(MinecraftRoomScreenKeys.ALLOW_ALL_CONFIRMATION),
        ) {
            override fun isPauseScreen(): Boolean = false
        }
        minecraft?.setScreen(confirmation)
    }

    override fun tick() {
        search.tick()
        val room = readRoom()
        if (room == null || !room.isHost) {
            onClose()
            return
        }
        val expired = pending.entries.removeAll { (id, deadline) -> id !in room.excludedPlayerIds || clock.now() >= deadline }
        if (room.excludedPlayerIds != entries || expired || entries.any { (it !in pending && labels[it] != name(it).string) || identities[it] != ExcludedParticipantIdentity.resolve(room, it) }) refreshRows()
    }

    override fun render(context: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        val room = readRoom()
        if (room == null || !room.isHost) {
            onClose()
            return
        }
        // 網路快照可能在兩次 tick 之間更新，繪製前先移除已不在名單上的卡片。
        if (room.excludedPlayerIds != entries) refreshRows()
        renderBackground(context)
        ScreenRendering.title(context, font, title, width)
        filtered.drop(scroll.index).take(rows).forEachIndexed { index, id ->
            val y = top + index * ROW_HEIGHT
            val hovered = mouseX in left until left + listWidth && mouseY in y until y + ROW_HEIGHT - 4
            context.fill(left, y, left + listWidth, y + ROW_HEIGHT - 4, if (hovered) 0xA0404040.toInt() else 0x90202020.toInt())
            face(context, id, left + 4, y + 6, 24)
            context.drawString(font, fitClientText(font, Component.literal(labels[id].orEmpty()), listWidth - 70), left + 36, y + 5, 0xFFFFFF)
            identities[id]?.let { identity ->
                val value = Component.translatable(MinecraftRoomScreenKeys.EXCLUDED_UUID, identity.id.toString())
                context.drawString(
                    font,
                    fitClientText(font, value, listWidth - 70),
                    left + 36,
                    y + 19,
                    0xA0A0A0,
                )
            }
        }
        if (filtered.isEmpty()) {
            val key = if (entries.isEmpty()) MinecraftRoomScreenKeys.EXCLUDED_EMPTY else MinecraftRoomScreenKeys.EXCLUDED_NO_RESULTS
            context.drawCenteredString(font, Component.translatable(key), width / 2, top + 16, 0xA0A0A0)
        }
        val bar = scrollbar
        if (bar.maximumScroll > 0 && rows > 0) {
            VerticalScrollbar.render(context, frame.scrollbarLeft, bar)
        }
        super.render(context, mouseX, mouseY, delta)
        filtered.drop(scroll.index).take(rows).forEachIndexed { index, id ->
            val y = top + index * ROW_HEIGHT
            if (mouseX in (left + 36) until (left + listWidth - 28) && mouseY in (y + 17) until (y + 31)) {
                identities[id]?.let {
                    context.renderTooltip(
                        font,
                        Component.translatable(MinecraftRoomScreenKeys.EXCLUDED_UUID, it.id.toString()),
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
        val scrollbarLeft = frame.scrollbarLeft
        if (button == 0 &&
            rows > 0 &&
            scrollbar.maximumScroll > 0 &&
            mouseX >= scrollbarLeft - FormScreenLayout.SCROLLBAR_HIT_PADDING &&
            mouseX < scrollbarLeft + VerticalScrollbar.WIDTH + FormScreenLayout.SCROLLBAR_HIT_PADDING &&
            mouseY >= top &&
            mouseY < bottom
        ) {
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

    override fun onClose() {
        minecraft?.setScreen(parent)
    }

    override fun isPauseScreen(): Boolean = false

    /** 清單的固定列高與既有回覆等待上限。 */
    private companion object {
        /** 頭像與角落按鈕不與下一列重疊。 */
        const val ROW_HEIGHT: Int = 40

        /** 搜尋欄的上界。 */
        const val SEARCH_Y: Int = FormScreenLayout.TITLE_BOTTOM

        /** 搜尋欄與清單之間的距離。 */
        const val SEARCH_GAP: Int = 6

        /** 解除按鈕與列右緣的距離。 */
        const val ROW_BUTTON_INSET: Int = 4

        /** 底部按鈕列的格數；完成固定在最右一格。 */
        const val FOOTER_SLOTS: Int = 4

        /** 只解除本地等待，不假裝伺服器已成功。 */
        val CONFIRMATION_TIMEOUT: Duration = 10.seconds
    }
}
