package capital.spatium.plugin.ssdp;

import java.io.Closeable;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.NetworkInterface;
import java.net.SocketAddress;
import java.nio.channels.UnsupportedAddressTypeException;

public class SsdpService implements Closeable, AutoCloseable {
    private final SsdpChannel channel;
    private final SsdpPacketListener listener;
    private final Thread multicastThread;

    public SsdpService(NetworkInterface networkInterface, SsdpPacketListener listener) throws IOException, UnsupportedAddressTypeException {
        this.listener = listener;
        this.channel = buildChannel(networkInterface);
        this.multicastThread = new Thread(new MulticastReceiver(), "SSDP_multicast_receiver");
    }

    public SsdpChannel getChannel() {
        return channel;
    }

    public void listen() throws IllegalStateException {
        if (multicastThread.isAlive() || multicastThread.isInterrupted()) {
            throw new IllegalStateException("the ssdp multicast service is already running");
        }
        multicastThread.start();
    }

    @Override
    public void close() {
        channel.close();
        multicastThread.interrupt();
    }

    private SsdpChannel buildChannel(NetworkInterface networkInterface) throws IOException {
        SsdpChannel channel = null;
        try {
            channel = new SsdpChannel(networkInterface);
        } catch (IOException e) {
            e.printStackTrace();
        } catch (UnsupportedAddressTypeException e) {
            e.printStackTrace();
        }
        return channel;
    }

    public void sendMulticast(SsdpMessage message) throws IOException {
        channel.sendMulticast(message);
    }

    public void sendUnicast(SsdpMessage message, DatagramPacket packet) throws IOException {
        channel.sendUnicast(message, packet);
    }

    private final class MulticastReceiver implements Runnable {
        @Override
        public void run() {
            while (!multicastThread.isInterrupted()) {
                if (channel == null) {
                    return;
                }

                byte[] buf = new byte[256];
                DatagramPacket msgPacket = new DatagramPacket(buf, buf.length);
                try {
                    channel.receiveMulticast(msgPacket);
                } catch (IOException e) {}

                String s = new String(msgPacket.getData());
                if (listener != null) {
                    listener.received(msgPacket);
                }
            }
        }
    }
}
