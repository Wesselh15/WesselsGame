package Controller;

import java.io.BufferedReader;
import java.io.IOException;

public class ServerHandler implements Runnable {
    private BufferedReader in;
    private Controller.Client client;
    private boolean running;



    public ServerHandler(BufferedReader in, Controller.Client client) {
        this.in = in;
        this.client = client;
        this.running = true;
    }

    @Override
    public void run() {
        try {
            String message;
            // Keep reading messages until connection is lost or we stop
            while (running && (message = in.readLine()) != null) {
                handleServerMessage(message.trim());
            }
        } catch (IOException e) {
            if (running) {
                System.err.println("Connection to server lost: " + e.getMessage());
            }
        }
    }

    private void handleServerMessage(String message) {
        if (message.isEmpty()) {
            return;
        }
    }

    public void stop() {
        running = false;
    }
}
