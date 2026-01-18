package model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.Random;
import java.util.List;
import java.util.HashMap;

import static model.GameConstants.*;


public class Game {

    private List<Player> players;

    private Map<Player, List<DiscardPile>> discardPiles;
    private Map<Player, StockPile> stockPiles;
    private Map<Player, List<model.Card>> hand;

    private List<Card> drawPile;
    private List<BuildingPile> buildingPiles;

    private int currentPlayerIndex;

    // Multi-round scoring (500 punten om te winnen)
    private Map<Player, Integer> totalScores;
    private int roundNumber;

    public Game(List<Player> players){
        // Generate and Shuffle cards
        drawPile = CardGenerator.generateCards();
        Collections.shuffle(drawPile);

        // Assign Players
        this.players = players;

        // Generate new maps
        this.stockPiles = new HashMap<>();
        this.hand = new HashMap<>();
        this.discardPiles = new HashMap<>();

        // Initialiseer scoring systeem (0 punten voor alle spelers)
        this.totalScores = new HashMap<>();
        for (Player p : players) {
            totalScores.put(p, 0);
        }
        this.roundNumber = 1;

        // Generates 4 building piles (shared by all players)
        this.buildingPiles = new ArrayList<>();
        for (int i = 0; i < NUM_BUILDING_PILES; i++) {
            buildingPiles.add(new BuildingPile());
        }

        // Assign cards to players
        // 2-4 players: 30 cards, 5-6 players: 20 cards
        int cardsToHandout = players.size() <= 4 ? STOCK_SIZE_SMALL_GAME : STOCK_SIZE_LARGE_GAME;
        for (Player player : players){
            List<Card> handOut = new ArrayList<>(drawPile.subList(0, cardsToHandout));
            stockPiles.put(player, new StockPile(handOut));
            hand.put(player, new ArrayList<>());
            drawPile.removeAll(handOut);

            // Initialize 4 discard piles per player
            List<DiscardPile> playerDiscardPiles = new ArrayList<>();
            for (int i = 0; i < NUM_DISCARD_PILES; i++) {
                playerDiscardPiles.add(new DiscardPile());
            }
            discardPiles.put(player, playerDiscardPiles);
        }

        // determine FirstPlayer
        Random r = new Random();
        currentPlayerIndex = r.nextInt(players.size());
        handCards(players.get(currentPlayerIndex));
    }

    public void handCards(Player player) {
        // Calculate how many cards to draw
        int cardsToDraw = HAND_SIZE - hand.get(player).size();

        // Don't draw more than available in draw pile
        if (cardsToDraw > drawPile.size()) {
            cardsToDraw = drawPile.size();
        }

        // Only draw if there are cards available
        if (cardsToDraw > 0 && !drawPile.isEmpty()) {
            List<Card> handOut = new ArrayList<>(drawPile.subList(0, cardsToDraw));
            hand.get(player).addAll(handOut);
            drawPile.removeAll(handOut);
        }
    }

    public void doMove(List<CardAction> cardActions, Player player) throws GameException {
        // Check if it is the players turn
        if (players.get(currentPlayerIndex) != player) {
            throw new GameException("It's not " + player + "'s turn");
        }

        // Validate every actions first
        for (CardAction cardAction : cardActions) {
            if (!cardAction.isValid(this, player)) {
                throw new GameException("Invalid action: " + cardAction);
            }
        }

        // Execute all actions
        for (CardAction cardAction : cardActions) {
            cardAction.execute(this, player);

            // Checks if any building pile is full and clears it
            for (model.BuildingPile pile : buildingPiles) {
                if (pile.isFull()) {
                    pile.clear();
                }
            }

            // Refill hand ONLY if hand is empty during turn (not after discard)
            if (hand.get(player).isEmpty() && !(cardAction instanceof model.CardActionHandToDiscardPile)) {
                handCards(player);
            }
        }

        // NOTE: Discard no longer automatically ends the turn
        // Client must send END command explicitly to end the turn
    }

    /**
     * Ends the current player's turn and advances to the next player
     * Called when END command is received from the client
     */
    public void endTurn() throws GameException {
        // Move to the next player
        currentPlayerIndex = (currentPlayerIndex + 1) % players.size();
        // Refill hand for the new current player
        handCards(players.get(currentPlayerIndex));
    }


    // Getter methods
    public List<model.Card> getHand(Player player) {
        return hand.get(player);
    }

    public StockPile getStockPile(Player player) {
        return stockPiles.get(player);
    }

    public DiscardPile getDiscardPile(Player player, int index) {
        return discardPiles.get(player).get(index);
    }

    public model.BuildingPile getBuildingPile(int index) {
        return buildingPiles.get(index);
    }

    public Player getCurrentPlayer() {
        return players.get(currentPlayerIndex);
    }

    public List<Player> getPlayers() {
        return players;
    }

    // Game logic methods

    public boolean isPlayersTurn(Player player) {
        return players.get(currentPlayerIndex) == player;
    }

    public boolean hasPlayerWon(Player player) {
        StockPile stockPile = stockPiles.get(player);
        return stockPile != null && stockPile.isEmpty();
    }

    public Card findCardInHand(Player player, int cardNumber) {
        List<Card> playerHand = hand.get(player);
        if (playerHand == null) {
            return null;
        }

        for (Card card : playerHand) {
            if (!card.isSkipBo() && card.getNumber() == cardNumber) {
                return card;
            }
        }
        return null;
    }

    // ========== SCORING SYSTEEM (Multi-round functionaliteit) ==========

    /**
     * Berekent de score voor de winnaar van een ronde
     * Scoring regels:
     * - Winnaar krijgt 25 punten (basis)
     * - Plus 5 punten voor elke kaart in de stock pile van tegenstanders
     *
     * @param winner De speler die deze ronde heeft gewonnen
     * @return Het aantal punten dat de winnaar verdient
     */
    public int calculateRoundScore(Player winner) {
        int score = 25;  // Basis punten voor winnen

        // Tel kaarten in stock piles van tegenstanders
        for (Player opponent : players) {
            if (!opponent.equals(winner)) {
                StockPile opponentStock = stockPiles.get(opponent);
                if (opponentStock != null) {
                    score += opponentStock.size() * 5;
                }
            }
        }

        return score;
    }

    /**
     * Voegt punten toe aan een speler's totale score
     *
     * @param player De speler
     * @param points Aantal punten om toe te voegen
     */
    public void addScore(Player player, int points) {
        int currentScore = totalScores.getOrDefault(player, 0);
        totalScores.put(player, currentScore + points);
    }

    /**
     * Haalt de huidige totale score op van een speler
     *
     * @param player De speler
     * @return De totale score van de speler
     */
    public int getScore(Player player) {
        return totalScores.getOrDefault(player, 0);
    }

    /**
     * Controleert of er een overall winnaar is (>= 500 punten)
     *
     * @return De overall winnaar, of null als niemand 500 punten heeft
     */
    public Player getOverallWinner() {
        for (Player player : players) {
            if (totalScores.getOrDefault(player, 0) >= 500) {
                return player;
            }
        }
        return null;
    }

    /**
     * Geeft het huidige ronde nummer
     *
     * @return Het ronde nummer (start bij 1)
     */
    public int getRoundNumber() {
        return roundNumber;
    }

    /**
     * Geeft een kopie van alle scores (voor protocol)
     *
     * @return Map met alle spelers en hun scores
     */
    public Map<Player, Integer> getAllScores() {
        return new HashMap<>(totalScores);
    }

    /**
     * Sluit een ronde af en berekent de scores
     * Deze methode wordt aangeroepen als een speler zijn stock pile leeg heeft
     *
     * @param winner De speler die deze ronde heeft gewonnen
     * @return RoundResult object met alle ronde informatie
     */
    public RoundResult finishRound(Player winner) {
        // Bereken score voor de winnaar
        int score = calculateRoundScore(winner);
        addScore(winner, score);

        // Check of er een overall winnaar is (>= 500 punten)
        Player overallWinner = getOverallWinner();
        boolean gameOver = (overallWinner != null);

        return new RoundResult(winner, score, getAllScores(), gameOver, overallWinner);
    }

    /**
     * Start een nieuwe ronde (maar behoudt de scores!)
     * - Reset draw pile en building piles
     * - Deel nieuwe stock piles uit
     * - Maak hands leeg
     * - Reset discard piles
     * - Kies random nieuwe eerste speler
     */
    public void startNewRound() {
        // Verhoog ronde nummer
        roundNumber++;

        // Genereer en schud nieuwe kaarten
        drawPile = CardGenerator.generateCards();
        Collections.shuffle(drawPile);

        // Reset building piles
        buildingPiles.clear();
        for (int i = 0; i < NUM_BUILDING_PILES; i++) {
            buildingPiles.add(new BuildingPile());
        }

        // Reset stock piles, hands en discard piles voor alle spelers
        int cardsToHandout = players.size() <= 4 ? STOCK_SIZE_SMALL_GAME : STOCK_SIZE_LARGE_GAME;
        for (Player player : players) {
            // Nieuwe stock pile met verse kaarten
            List<Card> handOut = new ArrayList<>(drawPile.subList(0, cardsToHandout));
            stockPiles.put(player, new StockPile(handOut));
            drawPile.removeAll(handOut);

            // Maak hand leeg
            hand.get(player).clear();

            // Reset alle 4 discard piles
            List<DiscardPile> playerDiscardPiles = discardPiles.get(player);
            for (DiscardPile dp : playerDiscardPiles) {
                dp.clear();
            }
        }

        // Kies random nieuwe eerste speler
        Random r = new Random();
        currentPlayerIndex = r.nextInt(players.size());
        handCards(players.get(currentPlayerIndex));

        System.out.println("Ronde " + roundNumber + " gestart!");
    }
}
