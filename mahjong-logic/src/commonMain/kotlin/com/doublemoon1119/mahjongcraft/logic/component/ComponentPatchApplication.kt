package com.doublemoon1119.mahjongcraft.logic.component

/**
 * 驗證局部變更使用正式登記、正確對象及仍然有效的舊值後套用，不影響其他組件。
 *
 * @param original 原本的組件集合。
 * @param patch 本次明確設定及移除的項目。
 * @param ownerType 接受變更的對象型別。
 * @return 經驗證的新集合。
 */
fun ComponentRegistry.applyPatch(
    original: ComponentSet,
    patch: ComponentPatch,
    ownerType: ComponentOwnerType<*>,
): ComponentSet {
    require(findOwner(ownerType.id) === ownerType) { "Unregistered component patch owner" }
    validate(original)
    validate(patch.values)
    validate(patch.expected)
    patch.removals.forEach { type ->
        require(find(type.id) === type) { "Unregistered component removal: ${type.id}" }
    }
    return patch.applyTo(original, ownerType)
}
