package model;


import java.util.ArrayList;
import java.util.List;

public class DiscardPile {
    private List<Card> cards = new ArrayList<>();

    public void addCard(model.Card card){
        cards.add(card);
    }

    public model.Card topCard(){
        if (isEmpty()) {
            return null;
        }
        return cards.get(cards.size() - 1);
    }

    public model.Card removeTopCard(){
        if (isEmpty()) {
            return null;
        }
        return cards.remove(cards.size() - 1);
    }

    public boolean isEmpty(){
        return cards.isEmpty();
    }

    /**
     * Wist alle kaarten uit de discard pile (gebruikt voor nieuwe ronde)
     */
    public void clear() {
        cards.clear();
    }
}
