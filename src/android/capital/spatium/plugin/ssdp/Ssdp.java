package capital.spatium.plugin.ssdp;

import android.util.Log;

import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;
import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaWebView;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;


public class Ssdp extends CordovaPlugin {

    private static final String TAG = "Cordova SSDP";

    @Override
    public void initialize(CordovaInterface cordova, CordovaWebView webView) {
        super.initialize(cordova, webView);
    }

    @Override
    public boolean execute(String action, JSONArray args, final CallbackContext callbackContext) throws JSONException {
        Log.d(TAG, action);

        if (action == "startSearching") {
            return true;
        } else if (action == "startAdvertising") {
            return true;
        } else if (action == "stop") {
            return true;
        } else if (action == "setDiscoveredCallback") {
            return true;
        } else {
            return false;
        }
    }

}