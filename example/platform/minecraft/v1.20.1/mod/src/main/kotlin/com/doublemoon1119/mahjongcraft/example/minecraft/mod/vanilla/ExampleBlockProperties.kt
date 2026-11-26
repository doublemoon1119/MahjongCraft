package com.doublemoon1119.mahjongcraft.example.minecraft.mod.vanilla

import net.minecraft.world.level.block.state.BlockBehaviour

/**
 * 複製一個方塊的全部方塊設定，作為新方塊設定的起點。
 *
 * @param block 要複製設定的方塊。
 * @return 可以繼續調整的方塊設定。
 */
fun copyBlockProperties(block: BlockBehaviour): BlockBehaviour.Properties = BlockBehaviour.Properties.copy(block)
