/*
 * FDPClient Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge by LiquidBounce.
 * https://github.com/Itamio/FDPClient/
 */
package net.asd.union.utils.inventory

import net.asd.union.utils.client.MinecraftInstance
import net.minecraft.enchantment.Enchantment
import net.minecraft.entity.item.EntityItem
import net.minecraft.item.ItemArmor
import net.minecraft.item.ItemStack

object ArmorComparator: MinecraftInstance {
	fun getBestArmorSet(stacks: List<ItemStack?>, entityStacksMap: Map<ItemStack, EntityItem>? = null): ArmorSet? {
		val thePlayer = mc.thePlayer ?: return null

		// Consider armor pieces dropped on ground
		// Their indices are always -1
		val droppedStacks = entityStacksMap?.keys.indexedArmorStacks { -1 }

		// Consider currently equipped armor, when searching useful stuff in chests
		// Their indices are always null to prevent any accidental impossible interactions when searching through chests
		val equippedArmorWhenInChest =
			if (thePlayer.openContainer.windowId != 0)
			// Filter out any non armor items player could be equipped (skull / pumpkin)
				thePlayer.inventory.armorInventory.asIterable().indexedArmorStacks { null }
			else emptyList()

		val inventoryStacks = stacks.indexedArmorStacks()

		val comparator = Comparator.comparingDouble<Pair<Int?, ItemStack>> { (index, stack) ->
			// Sort items by distance from player, equipped items are always preferred with distance -1
			if (index == -1)
				thePlayer.getDistanceSqToEntity(entityStacksMap?.get(stack) ?: return@comparingDouble -1.0)
			else -1.0
		}.thenComparingInt { (index, stack) ->
			// Prioritise sets that are in lower parts of inventory (not in chest) or equipped, prevents stealing multiple armor duplicates.
			if (stack in thePlayer.inventory.armorInventory) Int.MIN_VALUE
			else index?.inv() ?: Int.MIN_VALUE
		}.thenComparingInt {
			if (it.second in thePlayer.inventory.armorInventory) Int.MAX_VALUE
			else it.first ?: Int.MAX_VALUE
		}.thenComparingDouble {
			// Prefer armor pieces with higher intrinsic defense + Protection before using durability as a tiebreaker.
			-it.second.armorScore()
		}.thenComparingInt {
			-it.second.totalDurability
		}.thenComparingInt {
			-it.second.enchantmentCount
		}.thenComparingInt {
			-it.second.enchantmentSum
		}

		val armorMap = (droppedStacks + equippedArmorWhenInChest + inventoryStacks)
			.sortedWith(comparator)
			.groupBy { (it.second.item as ItemArmor).armorType }

		val helmets = armorMap[0] ?: NULL_LIST
		val chestplates = armorMap[1] ?: NULL_LIST
		val leggings = armorMap[2] ?: NULL_LIST
		val boots = armorMap[3] ?: NULL_LIST

		return sequence {
			helmets.forEach { helmet ->
				chestplates.forEach { chestplate ->
					leggings.forEach { leggings ->
						boots.forEach { boots ->
							yield(ArmorSet(helmet, chestplate, leggings, boots))
						}
					}
				}
			}
		}.maxByOrNull { it.defenseFactor }
	}

	@JvmStatic
	fun getArmorScore(stack: ItemStack): Double = stack.armorScore()
}

/**
 * This function takes an iterable of ItemStacks and an optional index callback function,
 * and returns a list of pairs. Each pair consists of an index and an ItemStack.
 *
 * @param indexCallback A function that takes an integer as input and returns an integer.
 *                      This function is used to manipulate the index of each ItemStack in the iterable.
 *                      By default, it returns the same index.
 *
 * @return A list of pairs. Each pair consists of an index (possibly manipulated by the indexCallback function)
 *         and an ItemStack. Only ItemStacks where the item is an instance of ItemArmor are included in the list.
 *         If the iterable is null, an empty list is returned.
 */
private inline fun Iterable<ItemStack?>?.indexedArmorStacks(indexCallback: (Int) -> Int? = { it }): List<Pair<Int?, ItemStack>> =
	this?.mapIndexedNotNull { index, stack ->
		if (stack?.item is ItemArmor) indexCallback(index) to stack
		else null
	} ?: emptyList()

class ArmorSet(private vararg val armorPairs: Pair<Int?, ItemStack>?) : Iterable<Pair<Int?, ItemStack>?> {
	/**
	 * Combined armor score of the whole set.
	 */
	val defenseFactor by lazy {
		armorPairs.fold(0.0) { score, pair ->
			score + (pair?.second?.armorScore() ?: 0.0)
		}
	}

	override fun iterator() = armorPairs.iterator()

	operator fun contains(stack: ItemStack) = armorPairs.any { it?.second == stack }

	operator fun contains(index: Int) = armorPairs.any { it?.first == index }

	fun indexOf(stack: ItemStack) = armorPairs.find { it?.second == stack }?.first ?: -1

	operator fun get(index: Int) = armorPairs.getOrNull(index)
}

operator fun ArmorSet?.contains(stack: ItemStack) = this?.contains(stack) ?: true

private fun ItemStack.armorScore(): Double {
	val armorItem = item as? ItemArmor ?: return 0.0
	val armorPoints = armorItem.getArmorMaterial().getDamageReductionAmount(armorItem.armorType)
	val protectionLevel = getEnchantmentLevel(Enchantment.protection)
	val protectionEpf = if (protectionLevel > 0) {
		((6 + protectionLevel * protectionLevel) * 0.75f / 3).toDouble()
	} else 0.0

	// Score the item's own armor value independently so strong Protection pieces are not diluted by the rest of the set.
	return armorPoints / 25.0 + protectionEpf.coerceAtMost(25.0) * 0.03
}

private val NULL_LIST = listOf<Pair<Int?, ItemStack>?>(null)