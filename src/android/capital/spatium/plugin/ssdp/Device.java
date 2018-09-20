package capital.spatium.plugin.ssdp;

import org.json.JSONException;
import org.json.JSONObject;

public class Device {
    private String ip;
    private Integer port;
    private String name;
    private String usn;
    private String cacheControl;
    private String networkId;

    public Device() {
    }

    public Device(String ip,
                  Integer port,
                  String name,
                  String usn,
                  String cacheControl,
                  String networkId) {
        this.ip = ip;
        this.port = port;
        this.name = name;
        this.usn = usn;
        this.cacheControl = cacheControl;
        this.networkId = networkId;
    }

    public JSONObject toJSON() throws JSONException {

        JSONObject jo = new JSONObject();
        jo.put("ip", ip);
        jo.put("port", port);
        jo.put("name", name);
        jo.put("usn", usn);
        jo.put("cacheControl", cacheControl);
        jo.put("networkId", networkId);

        return jo;

    }
}
