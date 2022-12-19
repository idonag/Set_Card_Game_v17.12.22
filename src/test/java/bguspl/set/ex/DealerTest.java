package bguspl.set.ex;

import bguspl.set.Config;
import bguspl.set.Env;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Properties;

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
    void run() {
        dealer.run();

// we give to the dealer a table and deck with no available set
    }



    @Test
    void placeCardsOnTable(){

    }
}