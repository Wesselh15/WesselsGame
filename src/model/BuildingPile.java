package model;

import java.util.ArrayList;
import java.util.List;

public class BuildingPile {
    private List<Card> cards;

    public BuildingPile() {
        this.cards = new ArrayList<>();
    }

    // Checks if a card can be added to this building pile
    public boolean canAddCard(Card card) {
        if (card == null) {
            return false;
        }

        // Logic for SkipBo cards, because they can always be added
        if (card.isSkipBo()) {
            return !isFull();
        }

        // If the pile is empty, only cards with integer 1 is allowed
        if (isEmpty()) {
            return card.getNumber() == 1;
        }

        // If pile is full (reached 12), no more cards can be added
        if (isFull()) {
            return false;
        }

        // Check if card is the next sequential number
        int expectedNumber = getTopValue() + 1;
        return card.getNumber() == expectedNumber;
    }

    // Add a card to the building pile
    public void addCard(Card card) {
        cards.add(card);
    }

    // Get the current top value of the pile. This is the same as the size of the pile, because the list has to be sequentially.
    private int getTopValue() {
        if (isEmpty()) {
            return 0;
        }
        return cards.size();
    }

    // checks if the list is empty
    public boolean isEmpty() {
        return cards.isEmpty();
    }

    // Check if the pile is full reached size 12
    public boolean isFull() {
        return cards.size() >= 12;
    }

    // Clears the pile when its full
    public void clear() {
        cards.clear();
    }

    // Gets the amount of cards in the pile
    public int size() {
        return cards.size();
    }
}
