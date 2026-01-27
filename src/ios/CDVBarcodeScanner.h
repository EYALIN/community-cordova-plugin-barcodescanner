/**
 * Community Cordova BarcodeScanner Plugin
 * Powered by Google ML Kit
 * Licensed under MIT License
 */

#import <Cordova/CDVPlugin.h>

@interface CDVBarcodeScanner : CDVPlugin

- (void)scan:(CDVInvokedUrlCommand *)command;

@end
