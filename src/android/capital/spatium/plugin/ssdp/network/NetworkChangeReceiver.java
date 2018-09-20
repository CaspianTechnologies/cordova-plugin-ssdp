package capital.spatium.plugin.ssdp.network;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.NetworkInfo;
import android.util.Log;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.PluginResult;

public class NetworkChangeReceiver extends BroadcastReceiver {
    public boolean isRegistered;

    private Context mContext;
    private CallbackContext callbackContext = null;
    private static final String TAG = "Cordova SSDP Network";
    private static final int NO_CONNECTION_TYPE = -1;
    private static int sLastType = NO_CONNECTION_TYPE;

    public NetworkChangeReceiver(CallbackContext callbackContext) {
        this.callbackContext = callbackContext;
    }

    @Override
    public void onReceive(Context ctx, Intent intent) {
        mContext = ctx;

        NetworkInfo activeNetworkInfo = NetworkUtil.getNetworkInfo(ctx);
        final int currentType = activeNetworkInfo != null
                ? activeNetworkInfo.getType() : NO_CONNECTION_TYPE;

        if (sLastType != currentType) {
            String log = "";
            if (activeNetworkInfo != null) {
                String type = NetworkUtil.getConnectivityTypeString(activeNetworkInfo);
                String status = NetworkUtil.getConnectivityStatusString(activeNetworkInfo);
                String extra = activeNetworkInfo.getExtraInfo();

                log = status + " " + type + " " + extra;
            } else {
                log = "Disconnected";
            }

            sLastType = currentType;
            Log.d(TAG, log);

            PluginResult result = new PluginResult(PluginResult.Status.NO_RESULT, intent.getAction());
            result.setKeepCallback(true);
            callbackContext.sendPluginResult(result);
        }
    }

    public Intent register(Context context, IntentFilter filter) {
        try {
            return !isRegistered
                    ? context.registerReceiver(this, filter)
                    : null;
        } finally {
            isRegistered = true;
        }
    }

    public boolean unregister(Context context) {
        return isRegistered
                && unregisterInternal(context);
    }

    private boolean unregisterInternal(Context context) {
        context.unregisterReceiver(this);
        isRegistered = false;
        return true;
    }
}
