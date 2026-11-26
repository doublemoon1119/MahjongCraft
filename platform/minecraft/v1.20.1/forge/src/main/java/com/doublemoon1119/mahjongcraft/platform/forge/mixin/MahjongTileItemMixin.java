package com.doublemoon1119.mahjongcraft.platform.forge.mixin;

import com.doublemoon1119.mahjongcraft.platform.forge.loader.ForgeItemRenderers;
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.item.MahjongTileItem;
import java.util.function.Consumer;
import net.minecraft.world.item.Item;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import org.spongepowered.asm.mixin.Mixin;

/**
 * 讓麻將牌物品以登記的程式繪製。
 *
 * <p>Forge 只向物品自己的類別詢問繪製方式，這裡替麻將牌物品加上 Forge 要的方法；繪製時由 {@link ForgeItemRenderers} 找出登記的繪製方式。</p>
 */
@Mixin(MahjongTileItem.class)
public abstract class MahjongTileItemMixin extends Item {
    /**
     * 僅供 mixin 編譯；mixin 的建構子不會被呼叫。
     *
     * @param properties 物品設定。
     */
    private MahjongTileItemMixin(Properties properties) {
        super(properties);
    }

    /**
     * 交出以程式繪製的設定；Forge 只在用戶端建立物品時呼叫。
     *
     * @param consumer 接收繪製設定。
     */
    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(ForgeItemRenderers.clientExtensions());
    }
}
