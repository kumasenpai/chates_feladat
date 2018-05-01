package hu.petrik.chatlib.client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

/**
 * Chat kliens osztály, amely segítségével egy chat szerverhez lehet kapcsolódni.
 */
public class ChatClient {
    private final String address;
    private final int port;
    private Socket socket;
    private OutputStreamWriter writer;
    private BufferedReader reader;

    /**
     * Létrehozza a chat klienst a megadott beállításokkal.
     * A kapcsolódást nem végzi el, ahhoz a {@link #connect()} függvényt kell meghívni.
     * 
     * @param address A szerver web- vagy IP címe
     * @param port A szerver portja
     */
    public ChatClient(String address, int port) {
        this.address = address;
        this.port = port;
    }
    
    /**
     * Kapcsolódik beállított szerverhez egy háttér szálon.
     * A függvény azonnal visszatér. Kapcsolódási hiba esetén nem itt, hanem
     * a beállított {@link MessageReceivedListener#error(java.io.IOException) }
     * függvényében jelzi.
     * 
     * @throws IOException 
     */
    public void connect() throws IOException {
        socket = new Socket(InetAddress.getByName(address), port);
        
        // Az írás/olvasás hasonló reader/writer-en keresztül történik, mint pl. a fájlba írás/olvasás.
        // Mivel a kapcsolat kétirányú, mindkettőből van.
        OutputStream stream = socket.getOutputStream();
        writer = new OutputStreamWriter(stream);
        reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        startListening();
    }
    
    /**
     * Elküldi a megadott üzenetet.
     * A függvény nem szálbiztos!
     * 
     * @param message Az üzenet (vagy parancs)
     * @throws IOException 
     */
    public void sendMessage(String message) throws IOException {
        writer.write(message + "\n");
        writer.flush();

        if ("/q".equals(message)) {
            socket.close();
            return;
        }
    }
    
    /**
     * Elküldi a szervernek a kilépési parancsot, majd bezárja a hálózati kapcsolatot.
     * A függvény meghívása után minden {@link #sendMessage(java.lang.String)} hívás
     * meghiúsul. A meghíváskor a hibakezelő eseményben kaphatunk egy {@link IOException} kivételt.
     * 
     * @throws IOException 
     */
    public void close() throws IOException {
        sendMessage("/q");
    }
    
    /**
     * Egy új háttérszálon elindítja az üzenetkre való figyelést,
     * majd továbbítja ezeket az beállított eseménykezelőkhöz.
     * 
     * @throws IOException 
     */
    private void startListening() throws IOException {
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    // Végtelen ciklus, mint egy fájlból olvasáskor
                    String line;
                    while ((line = reader.readLine()) != null) {
                        // A üzenetet továbbítjuk az összes eseménykezelőnek
                        for (MessageReceivedListener listener: receivedListeners) {
                            listener.messageReceived(line);
                        }
                    }
                }
                catch (IOException ex) {
                    // A hibát továbbítjuk az összes eseménykezelőnek
                    for (MessageReceivedListener listener: receivedListeners) {
                        listener.error(ex);
                    }
                }
            }
        });
        thread.start();
    }
    
    /**
     * Ezt az interfészt kell implementálni, ha egy objektum üzeneteket szeretne
     * fogadni.
     * Az események a háttérszálon fognak meghívódni, nem az UI szálon!
     */
    public interface MessageReceivedListener {
        /**
         * Kezeli az "Új üzenet" eseményt.
         * 
         * @param message Az üzenet
         */
        public void messageReceived(String message);
        /**
         * Kezeli a "Hiba történt" eseményt.
         * Ha a kliens meghívja ezt a függvényt, feltételezhetjük, hogy a kapcsolat
         * a továbbiakban használhatatlan.
         * 
         * @param ex A hibát jelető kivétel.
         */
        public void error(IOException ex);
    }
    private List<MessageReceivedListener> receivedListeners = new ArrayList<>();
    
    /**
     * Felírja a paramétert az "Új üzenet" és "Hiba történt" eseményekre.
     * 
     * @param listener Az eseménykezelő objektum
     */
    public void addMessageReceivedListener(MessageReceivedListener listener) {
        receivedListeners.add(listener);
    }
    /**
     * Törli a paramétert az eseménykezelők közül.
     * 
     * @param listener Az eseménykezelő objektum
     */
    public void removeMessageReceivedListener(MessageReceivedListener listener) {
        receivedListeners.remove(listener);
    }
    
    
    /**
     * Teszt metódus, kiírja a konzolra a szervertől kapott üzeneteket.
     * Ne használjuk együtt az {@link MessageReceivedListener} eseménykezelőkkel!
     * 
     * @throws IOException 
     * @deprecated 
     */
    @Deprecated
    private void writeToConsole() throws IOException {
        String line;
        while ((line = reader.readLine()) != null) {
            System.out.println(line);
        }
    }
    
    /**
     * Teszt metódus, a konzolról beolvas soronként egy üzenetet, és továbbítja a szervernek.
     * Ne használjuk együtt a {@link #sendMessage(java.lang.String) } üzenetküldő
     * függvénnyel!
     * 
     * @throws IOException
     * @deprecated
     */
    @Deprecated
    private void readFromConsole() throws IOException {
        Scanner sc = new Scanner(System.in);
        String line;
        while ((line = sc.nextLine()) != null) {
            writer.write(line + "\n");
            writer.flush();
            
            if ("/q".equals(line)) {
                socket.close();
                return;
            }
        }
    }
    
    public static void main(String[] args) throws IOException {
        ChatClient client = new ChatClient("localhost", 45000);
        client.connect();
        
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    client.writeToConsole();
                } catch (IOException ex) {
                    System.out.println("Disconnected");
                }
                System.exit(0);
            }
        });
        System.out.println("Type to chat, type /q to quit");
        t.start();
        
        client.readFromConsole();
    }
}
