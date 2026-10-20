package pl.bongo.clans.claims;

import java.util.*;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import pl.bongo.clans.*;

public final class ClaimLeaderboard {
    public record Plot(UUID owner,String world,boolean root,long area) {}
    public record Stats(long claims,long area,Map<String,Long> worlds) {}
    public static Map<UUID,Stats> calculate(ClanData.State state,Collection<Plot> plots) {
        Map<UUID,UUID> members=new HashMap<>();Map<UUID,Stats> result=new HashMap<>();
        for(var clan:state.clans.values()) { result.put(clan.id,new Stats(0,0,Map.of()));for(UUID member:clan.members.keySet()) members.put(member,clan.id); }
        for(Plot plot:plots) {
            UUID clan=members.get(plot.owner);if(!plot.root || clan==null) continue;
            if(plot.area<0) throw new IllegalArgumentException("Invalid claim area");
            Stats old=result.get(clan);Map<String,Long> worlds=new TreeMap<>(old.worlds);
            worlds.merge(plot.world,plot.area,Math::addExact);
            result.put(clan,new Stats(Math.addExact(old.claims,1),Math.addExact(old.area,plot.area),Map.copyOf(worlds)));
        }
        return result;
    }
    public static Map<UUID,Stats> current(ClanRuntime runtime) {
        GpClaims gp=ClanClaims.gp;if(gp==null) throw new IllegalStateException("GriefPrevention3D nie jest dostępne.");
        List<Plot> plots=new ArrayList<>();
        for(Object claim:gp.all()) {
            if(GpClaims.call(claim,"parentId")!=null) continue;
            UUID owner=(UUID)GpClaims.call(claim,"ownerId");if(owner==null) continue;
            Object bounds=GpClaims.call(claim,"bounds");
            long area=(boolean)GpClaims.call(bounds,"isShaped")?((Number)GpClaims.call(bounds,"area")).longValue()
                :Math.multiplyExact((long)(int)GpClaims.call(bounds,"maxX")-(int)GpClaims.call(bounds,"minX")+1,
                    (long)(int)GpClaims.call(bounds,"maxZ")-(int)GpClaims.call(bounds,"minZ")+1);
            plots.add(new Plot(owner,(String)GpClaims.call(claim,"worldKey"),true,area));
        }
        return calculate(runtime.service.state(),plots);
    }
    public static List<UUID> order(ClanData.State state,Map<UUID,Stats> stats,boolean byArea) {
        Comparator<UUID> cmp=Comparator.<UUID>comparingLong(id->byArea?stats.get(id).area:stats.get(id).claims).reversed()
            .thenComparing(Comparator.<UUID>comparingLong(id->byArea?stats.get(id).claims:stats.get(id).area).reversed())
            .thenComparing(id->state.clans.get(id).name.toLowerCase(Locale.ROOT)).thenComparing(UUID::toString);
        return stats.keySet().stream().sorted(cmp).toList();
    }
    public static void show(ClanRuntime runtime,ServerPlayer player,boolean byArea,int page) {
        boolean pl=player.clientInformation().language().startsWith("pl");
        if(ClanClaims.gp==null) { player.sendSystemMessage(Component.literal(pl?"Ranking działek wymaga GriefPrevention3D.":"Claim ranking requires GriefPrevention3D."));return; }
        Map<UUID,Stats> stats=current(runtime);List<UUID> order=order(runtime.service.state(),stats,byArea);
        int pages=Math.max(1,(order.size()+9)/10);page=Math.min(Math.max(1,page),pages);
        player.sendSystemMessage(Component.literal((pl?"Ranking klanów • ":"Clan ranking • ")+(byArea?"m²":pl?"działki":"claims")+" • "+page+"/"+pages).withStyle(ChatFormatting.GOLD));
        for(int i=(page-1)*10;i<Math.min(page*10,order.size());i++) {
            UUID id=order.get(i);var clan=runtime.service.state().clans.get(id);Stats row=stats.get(id);
            player.sendSystemMessage(Component.literal((i+1)+". ").append(CommunityClans.prefix(clan))
                .append(Component.literal(clan.name+" • "+row.claims+(pl?" działek • ":" claims • ")+row.area+" m²")));
        }
        if(order.isEmpty()) player.sendSystemMessage(Component.literal(pl?"Brak klanów.":"No clans."));
    }
}
