/**
 * Community Cordova BarcodeScanner Plugin
 * Powered by Google ML Kit
 * Licensed under MIT License
 */

#import "CDVBarcodeScanner.h"
#import <AVFoundation/AVFoundation.h>
#import <Vision/Vision.h>

// Forward declaration for Swift class
@class BarcodeScannerViewController;

// Protocol must be declared before it's used in interface
@protocol BarcodeScannerDelegate <NSObject>
- (void)barcodeScannerDidCancel;
- (void)barcodeScannerDidFailWithError:(NSString *)error;
- (void)barcodeScannerDidScanBarcode:(NSString *)value format:(NSString *)format type:(NSString *)type;
@end

@interface CDVBarcodeScanner () <BarcodeScannerDelegate>
@property (nonatomic, strong) NSString *callbackId;
@end

@implementation CDVBarcodeScanner

- (void)scan:(CDVInvokedUrlCommand *)command {
    self.callbackId = command.callbackId;

    // Check camera permission
    AVAuthorizationStatus status = [AVCaptureDevice authorizationStatusForMediaType:AVMediaTypeVideo];

    if (status == AVAuthorizationStatusNotDetermined) {
        [AVCaptureDevice requestAccessForMediaType:AVMediaTypeVideo completionHandler:^(BOOL granted) {
            dispatch_async(dispatch_get_main_queue(), ^{
                if (granted) {
                    [self presentScanner:command];
                } else {
                    [self sendError:@"Camera permission denied"];
                }
            });
        }];
    } else if (status == AVAuthorizationStatusAuthorized) {
        [self presentScanner:command];
    } else {
        [self sendError:@"Camera permission denied"];
    }
}

- (void)presentScanner:(CDVInvokedUrlCommand *)command {
    NSDictionary *options = [command.arguments objectAtIndex:0];
    if (![options isKindOfClass:[NSDictionary class]]) {
        options = @{};
    }

    // Get the Swift scanner view controller
    Class scannerClass = NSClassFromString(@"BarcodeScannerViewController");
    if (!scannerClass) {
        // Try with module prefix
        NSString *moduleName = [[NSBundle mainBundle] objectForInfoDictionaryKey:@"CFBundleName"];
        moduleName = [moduleName stringByReplacingOccurrencesOfString:@"-" withString:@"_"];
        moduleName = [moduleName stringByReplacingOccurrencesOfString:@" " withString:@"_"];
        NSString *className = [NSString stringWithFormat:@"%@.BarcodeScannerViewController", moduleName];
        scannerClass = NSClassFromString(className);
    }

    if (!scannerClass) {
        [self sendError:@"Failed to load scanner"];
        return;
    }

    UIViewController *scanner = [[scannerClass alloc] init];

    // Set options via KVC
    @try {
        [scanner setValue:self forKey:@"delegate"];

        if (options[@"showTorchButton"]) {
            [scanner setValue:options[@"showTorchButton"] forKey:@"showTorchButton"];
        }
        if (options[@"prompt"]) {
            [scanner setValue:options[@"prompt"] forKey:@"promptText"];
        }
        if (options[@"beepOnSuccess"]) {
            [scanner setValue:options[@"beepOnSuccess"] forKey:@"beepOnSuccess"];
        }
        if (options[@"vibrateOnSuccess"]) {
            [scanner setValue:options[@"vibrateOnSuccess"] forKey:@"vibrateOnSuccess"];
        }
        if (options[@"formats"]) {
            [scanner setValue:options[@"formats"] forKey:@"formats"];
        }
    } @catch (NSException *exception) {
        NSLog(@"Error setting scanner options: %@", exception);
    }

    scanner.modalPresentationStyle = UIModalPresentationFullScreen;
    [self.viewController presentViewController:scanner animated:YES completion:nil];
}

#pragma mark - APP-224: decodeFile (Vision)

- (void)decodeFile:(CDVInvokedUrlCommand *)command {
    NSString *path = [command.arguments objectAtIndex:0];
    NSString *callbackId = command.callbackId;

    if (![path isKindOfClass:[NSString class]] || path.length == 0) {
        CDVPluginResult *pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR
                                                           messageAsString:@"decodeFile: missing file path"];
        [self.commandDelegate sendPluginResult:pluginResult callbackId:callbackId];
        return;
    }

    NSURL *fileURL = [path hasPrefix:@"file://"] ? [NSURL URLWithString:path] : [NSURL fileURLWithPath:path];

    [self.commandDelegate runInBackground:^{
        VNImageRequestHandler *handler = [[VNImageRequestHandler alloc] initWithURL:fileURL options:@{}];
        VNDetectBarcodesRequest *request = [[VNDetectBarcodesRequest alloc] initWithCompletionHandler:
            ^(VNRequest * _Nonnull req, NSError * _Nullable error) {
                CDVPluginResult *pluginResult;
                if (error) {
                    pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR
                                                     messageAsString:[NSString stringWithFormat:@"decodeFile failed: %@", error.localizedDescription]];
                } else {
                    NSMutableArray *results = [NSMutableArray array];
                    for (VNBarcodeObservation *observation in req.results) {
                        NSDictionary *result = @{
                            @"text": observation.payloadStringValue ?: @"",
                            @"format": [CDVBarcodeScanner formatNameForVisionSymbology:observation.symbology],
                            @"type": @"TEXT",
                            @"cancelled": @NO
                        };
                        [results addObject:result];
                    }
                    pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsArray:results];
                }
                [self.commandDelegate sendPluginResult:pluginResult callbackId:callbackId];
            }];

        NSError *performError = nil;
        [handler performRequests:@[request] error:&performError];
        if (performError) {
            CDVPluginResult *pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR
                                                               messageAsString:[NSString stringWithFormat:@"decodeFile: unable to load image: %@", performError.localizedDescription]];
            [self.commandDelegate sendPluginResult:pluginResult callbackId:callbackId];
        }
    }];
}

+ (NSString *)formatNameForVisionSymbology:(VNBarcodeSymbology)symbology {
    if (symbology == VNBarcodeSymbologyQR) return @"QR_CODE";
    if (symbology == VNBarcodeSymbologyDataMatrix) return @"DATA_MATRIX";
    if (symbology == VNBarcodeSymbologyUPCE) return @"UPC_E";
    if (symbology == VNBarcodeSymbologyEAN8) return @"EAN_8";
    if (symbology == VNBarcodeSymbologyEAN13) return @"EAN_13";
    if (symbology == VNBarcodeSymbologyCode39) return @"CODE_39";
    if (symbology == VNBarcodeSymbologyCode93) return @"CODE_93";
    if (symbology == VNBarcodeSymbologyCode128) return @"CODE_128";
    if (symbology == VNBarcodeSymbologyCodabar) return @"CODABAR";
    if (symbology == VNBarcodeSymbologyITF14) return @"ITF";
    if (symbology == VNBarcodeSymbologyPDF417) return @"PDF_417";
    if (symbology == VNBarcodeSymbologyAztec) return @"AZTEC";
    return @"UNKNOWN";
}

#pragma mark - BarcodeScannerDelegate

- (void)barcodeScannerDidCancel {
    [self.viewController dismissViewControllerAnimated:YES completion:nil];

    NSDictionary *result = @{
        @"text": @"",
        @"format": @"",
        @"cancelled": @YES
    };

    CDVPluginResult *pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsDictionary:result];
    [self.commandDelegate sendPluginResult:pluginResult callbackId:self.callbackId];
}

- (void)barcodeScannerDidFailWithError:(NSString *)error {
    [self.viewController dismissViewControllerAnimated:YES completion:nil];
    [self sendError:error];
}

- (void)barcodeScannerDidScanBarcode:(NSString *)value format:(NSString *)format type:(NSString *)type {
    [self.viewController dismissViewControllerAnimated:YES completion:nil];

    NSDictionary *result = @{
        @"text": value ?: @"",
        @"format": format ?: @"UNKNOWN",
        @"type": type ?: @"TEXT",
        @"cancelled": @NO
    };

    CDVPluginResult *pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsDictionary:result];
    [self.commandDelegate sendPluginResult:pluginResult callbackId:self.callbackId];
}

- (void)sendError:(NSString *)error {
    CDVPluginResult *pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:error];
    [self.commandDelegate sendPluginResult:pluginResult callbackId:self.callbackId];
}

@end
