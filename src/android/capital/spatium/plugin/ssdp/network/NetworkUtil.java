package capital.spatium.plugin.ssdp.network;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

public class NetworkUtil {

    public static NetworkInfo getNetworkInfo(Context context) {
        ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);

        return connectivityManager != null ? connectivityManager.getActiveNetworkInfo() : null;
    }

    public static boolean equal(NetworkInfo x, NetworkInfo y) {
        return (x != null) == (y != null) &&
               ((x == null) ||
                (x.getType() == y.getType() && x.getExtraInfo().equals(y.getExtraInfo()))
               );
    }

    public static boolean isWiFi(int type){
        return ConnectivityManager.TYPE_WIFI == type;
    }

    public static String getConnectivityTypeString(NetworkInfo activeNetworkInfo) {
        switch (activeNetworkInfo.getType()) {
            case ConnectivityManager.TYPE_WIFI:
                return "WiFi";
            case ConnectivityManager.TYPE_MOBILE:
                return "Mobile";
            default:
                return "Not connected";
        }
    }

    public static String getConnectivityStatusString(NetworkInfo activeNetworkInfo) {
        switch (activeNetworkInfo.getState()) {
            case CONNECTED:
                return "Connected";
            case CONNECTING:
                return "Connecting";
            case DISCONNECTED:
                return "Disconnected";
            case DISCONNECTING:
                return "Disconnecting";
            case SUSPENDED:
                return "Suspended";
            case UNKNOWN:
                return "Unknown";
            default:
                return "???";
        }
    }
}
