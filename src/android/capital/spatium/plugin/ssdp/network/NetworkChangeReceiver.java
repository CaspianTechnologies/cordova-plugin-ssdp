package capital.spatium.plugin.ssdp.network;

import android.annotation.TargetApi;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.NetworkInfo;
import android.os.Build;
import android.util.Log;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.PluginResult;

import java.util.function.Consumer;

import capital.spatium.plugin.ssdp.Ssdp;

@TargetApi(Build.VERSION_CODES.N)

public class NetworkChangeReceiver extends BroadcastReceiver {
    public static final String ACTION_CONNECTIVITY_CHANGE = "android.net.conn.CONNECTIVITY_CHANGE";

    private final Context mContext;
    private boolean registered = false;
    private Consumer<NetworkInfo> mNetworkChangeConsumer = null;

    public NetworkChangeReceiver(Context context) {
        mContext = context;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        NetworkInfo activeNetworkInfo = NetworkUtil.getNetworkInfo(context);
        if (mNetworkChangeConsumer != null) {
            mNetworkChangeConsumer.accept(activeNetworkInfo);
        }
    }

    public void register() {
        if (!registered) {
            mContext.registerReceiver(this, new IntentFilter(ACTION_CONNECTIVITY_CHANGE));
        }
    }

    public void unregister() {
        if (registered) {
            mContext.unregisterReceiver(this);
        }
    }

    public void setNetworkChangeConsumer(Consumer<NetworkInfo> networkChangeConsumer) {
        mNetworkChangeConsumer = networkChangeConsumer;
    }
}
