package capital.spatium.plugin.ssdp;

import java.io.Closeable;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.net.SocketAddress;
import java.net.SocketException;
import java.nio.channels.UnsupportedAddressTypeException;

public class SsdpChannel implements Closeable, AutoCloseable {
    public static final InetSocketAddress SSDP_MCAST_ADDRESS = new InetSocketAddress(
            "239.255.255.250",
            1900);
    private static final int SSPD_UCAST_PORT = 1901;

    private final DatagramSocket unicastSocket;
    private final MulticastSocket multicastSocket;

    public SsdpChannel(NetworkInterface networkIf) throws IOException, UnsupportedAddressTypeException {
        this.unicastSocket = createUnicastSocket();
        this.multicastSocket = createMulticastSocket(networkIf);
    }

    public void sendMulticast(SsdpMessage message) throws IOException {
        byte[] bytes = message.toBytes();
        multicastSocket.send(new DatagramPacket(bytes, bytes.length, SSDP_MCAST_ADDRESS));
    }

    public void sendUnicast(SsdpMessage message, DatagramPacket packet) throws IOException {
        byte[] bytes = message.toBytes();
        packet.setPort(SSPD_UCAST_PORT);
        SocketAddress address = packet.getSocketAddress();
        unicastSocket.send(new DatagramPacket(bytes, bytes.length, address));
    }

    public void receiveMulticast(DatagramPacket msgPacket) throws IOException {
        multicastSocket.receive(msgPacket);
    }

    public void receiveUnicast(DatagramPacket msgPacket) throws IOException {
        unicastSocket.receive(msgPacket);
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

    private DatagramSocket createUnicastSocket() throws SocketException {
        DatagramSocket socket = new DatagramSocket(SSPD_UCAST_PORT);
        return socket;
    }

    private MulticastSocket createMulticastSocket(NetworkInterface networkIf)
            throws IOException {
        MulticastSocket socket = new MulticastSocket(SSDP_MCAST_ADDRESS.getPort());
        socket.joinGroup(SSDP_MCAST_ADDRESS, networkIf);
        return socket;
    }

}
