package model;

import java.util.ArrayList;
import java.util.List;

public class StockPile {
    private List<model.Card> cards;

    public StockPile(List<model.Card> cards) {
        this.cards = new ArrayList<>(cards);
    }

    public model.Card topCard() {
        if (isEmpty()) {
            return null;
        }
        return cards.get(cards.size() - 1);
    }

    public model.Card removeTopCard() {
        if (isEmpty()) {
            return null;
        }
        return cards.remove(cards.size() - 1);
    }

    public boolean isEmpty() {
        return cards.isEmpty();
    }

    public int size() {
        return cards.size();
    }
}
