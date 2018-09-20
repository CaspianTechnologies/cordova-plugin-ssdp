package capital.spatium.plugin.ssdp;


import android.content.Context;
import android.content.IntentFilter;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.util.Log;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.NetworkInterface;

import capital.spatium.plugin.ssdp.network.NetworkChangeReceiver;


public class Ssdp extends CordovaPlugin {

    private Thread thread;

    private SsdpService ssdpService = null;
    private String target = DEFAULT_TARGET;
    private String name = DEFAULT_NAME;
    private String uuid = DEFAULT_UUID;
    private int port = DEFAULT_PORT;

    private CallbackContext discoveredCallback = null;
    private CallbackContext goneCallback = null;
    private CallbackContext mSearchCallback = null;
    private CallbackContext mAdvertiseCallback = null;
    private CallbackContext mStopCallback = null;
    private CallbackContext mNetworkGoneCallback = null;

    private static final String DEFAULT_TARGET = "spatium";
    private static final String DEFAULT_NAME = "Android/" + Build.VERSION.RELEASE;
    private static final String DEFAULT_UUID = "uuid";
    private static final int DEFAULT_PORT = 5666;

    private static final String MAX_AGE = "max-age = 30";

    private static final String SSDP_ADDRESS =
            SsdpChannel.SSDP_MCAST_ADDRESS.getAddress().getHostName()
                    + ":"
                    + SsdpChannel.SSDP_MCAST_ADDRESS.getPort();

    private static final int ALIVE_PERIOD = 10000;
    private static final int MSEARCH_PERIOD = 1000;

    private static final String TAG = "Cordova SSDP";

    private NetworkChangeReceiver mReceiver = null;
    public static final String ACTION_CONNECTIVITY_CHANGE = "android.net.conn.CONNECTIVITY_CHANGE";
    public static SsdpDeviceType currentDeviceType = SsdpDeviceType.NONE;

    @Override
    public void initialize(CordovaInterface cordova, CordovaWebView webView) {
        super.initialize(cordova, webView);
    }

    @Override
    public boolean execute(String action, JSONArray args, final CallbackContext callbackContext) throws JSONException {
        if (mReceiver == null) mReceiver = new NetworkChangeReceiver(callbackContext, this);
        if (!mReceiver.isRegistered) {
            mReceiver.register(cordova.getActivity(), new IntentFilter(ACTION_CONNECTIVITY_CHANGE));
        }

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
            setSearchCallback(callbackContext);
            search();
            return true;
        } else if (action.equals("startAdvertising")) {
            setAdvertiseCallback(callbackContext);
            advertise();
            return true;
        } else if (action.equals("stop")) {
            setStopCallback(callbackContext);
            stop();
            return true;
        } else if (action.equals("setDeviceDiscoveredCallback")) {
            setDeviceDiscoveredCallback(callbackContext);
            return true;
        } else if (action.equals("setDeviceGoneCallback")) {
            setDeviceGoneCallback(callbackContext);
            return true;
        } else if (action.equals("setNetworkGoneCallback")) {
            setNetworkGoneCallback(callbackContext);
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
        currentDeviceType = SsdpDeviceType.NONE;
        mReceiver.unregister(cordova.getActivity());
        super.onDestroy();
    }

    public void refreshThread() {
        if (thread != null && !thread.isAlive()) {
            try {
                Log.d(TAG, "refreshThread");
                switch (currentDeviceType) {
                    case CONTROL_POINT:
                        search();
                        break;
                    case ROOT_DEVICE:
                        advertise();
                        break;
                    default:
                        break;

                }
            } catch (Exception e) {
                Log.d(TAG, e.getMessage());
            }
        }
    }

    public void stopThread() {
        if (thread != null) {
            Log.d(TAG, "stopThread");
            thread.interrupt();
        }
    }

    private void search() {
        thread = new SearchThread();
        thread.start();
        currentDeviceType = SsdpDeviceType.CONTROL_POINT;
        PluginResult result = new PluginResult(PluginResult.Status.NO_RESULT);
        result.setKeepCallback(true);
        mSearchCallback.sendPluginResult(result);
    }

    private void stop() {
        thread.interrupt();
        currentDeviceType = SsdpDeviceType.NONE;
        PluginResult result = new PluginResult(PluginResult.Status.NO_RESULT);
        result.setKeepCallback(true);
        mStopCallback.sendPluginResult(result);
    }

    private void advertise() {
        thread = new AdvertiseThread();
        thread.start();
        currentDeviceType = SsdpDeviceType.ROOT_DEVICE;
        PluginResult result = new PluginResult(PluginResult.Status.NO_RESULT);
        result.setKeepCallback(true);
        mAdvertiseCallback.sendPluginResult(result);
    }

    private void setSearchCallback(CallbackContext callbackContext) {
        mSearchCallback = callbackContext;
    }

    private void setAdvertiseCallback(CallbackContext callbackContext) {
        mAdvertiseCallback = callbackContext;
    }

    private void setDeviceDiscoveredCallback(final CallbackContext callbackContext) {
        discoveredCallback = callbackContext;
    }

    private void setDeviceGoneCallback(final CallbackContext callbackContext) {
        goneCallback = callbackContext;
    }

    private void setNetworkGoneCallback(final CallbackContext callbackContext) {
        mNetworkGoneCallback = callbackContext;
    }

    private void setStopCallback(final CallbackContext callbackContext) {
        mStopCallback = callbackContext;
    }

    private static byte[] convertIpAddress(int ip) {
        return new byte[]{
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
                ssdpService = new SsdpService(ni, searchPacketListener, true);

                ssdpService.listen();

                SsdpMessage searchMsg = new SsdpMessage(SsdpMessageType.MSEARCH);
                searchMsg.setHeader("HOST", SSDP_ADDRESS);
                searchMsg.setHeader("MAN", "\"ssdp:discover\"");
                searchMsg.setHeader("MX", "2");
                searchMsg.setHeader("ST", target);

                for (int i = 0; i < 3; i++) {
                    ssdpService.sendMulticast(searchMsg);
                    Thread.sleep(MSEARCH_PERIOD);
                }

                while (!this.isInterrupted()) {
                }
            } catch (IOException e) {
                e.printStackTrace();
                PluginResult result = new PluginResult(PluginResult.Status.ERROR, e.getMessage());
                result.setKeepCallback(true);
                mSearchCallback.sendPluginResult(result);
            } catch (InterruptedException e) {
                e.printStackTrace();
                PluginResult result = new PluginResult(PluginResult.Status.ERROR, e.getMessage());
                result.setKeepCallback(true);
                mSearchCallback.sendPluginResult(result);
            } finally {
                if (ssdpService != null) {
                    ssdpService.close();
                }
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
            } catch (IllegalArgumentException e) {
                e.printStackTrace();
                PluginResult result = new PluginResult(PluginResult.Status.ERROR, e.getMessage());
                result.setKeepCallback(true);
                mSearchCallback.sendPluginResult(result);
            }

            if (message == null || !containsTarget(message)) {
                return;
            }

            if (packet.getAddress() == null) {
                return;
            }

            Log.d(TAG, "__________________");
            if (packet.getAddress().getHostAddress() != null) {
                final String addr = packet.getAddress().getHostAddress() + ":" + packet.getPort();
                Log.d(TAG, addr);
            }
            Log.d(TAG, msgString);
            Log.d(TAG, "__________________");

            JSONObject item = new JSONObject();
            try {
                item.put("ip", packet.getAddress().getHostAddress());
                item.put("port", message.getHeader("PORT"));
                item.put("name", message.getHeader("SERVER"));
                item.put("usn", message.getHeader("USN"));
            } catch (JSONException e) {
                e.printStackTrace();
                PluginResult result = new PluginResult(PluginResult.Status.ERROR, e.getMessage());
                result.setKeepCallback(true);
                mSearchCallback.sendPluginResult(result);
            }

            PluginResult result = new PluginResult(PluginResult.Status.OK, item);
            result.setKeepCallback(true);

            if (message.getType().equals(SsdpMessageType.RESPONSE)) {
                discoveredCallback.sendPluginResult(result);
                return;
            }

            String nts = message.getHeader("NTS");

            if (nts != null && nts.equals(SsdpNotificationType.ALIVE.getRepresentation())) {
                discoveredCallback.sendPluginResult(result);
                return;
            }

            if (nts != null && nts.equals(SsdpNotificationType.BYEBYE.getRepresentation())) {
                goneCallback.sendPluginResult(result);
            }
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
                ssdpService = new SsdpService(ni, advertisePacketListener, false);

                ssdpService.listen();

                SsdpMessage aliveMsg = new SsdpMessage(SsdpMessageType.NOTIFY);
                aliveMsg.setHeader("HOST", SSDP_ADDRESS);
                aliveMsg.setHeader("CACHE-CONTROL", MAX_AGE);
                aliveMsg.setHeader("NT", target);
                aliveMsg.setHeader("NTS", SsdpNotificationType.ALIVE.getRepresentation());
                aliveMsg.setHeader("SERVER", name);
                aliveMsg.setHeader("USN", uuid);
                aliveMsg.setHeader("PORT", String.valueOf(port));

                while (!this.isInterrupted()) {
                    ssdpService.sendMulticast(aliveMsg);
                    Thread.sleep(ALIVE_PERIOD);
                }
            } catch (IOException e) {
                e.printStackTrace();
                PluginResult result = new PluginResult(PluginResult.Status.ERROR, e.getMessage());
                result.setKeepCallback(true);
                mAdvertiseCallback.sendPluginResult(result);
            } catch (InterruptedException e) {
                e.printStackTrace();
                PluginResult result = new PluginResult(PluginResult.Status.ERROR, e.getMessage());
                result.setKeepCallback(true);
                mAdvertiseCallback.sendPluginResult(result);
            } finally {
                if (ssdpService != null) {
                    sendByeBye();
                    ssdpService.close();
                }
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
            PluginResult result = new PluginResult(PluginResult.Status.ERROR, e.getMessage());
            result.setKeepCallback(true);
            mAdvertiseCallback.sendPluginResult(result);
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
            } catch (IllegalArgumentException e) {
                e.printStackTrace();
                PluginResult result = new PluginResult(PluginResult.Status.ERROR, e.getMessage());
                result.setKeepCallback(true);
                mAdvertiseCallback.sendPluginResult(result);
            }

            if (message == null || !containsTarget(message)) {
                return;
            }

            Log.d(TAG, "message __________________");
            if (packet.getAddress() != null) {
                final String addr = packet.getAddress().getHostAddress() + ":" + packet.getPort();
                Log.d(TAG, addr);
            }
            Log.d(TAG, msgString);

            if (!message.getType().equals(SsdpMessageType.MSEARCH)) {
                Log.d(TAG, "__________________");
                return;
            }

            Log.d(TAG, "search response __________________");
            SsdpMessage searchMsg = new SsdpMessage(SsdpMessageType.RESPONSE);
            searchMsg.setHeader("CACHE-CONTROL", MAX_AGE);
            searchMsg.setHeader("ST", target);
            searchMsg.setHeader("SERVER", name);
            searchMsg.setHeader("USN", uuid);
            searchMsg.setHeader("PORT", String.valueOf(port));
            Log.d(TAG, searchMsg.toString());
            Log.d(TAG, "__________________");

            try {
                ssdpService.sendUnicast(searchMsg, packet);
            } catch (IOException e) {
                e.printStackTrace();
                PluginResult result = new PluginResult(PluginResult.Status.ERROR, e.getMessage());
                result.setKeepCallback(true);
                mAdvertiseCallback.sendPluginResult(result);
            }

        }
    };

}