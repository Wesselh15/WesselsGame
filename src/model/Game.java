package model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.Random;
import java.util.List;
import java.util.HashMap;


public class Game {

    private List<Player> players;
    private Map<Player, Integer> scores;

    private Map<Player, List<DiscardPile>> discardPiles;
    private Map<Player, StockPile> stockPiles;
    private Map<Player, List<model.Card>> hand;

    private List<Card> drawPile;
    private List<BuildingPile> buildingPiles;

    private int currentPlayerIndex;
    private int round;

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
        this.scores = new HashMap<>();

        // Generates 4 building piles (shared by all players)
        this.buildingPiles = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            buildingPiles.add(new BuildingPile());
        }

        // Assign cards to players
        int cardsToHandout = players.size() <= 4 ? 30 : 20;
        for (Player player : players){
            List<Card> handOut = new ArrayList<>(drawPile.subList(0, cardsToHandout));
            stockPiles.put(player, new StockPile(handOut));
            hand.put(player, new ArrayList<>());
            drawPile.removeAll(handOut);

            // Initialize 4 discard piles per player
            List<DiscardPile> playerDiscardPiles = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                playerDiscardPiles.add(new DiscardPile());
            }
            discardPiles.put(player, playerDiscardPiles);
        }

        // determine FirstPlayer
        Random r = new Random();
        currentPlayerIndex = r.nextInt(players.size());
        handCards(players.get(currentPlayerIndex));

        // set to the first round
        this.round = 0;
    }

    public void handCards(Player player) {
        // Calculate how many cards to draw
        int cardsToDraw = 5 - hand.get(player).size();

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

            // Refill hand during turn if not a discard action
            // (hand refills immediately when cards are played)
            if (!(cardAction instanceof model.CardActionHandToDiscardPile)) {
                handCards(player);
            }
        }

        // Checks if last action was to discard pile this ends the turn
        if (!cardActions.isEmpty()) {
            CardAction lastAction = cardActions.get(cardActions.size() - 1);
            if (lastAction instanceof model.CardActionHandToDiscardPile) {
                // Move to the next player
                currentPlayerIndex = (currentPlayerIndex + 1) % players.size();
                handCards(players.get(currentPlayerIndex));
            }
        }
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

    public int getRound() {
        return round;
    }

    public Map<Player, Integer> getScores() {
        return scores;
    }
}
