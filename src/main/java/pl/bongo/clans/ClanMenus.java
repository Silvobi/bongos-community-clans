package pl.bongo.clans;

import java.util.*;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.*;
import net.minecraft.world.Container;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.ItemLore;

final class ClanMenus {
    static final int PAGE_SIZE = 27;
    private ClanMenus() {}
    static int pages(int size) { return Math.max(1, (size + PAGE_SIZE - 1) / PAGE_SIZE); }
    static Item sword(int members) {
        return switch (Math.clamp(members, 1, 6)) {
            case 1 -> Items.WOODEN_SWORD;
            case 2 -> Items.COPPER_SWORD;
            case 3 -> Items.IRON_SWORD;
            case 4 -> Items.GOLDEN_SWORD;
            case 5 -> Items.DIAMOND_SWORD;
            default -> Items.NETHERITE_SWORD;
        };
    }
    static ItemStack item(Item type, String name, List<String> lore) {
        ItemStack stack = new ItemStack(type);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(name).withStyle(ChatFormatting.AQUA).withStyle(s -> s.withItalic(false)));
        stack.set(DataComponents.LORE, new ItemLore(lore.stream().<Component>map(s -> Component.literal(s)
                .withStyle(ChatFormatting.GRAY).withStyle(style -> style.withItalic(false))).toList()));
        return stack;
    }
    static void fill(Container container) {
        for (int i = 0; i < container.getContainerSize(); i++)
            container.setItem(i, item(Items.STAINED_GLASS_PANE.black(), " ", List.of()));
        for (int i = 18; i < 45; i++) container.setItem(i, ItemStack.EMPTY);
    }
}
