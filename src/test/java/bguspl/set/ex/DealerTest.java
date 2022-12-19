package bguspl.set.ex;

import bguspl.set.Config;
import bguspl.set.Env;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
//import sun.jvm.hotspot.utilities.Assert;

import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.Vector;

import static org.junit.jupiter.api.Assertions.*;

class DealerTest {

    Table table;
    private Integer[] slotToCard;
    private Integer[] cardToSlot;
    Dealer dealer;
    Player[] players;


    @BeforeEach
    void setUp() {
        Properties properties = new Properties();
        properties.put("Rows", "2");
        properties.put("Columns", "2");
        properties.put("FeatureSize", "3");
        properties.put("FeatureCount", "4");
        properties.put("TableDelaySeconds", "0");
        properties.put("PlayerKeys1", "81,87,69,82");
        properties.put("PlayerKeys2", "85,73,79,80");
        TableTest.MockLogger logger = new TableTest.MockLogger();
        Config config = new Config(logger, properties);
        slotToCard = new Integer[config.tableSize];
        cardToSlot = new Integer[config.deckSize];
        Env env = new Env(logger, config, new TableTest.MockUserInterface(), new TableTest.MockUtil());
        table = new Table(env, slotToCard, cardToSlot);
        players=new Player[env.config.players];
        dealer=new Dealer(env,table,players);
        int i=0;
        while(i<env.config.players) {
            Player player = new Player(env, dealer, table, i, i < env.config.humanPlayers);
            players[i]=player;
            i++;
        }

    }

    @Test
    void removeCardsBySlots() {
        Vector<Integer> slots = new Vector(Arrays.asList(0,1,2));
        dealer.removeCardsBySlots(slots);
        assertEquals(null,table.slotToCard[0],"slot should be empty ");
        assertEquals(null,table.slotToCard[1],"slot should be empty ");
        assertEquals(null,table.slotToCard[2],"slot should be empty ");
    }



    @Test
    void placeCardsOnTable(){
        dealer.placeCardsOnTable();
        for (int i = 0; i < table.slotToCard.length;i++)
            assertTrue(table.slotToCard[i] != null,"slot shouldn't be empty");
    }

}