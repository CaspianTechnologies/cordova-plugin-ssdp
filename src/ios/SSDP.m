#import "SSDP.h"
#import <Cordova/CDVPlugin.h>
#import <SystemConfiguration/CaptiveNetwork.h>
#import "GCDAsyncUdpSocket.h"
#import "SSDPMessage.h"
#import "SSDPServiceTypes.h"
#import "SSDPService.h"
#import "Reachability.h"
#import "SMTWiFiStatus.h"

NSString *const SSDPMulticastGroupAddress = @"239.255.255.250";
int const SSDPMulticastUDPPort = 1900;
const int BYEBYE_TAG = 1000;

@interface SSDP () {
    SSDPServiceBrowser *_browser;
    
    bool lastIsEnabled;
    bool lastIsConnected;
    NSString *lastNetworkId;
    Reachability* reachability;
    NSString *deviceDiscoveredCallbackId;
    NSString *deviceGoneCallbackId;
    NSString *networkGoneCallbackId;
    NSString *availabilityChangedCallbackId;
    NSString *adapterStatusChangedCallbackId;
    NSString *connectionChangedCallbackId;
    
    NSString *stopCallbackId;
    GCDAsyncUdpSocket *multicastSocket;
    GCDAsyncUdpSocket *unicastSocket;
    BOOL isRunning;
    NSTimer *timer;
    NSString *target;
    NSString *usn;
    NSNumber *port;
    NSString *name;
}

@property (nonatomic) NetworkStatus currentNetworkStatus;

@end

@implementation SSDP

-(void)pluginInitialize {
    reachability = [Reachability reachabilityForInternetConnection];
    [reachability stopNotifier];
    [reachability startNotifier];
}

-(NSString *)getFakeAdapterId {
    return [[[UIDevice currentDevice] identifierForVendor] UUIDString];
}

-(NSString *)getCurrentWiFiName {
    NSString *wifiName = nil;
    NSArray *interFaceNames = (__bridge_transfer NSArray *)CNCopySupportedInterfaces();
    
    for (NSString *name in interFaceNames) {
        NSDictionary *info = (__bridge_transfer NSDictionary *)CNCopyCurrentNetworkInfo((__bridge CFStringRef)name);
        
        if (info && info[@"SSID"]) {
            wifiName = info[@"SSID"];
        } else {
            wifiName = [[UIDevice currentDevice] name]; // for personal hotspot info is null
        }
    }
    
    return wifiName;
}

-(void)stopReachability {
    [reachability stopNotifier];
}

- (void)startSearching:(CDVInvokedUrlCommand*)command {
    NSLog(@"starting search");
    
    [self.commandDelegate runInBackground:^{
        
        [self addSearchingObservers];
        
        target = [command.arguments objectAtIndex:0];
        
        
        self.currentNetworkStatus = [reachability currentReachabilityStatus];
        lastIsEnabled = [self isEnabled];
        lastIsConnected = [self isConnected];
        
        NSDictionary *availableInterfaces = [SSDPServiceBrowser availableNetworkInterfaces];
        if (self.currentNetworkStatus == NotReachable) {
            return;
        }
        
        if (self.currentNetworkStatus == ReachableViaWWAN && !availableInterfaces[@"bridge100"]) {
            return;
        }
        
        if (_browser) {
            [_browser stopBrowsingForServices];
        } else {
            _browser = [[SSDPServiceBrowser alloc] init];
            _browser.delegate = self;
        }
        
        [_browser startBrowsingForServices:target];
        CDVPluginResult* pluginResult = nil;
        pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK];
        [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
    }];
}

-(void) invokeConnectionChangedCallback {
    self.currentNetworkStatus = [reachability currentReachabilityStatus];
    
    if (connectionChangedCallbackId.length == 0) {
        return;
    }
    [self checkIfConnectedAndInvokeConnectionChangedCallback];
    
}

- (void) handleNetworkChangeForAdvertising:(NSNotification *)notice
{
    self.currentNetworkStatus = [reachability currentReachabilityStatus];
    [self restartAdvertising];
    [self invokeConnectionChangedCallback];
}

- (void) handleNetworkChangeForSearching:(NSNotification *)notice
{
    self.currentNetworkStatus = [reachability currentReachabilityStatus];
    [self invokeConnectionChangedCallback];
    
    if (networkGoneCallbackId.length > 0) {
        CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsString:lastNetworkId];
        [pluginResult setKeepCallbackAsBool:YES];
        [self.commandDelegate sendPluginResult:pluginResult callbackId:networkGoneCallbackId];
    }
    
    [self restartSearching];
}

-(void)invokeAdapterStatusChangedCallback {
    bool isEnabled = [self isEnabled];
    if (adapterStatusChangedCallbackId.length > 0 && isEnabled != lastIsEnabled) {
        lastIsEnabled = isEnabled;
        CDVPluginResult* pluginResult1 = nil;
        NSDictionary *result = @{@"enabled" : @(isEnabled), @"adapterId" : [self getFakeAdapterId]};
        pluginResult1 = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsDictionary:result];
        [pluginResult1 setKeepCallbackAsBool:YES];
        [self.commandDelegate sendPluginResult:pluginResult1 callbackId:adapterStatusChangedCallbackId];
    }
}

-(void)checkIfConnectedAndInvokeConnectionChangedCallback {
    bool isConnected = [self isConnected];
    
    if (isConnected == lastIsConnected) return;
    
    lastIsConnected = isConnected;
    CDVPluginResult* pluginResult2 = nil;
    NSDictionary *result = @{@"connected" : @(isConnected), @"adapterId" : [self getFakeAdapterId], @"wifiName": [self getCurrentWiFiName]};
    pluginResult2 = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsDictionary:result];
    [pluginResult2 setKeepCallbackAsBool:YES];
    [self.commandDelegate sendPluginResult:pluginResult2 callbackId:connectionChangedCallbackId];
}

-(void)restartSearching {
    [self invokeAdapterStatusChangedCallback];
    [self invokeConnectionChangedCallback];
    
    [reachability stopNotifier];
    [reachability startNotifier];
    self.currentNetworkStatus = [reachability currentReachabilityStatus];
    
    NSDictionary *availableInterfaces = [SSDPServiceBrowser availableNetworkInterfaces];
    if (self.currentNetworkStatus == NotReachable) {
        if (_browser) {
            [_browser stopBrowsingForServices];
        }
        return;
    }
    
    if (self.currentNetworkStatus == ReachableViaWWAN && !availableInterfaces[@"bridge100"]) {
        if (_browser) {
            [_browser stopBrowsingForServices];
        }
        return;
    }
    
    if (self.currentNetworkStatus == ReachableViaWiFi && [availableInterfaces count] == 0) {
        [NSTimer scheduledTimerWithTimeInterval:3 repeats:NO block:^(NSTimer *timer){
            [self restartSearching];
        }];
        return;
    }
    
    if (_browser) {
        [_browser stopBrowsingForServices];
    } else {
        _browser = [[SSDPServiceBrowser alloc] init];
        _browser.delegate = self;
    }
    [_browser startBrowsingForServices:target];
}

-(void)addSearchingObservers {
    [[NSNotificationCenter defaultCenter] removeObserver:self
                                                 name:UIApplicationWillResignActiveNotification
                                               object:nil];
    
    [[NSNotificationCenter defaultCenter] removeObserver:self
                                                name:UIApplicationDidBecomeActiveNotification
                                              object:nil];
    
    [[NSNotificationCenter defaultCenter] removeObserver:self
                                                 name:kReachabilityChangedNotification object:nil];
    
    [[NSNotificationCenter defaultCenter] addObserver:self
                                             selector:@selector(stopReachability)
                                                 name:UIApplicationWillResignActiveNotification
                                               object:nil];
    
    [[NSNotificationCenter defaultCenter] addObserver:self
                                            selector:@selector(restartSearching)
                                                name:UIApplicationDidBecomeActiveNotification
                                              object:nil];
    
    [[NSNotificationCenter defaultCenter] addObserver:self
                                             selector:@selector(handleNetworkChangeForSearching:)
                                                 name:kReachabilityChangedNotification object:nil];

    [reachability stopNotifier];
    [reachability startNotifier];
}

-(void)addAdvertisingObservers {
    [[NSNotificationCenter defaultCenter] removeObserver:self
                                                    name:UIApplicationWillResignActiveNotification
                                                  object:nil];
    
    [[NSNotificationCenter defaultCenter] removeObserver:self
                                                    name:UIApplicationDidBecomeActiveNotification
                                                  object:nil];
    
    [[NSNotificationCenter defaultCenter] removeObserver:self
                                                    name:kReachabilityChangedNotification object:nil];
    
    [[NSNotificationCenter defaultCenter] addObserver:self
                                             selector:@selector(stopReachability)
                                                 name:UIApplicationWillResignActiveNotification
                                               object:nil];
    
    [[NSNotificationCenter defaultCenter] addObserver:self
                                             selector:@selector(restartAdvertising)
                                                 name:UIApplicationDidBecomeActiveNotification
                                               object:nil];
    
    [[NSNotificationCenter defaultCenter] addObserver:self
                                             selector:@selector(handleNetworkChangeForAdvertising:)
                                                 name:kReachabilityChangedNotification object:nil];

    [reachability stopNotifier];
    [reachability startNotifier];
}

- (void)startAdvertising:(CDVInvokedUrlCommand*)command {
    [self addAdvertisingObservers];
    
    lastIsEnabled = [self isEnabled];
    lastIsConnected = [self isConnected];
    
    CDVPluginResult* pluginResult = nil;
    
    target = [command.arguments objectAtIndex:0];
    port = [command.arguments objectAtIndex:1];
    name = [command.arguments objectAtIndex:2];
    usn = [command.arguments objectAtIndex:3];
    
    NSError *error = nil;
    if ([self initializeAdvertisingSockets:target Port:port Name:name USN:usn error:error]) {
        pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK];
        [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
    } else {
        pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:error.localizedDescription];
        [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
    }
    
    NetworkStatus remoteHostStatus = [reachability currentReachabilityStatus];
    if(remoteHostStatus == NotReachable)
    {
        NSLog(@"no");
    }
    else if (remoteHostStatus == ReachableViaWiFi)
    {
        NSLog(@"wifi");
    }
    else if (remoteHostStatus == ReachableViaWWAN)
    {
        NSLog(@"cell");
    }
}

-(bool)initializeAdvertisingSockets:(NSString *)target Port:(NSNumber *)port Name:(NSString *)name USN:(NSString *)usn error:(NSError *)error{
    NSDictionary *interfaces = [SSDPServiceBrowser availableNetworkInterfaces];
    //    NSData *sourceAddress = _networkInterface? interfaces[_networkInterface] : nil;
    NSData *sourceAddress = [[interfaces allValues] firstObject];
    
    multicastSocket = [[GCDAsyncUdpSocket alloc] initWithDelegate:self delegateQueue:dispatch_get_main_queue()];
    
    NSString *errorMessage;
    if (![multicastSocket enableReusePort:YES error:&error]) {
        errorMessage = [NSString stringWithFormat:@"Error enabling port reuse: %@", [error localizedDescription]];
        NSLog(@"%@", errorMessage);
        return false;
    }
    
    if (![multicastSocket bindToPort:SSDPMulticastUDPPort error:&error]) {
        errorMessage = [NSString stringWithFormat:@"Error binding to port 1900: %@", [error localizedDescription]];
        NSLog(@"%@", errorMessage);
        return false;
    }
    if (![multicastSocket enableBroadcast:YES error:&error]) {
        errorMessage = [NSString stringWithFormat:@"Error enabling broadcast: %@", [error localizedDescription]];
        NSLog(@"%@", errorMessage);
        return false;
    }
    if (![multicastSocket joinMulticastGroup:SSDPMulticastGroupAddress onInterface:[[GCDAsyncUdpSocket class] hostFromAddress:sourceAddress] error:&error]) {
        errorMessage = [NSString stringWithFormat:@"Error joining multicast group: %@", [error localizedDescription]];
        NSLog(@"%@", errorMessage);
        return false;
    }
    if (![multicastSocket beginReceiving:&error])
    {
        [multicastSocket close];
        errorMessage = [NSString stringWithFormat:@"Error begin receiving for multicast socket: %@", [error localizedDescription]];
        NSLog(@"%@", errorMessage);
        return false;
    }
    
    timer = [NSTimer scheduledTimerWithTimeInterval: 10
                                             target: self
                                           selector: @selector(sendAliveMessage:)
                                           userInfo: nil
                                            repeats: YES];
    
    isRunning = YES;
    
    unicastSocket = [[GCDAsyncUdpSocket alloc] initWithDelegate:self delegateQueue:dispatch_get_main_queue()];
    
    if (![unicastSocket enableReusePort:YES error:&error]) {
        errorMessage = [NSString stringWithFormat:@"Error enabling port reuse (unicast socket): %@", [error localizedDescription]];
        NSLog(@"%@", errorMessage);
        return false;
    }
    if (![unicastSocket bindToPort:0 error:&error])
    {
        errorMessage = [NSString stringWithFormat:@"Error binding to port 0 (unicast socket): %@", [error localizedDescription]];
        NSLog(@"%@", errorMessage);
        return false;
    }
    
    if (![unicastSocket beginReceiving:&error])
    {
        [unicastSocket close];
        errorMessage = [NSString stringWithFormat:@"Error begin receiving for unicast socket: %@", [error localizedDescription]];
        NSLog(@"%@", errorMessage);
        return false;
    }
    
    return true;
}

-(void)restartAdvertising {
    [self invokeAdapterStatusChangedCallback];
    [self invokeConnectionChangedCallback];
    [reachability stopNotifier];
    [reachability startNotifier];
    if (multicastSocket) {
        [multicastSocket close];
        multicastSocket = nil;
        [unicastSocket close];
        unicastSocket = nil;
    }
    
    NSError *error = nil;
    [self initializeAdvertisingSockets:target Port:port Name:name USN:usn error:error];
    
    if (error) {
        NSLog(@"ERROR RESTARTING ADVERTISING: %@", [error localizedDescription]);
    }
    
}

-(void)sendAliveMessage:(NSTimer *)timer {
    NSString *msg = [NSString stringWithFormat:@"NOTIFY * HTTP/1.1\r\nHOST: %@:%i\r\nCACHE-CONTROL: max-age = 30\r\nNT: %@\r\nNTS: ssdp:alive\r\nSERVER: %@\r\nUSN: %@\r\nPORT: %i\r\n\r\n", SSDPMulticastGroupAddress, SSDPMulticastUDPPort, target, name, usn, [port intValue]];
    
    NSData *data = [msg dataUsingEncoding:NSUTF8StringEncoding];
    [multicastSocket sendData:data toHost:SSDPMulticastGroupAddress port:SSDPMulticastUDPPort withTimeout:-1 tag:0];
}

-(void)sendByeByeMessage {
    NSString *msg = [NSString stringWithFormat:@"NOTIFY * HTTP/1.1\r\nHOST: %@:%i\r\nNT: %@\r\nNTS: ssdp:byebye\r\nSERVER: %@\r\nUSN: %@\r\nPORT: %i\r\n\r\n", SSDPMulticastGroupAddress, SSDPMulticastUDPPort, target, name, usn, [port intValue]];
    
    NSData *data = [msg dataUsingEncoding:NSUTF8StringEncoding];
    [multicastSocket sendData:data toHost:SSDPMulticastGroupAddress port:SSDPMulticastUDPPort withTimeout:-1 tag:BYEBYE_TAG];
}

- (void)udpSocket:(GCDAsyncUdpSocket *)sock didSendDataWithTag:(long)tag {
    if (tag == BYEBYE_TAG) {
        [multicastSocket close];
        multicastSocket = nil;
        // or
        // [sock close];
        [unicastSocket close];
        unicastSocket = nil;
        
        if (stopCallbackId.length == 0) return;
        
        CDVPluginResult* pluginResult = nil;
        pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK];
        [self.commandDelegate sendPluginResult:pluginResult callbackId:stopCallbackId];
    }
}

- (void)udpSocket:(GCDAsyncUdpSocket *)sock didReceiveData:(NSData *)data
      fromAddress:(NSData *)address
withFilterContext:(id)filterContext
{
    if (!isRunning) return;
    
    if (sock != multicastSocket) {
        return;
    }
    
    NSString *msg = [[NSString alloc] initWithData:data encoding:NSUTF8StringEncoding];
    if (msg)
    {
        /* If you want to get a display friendly version of the IPv4 or IPv6 address, you could do this:
         
         NSString *host = nil;
         uint16_t port = 0;
         [GCDAsyncUdpSocket getHost:&host port:&port fromAddress:address];
         
         */
        
        SSDPMessage *message = [SSDPMessage SSDPMessageWithString:msg];
        //        NSDictionary *messageDict = [self parseSSDPMessage:msg];
        //        NSLog(@"message: %@", messageDict);
        
//        NSLog(@"msg: %@", msg);
//        NSLog(@"SSDPMessage: %@", message);
        
        if (message.messageType == SsdpMessageType_SearchRequest && message.ST && [message.ST isEqualToString:target]) {
            NSString *message = [NSString stringWithFormat: @"HTTP/1.1 200 OK\r\nCACHE-CONTROL: max-age = 30\r\nEXT:\r\nSERVER: %@\r\nST: %@\r\nUSN: %@\r\nPORT: %i\r\n\r\n", name, target, usn, [port intValue]];
            NSData *data2 = [message dataUsingEncoding:NSUTF8StringEncoding];
            [unicastSocket sendData:data2 toAddress:address withTimeout:-1 tag:0];
        }
        
    }
    else
    {
        NSLog(@"Error converting received data into UTF-8 String");
        //        [self logError:@"Error converting received data into UTF-8 String"];
    }
}

- (void)stop:(CDVInvokedUrlCommand*)command {
    
    [[NSNotificationCenter defaultCenter] removeObserver:self name:UIApplicationDidBecomeActiveNotification object:nil];
    
    stopCallbackId = command.callbackId;
    
    [_browser stopBrowsingForServices];
    
    if (multicastSocket) {
        [timer invalidate];
        timer = nil;
        [self sendByeByeMessage];
    } else {
        if (stopCallbackId.length > 0) {
            CDVPluginResult* pluginResult = nil;
            pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK];
            [self.commandDelegate sendPluginResult:pluginResult callbackId:stopCallbackId];
        }
    }
}

- (void)setNetworkGoneCallback:(CDVInvokedUrlCommand*)command {
   networkGoneCallbackId = command.callbackId;
}

- (void)setDeviceDiscoveredCallback:(CDVInvokedUrlCommand*)command {
    deviceDiscoveredCallbackId = command.callbackId;
}
- (void)setDeviceGoneCallback:(CDVInvokedUrlCommand*)command {
    deviceGoneCallbackId = command.callbackId;
}

/*
 * Processes the response received from a UPnP device.
 * Converts the string response to a NSMutableDictionary.
 */
- (NSMutableDictionary *)processResponse:(NSString *)message
{
    NSArray *msgLines = [message componentsSeparatedByString:@"\r"];
    //    NSLog(@"total lines:%lu", [msgLines count]);
    NSMutableDictionary *data = [[NSMutableDictionary alloc] init];
    
    int i = 0;
    for (i = 0; i < [msgLines count]; i++)
    {
        //   NSLog(@"working on:%@", msgLines[i]);
        NSRange range = [msgLines[i] rangeOfString:@":"];
        
        if(range.length == 1){
            NSRange p1range = NSMakeRange(0, range.location);
            NSString *part1 = [msgLines[i] substringWithRange:p1range];
            part1 = [part1 stringByTrimmingCharactersInSet:
                     [NSCharacterSet whitespaceAndNewlineCharacterSet]];
            //          NSLog(@"%@", part1);
            NSRange p2range = NSMakeRange(range.location + 1 , [msgLines[i] length] - range.location - 1);
            NSString *part2 = [msgLines[i] substringWithRange:p2range];
            part2 = [part2 stringByTrimmingCharactersInSet:
                     [NSCharacterSet whitespaceAndNewlineCharacterSet]];
            //          NSLog(@"%@", part2);
            
            data[part1] = part2;
        }
    }
    return data;
}

- (void) ssdpBrowser:(SSDPServiceBrowser *)browser didNotStartBrowsingForServices:(NSError *)error {
    NSLog(@"SSDP Browser got error: %@", error);
    [NSTimer scheduledTimerWithTimeInterval:3 repeats:NO block:^(NSTimer *timer){
        [self restartSearching];
    }];
}

- (void) ssdpBrowser:(SSDPServiceBrowser *)browser didFindService:(SSDPService *)service {
    NSLog(@"SSDP Browser found: %@", service);
    
    if (![service.serviceType isEqualToString:target]) {
        return;
    }
    
    NSString *currentWiFiName = [self getCurrentWiFiName];
    if (![lastNetworkId isEqualToString:currentWiFiName] && networkGoneCallbackId.length > 0) {
        CDVPluginResult* pluginResult = nil;
        pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsString:lastNetworkId];
        [pluginResult setKeepCallbackAsBool:YES];
        [self.commandDelegate sendPluginResult:pluginResult callbackId:networkGoneCallbackId];
    }
    
    lastNetworkId = currentWiFiName;
    
    if (deviceDiscoveredCallbackId.length == 0) return;
    
    NSDictionary *device = @{@"ip": service.host,
                             @"port" : service.port,
                             @"name" :  service.server,
                             @"usn" :  service.uniqueServiceName,
                             @"cacheControl" : service.cacheControl,
                             @"networkId" : lastNetworkId};
    
    CDVPluginResult* pluginResult = nil;
    pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsDictionary:device];
    [pluginResult setKeepCallbackAsBool:YES];
    [self.commandDelegate sendPluginResult:pluginResult callbackId:deviceDiscoveredCallbackId];

}

- (void) ssdpBrowser:(SSDPServiceBrowser *)browser didRemoveService:(SSDPService *)service {
    NSLog(@"SSDP Browser removed: %@", service);
    if (![service.serviceType isEqualToString:SSDPServiceType_Spatium]) {
        return;
    }
    
    if (deviceGoneCallbackId.length == 0) return;
    
    NSDictionary *device = @{@"ip": service.host,
                             @"port" : service.port,
                             @"name" :  service.server,
                             @"usn" :  service.uniqueServiceName,
                             @"networkId" : [self getCurrentWiFiName]};
    
    CDVPluginResult* pluginResult = nil;
    pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsDictionary:device];
    [pluginResult setKeepCallbackAsBool:YES];
    [self.commandDelegate sendPluginResult:pluginResult callbackId:deviceGoneCallbackId];
}

- (void)setAvailabilityChangedCallback:(CDVInvokedUrlCommand*)command {
    availabilityChangedCallbackId = command.callbackId;
}

- (void)setAdapterStatusChangedCallback:(CDVInvokedUrlCommand*)command {
    adapterStatusChangedCallbackId = command.callbackId;

}

- (void)setConnectionChangedCallback:(CDVInvokedUrlCommand*)command {
    connectionChangedCallbackId = command.callbackId;
}

- (void)isAvailable:(CDVInvokedUrlCommand*)command {
    CDVPluginResult* pluginResult = nil;
    pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsBool:true];
    [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
}

- (bool)isEnabled {
    return [SMTWiFiStatus isWiFiEnabled];
}

- (void)isEnabled:(CDVInvokedUrlCommand*)command {
    CDVPluginResult* pluginResult = nil;
    pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsBool:[self isEnabled]];
    [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
}

- (void)isConnected:(CDVInvokedUrlCommand*)command {
    bool isConnected = [self isConnected];
    CDVPluginResult* pluginResult = nil;
    pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsBool:isConnected];
    [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
}

- (bool)isConnected {
    return [SMTWiFiStatus isWiFiConnected];
}

@end
