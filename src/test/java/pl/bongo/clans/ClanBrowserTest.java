package pl.bongo.clans;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static pl.bongo.clans.ClanData.*;
import static pl.bongo.clans.ClanBrowserMenu.Sort.*;
import pl.bongo.clans.claims.ClaimLeaderboard.Stats;

class ClanBrowserTest {
    @Test void directorySupportsEverySortDirectionAndDeterministicTies() {
        Clan a=clan("Alpha",1,1), z=clan("Zulu",2,2);
        var stats=Map.of(a.id,new Stats(1,90,Map.of()),z.id,new Stats(2,20,Map.of()));
        for(var sort:List.of(OLDEST,AZ,FEWEST_MEMBERS,MOST_AREA))
            assertEquals(List.of(a,z),ClanBrowserMenu.sorted(List.of(z,a),sort,stats),sort.name());
        for(var sort:List.of(NEWEST,ZA,MOST_MEMBERS,LEAST_AREA))
            assertEquals(List.of(z,a),ClanBrowserMenu.sorted(List.of(a,z),sort,stats),sort.name());
        z.createdAt=1;
        assertEquals(List.of(a,z),ClanBrowserMenu.sorted(List.of(z,a),NEWEST,stats));
    }
    @Test void paginationUsesOnlyTheThreeMemberRows() {
        assertEquals(1,ClanMenus.pages(0)); assertEquals(1,ClanMenus.pages(27));
        assertEquals(2,ClanMenus.pages(28)); assertEquals(2,ClanMenus.pages(54)); assertEquals(3,ClanMenus.pages(55));
    }
    private Clan clan(String name,long time,int count) {
        var clan=new Clan(); clan.id=UUID.randomUUID(); clan.name=name; clan.createdAt=time;
        for(int i=0;i<count;i++) { UUID id=UUID.randomUUID(); clan.members.put(id,new Member(id,"Player"+i,Role.MEMBER)); }
        return clan;
    }
}
