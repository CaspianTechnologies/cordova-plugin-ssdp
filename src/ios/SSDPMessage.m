//
//  SSDPMessage.m
//  SSDP
//
//  Created by Alexei Vinidiktov on 04/10/2018.
//

#import "SSDPMessage.h"

@implementation SSDPMessage

+(SSDPMessage *)SSDPMessageWithString:(NSString *)message {
    SSDPMessage *result = [SSDPMessage new];
    [result parseSSDPMessage:message];
    return result;
}

-(void)parseSSDPMessage:(NSString *)message {
    NSMutableDictionary *result = [NSMutableDictionary new];
    NSString *EOL = @"\r\n";
    NSArray *linesArray = [message componentsSeparatedByString:EOL];
    NSArray *itemArray;
    for (NSString *item in linesArray) {
        if ([item isEqualToString:@"M-SEARCH * HTTP/1.1"]) {
            self.messageType = SsdpMessageType_SearchRequest;
        } else if ([item isEqualToString:@"HTTP/1.1 200 OK"]) {
            self.messageType = SsdpMessageType_SearchResponse;
        }
        else if ([item isEqualToString:@"NOTIFY * HTTP/1.1"]) {
            if ([message containsString:@"ssdp:alive"]) {
                self.messageType = SsdpMessageType_Alive;
            } else if ([message containsString:@"ssdp:byebye"]) {
                self.messageType = SsdpMessageType_ByeBye;
            }
        }
        
        itemArray = [item componentsSeparatedByString:@": "];
        if ([itemArray count] == 2) {
            result[itemArray[0]] = itemArray[1];
        }
        
        for (NSString *key in result) {
            if ([key isEqualToString:@"HOST"]) {
                self.host = result[key];
            } else if ([key isEqualToString:@"CACHE-CONTROL"]) {
                self.cacheControl = result[key];
            } else if ([key isEqualToString:@"SERVER"]) {
                self.server = result[key];
            } else if ([key isEqualToString:@"ST"]) {
                self.ST = result[key];
            } else if ([key isEqualToString:@"USN"]) {
                self.USN = result[key];
            } else if ([key isEqualToString:@"MAN"]) {
                self.MAN = result[key];
            } else if ([key isEqualToString:@"MX"]) {
                self.MX = result[key];
            } else if ([key isEqualToString:@"NT"]) {
                self.NT = result[key];
            } else if ([key isEqualToString:@"NTS"]) {
                self.NTS = result[key];
            }
        }
    }
}
@end
