import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;

class SocketClient {
    public static void sendToServer(String message) throws IOException {
        sendToServer(message, "192.168.1.10", 1234);
    }

    public static void sendToServer(String message, String ip, int port) throws IOException {
        BufferedReader inFromUser = new BufferedReader(new InputStreamReader(System.in));
        Socket clientSocket = new Socket(ip, port);
        DataOutputStream outToServer = new DataOutputStream(clientSocket.getOutputStream());
        outToServer.writeBytes(message);
        clientSocket.close();
    }
}