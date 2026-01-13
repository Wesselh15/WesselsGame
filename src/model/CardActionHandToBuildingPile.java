package model;

public class CardActionHandToBuildingPile implements CardAction {
    private model.Card card;
    private int buildingPileIndex;

    public CardActionHandToBuildingPile(model.Card card, int buildingPileIndex) {
        this.card = card;
        this.buildingPileIndex = buildingPileIndex;
    }

    public int getBuildingPileIndex() {
        return buildingPileIndex;
    }


    @Override
    public void execute(Game game, Player player) {
        game.getHand(player).remove(card);
        game.getBuildingPile(buildingPileIndex).addCard(card);
    }

    @Override
    public boolean isValid(Game game, Player player) {
        if (!game.getHand(player).contains(card)) {
            return false;
        }
        if (buildingPileIndex < 0 || buildingPileIndex >=4) {
            return false;
        }
        model.BuildingPile pile = game.getBuildingPile(buildingPileIndex);
        return pile.canAddCard(card);
    }
}
