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

    setDiscoveredCallback: function (_1, _2, params) {
        initializeControlPoint();
        const callback = params[0];
        controlPoint.addEventListener('devicediscovered', callback);
    },

    setGoneCallback: function (_1, _2, params) {
        initializeControlPoint();
        const callback = params[0];
        controlPoint.addEventListener('devicegone', callback);
    },

    stop: function (success, error, params) {
        try {
            if (controlPoint) controlPoint.stop();
            success();
        } catch (e) {
            error(e);
        }
    }
};

require("cordova/exec/proxy").add("SSDP", module.exports);