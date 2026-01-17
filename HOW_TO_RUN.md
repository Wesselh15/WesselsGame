# Skip-Bo Multiplayer Game - Handleiding

## Overzicht

Dit is een multiplayer implementatie van Skip-Bo volgens het protocol. De code is geschreven voor beginnende Java programmeurs.

## Structuur

```
src/
├── controller/          # Netwerk & game management
│   ├── Server.java      # Server die clients accepteert
│   ├── GameManager.java # Beheert 1 game instance
│   ├── ClientHandler.java   # Verwerkt berichten van 1 client
│   ├── ServerHandler.java   # Toont server berichten aan client
│   ├── Client.java          # Menselijke speler client
│   └── AIClient.java        # Computer speler client
├── model/               # Game logica (al gegeven)
│   ├── Game.java        # Hoofdgame logica
│   ├── Player.java      # Speler
│   ├── Card.java        # Kaart
│   ├── CardAction*.java # Move acties
│   └── *Pile.java       # Verschillende stapels
├── protocol/            # Protocol specificatie (NIET AANPASSEN)
│   └── ...
└── view/                # Gebruikersinterface
    └── GameView.java    # Simpele text UI
```

## Hoe werkt het?

### 1. Protocol Package (GEGEVEN - NIET AANPASSEN)
- **Doel**: Gemeenschappelijk formaat zodat verschillende groepen met elkaar kunnen spelen
- **Gebruik**: Alleen voor het maken van berichten die over het netwerk gaan
- Je **maakt** berichten met: `new Hello(...).transformToProtocolString()`
- Je **leest** berichten door zelf te parsen met `split("~")`

### 2. Game Logica (model package)
- Dit is al gegeven en bevat alle spelregels
- `Game.java` weet alles over beurten, kaarten, en winnen
- `doMove()` valideert en voert moves uit

### 3. GameManager
- **Rol**: Vertaallaag tussen netwerk en game
- **Doet**:
  - Houdt bij welke client welke speler is
  - Converteert protocol berichten → CardAction objecten
  - Converteert game state → protocol berichten
  - Broadcast updates naar alle clients
- **Doet NIET**: Game regels (dat doet Game.java)

### 4. Client/Server Flow

```
Client 1                Server                  Client 2
   |                      |                        |
   |---HELLO~Alice~------>|                        |
   |                      |<----HELLO~Bob~---------|
   |<--WELCOME~Alice~-----|                        |
   |<--WELCOME~Bob~-------|----WELCOME~Bob~------->|
   |                      |                        |
   |                  [Game start]                 |
   |<--START~Alice,Bob----|----START~Alice,Bob---->|
   |<--HAND~5,7,3,12,SB---|----HAND~8,2,4,11,6---->|
   |<--TURN~Alice~--------|----TURN~Alice~-------->|
   |                      |                        |
   |---PLAY~H.5~B.0------>|                        |
   |                   [Validate]                  |
   |<--PLAY~Alice~...-----|----PLAY~Alice~...----->|
   |<--HAND~...-----------|----HAND~...----------->|
```

## Starten

### Stap 1: Compileren

```bash
javac -d bin -sourcepath src src/controller/*.java src/model/*.java src/protocol/**/*.java src/view/*.java
```

### Stap 2: Server starten

Open terminal 1:
```bash
java -cp bin controller.Server
```

Je ziet:
```
Server started on port 5555
Waiting for clients...
```

### Stap 3: Menselijke speler

Open terminal 2:
```bash
java -cp bin controller.Client
```

Je wordt gevraagd om een naam:
```
Enter your player name: Alice
```

### Stap 4: Tweede speler (AI of mens)

**Optie A - AI speler:**
Open terminal 3:
```bash
java -cp bin controller.AIClient AI_Bot
```

**Optie B - Tweede menselijke speler:**
Open terminal 3:
```bash
java -cp bin controller.Client
# Enter naam: Bob
```

### Stap 5: Game speelt automatisch!

De game start zodra 2 spelers verbonden zijn. Je ziet:
```
>>> GAME STARTING!
Players: Alice, AI_Bot

>>> It's Alice's turn <<<

--- YOUR HAND ---
  Cards: 5, 7, 3, 12, SB
```

## Commands

Als het jouw beurt is:

```bash
HAND           # Bekijk je hand
TABLE          # Bekijk de tafel (nog niet geïmplementeerd in view)
PLAY~H.5~B.0   # Speel kaart 5 van hand naar building pile 0
PLAY~H.3~D.0   # Discard kaart 3 (beëindigt je beurt!)
PLAY~S~B.1     # Speel van stock pile naar building pile 1
```

## Belangrijke Concepten

### Posities
- `H.5` = Hand, kaart met waarde 5
- `H.SB` = Hand, Skip-Bo kaart
- `S` = Stock pile (je persoonlijke stapel)
- `B.0` = Building pile 0 (gedeeld, 0-3)
- `D.0` = Discard pile 0 (persoonlijk, 0-3)

### Beurt systeem
- Je kan meerdere kaarten spelen in één beurt
- Je beurt eindigt wanneer je een kaart naar een discard pile speelt (`PLAY~H.X~D.Y`)
- Dan is de volgende speler aan de beurt

### Winnen
- Eerste speler die zijn stock pile leeg heeft, wint
- Je krijgt dan 100 punten

## Debugging Tips

### Server ziet geen clients
- Check of de server draait op poort 5555
- Check firewall instellingen

### "Name already in use"
- Kies een andere naam
- Of herstart de server

### "Invalid move"
- Check of het jouw beurt is (`>>> It's YourName's turn <<<`)
- Check of de kaart waarde klopt met je hand
- Building piles moeten sequentieel zijn (1→2→3...→12)

### Game start niet
- Standaard wacht de server op 2 spelers
- Gebruik `GAME~3` om 3 spelers te eisen (moet voor game start)

## Code Wijzigen

### Nieuwe command toevoegen

1. Voeg toe aan `ClientHandler.handleClientMessage()`:
```java
else if (command.equals("MYCMD")) {
    // Handle command
}
```

2. Voeg toe aan `ServerHandler.handleServerMessage()` (voor server→client):
```java
else if (command.equals("MYCMD")) {
    view.showMyCommand(parts[1]);
}
```

### Aantal spelers wijzigen

In `GameManager.java`, regel 30:
```java
this.requiredPlayers = 4; // Wijzig naar gewenst aantal
```

## Veelgestelde Vragen

**Q: Kan ik andere groepen connecten op mijn server?**
A: Ja! Zolang ze hetzelfde protocol gebruiken en naar jouw IP:5555 connecten.

**Q: Moet ik de protocol package aanpassen?**
A: **NEE!** De protocol package is gedeeld tussen groepen. Alleen aanpassen als je docent dit zegt.

**Q: Waarom is de code zo simpel/lang?**
A: Dit is bewust voor beginnende programmeurs. Basic loops en if-statements in plaats van advanced Java features.

**Q: Hoe voeg ik een GUI toe?**
A: Vervang `GameView.java` met JavaFX/Swing. De rest blijft hetzelfde!

## Handige Wijzigingen

### AI sneller/langzamer maken
In `AIClient.java`, regel 101:
```java
Thread.sleep(1000); // Milliseconden tussen moves
```

### Meer output tijdens spel
In `GameManager.java`, voeg toe:
```java
System.out.println("DEBUG: " + message);
```

### Server op andere poort
In `Server.java`, regel 25:
```java
int port = 8080; // Wijzig poort
```
