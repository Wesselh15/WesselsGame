package protocol.common;

import java.net.ProtocolException;

public class Card {
    private Integer number;

    /**
     * Method for creating an card representing a number form 1 to 12
     * @param number a number between 1 and 12
     * @throws ProtocolException if the number is < 1 or > 12
     */
    public Card(int number) throws ProtocolException {
        if(number < 1 || number > 12)
            throw new ProtocolException("Invalid card number");

        this.number = number;
    }

    /**
     * Method for creating a card representing a Skip-BO
     */
    public Card(){
        this.number = null;
    }

    public String toString(){
        if(number == null) return "SB";
        else return "" + number;
    }

    public Integer getNumber() {
        return number;
    }
}
