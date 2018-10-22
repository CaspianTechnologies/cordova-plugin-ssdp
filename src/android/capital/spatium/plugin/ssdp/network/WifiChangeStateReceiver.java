package capital.spatium.plugin.ssdp.network;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;

import capital.spatium.plugin.ssdp.Consumer;

import static android.net.wifi.WifiManager.WIFI_STATE_CHANGED_ACTION;

public class WifiChangeStateReceiver extends BroadcastReceiver {
    private Consumer<Boolean> mWifiAdapterChangeConsumer;
    private Consumer<NetworkInfo> mWifiConnectionChangedConsumer;
    private boolean mRegistered = false;
    private final Context mContext;

    public WifiChangeStateReceiver(Context mContext) {
        this.mContext = mContext;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if(WIFI_STATE_CHANGED_ACTION.equals(action)) {
            int wifiState = intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE, WifiManager.WIFI_STATE_UNKNOWN);
            if(wifiState == WifiManager.WIFI_STATE_ENABLED) {
                if(mWifiAdapterChangeConsumer != null) {
                    mWifiAdapterChangeConsumer.accept(true);
                }
            }
            else if(wifiState == WifiManager.WIFI_STATE_DISABLED) {
                if(mWifiAdapterChangeConsumer != null) {
                    mWifiAdapterChangeConsumer.accept(false);
                }
            }
        } else if (WifiManager.NETWORK_STATE_CHANGED_ACTION.equals (action)) {
            NetworkInfo netInfo = intent.getParcelableExtra (WifiManager.EXTRA_NETWORK_INFO);
            if (ConnectivityManager.TYPE_WIFI == netInfo.getType () && mWifiConnectionChangedConsumer != null) {
                mWifiConnectionChangedConsumer.accept(netInfo);
            }
        }
    }

    public void setWifiAdapterChangedConsumer(Consumer<Boolean> wifiAdapterChangeConsumer) {
        mWifiAdapterChangeConsumer = wifiAdapterChangeConsumer;
    }

    public void setWifiConnectionChangedConsumer(Consumer<NetworkInfo> wifiConnectionChangedConsumer) {
        mWifiConnectionChangedConsumer = wifiConnectionChangedConsumer;
    }

    public void register() {
        if (!mRegistered) {
            IntentFilter filterRefreshUpdate = new IntentFilter();
            filterRefreshUpdate.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
            filterRefreshUpdate.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
            mContext.registerReceiver(this, filterRefreshUpdate);
            mRegistered = true;
        }
    }

    public void unregister() {
        if (mRegistered) {
            mContext.unregisterReceiver(this);
        }
    }
}
