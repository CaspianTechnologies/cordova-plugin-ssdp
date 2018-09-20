const exec = require('cordova/exec');
const device = require('cordova-plugin-device.device');

const devices = new Map();
let deviceDiscoveredClientCallback = null;
let deviceGoneClientCallback = null;

const maxAgePattern = new RegExp(/max-age\s*=\s*(\d+)/);
let cacheTimer = null;

function registerDevice(device) {
  if (devices.has(device.usn)) {
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
  const registeredDevice = devices.get(device.usn);
  if (!registeredDevice) {
    return;
  }

  devices.delete(registeredDevice.usn)
  
  if (deviceGoneClientCallback) {
    deviceGoneClientCallback(registeredDevice);
  }
}

function unregisterDevicesFromGoneNetwork(goneNetworkId) {
  for (let device of devices.values()) {
    if (device.networkId === goneNetworkId.toString()) {
      unregisterDevice(device);
    }
  }
}

exports.startSearching = function(target) {
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
            devices.delete(usn);
          }
        }
      }, 5000);
    }
    exec(unregisterDevicesFromGoneNetwork, null, "SSDP", "setNetworkGoneCallback", [unregisterDevicesFromGoneNetwork]);
    exec(success, error, "SSDP", "startSearching", [target]);
  });
}

exports.startAdvertising = function(target, port) {
  return new Promise(function(success, error) {
    const name = device.model;
    const usn = device.uuid;
    exec(success, error, "SSDP", "startAdvertising", [target, port, name, usn]);
  });
}

exports.stop = function() {
  return new Promise(function(success, error) {
    if (cacheTimer) {
      clearInterval(cacheTimer);
      cacheTimer = null;
    }
    exec(success, error, "SSDP", "stop", []);
  });
}

exports.reset = function() {
  devices.clear();
}

exports.setDeviceDiscoveredCallback = function(callback) {
  deviceDiscoveredClientCallback = callback;
  exec(registerDevice, null, "SSDP", "setDeviceDiscoveredCallback", [registerDevice]);
}

exports.setDeviceGoneCallback = function(callback) {
  deviceGoneClientCallback = callback;
  exec(unregisterDevice, null, "SSDP", "setDeviceGoneCallback", [unregisterDevice]);
}