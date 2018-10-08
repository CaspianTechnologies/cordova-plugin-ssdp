//
//  SSDPMessage.h
//  SSDP
//
//  Created by Alexei Vinidiktov on 04/10/2018.
//

#import <Foundation/Foundation.h>

typedef NS_ENUM(int, SsdpMessageType) {
    SsdpMessageType_SearchRequest,
    SsdpMessageType_SearchResponse,
    SsdpMessageType_Alive,
    SsdpMessageType_ByeBye
};

@interface SSDPMessage : NSObject
    //public SsdpMessageType Type { get; set; }
    @property (nonatomic, assign) SsdpMessageType messageType;
    @property (nonatomic) NSString *host;
    @property (nonatomic) NSString *cacheControl;
    //public DateTimeOffset Date { get; set; }
    //public string Location { get; set; }
    @property (nonatomic) NSString *server;
    @property (nonatomic) NSString *ST;
    @property (nonatomic) NSString *USN;
    @property (nonatomic) NSString *MAN;
    @property (nonatomic) NSString *userAgent;
    @property (nonatomic) NSString *MX;
    @property (nonatomic) NSString *NT;
    @property (nonatomic) NSString *NTS;

+(SSDPMessage *)SSDPMessageWithString:(NSString *)message;

@end
