package protocol.server;

import protocol.Command;

import protocol.common.position.Position;

/**
 * Client Command
 * Informs all players in a game about a play a player did
 */
public class Play implements Command {

    public static final String COMMAND = "PLAY";
    public String player;
    public Position from;
    public Position to;


    public Play(Position from, Position to, String player){
        this.from = from;
        this.to = to;
        this.player = player;
    }

    @Override
    public String transformToProtocolString() {
        return COMMAND
                + SEPERATOR
                + player
                + SEPERATOR
                + this.from
                + SEPERATOR
                + this.to;
    }
}
