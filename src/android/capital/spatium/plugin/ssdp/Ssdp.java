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
    private int port = DEFAULT_PORT;
    private String name = DEFAULT_NAME;
    private String uuid = DEFAULT_UUID;

    private static final String TAG = "Cordova SSDP";

    private static final String DEFAULT_TARGET = "spatandroid";
    private static final int DEFAULT_PORT = 5666;
    private static final String DEFAULT_NAME = "Android/" + Build.VERSION.RELEASE;
    private static final String DEFAULT_UUID = "uuid";

    private static final String MAX_AGE = "max-age = 30";

    private static final String SSDP_ADDRESS = 
            SsdpChannel.SSDP_MCAST_ADDRESS.getAddress().getHostName() 
            + ":" 
            +  SsdpChannel.SSDP_MCAST_ADDRESS.getPort();

    private static final int ALIVE_PERIOD = 10000;

    @Override
    public void initialize(CordovaInterface cordova, CordovaWebView webView) {
        super.initialize(cordova, webView);
    }

    @Override
    public boolean execute(String action, JSONArray args, final CallbackContext callbackContext) throws JSONException {
        Log.d(TAG, action);
        Log.d(TAG, args.toString());

        if (args != null && args.length() > 0) {
            String jsonTarget = args.optString(0);
            if (jsonTarget != null && !jsonTarget.isEmpty()) {
              target = jsonTarget;
            }
            
            int jsonPort = args.optInt(1);
            if (jsonPort > 0) {
              port = jsonPort;
            }
            
            String jsonName = args.optString(2);
            if (jsonName != null && !jsonName.isEmpty()) {
              name = jsonName;
            }

            String jsonUuid = args.optString(3);
            if (jsonUuid != null && !jsonUuid.isEmpty()) {
              uuid = jsonUuid;
            }
        }

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

    @Override
    public void onDestroy() {
        if (thread != null) {
          thread.interrupt();
        }
        super.onDestroy();
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
                    Thread.sleep(1000);
                }

                while(!this.isInterrupted()) {}
            } catch (IOException e) {
                e.printStackTrace();
            } catch (InterruptedException e) {
                e.printStackTrace();
            } finally {
                ssdpService.close();
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
                aliveMsg.setHeader("CACHE-CONTROL", MAX_AGE);
                aliveMsg.setHeader("NT", target);
                aliveMsg.setHeader("NTS", SsdpNotificationType.ALIVE.getRepresentation());
                aliveMsg.setHeader("SERVER", name);
                aliveMsg.setHeader("USN", uuid);
                aliveMsg.setHeader("PORT", String.valueOf(port));

                while(!this.isInterrupted()) {
                    ssdpService.sendMulticast(aliveMsg);
                    Thread.sleep(ALIVE_PERIOD);
                }
            } catch (IOException e) {
                e.printStackTrace();
            } catch (InterruptedException e) {
            } finally {
                sendByeBye();
                ssdpService.close();
            }
        }
    }

    private void sendByeBye() {
      SsdpMessage byebyeMsg = new SsdpMessage(SsdpMessageType.NOTIFY);
      byebyeMsg.setHeader("HOST", SSDP_ADDRESS);
      byebyeMsg.setHeader("NTS", SsdpNotificationType.BYEBYE.getRepresentation());
      byebyeMsg.setHeader("NT", target);
      byebyeMsg.setHeader("SERVER", name);
      byebyeMsg.setHeader("USN", uuid);
      try {
          ssdpService.sendMulticast(byebyeMsg);
      } catch (IOException e) {
        e.printStackTrace();
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

            if (packet.getAddress() != null) {
                final String addr = packet.getAddress().getHostAddress() + ":" + packet.getPort();
                Log.d(TAG, addr);
            }
            Log.d(TAG, msgString);
            Log.d(TAG, "__________________");

            if (message.getType() != SsdpMessageType.MSEARCH) {
                return;
            }

            SsdpMessage searchMsg = new SsdpMessage(SsdpMessageType.RESPONSE);
            searchMsg.setHeader("CACHE-CONTROL", MAX_AGE);
            searchMsg.setHeader("ST", target);
            searchMsg.setHeader("SERVER", name);
            searchMsg.setHeader("USN", uuid);
            searchMsg.setHeader("PORT", String.valueOf(port));

            try {
                ssdpService.sendUnicast(searchMsg, packet);
            } catch (IOException e) {
                e.printStackTrace();
            }

        }
    };

}