const exec = require('cordova/exec');
const device = require('cordova-plugin-device.device');

exports.startSearching = function(target) {
  return new Promise(function(success, error) {
    exec(success, error, "SSDP", "startSearching", [target])
  });
}

exports.startAdvertising = function(target, port) {
  return new Promise(function(success, error) {
    const usn = device.uuid;
    exec(success, error, "SSDP", "startAdvertising", [target, port])
  });
}

exports.stop = function() {
  return new Promise(function(success, error) {
    const usn = device.uuid;
    exec(success, error, "SSDP", "stop", []);
  });
}

exports.setDiscoveredCallback = function(callback) {
  exec(callback, null, "SSDP", "setDiscoveredCallback", []);
}