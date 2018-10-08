#import "SSDP.h"
#import <Cordova/CDVPlugin.h>
#import <SystemConfiguration/CaptiveNetwork.h>
#import "GCDAsyncUdpSocket.h"
#import "SSDPMessage.h"
#import "SSDPServiceTypes.h"
#import "SSDPService.h"

@interface SSDP () {
    SSDPServiceBrowser *_browser;
    NSMutableArray *_services;
}
@end

NSMutableArray *serviceArr;

//CDVInvokedUrlCommand *searchCommand;
//CDVInvokedUrlCommand *searchCommand;
NSString* deviceDiscoveredCallbackId;
NSString* deviceGoneCallbackId;
NSString* setDeviceGoneCallbackId;
NSString* stopCallbackId;
GCDAsyncUdpSocket *multicastSocket;
GCDAsyncUdpSocket *unicastSocket;
BOOL isRunning;
NSTimer *timer;
NSString *target;
NSString *usn;
NSNumber *port;
NSString *name;

const int BYEBYE_TAG = 1000;


@implementation SSDP

-(NSString *)getCurrentWIFIName {
    NSString *wifiName = nil;
    NSArray *interFaceNames = (__bridge_transfer NSArray *)CNCopySupportedInterfaces();
    
    for (NSString *name in interFaceNames) {
        NSDictionary *info = (__bridge_transfer NSDictionary *)CNCopyCurrentNetworkInfo((__bridge CFStringRef)name);
        
        if (info && info[@"SSID"]) {
            wifiName = info[@"SSID"];
        } else {
            wifiName = @"UNKNOWN";
        }
    }
    
    return wifiName;
}

- (void)startSearching:(CDVInvokedUrlCommand*)command {
    NSLog(@"starting search");
    
    [self.commandDelegate runInBackground:^{
        NSString* target = [command.arguments objectAtIndex:0];
        NSLog(@"target: %@", target);
        
        if (_browser) {
            [_browser stopBrowsingForServices];
            [_services removeAllObjects];
        } else {
            _browser = [[SSDPServiceBrowser alloc] init];
            _browser.delegate = self;
        }
        
        [_browser startBrowsingForServices:SSDPServiceType_Spatium];
        CDVPluginResult* pluginResult = nil;
        pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK];
        [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
    }];

}
- (void)startAdvertising:(CDVInvokedUrlCommand*)command {
    CDVPluginResult* pluginResult = nil;
    
    target = [command.arguments objectAtIndex:0];
    port = [command.arguments objectAtIndex:1];
    name = [command.arguments objectAtIndex:2];
    usn = [command.arguments objectAtIndex:3];
    
    multicastSocket = [[GCDAsyncUdpSocket alloc] initWithDelegate:self delegateQueue:dispatch_get_main_queue()];

    NSError *error = nil;

    NSString *errorMessage;
    if (![multicastSocket bindToPort:1900 error:&error]) {
        errorMessage = [NSString stringWithFormat:@"Error binding to port 1900: %@", [error localizedDescription]];
        pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:errorMessage];
        [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
        NSLog(@"%@", errorMessage);
        return;
    }
    if (![multicastSocket enableBroadcast:YES error:&error]) {
        errorMessage = [NSString stringWithFormat:@"Error enabling broadcast: %@", [error localizedDescription]];
        pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:errorMessage];
        [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
        NSLog(@"%@", errorMessage);
        return;
    }
    if (![multicastSocket joinMulticastGroup:@"239.255.255.250" error:&error]) {
        errorMessage = [NSString stringWithFormat:@"Error joining multicast group: %@", [error localizedDescription]];
        pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:errorMessage];
        [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
        NSLog(@"%@", errorMessage);
        return;
    }
    if (![multicastSocket beginReceiving:&error])
    {
        [multicastSocket close];
        errorMessage = [NSString stringWithFormat:@"Error begin receiving for multicast socket: %@", [error localizedDescription]];
        pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:errorMessage];
        [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
        NSLog(@"%@", errorMessage);
        return;
    }
    
    timer = [NSTimer scheduledTimerWithTimeInterval: 10
                                             target: self
                                           selector: @selector(sendAliveMessage:)
                                           userInfo: nil
                                            repeats: YES];

//    NSLog(@"Udp Echo server started on %@:%i",[udpSocket localHost_IPv4],[udpSocket localPort]);
    
    isRunning = YES;
    
    unicastSocket = [[GCDAsyncUdpSocket alloc] initWithDelegate:self delegateQueue:dispatch_get_main_queue()];
    
    if (![unicastSocket bindToPort:0 error:&error])
    {
        errorMessage = [NSString stringWithFormat:@"Error binding to port 0 (unicast socket): %@", [error localizedDescription]];
        pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:errorMessage];
        [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
        NSLog(@"%@", errorMessage);
        return;
    }
    
    if (![unicastSocket beginReceiving:&error])
    {
        [unicastSocket close];
        errorMessage = [NSString stringWithFormat:@"Error begin receiving for unicast socket: %@", [error localizedDescription]];
        pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:errorMessage];
        [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
        NSLog(@"%@", errorMessage);
        return;
    }

    pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK];
    [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
}

-(void)sendAliveMessage:(NSTimer *)timer {
    NSString *msg = [NSString stringWithFormat:@"NOTIFY * HTTP/1.1\r\nHOST: 239.255.255.250:1900\r\nCACHE-CONTROL: max-age = 30\r\nNT: %@\r\nNTS: ssdp:alive\r\nSERVER: %@\r\nUSN: %@\r\nPORT: %i\r\n\r\n", target, name, usn, [port intValue]];
    
    NSData *data = [msg dataUsingEncoding:NSUTF8StringEncoding];
    [multicastSocket sendData:data toHost:@"239.255.255.250" port:1900 withTimeout:-1 tag:0];
}

-(void)sendByeByeMessage {
    NSString *msg = [NSString stringWithFormat:@"NOTIFY * HTTP/1.1\r\nHOST: 239.255.255.250:1900\r\nNT: %@\r\nNTS: ssdp:byebye\r\nSERVER: %@\r\nUSN: %@\r\nPORT: %i\r\n\r\n", target, name, usn, [port intValue]];
    
    NSData *data = [msg dataUsingEncoding:NSUTF8StringEncoding];
    [multicastSocket sendData:data toHost:@"239.255.255.250" port:1900 withTimeout:-1 tag:BYEBYE_TAG];
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
    
//    NSString *host = @"";
//    UInt16 port1 = 0;
//    [GCDAsyncUdpSocket getHost:&host port:&port1 fromAddress:address];
//    NSLog(@"host: %@",host);
//    NSLog(@"port: %i",port1);
    
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
        
        NSLog(@"msg: %@", msg);
        NSLog(@"SSDPMessage: %@", message);
        
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
    
    stopCallbackId = command.callbackId;
    
    [_browser stopBrowsingForServices];
    [_services removeAllObjects];
    
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
//    CDVPluginResult* pluginResult = nil;

    // [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];

}

- (void)setDeviceDiscoveredCallback:(CDVInvokedUrlCommand*)command {
//    CDVPluginResult* pluginResult = nil;

    deviceDiscoveredCallbackId = command.callbackId;
    // [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];

}
- (void)setDeviceGoneCallback:(CDVInvokedUrlCommand*)command {
//    CDVPluginResult* pluginResult = nil;

    // [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
    deviceGoneCallbackId = command.callbackId;
}

/*
 * Processes the response received from a UPnP device.
 * Converts the string response to a NSMutableDictionary.
 */
- (NSMutableDictionary *)processResponse:(NSString *)message
{
    //    NSLog(@"%@", message);
    
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
    UIAlertView *alert = [[UIAlertView alloc] initWithTitle:error.domain message:error.localizedDescription delegate:self cancelButtonTitle:@"Ok" otherButtonTitles:nil];
    [alert show];
}

- (void) ssdpBrowser:(SSDPServiceBrowser *)browser didFindService:(SSDPService *)service {
    NSLog(@"SSDP Browser found: %@", service);
    
    if (![service.serviceType isEqualToString:SSDPServiceType_Spatium]) {
        return;
    }
          
    [_services insertObject:service atIndex:0];
    NSDictionary *device = @{@"ip": service.host,
                             @"port" : service.port,
                             @"name" :  service.server,
                             @"usn" :  service.uniqueServiceName,
                             @"cacheControl" : service.cacheControl,
                             @"networkId" : [self getCurrentWIFIName]};
    
    NSLog(@"device: %@", device);
    
    CDVPluginResult* pluginResult = nil;
    pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsDictionary:device];
    [pluginResult setKeepCallbackAsBool:YES];
    [self.commandDelegate sendPluginResult:pluginResult callbackId:deviceDiscoveredCallbackId];
    
    // NSIndexPath *indexPath = [NSIndexPath indexPathForRow:0 inSection:0];
    // TODO: [self.tableView insertRowsAtIndexPaths:@[indexPath] withRowAnimation:UITableViewRowAnimationAutomatic];
}

- (void) ssdpBrowser:(SSDPServiceBrowser *)browser didRemoveService:(SSDPService *)service {
    NSLog(@"SSDP Browser removed: %@", service);
    if (![service.serviceType isEqualToString:SSDPServiceType_Spatium]) {
        return;
    }
    
    // TODO: remove object with from services with service.uniqueServiceName
//    [_services removeObject]
//    [_services insertObject:service atIndex:0];
    NSDictionary *device = @{@"ip": service.host,
                             @"port" : service.port,
                             @"name" :  service.server,
                             @"usn" :  service.uniqueServiceName,
                             @"networkId" : [self getCurrentWIFIName]};
    
    NSLog(@"device: %@", device);
    
    CDVPluginResult* pluginResult = nil;
    pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsDictionary:device];
    [pluginResult setKeepCallbackAsBool:YES];
    [self.commandDelegate sendPluginResult:pluginResult callbackId:deviceGoneCallbackId];
}

@end
