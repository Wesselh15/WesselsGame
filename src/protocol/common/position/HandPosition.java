package protocol.common.position;

import protocol.Command;
import protocol.common.Card;

public class HandPosition implements Position {

    private final static String REP = "H";
    private Card card;

    public HandPosition(Card card){
        this.card = card;
    }

    public Card getCard() {
        return card;
    }

    public String toString(){
        return REP + Command.VALUE_SEPERATOR + (card == null ? "X" : card.toString());
    }
}
