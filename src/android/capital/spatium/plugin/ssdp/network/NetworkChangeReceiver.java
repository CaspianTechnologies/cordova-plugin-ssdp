package capital.spatium.plugin.ssdp.network;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.provider.Settings;

import java.lang.reflect.Method;

import capital.spatium.plugin.ssdp.Consumer;

public class NetworkChangeReceiver extends BroadcastReceiver {
    public static final String ACTION_CONNECTIVITY_CHANGE = "android.net.conn.CONNECTIVITY_CHANGE";

    private boolean mRegistered = false;

    private final Context mContext;
    private NetworkInfo mCurrentNetworkInfo;

    private Consumer<NetworkInfo> mNetworkChangeConsumer = null;

    public NetworkChangeReceiver(Context context) {
        mContext = context;
        mCurrentNetworkInfo = NetworkUtil.getNetworkInfo(context);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        NetworkInfo newNetworkInfo = NetworkUtil.getNetworkInfo(context);
        if (!NetworkUtil.equal(newNetworkInfo, mCurrentNetworkInfo)) {
            mCurrentNetworkInfo = newNetworkInfo;
            if (mNetworkChangeConsumer != null) {
                mNetworkChangeConsumer.accept(mCurrentNetworkInfo);
            }
        }
    }

    public boolean isHotspotEnabled() {
        boolean isWifiApEnabled = false;
        WifiManager wifi = (WifiManager) mContext.getApplicationContext()
                .getSystemService(Context.WIFI_SERVICE);
        Method[] wmMethods = wifi.getClass().getDeclaredMethods();
        for (Method method: wmMethods) {
            try {
                if (method.getName().equals("isWifiApEnabled")) {
                    isWifiApEnabled = (boolean) method.invoke(wifi);
                    return isWifiApEnabled;
                }
            } catch (Exception e) {
                e.printStackTrace();
                return isWifiApEnabled;
            }
        }
        return isWifiApEnabled;
    }

    public String getNetworkId() {
        if (isHotspotEnabled()) {
            String androidId = Settings.Secure.getString(mContext.getContentResolver(), Settings.Secure.ANDROID_ID);
            return "hotspot-" + androidId;
        } else {
            NetworkInfo networkInfo = NetworkUtil.getNetworkInfo(mContext);
            if (networkInfo != null) {
                return networkInfo.getExtraInfo();
            }
        }
        return "unknown";
    }

    public NetworkInfo getCurrentNetworkInfo() {
        return mCurrentNetworkInfo;
    }

    public void register() {
        if (!mRegistered) {
            mContext.registerReceiver(this, new IntentFilter(ACTION_CONNECTIVITY_CHANGE));
            mRegistered = true;
        }
    }

    public void unregister() {
        if (mRegistered) {
            mContext.unregisterReceiver(this);
        }
    }

    public void setNetworkChangeConsumer(Consumer<NetworkInfo> networkChangeConsumer) {
        mNetworkChangeConsumer = networkChangeConsumer;
    }
}
