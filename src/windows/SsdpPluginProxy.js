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
                controlPoint.searchDevices().then(res => {
                    console.log('proxy - searchDevices complete. Res:', res);
                    success(res);
                }, error);
            }, error);
    },

    setDiscoveredCallback: function (callback, _, params) {
        initializeControlPoint();
        controlPoint.addEventListener('devicediscovered', callback);
    },

    setGoneCallback: function (callback, _, params) {
        initializeControlPoint();
        controlPoint.addEventListener('devicegone', callback);
    }
};

require("cordova/exec/proxy").add("SSDP", module.exports);