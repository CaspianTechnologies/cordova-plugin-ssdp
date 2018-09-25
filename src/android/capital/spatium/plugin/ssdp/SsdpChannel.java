package capital.spatium.plugin.ssdp;

import java.io.Closeable;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.SocketAddress;
import java.nio.channels.UnsupportedAddressTypeException;

public class SsdpChannel implements Closeable, AutoCloseable {
    public static final InetSocketAddress SSDP_MCAST_ADDRESS = new InetSocketAddress(
            "239.255.255.250",
            1900);

    private final DatagramSocket unicastSocket;
    private final MulticastSocket multicastSocket;

    SsdpChannel() throws IOException, UnsupportedAddressTypeException {
        this.unicastSocket = createUnicastSocket();
        this.multicastSocket = createMulticastSocket();
    }

    public void sendMulticast(SsdpMessage message) throws IOException {
        byte[] bytes = message.toBytes();
        multicastSocket.send(new DatagramPacket(bytes, bytes.length, SSDP_MCAST_ADDRESS));
    }

    public void sendUnicast(SsdpMessage message, DatagramPacket packet) throws IOException {
        byte[] bytes = message.toBytes();
        SocketAddress address = packet.getSocketAddress();
        unicastSocket.send(new DatagramPacket(bytes, bytes.length, address));
    }

    public void receiveMulticast(DatagramPacket msgPacket) throws IOException {
        multicastSocket.receive(msgPacket);
    }

    public void receiveUnicast(DatagramPacket msgPacket) throws IOException {
        unicastSocket.receive(msgPacket);
    }

    public void joinGroup(NetworkInterface networkIf) throws IOException {
        this.multicastSocket.joinGroup(SSDP_MCAST_ADDRESS, networkIf);
    }

    public void close() {
        if (!multicastSocket.isClosed()) {
            try {
                multicastSocket.leaveGroup(SSDP_MCAST_ADDRESS.getAddress());
                multicastSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        if (!unicastSocket.isClosed()) {
            unicastSocket.close();
        }
    }

    private DatagramSocket createUnicastSocket() {
        try {
            ServerSocket s = new ServerSocket(0);
            int randomPort = s.getLocalPort();
            s.close();
            return new DatagramSocket(randomPort);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private MulticastSocket createMulticastSocket() throws IOException {
        return new MulticastSocket(SSDP_MCAST_ADDRESS.getPort());
    }

}
