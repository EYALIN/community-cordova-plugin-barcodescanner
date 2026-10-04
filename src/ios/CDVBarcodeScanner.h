/**
 * Community Cordova BarcodeScanner Plugin
 * Powered by Google ML Kit
 * Licensed under MIT License
 */

#import <Cordova/CDVPlugin.h>

@interface CDVBarcodeScanner : CDVPlugin

- (void)scan:(CDVInvokedUrlCommand *)command;

// APP-224: decode every barcode in an already-captured image file using Vision, instead of a live
// camera session. The iOS share extension (Share to Barcode Reader) is tracked separately and is
// NOT implemented by this method — it only covers the in-app upload flow calling decodeFile directly.
- (void)decodeFile:(CDVInvokedUrlCommand *)command;

@end
