package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.guide

import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.platform.minecraft.decision.PlayerDecisionPromptDto
import com.doublemoon1119.mahjongcraft.platform.minecraft.guide.MinecraftGuideScreenKeys
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.config.MahjongHudPreviewRenderer
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.fitClientText
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.gui.CardBackground
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.gui.PixelIcon
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.gui.PixelScrollState
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.gui.RestartableMarqueeButtonWidget
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.gui.ScreenRendering
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.gui.TabWidget
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.gui.VerticalScrollbar
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.gui.measuringTimeNow
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.guide.GuideAction
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.guide.GuideBlock
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.guide.GuideContentLayout
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.guide.GuideElement
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.guide.GuideMeasurer
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.guide.GuideRecipeLayout
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.guide.GuideTextStyle
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.guide.GuideTooltipPlacement
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.guide.scene.GuideSceneHover
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.guide.scene.GuideSceneRegion
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.guide.scene.GuideSceneRenderer
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.guide.scene.GuideViewer
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.render.MahjongTileFaceRenderer
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.text.toNameText
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MinecraftTileAssetRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileDisplayNameRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.toAssetKey
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.locale.Language
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.util.FormattedCharSequence
import net.minecraft.world.item.ItemStack
import org.lwjgl.glfw.GLFW
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.toKotlinUuid

/**
 * 「如何開始」畫面：三個頁籤分別說明準備與開局、對局操作與麻將基礎，內容區可以捲動。
 *
 * 示意場景以遊戲自己的繪製器畫出，可轉動的場景以滑鼠左鍵左右拖曳轉動，認識牌桌的場景指著區塊時顯示說明。
 * 從其他畫面開啟時返回原畫面，從按鍵或指令開啟時關回遊戲。
 *
 * @property tileFaces 平面牌面的繪製。
 * @property hudPreview 範例操作面板的繪製。
 * @property tileNames 牌名的查詢來源。
 * @property tileAssets 牌面素材鍵的查詢來源。
 * @property openAction 以此畫面為返回目標開啟按鈕對應的畫面。
 * @property returnScreen 返回時回到的畫面；直接關回遊戲時為 null。
 * @property tab 目前的頁籤。
 * @property onTabChanged 切換頁籤後通知開啟入口，下次開啟時從同一頁開始。
 */
internal class GuideScreen(
    private val tileFaces: MahjongTileFaceRenderer,
    private val hudPreview: MahjongHudPreviewRenderer,
    private val tileNames: TileDisplayNameRegistry,
    private val tileAssets: MinecraftTileAssetRegistry,
    private val openAction: (action: GuideAction, returnScreen: Screen) -> Unit,
    internal val returnScreen: Screen?,
    private var tab: GuideTab,
    private val onTabChanged: (GuideTab) -> Unit,
) : Screen(Component.translatable(MinecraftGuideScreenKeys.TITLE)) {
    /** 目前頁籤的區塊；切換頁籤時重新建立。 */
    private var blocks: List<GuideBlock> = GuidePages.blocks(tab, viewer())

    /** 目前的畫面版面。 */
    private var layout = GuideScreenLayout.of(width = 1, height = 1)

    /** 目前頁籤排好的內容。 */
    private var content = GuideContentLayout(elements = emptyList(), height = 0)

    /** 內容捲動與捲軸拖曳。 */
    private val scroll = PixelScrollState()

    /** 畫示意場景；畫面移除時釋放。 */
    private val sceneRenderer = GuideSceneRenderer()

    /** 各場景被拖曳轉動的水平角度，以場景在 [GuideContentLayout.elements] 中的位置為鍵。 */
    private val yawOffsets = mutableMapOf<Int, Float>()

    /** 正在拖曳轉動的場景；沒有拖曳時為 null。 */
    private var draggingScene: Int? = null

    /** 麻將桌與麻將凳的合成配方，開啟畫面時查詢。 */
    private var recipes: List<GuideRecipe> = emptyList()

    /** 目前顯示的配方位置。 */
    private var recipeIndex = 0

    /** 內容區裡的按鈕與它們在內容座標中的位置。 */
    private val contentButtons = mutableListOf<ContentButton>()

    /** 上次建立畫面時的語言，用於偵測語言切換。 */
    private var language: Language = Language.getInstance()

    /** 依目前尺寸、語言與世界配方重新建立控制項並排版，保留頁籤與捲動位置。 */
    override fun init() {
        language = Language.getInstance()
        layout = GuideScreenLayout.of(width = width, height = height)
        minecraft?.let { recipes = GuideRecipes.find(it) }
        recipeIndex = recipeIndex.coerceIn(0, (recipes.size - 1).coerceAtLeast(0))
        content = GuideContentLayout.measure(
            blocks = blocks,
            width = layout.contentWidth,
            measurer = measurer(),
            hasRecipes = recipes.isNotEmpty(),
            viewportHeight = layout.contentHeight,
        )
        scroll.relayout(layout = layout, contentHeight = content.height)
        addTabs()
        val footer = layout.footer
        addRenderableWidget(
            RestartableMarqueeButtonWidget.builder(
                message = Component.translatable(if (returnScreen == null) MinecraftGuideScreenKeys.CLOSE else MinecraftGuideScreenKeys.BACK),
            ) { onClose() }
                .dimensions(
                    x = footer.buttonX(GuideScreenLayout.CLOSE_SLOT),
                    y = layout.footerY,
                    width = footer.buttonWidth(GuideScreenLayout.CLOSE_SLOT),
                    height = GuideContentLayout.BUTTON_HEIGHT,
                )
                .build(),
        )
        addContentButtons()
    }

    /** 三個頁籤，選中的頁籤比其他頁籤高。 */
    private fun addTabs() {
        val strip = layout.tabs
        GuideTab.entries.forEachIndexed { index, entry ->
            val selected = entry == tab
            addRenderableWidget(
                TabWidget(
                    x = strip.tabX(index),
                    y = strip.tabY(selected),
                    width = strip.tabWidth,
                    height = strip.tabHeight(selected),
                    message = Component.translatable(entry.titleKey),
                    selected = selected,
                    clipLeft = strip.left,
                    clipRight = strip.right,
                    onFocus = {},
                    onSelect = { selectTab(entry) },
                ),
            )
        }
    }

    /** 內容區裡的按鈕：開啟其他畫面的按鈕，以及合成配方的上一個與下一個。 */
    private fun addContentButtons() {
        contentButtons.clear()
        content.elements.forEach { element ->
            when (element) {
                is GuideElement.ActionButton -> addContentButton(
                    label = element.label,
                    x = element.x,
                    y = element.y,
                    width = element.width,
                    height = element.height,
                ) { minecraft?.let { openAction(element.action, this) } }
                is GuideElement.Recipe -> {
                    val recipe = element.layout
                    val size = GuideRecipeLayout.CONTROL_SIZE
                    listOf(
                        Triple(recipe.previousX, PixelIcon.PREVIOUS_LEFT, MinecraftGuideScreenKeys.SETUP_RECIPE_PREVIOUS) to -1,
                        Triple(recipe.nextX, PixelIcon.OPEN_RIGHT, MinecraftGuideScreenKeys.SETUP_RECIPE_NEXT) to 1,
                    ).forEach { (control, step) ->
                        val (x, icon, tooltipKey) = control
                        val button = addContentButton(label = Component.translatable(tooltipKey), x = x, y = recipe.controlsY, width = size, height = size) {
                            recipeIndex = Math.floorMod(recipeIndex + step, recipes.size.coerceAtLeast(1))
                        }
                        button.icon = icon
                        button.showsMessage = false
                        button.tooltip = Tooltip.create(Component.translatable(tooltipKey))
                        button.active = recipes.size > 1
                    }
                }
                else -> Unit
            }
        }
    }

    /**
     * 加入隨內容捲動的按鈕；按鈕只在內容區內可點，由畫面在內容裁切內繪製。
     *
     * @param label 按鈕文字。
     * @param x 內容座標中的左界。
     * @param y 內容座標中的上界。
     * @param width 寬度。
     * @param height 高度。
     * @param action 按下後的行為。
     * @return 已加入的按鈕。
     */
    private fun addContentButton(
        label: Component,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        action: () -> Unit,
    ): RestartableMarqueeButtonWidget {
        val button = RestartableMarqueeButtonWidget.builder(label) { action() }
            .dimensions(layout.contentLeft + x, layout.contentTop + y, width, height)
            .build()
        button.clickClip = { mouseX, mouseY -> layout.inContent(mouseX, mouseY) }
        addWidget(button)
        contentButtons += ContentButton(widget = button, x = x, y = y)
        return button
    }

    /**
     * 切換頁籤：回到頂端並重新排版。
     *
     * @param next 要切換到的頁籤。
     */
    private fun selectTab(next: GuideTab) {
        if (next == tab) return
        tab = next
        onTabChanged(next)
        blocks = GuidePages.blocks(next, viewer())
        yawOffsets.clear()
        draggingScene = null
        scroll.reset()
        rebuildWidgets()
    }

    /**
     * 看著畫面的玩家：目前登入的帳號名稱與 UUID。
     *
     * @return 看著畫面的玩家。
     */
    private fun viewer(): GuideViewer {
        val profile = Minecraft.getInstance().user.gameProfile
        return GuideViewer(id = profile.id.toKotlinUuid(), name = profile.name)
    }

    /** 以目前的文字繪製器與操作面板量測內容。 */
    private fun measurer(): GuideMeasurer = object : GuideMeasurer {
        override fun width(text: Component): Int = font.width(text)

        override fun lineCount(text: Component, width: Int): Int = font.split(text, width.coerceAtLeast(1)).size.coerceAtLeast(1)

        override fun tileName(tile: Tile): Component = tile.toNameText(tileNames)

        override fun decisionPanelHeight(prompt: PlayerDecisionPromptDto, width: Int): Int = hudPreview.decisionPanelHeight(prompt, width)
    }

    /**
     * 繪製標題、頁籤、內容與捲軸；內容的提示在內容裁切結束後才繪製。
     *
     * @param context 繪製內容。
     * @param mouseX 游標水平位置。
     * @param mouseY 游標垂直位置。
     * @param delta 畫面更新比例。
     */
    override fun render(context: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        if (language !== Language.getInstance()) rebuildWidgets()
        renderBackground(context)
        ScreenRendering.title(context, font, fitClientText(font, title, layout.contentWidth), width)
        val tooltip = renderContent(context = context, mouseX = mouseX, mouseY = mouseY, delta = delta)
        val bar = layout.scrollbar(contentHeight = content.height, scroll = scroll.offset)
        if (bar.maximumScroll > 0) VerticalScrollbar.renderWithin(context, layout.scrollbarLeft, layout.scrollbarLeft + VerticalScrollbar.WIDTH, bar)
        ScreenRendering.tabLine(context, layout.tabs)
        super.render(context, mouseX, mouseY, delta)
        if (scroll.isDragging || draggingScene != null) return
        when (tooltip) {
            is ContentTooltip.Scene -> context.renderTooltip(
                font,
                sceneTooltipLines(tooltip.hover.region),
                GuideTooltipPlacement(anchorTop = tooltip.hover.top, anchorBottom = tooltip.hover.bottom),
                mouseX,
                mouseY,
            )
            is ContentTooltip.Item -> context.renderComponentTooltip(font, tooltip.lines, mouseX, mouseY)
            null -> Unit
        }
    }

    /**
     * 在內容區裁切內繪製與可見範圍相交的元素與內容按鈕。
     *
     * @param context 繪製內容。
     * @param mouseX 游標水平位置。
     * @param mouseY 游標垂直位置。
     * @param delta 畫面更新比例。
     * @return 游標指向的場景區塊或配方物品的提示；沒有時為 null。
     */
    private fun renderContent(
        context: GuiGraphics,
        mouseX: Int,
        mouseY: Int,
        delta: Float,
    ): ContentTooltip? {
        if (layout.contentHeight <= 0) return null
        val inside = layout.inContent(mouseX.toDouble(), mouseY.toDouble()) && !scroll.isDragging
        val pointerX = if (inside) mouseX else OUTSIDE
        val pointerY = if (inside) mouseY else OUTSIDE
        val originX = layout.contentLeft
        val originY = layout.contentTop - scroll.offset.toInt()
        var tooltip: ContentTooltip? = null
        context.enableScissor(layout.contentLeft, layout.contentTop, layout.contentLeft + layout.contentWidth, layout.contentBottom)
        content.elements.forEachIndexed { index, element ->
            val top = originY + element.y
            if (top >= layout.contentBottom || top + element.height <= layout.contentTop) return@forEachIndexed
            when (element) {
                is GuideElement.Label -> renderLabel(context, element, originX, originY)
                is GuideElement.TileFace -> tileFaces.renderGui(
                    context = context,
                    assetKey = element.tile.toAssetKey(tileAssets),
                    x = originX + element.x,
                    y = top,
                    width = GuideContentLayout.TILE_WIDTH,
                    height = GuideContentLayout.TILE_HEIGHT,
                )
                is GuideElement.CardBackground -> context.fill(
                    originX + element.x,
                    top,
                    originX + element.x + element.width,
                    top + element.height,
                    CardBackground.COLOR,
                )
                is GuideElement.Divider -> context.fill(originX + element.x, top, originX + element.x + element.width, top + 1, DIVIDER_COLOR)
                is GuideElement.SceneView -> {
                    val hover = sceneRenderer.render(
                        context = context,
                        scene = element.scene,
                        left = originX + element.x,
                        top = top,
                        width = element.width,
                        height = element.height,
                        mouseX = if (draggingScene == null) pointerX else OUTSIDE,
                        mouseY = if (draggingScene == null) pointerY else OUTSIDE,
                        yawOffset = yawOffsets[index] ?: 0f,
                    )
                    if (hover != null) tooltip = ContentTooltip.Scene(hover)
                }
                is GuideElement.Recipe -> renderRecipe(context, element.layout, originX, originY, pointerX, pointerY)?.let { tooltip = ContentTooltip.Item(it) }
                is GuideElement.DecisionPanel -> hudPreview.renderDecisionPanelAt(context, element.prompt, originX + element.x, top, element.width)
                is GuideElement.ActionButton -> Unit
            }
        }
        contentButtons.forEach { button ->
            button.widget.x = originX + button.x
            button.widget.y = originY + button.y
            button.widget.visible = button.widget.y < layout.contentBottom && button.widget.y + button.widget.height > layout.contentTop
            button.widget.render(context, pointerX, pointerY, delta)
        }
        context.disableScissor()
        return tooltip
    }

    /**
     * 繪製一段換行文字。
     *
     * @param context 繪製內容。
     * @param label 文字元素。
     * @param originX 內容原點的水平位置。
     * @param originY 內容原點套用捲動後的垂直位置。
     */
    private fun renderLabel(
        context: GuiGraphics,
        label: GuideElement.Label,
        originX: Int,
        originY: Int,
    ) {
        val color = when (label.style) {
            GuideTextStyle.HEADING -> HEADING_COLOR
            GuideTextStyle.BODY -> ScreenRendering.TEXT_COLOR
            GuideTextStyle.HINT -> ScreenRendering.MUTED_TEXT_COLOR
            GuideTextStyle.LABEL -> LABEL_COLOR
            GuideTextStyle.OPERATOR -> OPERATOR_COLOR
            GuideTextStyle.ACCENT -> HEADING_COLOR
        }
        val lines = if (label.lineCount == 1 && label.width < font.width(label.text)) {
            listOf(label.text.visualOrderText)
        } else {
            font.split(label.text, label.width.coerceAtLeast(1))
        }
        lines.forEachIndexed { line, text ->
            val x = if (label.centered) originX + label.x + (label.width - font.width(text)) / 2 else originX + label.x
            context.drawString(font, text, x, originY + label.y + line * GuideContentLayout.LINE_HEIGHT, color)
        }
    }

    /**
     * 繪製合成配方輪播：原版工作台介面圖、標題、材料、成品、成品名稱與頁碼；同一格有多種材料時每秒輪流顯示。
     *
     * @param context 繪製內容。
     * @param recipe 輪播的位置。
     * @param originX 內容原點的水平位置。
     * @param originY 內容原點套用捲動後的垂直位置。
     * @param mouseX 游標水平位置；不在內容區時在範圍外。
     * @param mouseY 游標垂直位置；不在內容區時在範圍外。
     * @return 游標指向的物品提示；沒有時為 null。
     */
    private fun renderRecipe(
        context: GuiGraphics,
        recipe: GuideRecipeLayout,
        originX: Int,
        originY: Int,
        mouseX: Int,
        mouseY: Int,
    ): List<Component>? {
        val current = recipes.getOrNull(recipeIndex) ?: return null
        val panelX = originX + recipe.panelLeft
        val panelY = originY + recipe.top
        context.blit(CRAFTING_TABLE_TEXTURE, panelX, panelY, 0, 0, GuideRecipeLayout.PANEL_WIDTH, GuideRecipeLayout.PANEL_TOP_HEIGHT)
        context.blit(
            CRAFTING_TABLE_TEXTURE,
            panelX,
            panelY + GuideRecipeLayout.PANEL_TOP_HEIGHT,
            0,
            GuideRecipeLayout.PANEL_BORDER_V,
            GuideRecipeLayout.PANEL_WIDTH,
            GuideRecipeLayout.PANEL_BORDER_HEIGHT,
        )
        context.drawString(
            font,
            Component.translatable(MinecraftGuideScreenKeys.SETUP_RECIPE),
            originX + recipe.titleX,
            originY + recipe.titleY,
            CONTAINER_TITLE_COLOR,
            false,
        )
        val cycle = (measuringTimeNow() / INGREDIENT_CYCLE).toInt()
        var hovered: ItemStack? = null
        current.slots.forEachIndexed { index, stacks ->
            val (x, y) = recipe.slot(index)
            val stack = stacks.takeIf { it.isNotEmpty() }?.let { it[Math.floorMod(cycle, it.size)] }
            if (renderItem(context, stack, originX + x, originY + y, mouseX, mouseY)) hovered = stack
        }
        if (renderItem(context, current.output, originX + recipe.outputX, originY + recipe.outputY, mouseX, mouseY)) hovered = current.output
        val centerX = originX + recipe.centerX
        val name = fitClientText(font, current.output.hoverName, recipe.width)
        context.drawCenteredString(font, name, centerX, originY + recipe.nameY, ScreenRendering.TEXT_COLOR)
        context.drawCenteredString(
            font,
            "${recipeIndex + 1}/${recipes.size}",
            centerX,
            originY + recipe.controlsY + (GuideRecipeLayout.CONTROL_SIZE - GuideContentLayout.LINE_HEIGHT) / 2 + 1,
            ScreenRendering.MUTED_TEXT_COLOR,
        )
        val client = minecraft ?: return null
        return hovered?.let { getTooltipFromItem(client, it) }
    }

    /**
     * 在介面圖的格子裡繪製物品；游標指著時與原版一樣蓋上半透明的白色。
     *
     * @param context 繪製內容。
     * @param stack 物品；空格為 null。
     * @param x 物品左界。
     * @param y 物品上界。
     * @param mouseX 游標水平位置。
     * @param mouseY 游標垂直位置。
     * @return 游標指著有物品的格子時為 true。
     */
    private fun renderItem(
        context: GuiGraphics,
        stack: ItemStack?,
        x: Int,
        y: Int,
        mouseX: Int,
        mouseY: Int,
    ): Boolean {
        val size = GuideRecipeLayout.ITEM_SIZE
        stack?.let {
            context.renderItem(it, x, y)
            context.renderItemDecorations(font, it, x, y)
        }
        val hovered = mouseX in x until x + size && mouseY in y until y + size
        if (hovered) AbstractContainerScreen.renderSlotHighlight(context, x, y, 0)
        return hovered && stack != null
    }

    /**
     * 場景區塊的提示：第一行是名稱，其餘是灰色的說明；每行最寬為畫面寬度的一部分，超過時換行。
     *
     * @param region 游標指著的區塊。
     * @return 提示的各行。
     */
    private fun sceneTooltipLines(region: GuideSceneRegion): List<FormattedCharSequence> {
        val maximumWidth = (width * GuideTooltipPlacement.MAXIMUM_WIDTH_RATIO).toInt().coerceAtLeast(MINIMUM_TOOLTIP_WIDTH)
        val lines = listOf(Component.translatable(region.nameKey)) +
            Component.translatable(region.descriptionKey).string.split('\n').map { Component.literal(it).withStyle(ChatFormatting.GRAY) }
        return lines.flatMap { line -> font.split(line, maximumWidth) }
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, amount: Double): Boolean {
        if (layout.inContent(mouseX, mouseY) || layout.onScrollbar(mouseX, mouseY)) {
            scroll.scrollBy(amount = amount, layout = layout, contentHeight = content.height)
            return true
        }
        return super.mouseScrolled(mouseX, mouseY, amount)
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT &&
            layout.onScrollbar(mouseX, mouseY) &&
            scroll.press(mouseY = mouseY, layout = layout, contentHeight = content.height)
        ) {
            return true
        }
        if (super.mouseClicked(mouseX, mouseY, button)) return true
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && layout.inContent(mouseX, mouseY)) {
            val originY = layout.contentTop - scroll.offset
            val index = content.elements.indices.firstOrNull { index ->
                val element = content.elements[index]
                element is GuideElement.SceneView &&
                    element.scene.rotatable &&
                    mouseX >= layout.contentLeft + element.x &&
                    mouseX < layout.contentLeft + element.x + element.width &&
                    mouseY >= originY + element.y &&
                    mouseY < originY + element.y + element.height
            }
            if (index != null) {
                draggingScene = index
                return true
            }
        }
        return false
    }

    override fun mouseDragged(mouseX: Double, mouseY: Double, button: Int, deltaX: Double, deltaY: Double): Boolean {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)
        if (scroll.drag(mouseY = mouseY, layout = layout, contentHeight = content.height)) return true
        val index = draggingScene ?: return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)
        yawOffsets[index] = ((yawOffsets[index] ?: 0f) + deltaX.toFloat() * DEGREES_PER_PIXEL) % FULL_TURN_DEGREES
        return true
    }

    override fun mouseReleased(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            val wasDraggingScene = draggingScene != null
            draggingScene = null
            if (scroll.release() || wasDraggingScene) return true
        }
        return super.mouseReleased(mouseX, mouseY, button)
    }

    /** 返回原畫面，或從按鍵與指令開啟時關回遊戲。 */
    override fun onClose() {
        minecraft?.setScreen(returnScreen)
    }

    /** 釋放場景繪製使用的資源。 */
    override fun removed() {
        sceneRenderer.close()
        super.removed()
    }

    /** 「如何開始」不暫停單人遊戲。 */
    override fun isPauseScreen(): Boolean = false

    /** 內容區游標指向的東西要顯示的提示。 */
    private sealed interface ContentTooltip {
        /**
         * 場景中指著的東西，提示框避開它的範圍。
         *
         * @property hover 指著的東西與它在畫面上的範圍。
         */
        data class Scene(val hover: GuideSceneHover) : ContentTooltip

        /**
         * 配方中指著的物品，使用原版物品提示。
         *
         * @property lines 物品提示的各行。
         */
        data class Item(val lines: List<Component>) : ContentTooltip
    }

    /**
     * 隨內容捲動的按鈕。
     *
     * @property widget 按鈕。
     * @property x 內容座標中的左界。
     * @property y 內容座標中的上界。
     */
    private data class ContentButton(val widget: RestartableMarqueeButtonWidget, val x: Int, val y: Int)

    /** 畫面配色與尺寸。 */
    private companion object {
        /** 游標不在內容區時傳給繪製的位置，不會命中任何東西。 */
        const val OUTSIDE = Int.MIN_VALUE / 2

        /** 原版工作台介面圖。 */
        val CRAFTING_TABLE_TEXTURE: ResourceLocation = ResourceLocation("textures/gui/container/crafting_table.png")

        /** 原版容器標題的文字顏色。 */
        const val CONTAINER_TITLE_COLOR = 0x404040

        /** 場景提示框文字寬度的下限。 */
        const val MINIMUM_TOOLTIP_WIDTH = 120

        /** 段落標題與強調文字的顏色。 */
        const val HEADING_COLOR = 0xFFD54F

        /** 牌組標籤與牌名的顏色。 */
        const val LABEL_COLOR = 0xD6D6D6

        /** 牌組之間符號的顏色。 */
        const val OPERATOR_COLOR = 0x9A9A9A

        /** 分隔線的顏色。 */
        const val DIVIDER_COLOR = 0x40FFFFFF

        /** 同一格的材料每隔多久換下一種。 */
        val INGREDIENT_CYCLE: Duration = 1.seconds

        /** 拖曳一個 GUI 像素轉動的角度。 */
        const val DEGREES_PER_PIXEL = 1f

        /** 一整圈的角度。 */
        const val FULL_TURN_DEGREES = 360f
    }
}
