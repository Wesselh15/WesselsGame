package model;

import static model.GameConstants.*;

public class CardActionHandToDiscardPile implements CardAction {

    private model.Card card;
    private int discardPile;

    public CardActionHandToDiscardPile(model.Card card, int discardPile) {
        this.card = card;
        this.discardPile = discardPile;
    }

    @Override
    public void execute(Game game, Player player) {
        game.getHand(player).remove(card);
        game.getDiscardPile(player,discardPile).addCard(card);
    }

    @Override
    public boolean isValid(Game game, Player player) {
        return game.getHand(player).contains(card) &&
                discardPile >= 0 && discardPile < NUM_DISCARD_PILES;
    }

}
