package capital.spatium.plugin.ssdp;


import android.content.Context;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import capital.spatium.plugin.ssdp.SsdpChannel;
import capital.spatium.plugin.ssdp.SsdpMessage;
import capital.spatium.plugin.ssdp.SsdpMessageType;
import capital.spatium.plugin.ssdp.SsdpPacketListener;
import capital.spatium.plugin.ssdp.SsdpService;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Date;

import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;
import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaWebView;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;


public class Ssdp extends CordovaPlugin {

    private Thread thread;
    private SsdpService ssdpService = null;
    private String target = DEFAULT_TARGET;

    private static final String TAG = "Cordova SSDP";
    private static final String DEFAULT_TARGET = "spatandroid";
    private static final String SSDP_ADDRESS = 
            SsdpChannel.SSDP_MCAST_ADDRESS.getAddress().getHostName() 
            + ":" 
            +  SsdpChannel.SSDP_MCAST_ADDRESS.getPort();

    @Override
    public void initialize(CordovaInterface cordova, CordovaWebView webView) {
        super.initialize(cordova, webView);
    }

    @Override
    public boolean execute(String action, JSONArray args, final CallbackContext callbackContext) throws JSONException {
        Log.d(TAG, action);
        Log.d(TAG, args.toString());

        if (action.equals("startSearching")) {
            search(callbackContext);
            return true;
        } else if (action.equals("startAdvertising")) {
            advertise(callbackContext);
            return true;
        } else if (action.equals("stop")) {
            stop(callbackContext);
            return true;
        } else if (action.equals("setDiscoveredCallback")) {
            return true;
        } else if (action.equals("setGoneCallback")) {
          return true;
        }
        
        PluginResult result = new PluginResult(PluginResult.Status.INVALID_ACTION);
        callbackContext.sendPluginResult(result);
        return false;
    }

    private void search(final CallbackContext callbackContext) {
      thread = new SearchThread();
      thread.start();
      PluginResult result = new PluginResult(PluginResult.Status.OK);
      callbackContext.sendPluginResult(result);
    }

    private void stop(final CallbackContext callbackContext) {
        thread.interrupt();
        PluginResult result = new PluginResult(PluginResult.Status.OK);
        callbackContext.sendPluginResult(result);
    }

    private void advertise(final CallbackContext callbackContext) {
        thread = new AdvertiseThread();
        thread.start();
        PluginResult result = new PluginResult(PluginResult.Status.OK);
        callbackContext.sendPluginResult(result);
    }

    private static byte[] convertIpAddress(int ip) {
      return new byte[] {
              (byte) (ip & 0xFF),
              (byte) ((ip >> 8) & 0xFF),
              (byte) ((ip >> 16) & 0xFF),
              (byte) ((ip >> 24) & 0xFF)};
    }

    private boolean containsTarget(SsdpMessage message) {
      if (message.getHeader("ST") != null && message.getHeader("ST").equals(target)) {
        return true;
      }
      if (message.getHeader("NT") != null && message.getHeader("NT").equals(target)) {
        return true;
      }
      return false;
    }

    private NetworkInterface getWifiNetworkInterface() throws IOException {
        Context context = this.cordova.getActivity().getApplicationContext();
        WifiManager wifiMgr = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        WifiInfo wifiInfo = wifiMgr.getConnectionInfo();
        int ip = wifiInfo.getIpAddress();
        byte[] b = convertIpAddress(ip);
        return NetworkInterface.getByInetAddress(InetAddress.getByAddress(b));
    }

    class SearchThread extends Thread {
        @Override
        public void run() {
            Log.d(TAG, "search thread started");
            try {
                if (ssdpService != null) {
                    ssdpService.close();
                }

                NetworkInterface ni = getWifiNetworkInterface();
                ssdpService = new SsdpService(ni, searchPacketListener);

                ssdpService.listen();

                SsdpMessage searchMsg = new SsdpMessage(SsdpMessageType.MSEARCH);
                searchMsg.setHeader("HOST", SSDP_ADDRESS);
                searchMsg.setHeader("MAN", "\"ssdp:discover\"");
                searchMsg.setHeader("MX", "2");
                searchMsg.setHeader("ST", target);

                for (int i = 0; i < 3; i++) {
                    ssdpService.sendMulticast(searchMsg);
                    Log.d(TAG, "send msearch");
                    Thread.sleep(2000);
                }

                while(!this.isInterrupted()) {}

                ssdpService.close();
            } catch (IOException e) {
                e.printStackTrace();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private SsdpPacketListener searchPacketListener = new SsdpPacketListener() {
        @Override
        public void received(final DatagramPacket packet) {
            if (packet == null || packet.getData() == null) {
                return;
            }

            final String msgString = new String(packet.getData()).trim();
            SsdpMessage message = null;
            try {
                message = SsdpMessage.toMessage(msgString);
            } catch (IllegalArgumentException e) {}

            if (message == null || !containsTarget(message)) {
                return;
            }

            final String addr = packet.getAddress().getHostAddress() + ":" + packet.getPort();
            Log.d(TAG, addr);
            Log.d(TAG, msgString);
            Log.d(TAG, "__________________");
        }
    };

    class AdvertiseThread extends Thread {
        @Override
        public void run() {
            try {
                if (ssdpService != null) {
                    ssdpService.close();
                }

                NetworkInterface ni = getWifiNetworkInterface();
                ssdpService = new SsdpService(ni, advertisePacketListener);

                ssdpService.listen();

                SsdpMessage aliveMsg = new SsdpMessage(SsdpMessageType.NOTIFY);
                aliveMsg.setHeader("HOST", SSDP_ADDRESS);
                aliveMsg.setHeader("NTS", SsdpNotificationType.ALIVE.getRepresentation());
                aliveMsg.setHeader("NT", target);
                ssdpService.sendMulticast(aliveMsg);

                while(!this.isInterrupted()) {}

                SsdpMessage byebyeMsg = new SsdpMessage(SsdpMessageType.NOTIFY);
                byebyeMsg.setHeader("HOST", SSDP_ADDRESS);
                byebyeMsg.setHeader("NTS", SsdpNotificationType.BYEBYE.getRepresentation());
                byebyeMsg.setHeader("NT", target);
                ssdpService.sendMulticast(byebyeMsg);

                ssdpService.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private SsdpPacketListener advertisePacketListener = new SsdpPacketListener() {
        @Override
        public void received(final DatagramPacket packet) {
            if (packet == null || packet.getData() == null) {
                return;
            }

            final String msgString = new String(packet.getData()).trim();
            SsdpMessage message = null;
            try {
                message = SsdpMessage.toMessage(msgString);
            } catch (IllegalArgumentException e) {}

            if (message == null || !containsTarget(message)) {
                return;
            }

            final String addr = packet.getAddress().getHostAddress() + ":" + packet.getPort();
            Log.d(TAG, addr);
            Log.d(TAG, msgString);
            Log.d(TAG, "__________________");

            if (message.getType() != SsdpMessageType.MSEARCH) {
                return;
            }

            SsdpMessage searchMsg = new SsdpMessage(SsdpMessageType.RESPONSE);
            searchMsg.setHeader("CACHE-CONTROL", "100");
            searchMsg.setHeader("DATE", new Date().toString());
            searchMsg.setHeader("SERVER", "Android/" + Build.VERSION.RELEASE);
            searchMsg.setHeader("ST", target);

            try {
                ssdpService.sendUnicast(searchMsg, packet.getSocketAddress());
            } catch (IOException e) {
                e.printStackTrace();
            }

        }
    };

}