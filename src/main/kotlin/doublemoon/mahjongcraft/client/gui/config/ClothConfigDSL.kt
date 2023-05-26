package doublemoon.mahjongcraft.client.gui.config

import me.shedaniel.clothconfig2.api.ConfigBuilder
import me.shedaniel.clothconfig2.api.ConfigCategory
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder
import me.shedaniel.clothconfig2.gui.entries.*
import me.shedaniel.clothconfig2.impl.builders.SubCategoryBuilder
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.util.InputUtil
import net.minecraft.text.Text
import java.util.*

/**
 * 這裡的寫法看起來很 heck, 等我想到更好的寫法就回來修正
 * */
fun configBuilder(
    title: Text,
    parent: Screen?,
    transparentBackground: Boolean = true,
    doesConfirmSave: Boolean = true,
    savingRunnable: () -> Unit,
    block: Pair<ConfigBuilder, ConfigEntryBuilder>.() -> Unit
): ConfigBuilder {
    val builder = ConfigBuilder.create()
        .setTitle(title)
        .setParentScreen(parent)
        .setTransparentBackground(transparentBackground)
        .setDoesConfirmSave(doesConfirmSave)
        .setSavingRunnable(savingRunnable)
    val entryBuilder = builder.entryBuilder()
    (builder to entryBuilder).block()
    return builder
}

fun Pair<ConfigBuilder, ConfigEntryBuilder>.category(
    text: Text,
    block: Pair<ConfigCategory, ConfigEntryBuilder>.() -> Unit
): ConfigCategory {
    val (builder, entryBuilder) = this
    val category = builder.getOrCreateCategory(text)
    (category to entryBuilder).block()
    return category
}

fun Pair<ConfigCategory, ConfigEntryBuilder>.subCategory(
    text: Text,
    expanded: Boolean = true,
    tooltipSupplier: () -> Optional<Array<Text>> = { Optional.empty<Array<Text>>() },
    block: Pair<SubCategoryBuilder, ConfigEntryBuilder>.() -> Unit
): SubCategoryListEntry {
    val (category, entryBuilder) = this
    val subCategory = entryBuilder.startSubCategory(text)
        .setExpanded(expanded)
        .setTooltipSupplier(tooltipSupplier)
    (subCategory to entryBuilder).block()
    val subCategoryListEntry = subCategory.build()
    category.addEntry(subCategoryListEntry)
    return subCategoryListEntry
}

fun Pair<ConfigCategory, ConfigEntryBuilder>.keyCodeField(
    text: Text,
    startKey: InputUtil.Key,
    defaultKey: () -> InputUtil.Key,
    tooltipSupplier: (InputUtil.Key) -> Optional<Array<Text>> = { Optional.empty<Array<Text>>() },
    saveConsumer: (InputUtil.Key) -> Unit
): KeyCodeEntry {
    val (category, entryBuilder) = this
    val keyCodeEntry = entryBuilder.startKeyCodeField(text, startKey)
        .setDefaultValue(defaultKey)
        .setKeyTooltipSupplier(tooltipSupplier)
        .setKeySaveConsumer(saveConsumer)
        .build()
    category.addEntry(keyCodeEntry)
    return keyCodeEntry
}

fun Pair<ConfigCategory, ConfigEntryBuilder>.booleanToggle(
    text: Text,
    startValue: Boolean,
    defaultValue: () -> Boolean,
    tooltipSupplier: (Boolean) -> Optional<Array<Text>> = { Optional.empty<Array<Text>>() },
    saveConsumer: (Boolean) -> Unit
): BooleanListEntry {
    val (category, entryBuilder) = this
    val booleanListEntry = entryBuilder.startBooleanToggle(text, startValue)
        .setDefaultValue(defaultValue)
        .setTooltipSupplier(tooltipSupplier)
        .setSaveConsumer(saveConsumer)
        .build()
    category.addEntry(booleanListEntry)
    return booleanListEntry
}


@JvmName("booleanToggleSubCategoryBuilderConfigEntryBuilder")
fun Pair<SubCategoryBuilder, ConfigEntryBuilder>.booleanToggle(
    text: Text,
    startValue: Boolean,
    defaultValue: () -> Boolean,
    tooltipSupplier: (Boolean) -> Optional<Array<Text>> = { Optional.empty<Array<Text>>() },
    saveConsumer: (Boolean) -> Unit
): BooleanListEntry {
    val (subCategory, entryBuilder) = this
    val booleanListEntry = entryBuilder.startBooleanToggle(text, startValue)
        .setDefaultValue(defaultValue)
        .setTooltipSupplier(tooltipSupplier)
        .setSaveConsumer(saveConsumer)
        .build()
    subCategory.add(booleanListEntry)
    return booleanListEntry
}

fun Pair<SubCategoryBuilder, ConfigEntryBuilder>.alphaColorField(
    text: Text,
    startColor: Int,
    defaultColor: () -> Int,
    tooltipSupplier: (Int) -> Optional<Array<Text>> = { Optional.empty<Array<Text>>() },
    saveConsumer: (Int) -> Unit
): ColorEntry {
    val (subCategory, entryBuilder) = this
    val colorEntry = entryBuilder.startAlphaColorField(text, startColor)
        .setDefaultValue(defaultColor)
        .setTooltipSupplier(tooltipSupplier)
        .setSaveConsumer(saveConsumer)
        .build()
    subCategory.add(colorEntry)
    return colorEntry
}

fun Pair<SubCategoryBuilder, ConfigEntryBuilder>.intSlider(
    text: Text,
    startValue: Int,
    minValue: Int,
    maxValue: Int,
    defaultValue: () -> Int,
    tooltipSupplier: (Int) -> Optional<Array<Text>> = { Optional.empty<Array<Text>>() },
    textGetter: (Int) -> Text,
    saveConsumer: (Int) -> Unit
): IntegerSliderEntry {
    val (subCategory, entryBuilder) = this
    val sliderEntry = entryBuilder.startIntSlider(text, startValue, minValue, maxValue)
        .setDefaultValue(defaultValue)
        .setTooltipSupplier(tooltipSupplier)
        .setSaveConsumer(saveConsumer)
        .setTextGetter(textGetter)
        .build()
    subCategory.add(sliderEntry)
    return sliderEntry
}