package model;

import static model.GameConstants.*;

public class CardActionStockPileToBuildingPile implements CardAction{
    private int buildingPileIndex;

    public CardActionStockPileToBuildingPile(int buildingPileIndex) {
        this.buildingPileIndex = buildingPileIndex;
    }

    public int getBuildingPileIndex() {
        return buildingPileIndex;
    }


    @Override
    public void execute(model.Game game, Player player) {
        model.StockPile stockPile = game.getStockPile(player);
        model.Card card = stockPile.removeTopCard();
        game.getBuildingPile(buildingPileIndex).addCard(card);
    }

    @Override
    public boolean isValid(model.Game game, Player player) {
        model.StockPile stockPile = game.getStockPile(player);
        if (stockPile.isEmpty()) {
            return false;
        }

        if (buildingPileIndex < 0 || buildingPileIndex >= NUM_BUILDING_PILES) {
            return false;
        }

        model.Card topCard = stockPile.topCard();
        model.BuildingPile pile = game.getBuildingPile(buildingPileIndex);
        return pile.canAddCard(topCard);
    }
}
