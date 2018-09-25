package capital.spatium.plugin.ssdp;

import java.io.Closeable;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.NetworkInterface;
import java.nio.channels.UnsupportedAddressTypeException;

public class SsdpService implements Closeable, AutoCloseable {
    private final SsdpChannel mChannel;
    private final Thread mMulticastThread;
    private final Thread mUnicastThread;

    private Consumer<DatagramPacket> mMulticastConsumer = null;
    private Consumer<DatagramPacket> mUnicastConsumer = null;

    public void setMulticastConsumer(Consumer<DatagramPacket> consumer) {
        mMulticastConsumer = consumer;
    }

    public void setUnicastConsumer(Consumer<DatagramPacket> consumer) {
        mUnicastConsumer = consumer;
    }

    SsdpService(
        NetworkInterface networkInterface,
        boolean listenUnicast
    ) throws IOException, UnsupportedAddressTypeException {
        mChannel = SsdpService.buildChannel(networkInterface);

        if (mChannel == null) {
            throw new IOException("Channel is null");
        }

        mMulticastThread = new Thread(new MulticastReceiver(mChannel), "SSDP_multicast_receiver");
        mUnicastThread =
            listenUnicast
            ? new Thread(new UnicastReceiver(mChannel), "SSDP_unicast_receiver")
            : null;
    }

    public SsdpChannel getChannel() {
        return mChannel;
    }

    public void listen() throws IllegalStateException {
        if (mMulticastThread.isAlive() || mMulticastThread.isInterrupted()) {
            throw new IllegalStateException("the ssdp multicast service is already running");
        }
        mMulticastThread.start();

        if (mUnicastThread != null) {
            if (mUnicastThread.isAlive() || mUnicastThread.isInterrupted()) {
                throw new IllegalStateException("the ssdp unicast service is already running");
            }
            mUnicastThread.start();
        }
    }

    @Override
    public void close() {
        mChannel.close();
        mMulticastThread.interrupt();
        if (mUnicastThread != null) {
            mUnicastThread.interrupt();
        }
    }

    private static SsdpChannel buildChannel(NetworkInterface networkInterface) {
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
        mChannel.sendMulticast(message);
    }

    public void sendUnicast(SsdpMessage message, DatagramPacket packet) throws IOException {
        mChannel.sendUnicast(message, packet);
    }

    private final class MulticastReceiver implements Runnable {
        final SsdpChannel mChannel;

        MulticastReceiver(SsdpChannel channel) {
            mChannel = channel;
        }

        @Override
        public void run() {
            while (!Thread.currentThread().isInterrupted()) {
                byte[] buf = new byte[256];
                DatagramPacket msgPacket = new DatagramPacket(buf, buf.length);
                try {
                    mChannel.receiveMulticast(msgPacket);

                    if (mMulticastConsumer != null) {
                        mMulticastConsumer.accept(msgPacket);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private final class UnicastReceiver implements Runnable {
        final SsdpChannel mChannel;

        UnicastReceiver(SsdpChannel channel) {
            mChannel = channel;
        }

        @Override
        public void run() {
            while (!Thread.currentThread().isInterrupted()) {
                byte[] buf = new byte[256];
                DatagramPacket msgPacket = new DatagramPacket(buf, buf.length);
                try {
                    mChannel.receiveUnicast(msgPacket);

                    if (mUnicastConsumer != null) {
                        mUnicastConsumer.accept(msgPacket);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
