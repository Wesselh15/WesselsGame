package model;

import java.util.ArrayList;
import java.util.List;

public class CardGenerator {

    public static List<model.Card> generateCards(){
        List<model.Card> cards = new ArrayList<model.Card>();

        // add Skip-Bo cards (wildcards, no number)
        for (int i = 0; i < 18; i++){
            cards.add(new model.Card(model.Card.CardColor.SKIPBO));
        }

        // add Number Cards

        model.Card.CardColor[] colors = new model.Card.CardColor[]{
                model.Card.CardColor.GREEN, model.Card.CardColor.RED, model.Card.CardColor.BLUE};
        for (int x = 0; x < 3; x++){
            for (int i = 0; i < colors.length; i++){
                for (int j = 0; j < 12; j++){
                    cards.add(new model.Card(colors[i], j + 1));
                }
            }
        }

        return cards;
    }
}
