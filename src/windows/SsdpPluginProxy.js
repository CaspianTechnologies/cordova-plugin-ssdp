let controlPoint;
let wiFiInfo;

function initializeControlPoint() {
    if (!controlPoint) {
        controlPoint = new SSDP.ControlPoint();
    }
}

function initializeWiFiInfo() {
    if (!wiFiInfo) {
        wiFiInfo = new SSDP.WiFiInfo();
    }
}

module.exports = {
    startSearching: function (success, error, params) {
        initializeControlPoint();
        const target = params[0];
        controlPoint.target = target;
        controlPoint.start()
            .then(function () {
                controlPoint.searchDevices().then(success, error);
            }, error);
    },

    isAvailable: function (success, error, params) {
        initializeWiFiInfo();
        wiFiInfo.isAvailable().then(success, error);
    },

    isEnabled: function (success, error, params) {
        initializeWiFiInfo();
        wiFiInfo.isEnabled().then(success, error);
    },

    isConnected: function (success, error, params) {
        initializeWiFiInfo();
        wiFiInfo.isConnected().then(success, error);
    },

    setDeviceDiscoveredCallback: function (_1, _2, params) {
        initializeControlPoint();
        const callback = params[0];
        controlPoint.addEventListener('devicediscovered', callback);
    },

    setDeviceGoneCallback: function (_1, _2, params) {
        initializeControlPoint();
        const callback = params[0];
        controlPoint.addEventListener('devicegone', callback);
    },

    setNetworkGoneCallback: function (_1, _2, params) {
        initializeControlPoint();
        const callback = params[0];
        controlPoint.addEventListener('networkgone', callback);
    },

    setAvailabilityChangedCallback: function (_1, _2, params) {
        initializeWiFiInfo();
        const callback = params[0];
        wiFiInfo.addEventListener('availabilitychanged', callback);
    },

    setAdapterStatusChangedCallback: function (_1, _2, params) {
        initializeWiFiInfo();
        const callback = params[0];
        wiFiInfo.addEventListener('adapterstatuschanged', callback);
    },

    setConnectionChangedCallback: function (_1, _2, params) {
        initializeWiFiInfo();
        const callback = params[0];
        wiFiInfo.addEventListener('connectionchanged', callback);
    },

    stop: function (success, error, params) {
        try {
            if (controlPoint) controlPoint.stop();
            success();
        } catch (e) {
            error(e);
        }
    },

    reset: function (success, error, params) {
        try {
            if (controlPoint) controlPoint.reset();
            success();
        } catch (e) {
            error(e);
        }
    }
};

require("cordova/exec/proxy").add("SSDP", module.exports);