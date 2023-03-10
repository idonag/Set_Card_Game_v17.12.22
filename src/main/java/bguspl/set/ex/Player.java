package bguspl.set.ex;

import bguspl.set.Env;

import java.util.List;
import java.util.Random;
import java.util.Vector;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * This class manages the players' threads and data
 *
 * @inv id >= 0
 * @inv score >= 0
 */
public class Player implements Runnable {
    private BlockingQueue<Integer> playerPresses;
    private Vector<Integer> playerTokens;
    protected boolean changeAfterPenalty;

    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Game entities.
     */
    private final Table table;

    /**
     * The id of the player (starting from 0).
     */
    public final int id;

    /**
     * The thread representing the current player.
     */
    private Thread playerThread;

    /**
     * The thread of the AI (computer) player (an additional thread used to generate key presses).
     */
    private Thread aiThread;

    /**
     * True iff the player is human (not a computer player).
     */
    private final boolean human;

    /**
     * True iff game should be terminated.
     */
    private volatile boolean terminate;

    /**
     * The current score of the player.
     */
    private int score;
    final Dealer dealer;

    boolean isTested;
    /**
     * The class constructor.
     *
     * @param env    - the environment object.
     * @param dealer - the dealer object.
     * @param table  - the table object.
     * @param id     - the id of the player.
     * @param human  - true iff the player is a human player (i.e. input is provided manually, via the keyboard).
     */
    public Player(Env env, Dealer dealer, Table table, int id, boolean human) {
        this.env = env;
        this.table = table;
        this.id = id;
        this.human = human;
        playerTokens = new Vector<>();
        playerPresses = new LinkedBlockingQueue<>(3);
        changeAfterPenalty = true;
        this.dealer = dealer;
        isTested = true;
    }

    public Vector<Integer> tokenToSlots(){
        return playerTokens;
    }
    /**
     * The main player thread of each player starts here (main loop for the player thread).
     */
    @Override
    public void run() {
        playerThread = Thread.currentThread();
        env.logger.info("Thread " + Thread.currentThread().getName() + " starting.");
        if (!human) createArtificialIntelligence();

        while (!terminate) {
            try {
                System.out.println(id +" preform step");
                step();
            }
            catch (InterruptedException e){
                terminate();
            }

            if(playerTokens.size() == 3 && changeAfterPenalty ) {
                System.out.println(id+ " entered if size = 3");
                dealer.addToPlayersQueue(this);
                System.out.println(id+ " added himself to dealer's queue");
                isTested = false;
                synchronized (dealer) {
                    dealer.notifyAll();
                }
                try {
                    synchronized (this) {
                        System.out.println(id+" is sleeping");
                        this.wait();
                    }
                } catch (InterruptedException e) {
                    terminate();
                }
                playerPresses.clear();
            }

        }

        if (!human) try { aiThread.interrupt();
            aiThread.join(); } catch (InterruptedException ignored) {}
        System.out.println(id + " terminated");
        env.logger.info("Thread " + Thread.currentThread().getName() + " terminated.");
    }

    /**
     * Creates an additional thread for an AI (computer) player. The main loop of this thread repeatedly generates
     * key presses. If the queue of key presses is full, the thread waits until it is not full.
     */
    private void createArtificialIntelligence() {
        // note: this is a very, very smart AI (!)
        aiThread = new Thread(() -> {
            env.logger.info("Thread " + Thread.currentThread().getName() + " starting.");
            while (!terminate) {
                Random random = new Random();
                int slotChosen =random.nextInt(12);
                try {
                    if (!dealer.isPlacingCards()) {
                        playerPresses.put(slotChosen);
                    }
                } catch (InterruptedException e) {
                }
            }
            env.logger.info("Thread " + Thread.currentThread().getName() + " terminated.");
        }, "computer-" + id);
        aiThread.start();
    }

    /**
     * Called when the game should be terminated.
     */
    public void terminate() {
        terminate = true;
    }

    /**
     * This method is called when a key is pressed.
     *
     * @param slot - the slot corresponding to the key pressed.
     */
    public void keyPressed(int slot) {
        if(!dealer.isPlacingCards()){
            playerPresses.add(slot);
        }
    }

    public void  step() throws InterruptedException {
        int slot = playerPresses.take();
        if (playerTokens.contains(slot)){
            playerTokens.remove((Integer) slot);
            table.removeToken(this.id,slot);
            changeAfterPenalty=true;
        }
        else {
            if (playerTokens.size() < 3) {
                if (table.slotToCard[slot] != null) {
                    table.placeToken(this.id, slot);
                    playerTokens.add(slot);
                }
            }
        }
    }

    /**
     * Award a point to a player and perform other related actions.
     *
     * @post - the player's score is increased by 1.
     * @post - the player's score is updated in the ui.
     */
    public void point() {
        // TODO implement

        int ignored = table.countCards(); // this part is just for demonstration in the unit tests
        env.ui.setScore(id, ++score);
    }

    /**
     * Penalize a player and perform other related actions.
     */
    public void penalty() {
        changeAfterPenalty=false;
    }

    public int score() {
        return score;
    }

    public void clearTokens(List<Integer> slots) {
        for(int slot:slots) {
            if (tokenToSlots().contains(slot)) {
                playerTokens.remove((Integer) slot);
                table.removeToken(id, slot);
            }
        }

    }
}
