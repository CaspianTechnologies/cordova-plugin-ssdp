package capital.spatium.plugin.ssdp;


import java.net.DatagramPacket;

public interface SsdpPacketListener {
    void received(DatagramPacket packet);
}
