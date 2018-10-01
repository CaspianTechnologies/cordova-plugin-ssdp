#import "SSDP.h"
#import <Cordova/CDVPlugin.h>
#import <SystemConfiguration/CaptiveNetwork.h>

NSMutableArray *serviceArr;

//CDVInvokedUrlCommand *searchCommand;
//CDVInvokedUrlCommand *searchCommand;
NSString* deviceDiscoveredCallbackId;
NSString* setDeviceGoneCallbackId;


@implementation SSDP



- (void)echo:(CDVInvokedUrlCommand*)command
{
    CDVPluginResult* pluginResult = nil;
    NSString* echo = [command.arguments objectAtIndex:0];

    if (echo != nil && [echo length] > 0) {
        pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsString:echo];
    } else {
        pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR];
    }

    [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
}

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
    NSString* target = [command.arguments objectAtIndex:0];

    NSLog(@"target: %@", target);
    [self.commandDelegate runInBackground:^{
        
        CDVPluginResult* pluginResult = nil;
        if (target == nil)
        {
            pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:@"service (target) not provided"];
        }
        else
        {
            
            serviceArr = [[NSMutableArray alloc] init];
            
            // Open a socket
            int sd = socket(PF_INET, SOCK_DGRAM, IPPROTO_UDP);
            if (sd <= 0) {
                NSLog(@"Error: Could not open socket");
                pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:@"TX socket creation failed"];
            }
            else {
                // Set socket options
                int broadcastEnable = 1;
                int ret = setsockopt(sd, SOL_SOCKET, SO_BROADCAST, &broadcastEnable, sizeof(broadcastEnable));
                if (ret) {
                    NSLog(@"Error: setsockopt failed to enable broadcast mode");
                    pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:@"TX socket setsockopt failed"];
                    close(sd);
                }
                else {
                    
                    // Configure the broadcast IP and port
                    struct sockaddr_in broadcastAddr;
                    memset(&broadcastAddr, 0, sizeof broadcastAddr);
                    broadcastAddr.sin_family = AF_INET;
                    inet_pton(AF_INET, "239.255.255.250", &broadcastAddr.sin_addr);
                    broadcastAddr.sin_port = htons(1900);
                    
                    // Send the broadcast request for the given service type
                     NSString *request = [[@"M-SEARCH * HTTP/1.1\r\nHOST: 239.255.255.250:1900\r\nMAN: \"ssdp:discover\"\r\nST: " stringByAppendingString:target] stringByAppendingString:@"\r\nMX: 1\r\n\r\n"];
                    char *requestStr = [request UTF8String];
                    
                    ret = sendto(sd, requestStr, strlen(requestStr), 0, (struct sockaddr*)&broadcastAddr, sizeof broadcastAddr);
                    if (ret < 0) {
                        NSLog(@"Error: Could not send broadcast");
                        pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:@"sendto failed"];
                        close(sd);
                    }
                    else {
                        
                        NSLog(@"ret:%d", ret);
                        NSLog(@"Bcast msg sent");
                        
                        
                        NSLog(@"recv: On to listening");
                        
                        // set timeout to 2 seconds.
                        struct timeval timeV;
                        timeV.tv_sec = 2;
                        timeV.tv_usec = 0;
                        
                        if (setsockopt(sd, SOL_SOCKET, SO_RCVTIMEO, &timeV, sizeof(timeV)) == -1) {
                            NSLog(@"Error: listenForPackets - setsockopt failed");
                            pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:@"RX socket setsockopt failed"];
                            close(sd);
                        }
                        else {
                            NSLog(@"recv: socketopt set");
                            
                            // receive
                            struct sockaddr_in receiveSockaddr;
                            socklen_t receiveSockaddrLen = sizeof(receiveSockaddr);
                            
                            size_t bufSize = 9216;
                            void *buf = malloc(bufSize);
                            NSLog(@"recv: listening now: %d", sd);
                            
                            
                            // Keep listening till the socket timeout event occurs
                            while (true)
                            {
                                ssize_t result = recvfrom(sd, buf, bufSize, 0,
                                                          (struct sockaddr *)&receiveSockaddr,
                                                          (socklen_t *)&receiveSockaddrLen);
                                //                                NSLog(@"got sthing:%ld", result);
                                
                                if (result < 0)
                                {
                                    NSLog(@"timeup");
                                    break;
                                }
                                
                                NSData *data = nil;
                                data = [NSData dataWithBytesNoCopy:buf length:result freeWhenDone:NO];
                                
                                NSString *msg = [[NSString alloc] initWithData:data encoding:NSUTF8StringEncoding];
                                
                                char* address = inet_ntoa(receiveSockaddr.sin_addr);
                                printf("address: %s\n",address);
                                NSMutableDictionary *messageDict = [self processResponse:msg];
                                messageDict[@"IP"] = [NSString stringWithUTF8String:address];
                                [serviceArr addObject: messageDict];
                                
                                NSDictionary *device = @{@"ip": messageDict[@"IP"],
                                                         @"port" : [NSNumber numberWithInt:[messageDict[@"PORT"] intValue]],
                                                         @"name" :  messageDict[@"SERVER"],
                                                         @"usn" :  messageDict[@"USN"],
                                                         @"cacheControl" : messageDict[@"CACHE-CONTROL"],
                                                         @"networkId" : [self getCurrentWIFIName]};
                            
                                NSLog(@"device: %@", device);
                                
                                pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsDictionary:device];
                                [pluginResult setKeepCallbackAsBool:YES];
                                [self.commandDelegate sendPluginResult:pluginResult callbackId:deviceDiscoveredCallbackId];
                            }
                            
                            free(buf);
                            close(sd);
                            
//                            pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsArray:serviceArr];
                        }
                    }
                }
            }
            [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
        }
    }];

    // [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];

}
- (void)startAdvertising:(CDVInvokedUrlCommand*)command {
    CDVPluginResult* pluginResult = nil;

    NSString* target = [command.arguments objectAtIndex:0]; 
    NSNumber* port = [command.arguments objectAtIndex:1];
    NSString* name = [command.arguments objectAtIndex:2];
    NSString* usn = [command.arguments objectAtIndex:3];

    NSLog(@"target: %@", target);
    NSLog(@"port: %@", [port stringValue]);
    NSLog(@"name: %@", name);
    NSLog(@"usn: %@", usn);

    // [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];

}
- (void)stop:(CDVInvokedUrlCommand*)command {
//    CDVPluginResult* pluginResult = nil;

    // [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
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
    CDVPluginResult* pluginResult = nil;

    // [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];

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

@end
