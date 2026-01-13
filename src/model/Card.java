package model;

public class Card {
    public enum CardColor {RED, BLUE, GREEN, SKIPBO}
    private CardColor cardColor;
    private Integer number;

    // Constructor for normal cards
    public Card(CardColor cardColor, int number){
        this.cardColor = cardColor;
        this.number = number;
    }

    // Constructor for SkipBo cards because they dont have a number
    public Card(CardColor cardColor){
        this.cardColor = cardColor;
        this.number = null;
    }

    public CardColor getCardColor() {
        return cardColor;
    }

    public Integer getNumber() {
        return number;
    }

    // Checks if card is a SkipBo card
    public Boolean isSkipBo() {
        return cardColor == CardColor.SKIPBO;
    }

}
