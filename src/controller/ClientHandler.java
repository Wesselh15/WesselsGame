package controller;

import java.io.*;
import java.net.*;

public class ClientHandler implements Runnable {
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private Server server;
    private String clientName;
    private boolean running; // checks which handlers are still running
    String message;

    public ClientHandler(Socket socket, Server server){
        this.socket = socket;
        this.server = server;
        this.running = true;
    }



    @Override
    public void run() {
        try {
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);
            while (running && (message = in.readLine()) != null) {
                handleClientMessage(message.trim());
            }
        } catch (IOException e) {
            System.err.println("Client handler error: " + e.getMessage());
        } finally {
            cleanup();
        }
    }
    private void handleClientMessage(String message) {
        if (message.isEmpty()) {
            return;
        }
    }

    public void sendMessage(String message) {
        if (out != null) {
            out.println(message);
        }
    }

    public String getClientName() {
        return clientName;
    }

    public void setClientName(String name) {
        this.clientName = name;
    }

    private void cleanup() {
        running = false;
        // Tell the server this client disconnected
        server.removeClient(this);
        // Close all connections
        try {
            if (in != null) in.close();
            if (out != null) out.close();
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException e) {
            System.err.println("Error cleaning up: " + e.getMessage());
        }
    }

    public void stop() {
        running = false;
    }

}