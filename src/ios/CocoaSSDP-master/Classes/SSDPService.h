//
//  SSDPService.h
//  Copyright (c) 2014 Stephane Boisson
//
//  Permission is hereby granted, free of charge, to any person obtaining a copy
//  of this software and associated documentation files (the "Software"), to deal
//  in the Software without restriction, including without limitation the rights
//  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
//  copies of the Software, and to permit persons to whom the Software is
//  furnished to do so, subject to the following conditions:
//
//  The above copyright notice and this permission notice shall be included in
//  all copies or substantial portions of the Software.
//
//  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
//  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
//  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
//  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
//  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
//  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
//  THE SOFTWARE.
//

#import <Foundation/Foundation.h>

/**
 A lightweight model class that describes an SSDP service, populated from headers
 returned by an SSDP search
 */
@interface SSDPService : NSObject

/**
 The location of the service's description XML file
 */
@property(readonly, nonatomic) NSURL *location;

/**
 The UPnP service type of the device
 */
@property(readonly, nonatomic) NSString *serviceType;

/**
 The services unique service name
 */
@property(readonly, nonatomic) NSString *uniqueServiceName;

/**
 The port
 */
@property(readonly, nonatomic) NSNumber *port;

/**
 The host
 */
@property(readonly, nonatomic) NSString *host;


/**
 The server description
 */
@property(readonly, nonatomic) NSString *server;

/**
 The services cache control as full string
 */
@property(readonly, nonatomic) NSString *cacheControl;

/**
 The services cache control max age
 */
@property(readonly, nonatomic) NSNumber *cacheControlTime;

/**
 Intialize a new instance

 @param headers The headers returned by the SSDP search response

 @return Returns a new `SSDPService` instance, populated from the headers
 dictionary
 */
- (id)initWithHeaders:(NSDictionary *)headers andHost:(NSString *)host;

@end
