package model;

import static model.GameConstants.*;

public class CardActionDiscardPileToBuildingPile implements CardAction {
    private int discardPileIndex;
    private int buildingPileIndex;

    public CardActionDiscardPileToBuildingPile(int discardPileIndex, int buildingPileIndex) {
        this.discardPileIndex = discardPileIndex;
        this.buildingPileIndex = buildingPileIndex;
    }

        @Override
    public void execute(Game game, Player player) {
        DiscardPile discardPile = game.getDiscardPile(player, discardPileIndex);
        model.Card card = discardPile.removeTopCard();
        game.getBuildingPile(buildingPileIndex).addCard(card);
    }

    @Override
    public boolean isValid(Game game, Player player) {
        DiscardPile discardPile = game.getDiscardPile(player, discardPileIndex);
        if (discardPile.isEmpty()) {
            return false;
        }

        if (discardPileIndex < 0 || discardPileIndex >= NUM_DISCARD_PILES) {
            return false;
        }
        if (buildingPileIndex < 0 || buildingPileIndex >= NUM_BUILDING_PILES) {
            return false;
        }
        model.Card topCard = discardPile.topCard();
        model.BuildingPile pile = game.getBuildingPile(buildingPileIndex);
        return pile.canAddCard(topCard);
    }
}
