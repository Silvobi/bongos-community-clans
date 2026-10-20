package pl.bongo.clans.claims;
import java.util.*;
import org.junit.jupiter.api.Test;
import pl.bongo.clans.ClanData;
import static org.junit.jupiter.api.Assertions.*;
class ClaimLeaderboardTest {
    @Test void areaCountsRootsOfflineMembersAndSeparateWorldsWithoutSubclaims() {
        var state=new ClanData.State();UUID a=UUID.randomUUID(),b=UUID.randomUUID(),other=UUID.randomUUID();
        var clan=new ClanData.Clan();clan.id=UUID.randomUUID();clan.name="Test";
        clan.members.put(a,new ClanData.Member(a,"A",ClanData.Role.FOUNDER));clan.members.put(b,new ClanData.Member(b,"Offline",ClanData.Role.MEMBER));state.clans.put(clan.id,clan);
        var plots=List.of(new ClaimLeaderboard.Plot(a,"world",true,100),new ClaimLeaderboard.Plot(b,"world_nether",true,300),
            new ClaimLeaderboard.Plot(a,"world",false,80),new ClaimLeaderboard.Plot(null,"world",true,10000),new ClaimLeaderboard.Plot(other,"world",true,10000));
        var stats=ClaimLeaderboard.calculate(state,plots).get(clan.id);
        assertEquals(2,stats.claims());assertEquals(400,stats.area());assertEquals(Map.of("world",100L,"world_nether",300L),stats.worlds());
        clan.members.remove(b);assertEquals(100,ClaimLeaderboard.calculate(state,plots).get(clan.id).area());
    }
    @Test void clanBuildIncludesContainersAndAccessButNeverManagement() {
        assertTrue(ClanClaims.levelAllows("build","BUILD"));assertTrue(ClanClaims.levelAllows("build","CONTAINER"));assertTrue(ClanClaims.levelAllows("build","ACCESS"));
        for(String permission:List.of("MANAGE","EDIT","NEIGHBOR")) assertFalse(ClanClaims.levelAllows("build",permission));
        assertFalse(ClanClaims.levelAllows("container","BUILD"));assertTrue(ClanClaims.levelAllows("container","ACCESS"));
        assertFalse(ClanClaims.levelAllows("off","ACCESS"));assertThrows(IllegalArgumentException.class,()->ClanClaims.levelAllows("bogus","BUILD"));
    }
    @Test void rankingsUseLongAreaAndDifferentSorting() {
        var s=new ClanData.State();var a=new ClanData.Clan();a.id=UUID.randomUUID();a.name="A";var b=new ClanData.Clan();b.id=UUID.randomUUID();b.name="B";s.clans.put(a.id,a);s.clans.put(b.id,b);
        var stats=Map.of(a.id,new ClaimLeaderboard.Stats(1,5_000_000_000L,Map.of()),b.id,new ClaimLeaderboard.Stats(20,500,Map.of()));
        assertEquals(List.of(a.id,b.id),ClaimLeaderboard.order(s,stats,true));assertEquals(List.of(b.id,a.id),ClaimLeaderboard.order(s,stats,false));
    }
}
