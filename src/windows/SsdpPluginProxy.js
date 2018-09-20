let controlPoint;

function initializeControlPoint() {
    if (!controlPoint) {
        controlPoint = new SSDP.ControlPoint();
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