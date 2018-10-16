#import "SSDP.h"
#import <Cordova/CDVPlugin.h>
#import <SystemConfiguration/CaptiveNetwork.h>
#import "GCDAsyncUdpSocket.h"
#import "SSDPMessage.h"
#import "SSDPServiceTypes.h"
#import "SSDPService.h"
#import "Reachability.h"

NSString *const SSDPMulticastGroupAddress = @"239.255.255.250";
int const SSDPMulticastUDPPort = 1900;
const int BYEBYE_TAG = 1000;

@interface SSDP () {
    SSDPServiceBrowser *_browser;
    
    NetworkStatus currentNetworkStatus;
    NSString *lastNetworkId;
    Reachability* reachability;
    NSString *deviceDiscoveredCallbackId;
    NSString *deviceGoneCallbackId;
    NSString *networkGoneCallbackId;
    NSString *setDeviceGoneCallbackId;
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
@end

@implementation SSDP

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

- (void)startSearching:(CDVInvokedUrlCommand*)command {
    NSLog(@"starting search");
    
    [self.commandDelegate runInBackground:^{
        
        [[NSNotificationCenter defaultCenter]addObserver:self
                                                selector:@selector(restartSearching)
                                                    name:UIApplicationDidBecomeActiveNotification
                                                  object:nil];
        
        target = [command.arguments objectAtIndex:0];
        
        [[NSNotificationCenter defaultCenter] addObserver:self
                                                 selector:@selector(handleNetworkChangeForSearching:)
                                                     name:kReachabilityChangedNotification object:nil];
        reachability = [Reachability reachabilityForInternetConnection];
        [reachability startNotifier];
        NetworkStatus remoteHostStatus = [reachability currentReachabilityStatus];
        currentNetworkStatus = remoteHostStatus;
        
        NSDictionary *availableInterfaces = [SSDPServiceBrowser availableNetworkInterfaces];
        if (currentNetworkStatus == NotReachable) {
            return;
        }
        
        if (currentNetworkStatus == ReachableViaWWAN && !availableInterfaces[@"bridge100"]) {
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

- (void) handleNetworkChangeForAdvertising:(NSNotification *)notice
{
    currentNetworkStatus = [reachability currentReachabilityStatus];
    [self restartAdvertising];
}

- (void) handleNetworkChangeForSearching:(NSNotification *)notice
{
    currentNetworkStatus = [reachability currentReachabilityStatus];
    
    CDVPluginResult* pluginResult = nil;
    pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsString:lastNetworkId];
    [pluginResult setKeepCallbackAsBool:YES];
    [self.commandDelegate sendPluginResult:pluginResult callbackId:networkGoneCallbackId];
    
    [self restartSearching];
}

-(void)restartSearching {
    currentNetworkStatus = [reachability currentReachabilityStatus];
    NSDictionary *availableInterfaces = [SSDPServiceBrowser availableNetworkInterfaces];
    if (currentNetworkStatus == NotReachable) {
        if (_browser) {
            [_browser stopBrowsingForServices];
        }
        return;
    }
    
    if (currentNetworkStatus == ReachableViaWWAN && !availableInterfaces[@"bridge100"]) {
        if (_browser) {
            [_browser stopBrowsingForServices];
        }
        return;
    }
    
    if (currentNetworkStatus == ReachableViaWiFi && [availableInterfaces count] == 0) {
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

- (void)startAdvertising:(CDVInvokedUrlCommand*)command {
    CDVPluginResult* pluginResult = nil;
    
    [[NSNotificationCenter defaultCenter] addObserver:self
                                             selector:@selector(restartAdvertising)
                                                 name:UIApplicationDidBecomeActiveNotification
                                               object:nil];
    
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
    
    [[NSNotificationCenter defaultCenter] addObserver:self
                                             selector:@selector(handleNetworkChangeForAdvertising:)
                                                 name:kReachabilityChangedNotification object:nil];
    reachability = [Reachability reachabilityForInternetConnection];
    [reachability startNotifier];
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
        CDVPluginResult* pluginResult = nil;
        pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK];
        [self.commandDelegate sendPluginResult:pluginResult callbackId:stopCallbackId];
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
    if (![lastNetworkId isEqualToString:currentWiFiName]) {
        CDVPluginResult* pluginResult = nil;
        pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsString:lastNetworkId];
        [pluginResult setKeepCallbackAsBool:YES];
        [self.commandDelegate sendPluginResult:pluginResult callbackId:networkGoneCallbackId];
    }
    
    lastNetworkId = currentWiFiName;
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

@end
