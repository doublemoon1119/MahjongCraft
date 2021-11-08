package doublemoon.mahjongcraft.client.gui

import doublemoon.mahjongcraft.MOD_ID
import doublemoon.mahjongcraft.blockentity.MahjongTableBlockEntity
import doublemoon.mahjongcraft.client.gui.widget.*
import doublemoon.mahjongcraft.game.mahjong.riichi.MahjongRule
import doublemoon.mahjongcraft.game.mahjong.riichi.MahjongTableBehavior
import doublemoon.mahjongcraft.network.MahjongTablePacketHandler.sendMahjongTablePacket
import io.github.cottonmc.cotton.gui.client.CottonClientScreen
import io.github.cottonmc.cotton.gui.client.LightweightGuiDescription
import io.github.cottonmc.cotton.gui.widget.WButton
import io.github.cottonmc.cotton.gui.widget.WPlainPanel
import io.github.cottonmc.cotton.gui.widget.data.HorizontalAlignment
import io.github.cottonmc.cotton.gui.widget.data.VerticalAlignment
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.minecraft.client.MinecraftClient
import net.minecraft.client.network.ClientPlayerEntity
import net.minecraft.text.LiteralText
import net.minecraft.text.MutableText
import net.minecraft.text.Text
import net.minecraft.text.TranslatableText
import net.minecraft.util.Formatting


@Environment(EnvType.CLIENT)
class MahjongRuleEditorScreen(description: MahjongRuleEditorGui) : CottonClientScreen(description) {
    constructor(mahjongTable: MahjongTableBlockEntity) : this(MahjongRuleEditorGui(mahjongTable))

    override fun isPauseScreen(): Boolean = false
    override fun tick() {
        super.tick()
        (description as MahjongRuleEditorGui).tick()
    }
}

@Environment(EnvType.CLIENT)
class MahjongRuleEditorGui(
    val mahjongTable: MahjongTableBlockEntity
) : LightweightGuiDescription() {

    private val client = MinecraftClient.getInstance()
    private val player: ClientPlayerEntity = client.player!!

    //編輯中的設定
    private val editingRule: MahjongRule = mahjongTable.rule.copy()
    private val lengthItem = RuleSelectItem(
        name = TranslatableText("$MOD_ID.game.length"),
        value = { editingRule.length },
        label = { it.toText() },
        tooltip = { length -> getTooltip(length) }
    ) {
        val values = MahjongRule.GameLength.values()
        val nextValue = values[(editingRule.length.ordinal + 1) % values.size]
        editingRule.length = nextValue
    }
    private val thinkingTimeItem = RuleSelectItem(
        name = TranslatableText("$MOD_ID.game.thinking_time"),
        value = { editingRule.thinkingTime },
        label = { it.toText() },
        tooltip = { thinkingTime -> getTooltip(thinkingTime) }
    ) {
        val values = MahjongRule.ThinkingTime.values()
        val nextValue = values[(editingRule.thinkingTime.ordinal + 1) % values.size]
        editingRule.thinkingTime = nextValue
    }
    private val startingPointsItem = RuleIntegerTextFieldItem(
        name = TranslatableText("$MOD_ID.game.starting_points"),
        description = arrayOf(
            TranslatableText(
                "mahjongcraft.game.starting_points.description",
                MahjongRule.MIN_POINTS,
                MahjongRule.MAX_POINTS
            )
        ),
        defaultValue = editingRule.startingPoints
    )
    private val minPointsToWinItem = RuleIntegerTextFieldItem(
        name = TranslatableText("$MOD_ID.game.min_points_to_win"),
        description = arrayOf(TranslatableText("$MOD_ID.game.min_points_to_win.description")),
        defaultValue = editingRule.minPointsToWin
    )
    private val minimumHanItem = RuleSelectItem(
        name = TranslatableText("$MOD_ID.game.minimum_han"),
        value = { editingRule.minimumHan },
        label = { it.toText() },
        tooltip = { minimumHan -> getTooltip(minimumHan) }
    ) {
        val values = MahjongRule.MinimumHan.values()
        val nextValue = values[(editingRule.minimumHan.ordinal + 1) % values.size]
        editingRule.minimumHan = nextValue
    }
    private val spectateItem = RuleToggleItem(
        name = TranslatableText("$MOD_ID.game.spectate"),
        enabled = editingRule.spectate,
        onClick = { editingRule.spectate = it }
    )
    private val redFiveItem = RuleSelectItem(
        name = TranslatableText("$MOD_ID.game.red_five"),
        value = { editingRule.redFive },
        label = { it.toText() },
        tooltip = { redFive -> getTooltip(redFive) }
    ) {
        val values = MahjongRule.RedFive.values()
        val nextValue = values[(editingRule.redFive.ordinal + 1) % values.size]
        editingRule.redFive = nextValue
    }

    //這兩個是檢查用的
    private val startingPointsItemValueInvalid: Boolean
        get() {
            val intValue = startingPointsItem.value
            return intValue == null || intValue > MahjongRule.MAX_POINTS || intValue < MahjongRule.MIN_POINTS
        }
    private val minPointsToWinItemValueInvalid: Boolean
        get() {
            val intValue = minPointsToWinItem.value
            return if (intValue == null || intValue > MahjongRule.MAX_POINTS || intValue < MahjongRule.MIN_POINTS) true
            else {
                val startingPointValue = startingPointsItem.value
                val biggerOrEqualStartingPoint =
                    if (startingPointValue == null) true else intValue >= startingPointValue
                !biggerOrEqualStartingPoint
            }
        }

    private val items = listOf(
        lengthItem,
        thinkingTimeItem,
        startingPointsItem,
        minPointsToWinItem,
        minimumHanItem,
        spectateItem,
        redFiveItem
    )

    private lateinit var buttonBack: WButton
    private lateinit var buttonConfirm: WTooltipButton
    private lateinit var buttonApply: WTooltipButton

    init {
        rootScrollPanel(width = ROOT_WIDTH, height = ROOT_HEIGHT) {
            plainPanel(x = 24, y = 0) {
                items.forEachIndexed { index, item ->
                    val previousItem = if (index > 0) items[index - 1] else null
                    val previousItemY = previousItem?.y ?: 0
                    val previousItemHeight = previousItem?.height ?: 0
                    val y = previousItemY + previousItemHeight + ITEM_PADDING
                    this.add(item, 0, y)
                }
            }
            buttonBack = button(
                x = ROOT_WIDTH - ITEM_PADDING - BUTTON_WIDTH,
                y = ROOT_HEIGHT - ITEM_PADDING - BUTTON_HEIGHT,
                width = BUTTON_WIDTH,
                label = TranslatableText("$MOD_ID.gui.button.back"),
                onClick = { back() }
            )
            buttonConfirm = tooltipButton(
                x = buttonBack.x,
                y = buttonBack.y - buttonBack.height - BUTTON_PADDING,
                width = BUTTON_WIDTH,
                label = TranslatableText("$MOD_ID.gui.button.confirm"),
                tooltip = arrayOf(),
                onClick = { confirm() }
            ) {
                isEnabled = false
            }
            buttonApply = tooltipButton(
                x = buttonConfirm.x,
                y = buttonConfirm.y - buttonConfirm.height - BUTTON_PADDING,
                width = BUTTON_WIDTH,
                label = TranslatableText("$MOD_ID.gui.button.apply"),
                tooltip = arrayOf(),
                onClick = { apply() }
            ) {
                isEnabled = false
            }
        }
    }

    fun tick() {
        startingPointsItem.apply {
            hint = if (startingPointsItemValueInvalid) {
                TranslatableText("$MOD_ID.gui.invalid_value").formatted(Formatting.RED)
            } else {
                LiteralText("")
            }
        }
        minPointsToWinItem.apply {
            hint = if (minPointsToWinItemValueInvalid) {
                TranslatableText("$MOD_ID.gui.invalid_value").formatted(Formatting.RED)
            } else {
                LiteralText("")
            }
        }
        buttonConfirm.isEnabled = buttonConfirmEnabled
        buttonApply.isEnabled = buttonConfirm.isEnabled
    }

    private val buttonConfirmEnabled: Boolean
        get() {
            if (startingPointsItemValueInvalid || minPointsToWinItemValueInvalid) return false
            val origin = mahjongTable.rule
            val lengthDiff = origin.length != editingRule.length
            val thinkingTimeDiff = origin.thinkingTime != editingRule.thinkingTime
            val minimumHanDiff = origin.minimumHan != editingRule.minimumHan
            val spectateDiff = origin.spectate != editingRule.spectate
            val redFiveDiff = origin.redFive != editingRule.redFive
            val startingPointsDiff = origin.startingPoints != startingPointsItem.value!!
            val minPointsToWinDiff = origin.minPointsToWin != minPointsToWinItem.value!!
            return lengthDiff || thinkingTimeDiff || minimumHanDiff || spectateDiff || redFiveDiff || startingPointsDiff || minPointsToWinDiff
        }

    private fun apply() {
        editingRule.startingPoints = startingPointsItem.value!!
        editingRule.minPointsToWin = minPointsToWinItem.value!!
        player.sendMahjongTablePacket(
            behavior = MahjongTableBehavior.CHANGE_RULE,
            pos = mahjongTable.pos,
            extraData = editingRule.toJsonString()
        )
    }

    private fun confirm() {
        apply()
        back()
    }

    private fun back() {
        client.setScreen(MahjongTableWaitingScreen(mahjongTable = mahjongTable))
    }

    companion object {
        //背景圖片的大小
        private const val ROOT_WIDTH = 400
        private const val ROOT_HEIGHT = 200

        private const val BUTTON_WIDTH = 80
        private const val BUTTON_HEIGHT = 20

        private const val ITEM_PADDING = 8
        private const val BUTTON_PADDING = 5


        private inline fun <reified T : Enum<T>> getTooltip(nowValue: T): Array<Text> =
            enumValues<T>().map {
                if (it is MahjongRule.TextFormatting) {
                    it.toText().also { text ->
                        if (text is MutableText) {
                            val color = if (it == nowValue) Formatting.GREEN else Formatting.RED
                            text.formatted(color)
                        }
                    }
                } else {
                    LiteralText(it.name)
                }
            }.toTypedArray()
    }


    abstract class SettingItem(
        name: Text
    ) : WPlainPanel() {

        init {
            this.setSize(WIDTH, HEIGHT)
        }

        val text = text(
            x = 0,
            y = 0,
            width = TEXT_WIDTH,
            horizontalAlignment = HorizontalAlignment.RIGHT,
            verticalAlignment = VerticalAlignment.CENTER,
            text = name
        )
        val space = plainPanel(
            x = text.x + text.width,
            y = text.y,
            width = SPACE_WIDTH,
            height = text.height
        )

        companion object {
            private const val TEXT_WIDTH = 120
            private const val SPACE_WIDTH = 16
            const val HEIGHT = 36
            const val WIDTH = 296
        }
    }

    class RuleSelectItem<T : Enum<T>>(
        val name: Text,
        val value: () -> T,
        private val label: (T) -> Text,
        private val tooltip: (T) -> Array<Text>,
        private val onSelect: RuleSelectItem<T>.() -> Unit
    ) : SettingItem(name) {
        private val nowLabel: Text
            get() = label.invoke(value.invoke())
        private val nowTooltip: Array<Text>
            get() = tooltip.invoke(value.invoke())

        private val selectButton = tooltipButton(
            x = space.x + space.width,
            y = text.y + (text.height - BUTTON_HEIGHT) / 2,
            width = BUTTON_WIDTH,
            label = nowLabel,
            tooltip = nowTooltip,
            onClick = {
                onSelect.invoke(this@RuleSelectItem)
                refreshButtonLabel()
                refreshButtonTooltip()
            }
        )

        private fun refreshButtonLabel() {
            selectButton.label = nowLabel
        }

        private fun refreshButtonTooltip() {
            selectButton.tooltip = nowTooltip
        }
    }

    class RuleToggleItem(
        val name: Text,
        var enabled: Boolean,
        private val onClick: (Boolean) -> Unit
    ) : SettingItem(name) {

        init {
            button(
                x = space.x + space.width,
                y = text.y + (text.height - BUTTON_HEIGHT) / 2,
                width = BUTTON_WIDTH,
                label = buttonText,
                onClick = {
                    enabled = !enabled
                    label = buttonText
                    this@RuleToggleItem.onClick.invoke(enabled)
                }
            )
        }

        private val buttonText: Text
            get() = if (enabled) TranslatableText("$MOD_ID.game.enabled") else TranslatableText("$MOD_ID.game.disabled")
    }

    class RuleIntegerTextFieldItem(
        val name: Text,
        description: Array<Text> = arrayOf(),
        defaultValue: Int,
    ) : SettingItem(name) {

        var hint: Text
            get() = hintLabel.text
            set(value) {
                hintLabel.text = value
            }

        /**
         * null 表示輸入的 [textField] 之中的值不是 [Int]
         * */
        var value: Int?
            get() = textField.text.toIntOrNull()
            set(value) {
                textField.text = (value?.toString() ?: "")
            }

        private val textField = tooltipTextField(
            x = space.x + space.width,
            y = text.y + (text.height - BUTTON_HEIGHT) / 2,
            width = BUTTON_WIDTH,
            text = "$defaultValue",
            tooltip = description,
            suggestion = LiteralText("$defaultValue"),
        )


        private val hintLabel = label(
            x = textField.x + textField.width + space.width,
            y = textField.y,
            height = textField.height,
            verticalAlignment = VerticalAlignment.CENTER,
            text = LiteralText("")
        )
    }
}
