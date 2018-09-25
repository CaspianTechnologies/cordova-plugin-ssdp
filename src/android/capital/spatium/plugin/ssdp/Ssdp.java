package capital.spatium.plugin.ssdp;

import android.content.Context;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.util.Log;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.NetworkInterface;

import capital.spatium.plugin.ssdp.network.NetworkChangeReceiver;
import capital.spatium.plugin.ssdp.network.NetworkUtil;

public class Ssdp extends CordovaPlugin {
    private Thread thread;

    private CallbackContext deviceDiscoveredCallback = null;
    private CallbackContext deviceGoneCallback = null;
    private CallbackContext mNetworkGoneCallback = null;

    private static final String MAX_AGE = "max-age = 30";

    private static final String SSDP_ADDRESS = SsdpChannel.SSDP_MCAST_ADDRESS.getAddress().getHostName() + ":"
            + SsdpChannel.SSDP_MCAST_ADDRESS.getPort();

    private static final int ALIVE_PERIOD = 10000;
    private static final int MSEARCH_PERIOD = 1000;

    private static final String TAG = "Cordova SSDP";

    private NetworkChangeReceiver mReceiver;

    @Override
    public void initialize(CordovaInterface cordova, CordovaWebView webView) {
        super.initialize(cordova, webView);

        mReceiver = new NetworkChangeReceiver(cordova.getActivity());
        mReceiver.setNetworkChangeConsumer(new NetworkChangeConsumer(mReceiver.getCurrentNetworkInfo()));
        mReceiver.register();
    }

    @Override
    public boolean execute(String action, JSONArray args, final CallbackContext callbackContext) throws JSONException {
        Log.d(TAG, action);
        Log.d(TAG, args.toString());

        if (action.equals("startSearching")) {
            search(args, callbackContext);
            return true;
        } else if (action.equals("startAdvertising")) {
            advertise(args, callbackContext);
            return true;
        } else if (action.equals("stop")) {
            stop(args, callbackContext);
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
        mReceiver.unregister();
        super.onDestroy();
    }

    private void search(JSONArray args, final CallbackContext callbackContext) {
        String target = args.optString(0);

        if (thread != null && thread.isAlive()) {
            thread.interrupt();
        }

        thread = new SearchThread(target, callbackContext);
        thread.start();
    }

    private void advertise(JSONArray args, final CallbackContext callbackContext) {
        String target = args.optString(0);
        int port = args.optInt(1);
        String name = args.optString(2);
        String uuid = args.optString(3);

        if (thread != null && thread.isAlive()) {
            thread.interrupt();
        }

        thread = new AdvertiseThread(target, port, name, uuid, callbackContext);
        thread.start();
    }

    private void stop(JSONArray args, final CallbackContext callbackContext) {
        if (thread != null && thread.isAlive()) {
            thread.interrupt();
        }

        PluginResult result = new PluginResult(PluginResult.Status.OK);
        callbackContext.sendPluginResult(result);
    }

    private void setDeviceDiscoveredCallback(final CallbackContext callbackContext) {
        deviceDiscoveredCallback = callbackContext;
    }

    private void setDeviceGoneCallback(final CallbackContext callbackContext) {
        deviceGoneCallback = callbackContext;
    }

    private void setNetworkGoneCallback(final CallbackContext callbackContext) {
        mNetworkGoneCallback = callbackContext;
    }

    private static byte[] convertIpAddress(int ip) {
        return new byte[] { (byte) (ip & 0xFF), (byte) ((ip >> 8) & 0xFF), (byte) ((ip >> 16) & 0xFF),
                (byte) ((ip >> 24) & 0xFF) };
    }

    private boolean containsTarget(SsdpMessage message, String target) {
        return message.getHeader("ST") != null &&
               message.getHeader("ST").equals(target) ||
               message.getHeader("NT") != null &&
               message.getHeader("NT").equals(target);
    }

    private NetworkInterface getWifiNetworkInterface() throws IOException {
        Context context = this.cordova.getActivity().getApplicationContext();
        WifiManager wifiMgr = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        WifiInfo wifiInfo = wifiMgr.getConnectionInfo();
        int ip = wifiInfo.getIpAddress();
        byte[] b = convertIpAddress(ip);
        return NetworkInterface.getByInetAddress(InetAddress.getByAddress(b));
    }

    private SsdpMessage parseMessage(DatagramPacket packet) {
        if (packet == null || packet.getData() == null) {
            throw new IllegalArgumentException("Not an ssdp packet");
        }

        final String msgString = new String(packet.getData()).trim();
        return SsdpMessage.toMessage(msgString);
    }

    class SearchConsumer implements Consumer<DatagramPacket> {
        private final String mTarget;

        SearchConsumer(String target) {
            mTarget = target;
        }

        @Override
        public void accept(DatagramPacket packet) {
            SsdpMessage message = parseMessage(packet);

            if (message == null || !containsTarget(message, mTarget)) {
                return;
            }

            final InetAddress origin = packet.getAddress();

            if (origin == null) {
                return;
            }

            Log.d(TAG, "__________________");
            if (origin.getHostAddress() != null) {
                final String addr = origin.getHostAddress();
                Log.d(TAG, addr);
            }
            Log.d(TAG, message.toString());
            Log.d(TAG, "__________________");

            Device device = new Device(
                origin.getHostAddress(),
                Integer.parseInt(message.getHeader("PORT")),
                message.getHeader("SERVER"),
                message.getHeader("USN"),
                message.getHeader("CACHE-CONTROL"),
                mReceiver.getCurrentNetworkInfo().getExtraInfo()
            );

            try {
                PluginResult result = new PluginResult(PluginResult.Status.OK, device.toJSON());
                result.setKeepCallback(true);

                if (message.getType().equals(SsdpMessageType.RESPONSE)) {
                    deviceDiscoveredCallback.sendPluginResult(result);
                } else {
                    String nts = message.getHeader("NTS");

                    if (nts != null && nts.equals(SsdpNotificationType.ALIVE.getRepresentation())) {
                        deviceDiscoveredCallback.sendPluginResult(result);
                    } else if (nts != null && nts.equals(SsdpNotificationType.BYEBYE.getRepresentation())) {
                        deviceGoneCallback.sendPluginResult(result);
                    }
                }


            } catch (JSONException ignored) {}
        }
    }

    private class SearchThread extends Thread {
        private final String mTarget;
        private final CallbackContext mCallbackContext;

        private boolean silent = false;

        SearchThread(String target, final CallbackContext callbackContext) {
            mTarget = target;
            mCallbackContext = callbackContext;
        }

        @Override
        public void run() {
            SsdpService ssdpService = null;
            try {
                NetworkInterface ni = getWifiNetworkInterface();
                ssdpService = new SsdpService(ni, true);
                ssdpService.setMulticastConsumer(new SearchConsumer(mTarget));
                ssdpService.setUnicastConsumer(new SearchConsumer(mTarget));

                ssdpService.listen();

                SsdpMessage searchMsg = new SsdpMessage(SsdpMessageType.MSEARCH);
                searchMsg.setHeader("HOST", SSDP_ADDRESS);
                searchMsg.setHeader("MAN", "\"ssdp:discover\"");
                searchMsg.setHeader("MX", "1");
                searchMsg.setHeader("ST", mTarget);

                for (int i = 0; i < 3 && !this.isInterrupted(); i++) {
                    ssdpService.sendMulticast(searchMsg);
                    Thread.sleep(MSEARCH_PERIOD);
                }

                if (mCallbackContext != null && !silent) {
                    PluginResult result = new PluginResult(PluginResult.Status.OK);
                    mCallbackContext.sendPluginResult(result);
                }
            } catch (IOException | InterruptedException e) {
                e.printStackTrace();

                if (mCallbackContext != null && !silent) {
                    PluginResult result = new PluginResult(PluginResult.Status.ERROR, e.getMessage());
                    mCallbackContext.sendPluginResult(result);
                }
            } finally {
                if (ssdpService != null) {
                    ssdpService.close();
                }
            }
        }
    }

    private class AdvertiseConsumer implements Consumer<DatagramPacket> {
        private final SsdpService mSsdpService;
        private final String mTarget;
        private final int mPort;
        private final String mName;
        private final String mUuid;

        AdvertiseConsumer(SsdpService ssdpService, String target, int port, String name, String uuid) {
            mSsdpService = ssdpService;
            mTarget = target;
            mPort = port;
            mName = name;
            mUuid = uuid;
        }

        @Override
        public void accept(DatagramPacket packet) {
            SsdpMessage message = parseMessage(packet);

            if (message == null || !containsTarget(message, mTarget)) {
                return;
            }

            final InetAddress origin = packet.getAddress();

            Log.d(TAG, "message __________________");
            if (origin != null) {
                final String addr = origin.getHostAddress() + ":" + packet.getPort();
                Log.d(TAG, addr);
            }
            Log.d(TAG, message.toString());

            if (!message.getType().equals(SsdpMessageType.MSEARCH)) {
                Log.d(TAG, "__________________");
                return;
            }

            Log.d(TAG, "search response __________________");
            SsdpMessage searchMsg = new SsdpMessage(SsdpMessageType.RESPONSE);
            searchMsg.setHeader("CACHE-CONTROL", MAX_AGE);
            searchMsg.setHeader("EXT", "");
            searchMsg.setHeader("SERVER", mName);
            searchMsg.setHeader("ST", mTarget);
            searchMsg.setHeader("USN", mUuid);
            searchMsg.setHeader("PORT", String.valueOf(mPort));
            Log.d(TAG, searchMsg.toString());
            Log.d(TAG, "__________________");

            try {
                mSsdpService.sendUnicast(searchMsg, packet);
            } catch (IOException ignored) {}
        }
    }

    private class AdvertiseThread extends Thread {
        private final String mTarget;
        private final int mPort;
        private final String mName;
        private final String mUuid;
        private final CallbackContext mCallbackContext;

        AdvertiseThread(String target, int port, String name, String uuid, final CallbackContext callbackContext) {
            mTarget = target;
            mPort = port;
            mName = name;
            mUuid = uuid;
            mCallbackContext = callbackContext;
        }

        @Override
        public void run() {
            SsdpService ssdpService = null;
            try {
                NetworkInterface ni = getWifiNetworkInterface();
                ssdpService = new SsdpService(ni, false);
                ssdpService.setMulticastConsumer(new AdvertiseConsumer(ssdpService, mTarget, mPort, mName, mUuid));

                ssdpService.listen();

                SsdpMessage aliveMsg = new SsdpMessage(SsdpMessageType.NOTIFY);
                aliveMsg.setHeader("HOST", SSDP_ADDRESS);
                aliveMsg.setHeader("CACHE-CONTROL", MAX_AGE);
                aliveMsg.setHeader("NT", mTarget);
                aliveMsg.setHeader("NTS", SsdpNotificationType.ALIVE.getRepresentation());
                aliveMsg.setHeader("SERVER", mName);
                aliveMsg.setHeader("USN", mUuid);
                aliveMsg.setHeader("PORT", String.valueOf(mPort));

                while (!this.isInterrupted()) {
                    ssdpService.sendMulticast(aliveMsg);
                    try {
                        Thread.sleep(ALIVE_PERIOD);
                    } catch (InterruptedException ignored) {
                        this.interrupt();
                    }
                }

                if (mCallbackContext != null) {
                    PluginResult result = new PluginResult(PluginResult.Status.OK);
                    mCallbackContext.sendPluginResult(result);
                }
            } catch (IOException e) {
                e.printStackTrace();

                if (mCallbackContext != null) {
                    PluginResult result = new PluginResult(PluginResult.Status.ERROR, e.getMessage());
                    mCallbackContext.sendPluginResult(result);
                }
            } finally {
                if (ssdpService != null) {
                    SsdpMessage byebyeMsg = new SsdpMessage(SsdpMessageType.NOTIFY);
                    byebyeMsg.setHeader("HOST", SSDP_ADDRESS);
                    byebyeMsg.setHeader("NT", mTarget);
                    byebyeMsg.setHeader("NTS", SsdpNotificationType.BYEBYE.getRepresentation());
                    byebyeMsg.setHeader("SERVER", mName);
                    byebyeMsg.setHeader("USN", mUuid);
                    byebyeMsg.setHeader("PORT", String.valueOf(mPort));

                    try {
                        ssdpService.sendMulticast(byebyeMsg);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                    ssdpService.close();
                }
            }
        }
    }

    private class NetworkChangeConsumer implements Consumer<NetworkInfo> {
        private NetworkInfo mLastNetworkInfo;

        NetworkChangeConsumer(NetworkInfo initialNetworkInfo) {
            mLastNetworkInfo = initialNetworkInfo;
        }

        @Override
        public void accept(NetworkInfo networkInfo) {
            if (mLastNetworkInfo != null && NetworkUtil.isWiFi(mLastNetworkInfo.getType()) && mNetworkGoneCallback != null) {
                PluginResult result = new PluginResult(PluginResult.Status.OK, mLastNetworkInfo.getExtraInfo());
                result.setKeepCallback(true);
                mNetworkGoneCallback.sendPluginResult(result);
            }

            mLastNetworkInfo = networkInfo;
        }
    }
}