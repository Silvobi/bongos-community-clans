package pl.bongo.clans;

import static pl.bongo.clans.ClanData.*;
import java.util.*;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.ItemLore;
import pl.bongo.clans.claims.*;

/** Server-owned, read-only clan directory. UUIDs keep clicks stable when names/order change. */
public final class ClanBrowserMenu extends ChestMenu {
    public enum Sort { NEWEST, OLDEST, AZ, ZA, MOST_MEMBERS, FEWEST_MEMBERS, MOST_AREA, LEAST_AREA }
    public record Position(Sort sort, int page) {}
    private final ClanRuntime runtime;
    private final Position position;
    private final int pages;
    private final List<UUID> entries;

    static List<Clan> sorted(Collection<Clan> clans, Sort sort, Map<UUID, ClaimLeaderboard.Stats> stats) {
        Comparator<Clan> comparator = switch (sort) {
            case NEWEST -> Comparator.comparingLong((Clan c) -> c.createdAt).reversed();
            case OLDEST -> Comparator.comparingLong(c -> c.createdAt);
            case AZ -> Comparator.comparing(c -> c.name, String.CASE_INSENSITIVE_ORDER);
            case ZA -> Comparator.comparing((Clan c) -> c.name, String.CASE_INSENSITIVE_ORDER).reversed();
            case MOST_MEMBERS -> Comparator.comparingInt((Clan c) -> c.members.size()).reversed();
            case FEWEST_MEMBERS -> Comparator.comparingInt(c -> c.members.size());
            case MOST_AREA -> Comparator.comparingLong((Clan c) -> stats.get(c.id).area()).reversed();
            case LEAST_AREA -> Comparator.comparingLong(c -> stats.get(c.id).area());
        };
        return clans.stream().sorted(comparator.thenComparing(c -> c.name, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(c -> c.id)).toList();
    }
    public static void open(ClanRuntime runtime, ServerPlayer player, Position requested) {
        Sort sort = ClanClaims.gp == null && requested.sort.ordinal() >= 6 ? Sort.NEWEST : requested.sort;
        Map<UUID, ClaimLeaderboard.Stats> stats = ClanClaims.gp == null ? Map.of() : ClaimLeaderboard.current(runtime);
        List<Clan> clans = sorted(runtime.service.state().clans.values(), sort, stats);
        int pages = ClanMenus.pages(clans.size()), page = Math.clamp(requested.page, 0, pages - 1);
        player.openMenu(new SimpleMenuProvider((id, inventory, owner) -> new ClanBrowserMenu(id, inventory, runtime,
                player, new Position(sort, page), pages, clans, stats), Lang.component(player, "browser_title")));
    }
    private ClanBrowserMenu(int id, Inventory inventory, ClanRuntime runtime, ServerPlayer viewer, Position position,
                            int pages, List<Clan> clans, Map<UUID, ClaimLeaderboard.Stats> stats) {
        super(MenuType.GENERIC_9x6, id, inventory, new SimpleContainer(54), 6);
        this.runtime = runtime; this.position = position; this.pages = pages;
        ClanMenus.fill(getContainer());
        for (Sort sort : Sort.values()) {
            if (sort.ordinal() >= 6 && ClanClaims.gp == null) continue;
            Item icon = switch (sort) {
                case NEWEST, OLDEST -> Items.CLOCK;
                case AZ, ZA -> Items.NAME_TAG;
                case MOST_MEMBERS, FEWEST_MEMBERS -> Items.PLAYER_HEAD;
                default -> Items.SPAWNER;
            };
            var item = ClanMenus.item(icon, Lang.text(viewer, "sort_" + sort.name().toLowerCase(Locale.ROOT)),
                    List.of(Lang.text(viewer, sort == position.sort ? "sort_selected" : "sort_choose")));
            if (sort == position.sort) item.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
            getContainer().setItem(sort.ordinal(), item);
        }
        List<Clan> shown = clans.subList(Math.min(position.page * ClanMenus.PAGE_SIZE, clans.size()),
                Math.min((position.page + 1) * ClanMenus.PAGE_SIZE, clans.size()));
        entries = shown.stream().map(c -> c.id).toList();
        for (int i = 0; i < shown.size(); i++) {
            Clan clan = shown.get(i);
            var icon = ClanMenus.item(ClanMenus.sword(clan.members.size()), clan.name,
                    List.of(Lang.text(viewer, "info_members", clan.members.size()), Lang.text(viewer, "browser_open")));
            icon.set(DataComponents.CUSTOM_NAME, CommunityClans.prefix(clan).append(clan.name).withStyle(s -> s.withItalic(false)));
            if (stats.containsKey(clan.id)) {
                var lore = new ArrayList<>(icon.get(DataComponents.LORE).lines());
                lore.add(Lang.component(viewer, "info_area", stats.get(clan.id).area()).copy().withStyle(ChatFormatting.GRAY));
                icon.set(DataComponents.LORE, new ItemLore(lore));
            }
            getContainer().setItem(18 + i, icon);
        }
        if (shown.isEmpty()) getContainer().setItem(31, ClanMenus.item(Items.PAPER, Lang.text(viewer, "browser_empty"), List.of()));
        if (position.page > 0) getContainer().setItem(48, ClanMenus.item(Items.PAPER, Lang.text(viewer, "info_previous"), List.of()));
        getContainer().setItem(49, ClanMenus.item(Items.BARRIER, Lang.text(viewer, "info_close"), List.of(Lang.text(viewer, "info_page", position.page + 1, pages))));
        if (position.page + 1 < pages) getContainer().setItem(50, ClanMenus.item(Items.PAPER, Lang.text(viewer, "info_next"), List.of()));
    }
    @Override public boolean stillValid(Player player) { return true; }
    @Override public ItemStack quickMoveStack(Player player, int slot) { return ItemStack.EMPTY; }
    @Override public boolean canDragTo(Slot slot) { return false; }
    @Override public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) { return false; }
    @Override public void clicked(int slot, int button, ContainerInput input, Player player) {
        if (!(player instanceof ServerPlayer viewer)) return;
        if (input == ContainerInput.PICKUP && button == 0) {
            if (slot == 49) { viewer.closeContainer(); return; }
            if (slot >= 0 && slot < (ClanClaims.gp == null ? 6 : 8)) {
                open(runtime, viewer, new Position(Sort.values()[slot], 0)); return;
            }
            if (slot == 48 && position.page > 0) { open(runtime, viewer, new Position(position.sort, position.page - 1)); return; }
            if (slot == 50 && position.page + 1 < pages) { open(runtime, viewer, new Position(position.sort, position.page + 1)); return; }
            if (slot >= 18 && slot - 18 < entries.size()) {
                UUID clan = entries.get(slot - 18);
                if (runtime.service.state().clans.containsKey(clan)) ClanInfoMenu.open(runtime, viewer, clan, 0, position);
                else open(runtime, viewer, position);
                return;
            }
        }
        broadcastFullState();
    }
}
