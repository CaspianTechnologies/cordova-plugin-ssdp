const exec = require('cordova/exec');

const devices = new Map();
let deviceDiscoveredClientCallback = null;
let deviceGoneClientCallback = null;
let availabilityChangedCallback = null;
let adapterStatusChangedCallback = null;
let connectionChangedCallback = null;

const maxAgePattern = new RegExp(/max-age\s*=\s*(\d+)/);
let cacheTimer = null;

function registerDevice(device) {
  console.log('registerDevice callback, device:', device);
  if (devices.has(device.usn)) {
    console.log('registerDevice update cache for usn:', device.usn);
    const registeredDevice = devices.get(device.usn);
    registeredDevice.cacheControl = device.cacheControl;
    registeredDevice.date = new Date();
    return;
  }
  
  device.date = new Date();
  devices.set(device.usn, device);

  if (deviceDiscoveredClientCallback) {
    deviceDiscoveredClientCallback({
      ip: device.ip,
      port: device.port,
      name: device.name,
      usn: device.usn
    });
  }
}

function unregisterDevice(device) {
  console.log('unregisterDevice callback, device:', device);
  const registeredDevice = devices.get(device.usn);
  if (!registeredDevice) {
    return;
  }

  devices.delete(registeredDevice.usn);
  
  if (deviceGoneClientCallback) {
    deviceGoneClientCallback(registeredDevice);
  }
}

function unregisterDevicesFromGoneNetwork(goneNetworkId) {
  console.log('unregisterDevicesFromGoneNetwork, network:', goneNetworkId);
  for (let device of devices.values()) {
    if (device.networkId === goneNetworkId.toString()) {
      unregisterDevice(device);
    }
  }
}

function notifyAvailabilityChanged(event) {
  if (availabilityChangedCallback) {
    availabilityChangedCallback(event.available);
  }
}

function notifyAdapterStatusChanged(event) {
  if (adapterStatusChangedCallback) {
    adapterStatusChangedCallback(event.enabled);
  }
}

function notifyConnectionChanged(event) {
  if (connectionChangedCallback) {
    connectionChangedCallback(event.connected);
  }
}

exports.startSearching = function(target) {
  console.log('startSearching, target:', target);
  return new Promise(function(success, error) {
    if (!cacheTimer) {
      cacheTimer = setInterval(() => {
        for (let kv of devices.entries()) {
          const usn = kv[0];
          const device = kv[1];

          const match = device.cacheControl.match(maxAgePattern);
          if (!match) {
            continue;
          }

          const seconds = parseInt(match[1]);
          const dateWithCache = new Date(device.date);
          dateWithCache.setSeconds(dateWithCache.getSeconds() + seconds);
          const now = new Date();
          const isDeviceExpired = dateWithCache < now;

          if (isDeviceExpired) {
            unregisterDevice(device);
          }
        }
      }, 5000);
    }
    exec(unregisterDevicesFromGoneNetwork, null, "SSDP", "setNetworkGoneCallback", [unregisterDevicesFromGoneNetwork]);
    exec(success, error, "SSDP", "startSearching", [target]);
  });
};

exports.startAdvertising = function(target, name, usn, port) {
  console.log('startAdvertising', target, name, usn, port);
  return new Promise(function(success, error) {
    exec(success, error, "SSDP", "startAdvertising", [target, port, name, usn]);
  });
};

exports.stop = function() {
  console.log('stop');
  return new Promise(function(success, error) {
    if (cacheTimer) {
      clearInterval(cacheTimer);
      cacheTimer = null;
    }
    exec(success, error, "SSDP", "stop", []);
  });
};

exports.reset = function() {
  devices.clear();
};

exports.setDeviceDiscoveredCallback = function(callback) {
  deviceDiscoveredClientCallback = callback;
  exec(registerDevice, null, "SSDP", "setDeviceDiscoveredCallback", [registerDevice]);
};

exports.setDeviceGoneCallback = function(callback) {
  deviceGoneClientCallback = callback;
  exec(unregisterDevice, null, "SSDP", "setDeviceGoneCallback", [unregisterDevice]);
};

exports.setAvailabilityChangedCallback = function(callback) {
  availabilityChangedCallback = callback;
  exec(notifyAvailabilityChanged, null, "SSDP", "setAvailabilityChangedCallback", [notifyAvailabilityChanged]);
};

exports.setAdapterStatusChangedCallback = function(callback) {
  adapterStatusChangedCallback = callback;
  exec(notifyAdapterStatusChanged, null, "SSDP", "setAdapterStatusChangedCallback", [notifyAdapterStatusChanged]);
};

exports.setConnectionChangedCallback = function(callback) {
  connectionChangedCallback = callback;
  exec(notifyConnectionChanged, null, "SSDP", "setConnectionChangedCallback", [notifyConnectionChanged]);
};

exports.isAvailable = function() {
  return new Promise(function(success, error) {
    exec(success, error, "SSDP", "isAvailable", []);
  });
};

exports.isEnabled = function() {
  return new Promise(function(success, error) {
    exec(success, error, "SSDP", "isEnabled", []);
  });
};

exports.isConnected = function() {
  return new Promise(function(success, error) {
    exec(success, error, "SSDP", "isConnected", []);
  });
};