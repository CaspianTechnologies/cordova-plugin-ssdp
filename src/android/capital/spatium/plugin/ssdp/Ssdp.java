package capital.spatium.plugin.ssdp;

import android.net.NetworkInfo;
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
import java.net.SocketException;
import java.util.Enumeration;

import capital.spatium.plugin.ssdp.network.NetworkChangeReceiver;
import capital.spatium.plugin.ssdp.network.NetworkUtil;

public class Ssdp extends CordovaPlugin {
    private MulticastThread thread;

    private CallbackContext mDeviceDiscoveredCallback = null;
    private CallbackContext mDeviceGoneCallback = null;
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

    private void search(JSONArray args, final CallbackContext callbackContext) throws JSONException {
        String target = args.getString(0);

        if (thread != null && thread.isAlive()) {
            thread.interrupt();
        }

        thread = new SearchThread(target);
        thread.start();

        if (callbackContext != null) {
            PluginResult result = new PluginResult(PluginResult.Status.OK);
            callbackContext.sendPluginResult(result);
        }
    }

    private void advertise(JSONArray args, final CallbackContext callbackContext) throws JSONException {
        String target = args.getString(0);
        int port = args.getInt(1);
        String name = args.getString(2);
        String uuid = args.getString(3);

        if (thread != null && thread.isAlive()) {
            thread.interrupt();
        }

        thread = new AdvertiseThread(target, port, name, uuid);
        thread.start();

        if (callbackContext != null) {
            PluginResult result = new PluginResult(PluginResult.Status.OK);
            callbackContext.sendPluginResult(result);
        }
    }

    private void stop(JSONArray args, final CallbackContext callbackContext) {
        if (thread != null && thread.isAlive()) {
            thread.interrupt();
            thread = null;
        }

        PluginResult result = new PluginResult(PluginResult.Status.OK);
        callbackContext.sendPluginResult(result);
    }

    private void setDeviceDiscoveredCallback(final CallbackContext callbackContext) {
        mDeviceDiscoveredCallback = callbackContext;
    }

    private void setDeviceGoneCallback(final CallbackContext callbackContext) {
        mDeviceGoneCallback = callbackContext;
    }

    private void setNetworkGoneCallback(final CallbackContext callbackContext) {
        mNetworkGoneCallback = callbackContext;
    }

    private boolean containsTarget(SsdpMessage message, String target) {
        return message.getHeader("ST") != null &&
               message.getHeader("ST").equals(target) ||
               message.getHeader("NT") != null &&
               message.getHeader("NT").equals(target);
    }

    private NetworkInterface getWifiNetworkInterface() throws SocketException {
        Enumeration<NetworkInterface> enumeration = NetworkInterface.getNetworkInterfaces();
        NetworkInterface wlan0;

        while (enumeration.hasMoreElements()) {
            wlan0 = enumeration.nextElement();
            if (wlan0.getName().equals("wlan0")) {
                return wlan0;
            }
        }
        return null;
    }

    private SsdpMessage parseMessage(DatagramPacket packet) {
        if (packet == null || packet.getData() == null) {
            throw new IllegalArgumentException("Not an ssdp packet");
        }

        final String msgString = new String(packet.getData()).trim();
        return SsdpMessage.toMessage(msgString);
    }

    private class SearchConsumer implements Consumer<DatagramPacket> {
        private final String mTarget;

        SearchConsumer(String target) {
            mTarget = target;
        }

        @Override
        public void accept(DatagramPacket packet) {
            try {
                SsdpMessage message = parseMessage(packet);

                if (message == null || !containsTarget(message, mTarget)) {
                    return;
                }

                if (message.getType().equals(SsdpMessageType.MSEARCH)) {
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
                        mReceiver.getNetworkId()
                );

                try {
                    PluginResult result = new PluginResult(PluginResult.Status.OK, device.toJSON());
                    result.setKeepCallback(true);

                    if (message.getType().equals(SsdpMessageType.RESPONSE)) {
                        if (mDeviceDiscoveredCallback != null) {
                            mDeviceDiscoveredCallback.sendPluginResult(result);
                        }
                    } else {
                        String nts = message.getHeader("NTS");

                        if (nts != null && nts.equals(SsdpNotificationType.ALIVE.getRepresentation())) {
                            if (mDeviceDiscoveredCallback != null) {
                                mDeviceDiscoveredCallback.sendPluginResult(result);
                            }
                        } else if (nts != null && nts.equals(SsdpNotificationType.BYEBYE.getRepresentation())) {
                            if (mDeviceGoneCallback != null) {
                                mDeviceGoneCallback.sendPluginResult(result);
                            }
                        }
                    }


                } catch (JSONException ignored) {}
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private class SearchThread extends MulticastThread {
        private final String mTarget;

        SearchThread(String target) {
            mTarget = target;
        }

        public void search() {
            try {
                SsdpMessage searchMsg = new SsdpMessage(SsdpMessageType.MSEARCH);
                searchMsg.setHeader("HOST", SSDP_ADDRESS);
                searchMsg.setHeader("MAN", "\"ssdp:discover\"");
                searchMsg.setHeader("MX", "1");
                searchMsg.setHeader("ST", mTarget);

                for (int i = 0; i < 3 && !this.isInterrupted(); i++) {
                    mSsdpService.sendMulticast(searchMsg);
                    try {
                        Thread.sleep(MSEARCH_PERIOD);
                    } catch (InterruptedException e) {
                        this.interrupt();
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void run() {
            try {
                NetworkInterface ni = getWifiNetworkInterface();
                mSsdpService = new SsdpService(ni, true);
                mSsdpService.setMulticastConsumer(new SearchConsumer(mTarget));
                mSsdpService.setUnicastConsumer(new SearchConsumer(mTarget));

                mSsdpService.listen();

                search();

                while (!this.isInterrupted()) {
                    try {
                        Thread.sleep(MSEARCH_PERIOD);
                    } catch (InterruptedException e) {
                        this.interrupt();
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                if (mSsdpService != null) {
                    mSsdpService.close();
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

    private class AdvertiseThread extends MulticastThread {
        private final String mTarget;
        private final int mPort;
        private final String mName;
        private final String mUuid;

        AdvertiseThread(String target, int port, String name, String uuid) {
            mTarget = target;
            mPort = port;
            mName = name;
            mUuid = uuid;
        }

        @Override
        public void run() {
            try {
                NetworkInterface ni = getWifiNetworkInterface();
                mSsdpService = new SsdpService(ni, false);
                mSsdpService.setMulticastConsumer(new AdvertiseConsumer(mSsdpService, mTarget, mPort, mName, mUuid));

                mSsdpService.listen();

                SsdpMessage aliveMsg = new SsdpMessage(SsdpMessageType.NOTIFY);
                aliveMsg.setHeader("HOST", SSDP_ADDRESS);
                aliveMsg.setHeader("CACHE-CONTROL", MAX_AGE);
                aliveMsg.setHeader("NT", mTarget);
                aliveMsg.setHeader("NTS", SsdpNotificationType.ALIVE.getRepresentation());
                aliveMsg.setHeader("SERVER", mName);
                aliveMsg.setHeader("USN", mUuid);
                aliveMsg.setHeader("PORT", String.valueOf(mPort));

                while (!this.isInterrupted()) {
                    try {
                        mSsdpService.sendMulticast(aliveMsg);
                    } catch (IOException ignored) {}

                    try {
                        Thread.sleep(ALIVE_PERIOD);
                    } catch (InterruptedException ignored) {
                        this.interrupt();
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                if (mSsdpService != null) {
                    SsdpMessage byebyeMsg = new SsdpMessage(SsdpMessageType.NOTIFY);
                    byebyeMsg.setHeader("HOST", SSDP_ADDRESS);
                    byebyeMsg.setHeader("NT", mTarget);
                    byebyeMsg.setHeader("NTS", SsdpNotificationType.BYEBYE.getRepresentation());
                    byebyeMsg.setHeader("SERVER", mName);
                    byebyeMsg.setHeader("USN", mUuid);
                    byebyeMsg.setHeader("PORT", String.valueOf(mPort));

                    try {
                        mSsdpService.sendMulticast(byebyeMsg);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                    mSsdpService.close();
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

            if (
              mLastNetworkInfo != null &&
              NetworkUtil.isWiFi(mLastNetworkInfo.getType()) &&
              thread != null
            ) {
                try {
                    thread.joinGroup();
                    if (thread instanceof SearchThread) {
                        cordova.getThreadPool().submit(new Runnable() {
                            @Override
                            public void run() {
                                ((SearchThread) thread).search();
                            }
                        });
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private class MulticastThread extends Thread {
        SsdpService mSsdpService = null;

        public void joinGroup() throws IOException {
            if (mSsdpService != null) {
                NetworkInterface ni = getWifiNetworkInterface();
                mSsdpService.joinGroup(ni);
            }
        }
    }
}
