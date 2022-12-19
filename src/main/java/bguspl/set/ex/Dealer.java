package bguspl.set.ex;

import bguspl.set.Env;

import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * This class manages the dealer's threads and data
 */
public class Dealer implements Runnable {

    /**
     * The game environment object.
     */
    private final Env env;
    private Stack<Player> playerStack;
    private Stack<Thread> playersThreadStack;

    private long startTime;
    /**
     * Game entities.
     */
    private final Table table;
    private final Player[] players;

    /**
     * The list of card ids that are left in the dealer's deck.
     */
    private final List<Integer> deck;

    /**
     * True iff game should be terminated.
     */
    private volatile boolean terminate;

    /**
     * The time when the dealer needs to reshuffle the deck due to turn timeout.
     */
    private long reshuffleTime = Long.MAX_VALUE;

    private BlockingQueue<Player> playersToCheck;

    private Dictionary<Player,Long> playerToPenaltyTime;
    private boolean placingCards;
    public Dealer(Env env, Table table, Player[] players) {
        this.env = env;
        this.table = table;
        this.players = players;
        deck = IntStream.range(0, env.config.deckSize).boxed().collect(Collectors.toList());
        reshuffleTime = env.config.turnTimeoutMillis;
        playersToCheck = new LinkedBlockingQueue<>();
        playerToPenaltyTime = new Hashtable<>();
        placingCards = true;
        playerStack=new Stack<>();
        playersThreadStack = new Stack<>();
    }

    /**
     * The dealer thread starts here (main loop for the dealer thread).
     */
    @Override
    public void run() {
        env.logger.info("Thread " + Thread.currentThread().getName() + " starting.");
        placeCardsOnTable();
        for (Player p : this.players) {
            playerToPenaltyTime.put(p, (long) 0);
        }

        for (Player p :players){
            Thread t = new Thread(p,p.id+"");
            playerStack.push(p);
            playersThreadStack.push(t);
            System.out.println(p.id);
            t.start();
        }
        while (!shouldFinish()) {
            placingCards = true;
            placeCardsOnTable();

            startTime = System.currentTimeMillis();
            timerLoop();

            updateTimerDisplay(false);
            removeAllCardsFromTable();
        }
        announceWinners();
        terminate();
        env.logger.info("Thread " + Thread.currentThread().getName() + " terminated.");

    }

    /**
     * The inner loop of the dealer thread that runs as long as the countdown did not time out.
     */
    private void timerLoop() {

        while (!terminate && System.currentTimeMillis() - startTime< reshuffleTime) {

            try {
                sleepUntilWokenOrTimeout(System.currentTimeMillis() - startTime > reshuffleTime - env.config.turnTimeoutWarningMillis);
            } catch (Exception e) {
                throw new IllegalArgumentException(e.getMessage());
            }
            updateTimerDisplay(false);
            placeCardsOnTable();
            try {

                tokensValidation();
                // System.out.println("dealer validatis cards");
            } catch (InterruptedException e) {
                System.out.println(e.getMessage());
            }
            for (Player p : players) {
                //System.out.println("players loop");

                if (playerToPenaltyTime.get(p) > System.currentTimeMillis())
                    env.ui.setFreeze(p.id, playerToPenaltyTime.get(p) - System.currentTimeMillis()+1000);
                else if(p.isTested) {
                    p.isTested = false;
                    //System.out.println("before sync");
                    env.ui.setFreeze(p.id, 0);
                    synchronized (p) {
                        //   System.out.println("before notify player");
                        p.notify();
                    }
                }
            }
            terminate = zeroSetsLeft();
        }
    }

    private boolean zeroSetsLeft() {
        List<Integer> currentSlots = Arrays.asList(table.slotToCard);
        currentSlots = currentSlots.parallelStream().filter(Objects::nonNull).collect(Collectors.toList()); //remove null values
        return env.util.findSets(deck, 1).size() == 0 && env.util.findSets(currentSlots, 1).size() == 0;
    }

    private void tokensValidation() throws InterruptedException {
        // System.out.println("enters token val");
        if (playersToCheck.size()>0) {
            Player p = playersToCheck.take();
            if(p.tokenToSlots().size() == 3) {
                if (isSet(p.tokenToSlots())) {
                    Vector<Integer> slotsToRemove = new Vector<>();
                    slotsToRemove.addAll(p.tokenToSlots());
                    removeCardsBySlots(slotsToRemove);
                    p.point();
                    updateTimerDisplay(true);
                    playerToPenaltyTime.put(p, System.currentTimeMillis() + env.config.pointFreezeMillis);
                } else {
                    p.penalty();
                    playerToPenaltyTime.put(p, System.currentTimeMillis() + env.config.penaltyFreezeMillis);
                }
                try {
                    placeCardsOnTable();
                } catch (Exception e) {

                }
                p.isTested = true;
            }

        }
    }
    /**
     * Called when the game should be terminated.
     */
    public void terminate() {
        while (!playerStack.isEmpty()){
           Player p=playerStack.pop();
           Thread playerThread = playersThreadStack.pop();
           try {
                   playerThread.interrupt();
                   playerThread.join();
           }
           catch (Exception e){
           }
        }
        terminate = true;
    }

    /**
     * Check if the game should be terminated or the game end conditions are met.
     *
     * @return true iff the game should be finished.
     */
    private boolean shouldFinish() {
        return terminate || env.util.findSets(deck, 1).size() == 0;
    }

    /**
     * Checks cards should be removed from the table and removes them.
     */
    private void removeCardsFromTable() {
        // TODO implement
    }

    /**
     * Check if any cards can be removed from the deck and placed on the table.
     */
    public void placeCardsOnTable() {
        List<Integer> slotNumbers = new ArrayList<>();
        for (int i = 0; i < 12; i++)
            slotNumbers.add(i);
        while (!slotNumbers.isEmpty()) {
            Random rand = new Random();
            int index = rand.nextInt(slotNumbers.size());
            Integer j = slotNumbers.get(index);
            slotNumbers.remove(j);
            if (table.slotToCard[j] == null) {
                Random random = new Random();
                if (!deck.isEmpty()) {
                    table.placeCard(deck.remove(random.nextInt(deck.size())), j);
                }
            }

        }
        placingCards = false;
    }

    /**
     * Sleep for a fixed amount of time or until the thread is awakened for some purpose.
     */
    private synchronized void sleepUntilWokenOrTimeout(boolean shortSleep) throws InterruptedException {
        long sleepPeriod = 200;
        if (shortSleep){
            sleepPeriod =50;
        }

            this.wait(1);

    }

    /**
     * Reset and/or update the countdown and the countdown display.
     */
    private void updateTimerDisplay(boolean reset) {
        long elapsed =env.config.turnTimeoutMillis- (System.currentTimeMillis()-startTime);
        boolean warn = false;
        if (reset) {
            elapsed = env.config.turnTimeoutMillis;
            startTime = System.currentTimeMillis();
        }
        else if(elapsed<=env.config.turnTimeoutWarningMillis)
            warn = true;

        env.ui.setCountdown(Math.max(elapsed,0),warn);
    }

    /**
     * Returns all the cards from the table to the deck.
     */
    private void removeAllCardsFromTable() {
        placingCards = true;
        for (Player p : players){
            p.clearTokens(Arrays.asList(0,1,2,3,4,5,6,7,8,9,10,11));
            p.changeAfterPenalty = true;
        }
        for(int i = 0; i < table.slotToCard.length; i++) {
            if (table.slotToCard[i] != null) {
                deck.add(table.slotToCard[i]);
                table.removeCard(i);
            }
        }
        placingCards = false;
    }

    /**
     * Check who is/are the winner/s and displays them.
     */
    private void announceWinners() {

        int maxPoint = 0;

        for (Player p : players) {
            p.clearTokens(Arrays.asList(0,1,2,3,4,5,6,7,8,9,10,11));
            if (p.score() > maxPoint)
                maxPoint = p.score();
        }
        List<Integer> winners = new LinkedList();
        for (Player p : players){
            if (p.score() == maxPoint)
                winners.add(p.id);
        }
        int[] winnersArray = new int[winners.size()];
        for (int i = 0; i < winnersArray.length;i++){
            winnersArray[i] = winners.get(i);
        }
        env.ui.announceWinner(winnersArray);

    }
    private boolean isSet(List<Integer> setToTest) {
        System.out.println("testing set for cards:");
        System.out.println(table.slotToCard[setToTest.get(0)]);
        System.out.println(table.slotToCard[setToTest.get(1)]);
        System.out.println(table.slotToCard[setToTest.get(2)]);
        int[] setToTestArray = new int[3];
        for (int i = 0; i < setToTest.size(); i++) {
                setToTestArray[i] = table.slotToCard[setToTest.get(i)];
        }
        return env.util.testSet(setToTestArray);
    }
    public void removeCardsBySlots(Vector<Integer> slots){

        for (Player p:players) {
            p.clearTokens(slots);
        }
        for (int i = 0; i < slots.size(); i ++) {
            table.removeCard(slots.get(i));
        }
    }
    public void addToPlayersQueue(Player p){
        playersToCheck.add(p);
    }


    public boolean isPlacingCards() {
        return placingCards;
    }
}
