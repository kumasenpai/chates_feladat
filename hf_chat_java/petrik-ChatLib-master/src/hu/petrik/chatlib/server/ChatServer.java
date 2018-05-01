package hu.petrik.chatlib.server;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Chat szerver osztály, amely hálózaton keresztül több klienst képes összekapcsolni
 * és kezelni.
 * A szerver folyamatosan logol a konzolra, egy grafikus alkalmazás esetén ezt figyelmen kívül lehet hagyni.
 */
public class ChatServer {
    private final String address;
    private final int port;
    // Sima ArrayList helyett ezt a szálbiztos alternatívát használjuk.
    // Így a send függvényt nem kell külön szinkronizálni.
    private final List<Client> clients = new CopyOnWriteArrayList<>();
    private ServerSocket socket;
    
    /**
     * Létrehoz egy szerver objektumot a megadott beállításokkal.
     * A kapcsolatokra még nem kezd el figyelni, ehhez a {@link #listen()}
     * metódust kell használni.
     * 
     * @param address A szerver ehhez a címhez tartozó interface-re fog bind-olni.
     *                Ha minden interfészre szeretnénk, akkor adjunk meg "0.0.0.0"-t
     *                vagy [::]-t.
     * @param port Ezen a port-on induljon el a szerver.
     */
    public ChatServer(String address, int port) {
        this.address = address;
        this.port = port;
    }
    
    /**
     * A szerver elindul, és elkezdni figyelni a beérkező kapcsolatokat.
     * A függény blokkol, ezért célszerű egy háttérszálon elindítani.
     * Csak kivétellel tér vissza, még sikeres befejezéskor is.
     * 
     * @throws IOException 
     */
    public void listen() throws IOException {
        socket = new ServerSocket(port, -1, InetAddress.getByName(address));
        Socket connection;
        
        // Csak kivétellel tudunk kilépni - ez a tervezett működés.
        while (true) {
            connection = socket.accept();
            
            Client client = new Client(connection, this);
            clients.add(client);
            
            // Minden kliensnek indítsunk egy új szálat, mert a start()
            // függvényük blokkoló
            Thread t = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        System.out.println("Client connected");
                        client.start();
                    }
                    catch (IOException ex) {
                        System.out.println("Client error");
                    }
                    finally {
                        System.out.println("Client disconnected");
                        clients.remove(client);
                    }
                }
            });
            t.start();
            
        }
    }
    
    /**
     * Elküldi a megadott üzenetet az összes kliensnek.
     * A függvény szálbiztos.
     * 
     * @param message Az üzenet
     * @throws IOException 
     */
    void send(String message) throws IOException {
        for (Client client: clients) {
            client.send(message);
        }
    }
    
    /**
     * Leállítja a szervert és bezárja a hálózati kapcsolatokat.
     * 
     * @throws IOException 
     */
    public void stop() throws IOException {
        socket.close();
        for (Client c: clients) {
            c.stop();
        }
    }
    
    /**
     * Teszt szerver indítása a konzolról.
     * Alapból minden címre bind-ol, a 45000-es portra.
     * Ez egy egyszerű példa a szerver használatára.
     * 
     * @param args
     * @throws IOException
     * @throws InterruptedException 
     */
    public static void main(String[] args) throws IOException, InterruptedException {
        ChatServer server = new ChatServer("0.0.0.0", 45000);
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    server.listen();
                } catch (IOException ex) {
                    System.out.println("Server stopped");
                }
            }
        });
        t.start();
        
        System.out.println("Press ENTER to stop the server");
        Scanner sc = new Scanner(System.in);
        sc.nextLine();
        server.stop();
        Thread.sleep(5000);
    }
}
