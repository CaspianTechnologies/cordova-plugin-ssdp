let controlPoint;
let discoveredCallback;

function initializeControlPoint() {
    return new Promise(function (success, error) {
        if (!controlPoint) {
            controlPoint = new SSDP.ControlPoint();
            controlPoint.addEventListener('devicediscovered', discoveredCallback);
            controlPoint.start().then(success, error);
        } else {
            success();
        }
    });
}

module.exports = {
    startSearching: function (success, error, params) {
        initializeControlPoint()
            .then(function () {
                const target = params[0];
                controlPoint.searchDevices(target).then(res => {
                    console.log('proxy - searchDevices complete. Res:', res);
                    success(res);
                }, error);
            })
            .catch(error);
    },

    setDiscoveredCallback: function (callback, _, params) {
        discoveredCallback = callback;
    }
};

require("cordova/exec/proxy").add("SSDP", module.exports);