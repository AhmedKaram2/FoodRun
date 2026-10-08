#import <React/RCTBridgeModule.h>
#import <React/RCTEventEmitter.h>
@interface RCT_EXTERN_MODULE(FoodRun, RCTEventEmitter)
RCT_EXTERN_METHOD(getSnapshot:(RCTPromiseResolveBlock)resolve rejecter:(RCTPromiseRejectBlock)reject)
RCT_EXTERN_METHOD(dispatch:(NSString *)action value:(NSString *)value)
RCT_EXTERN_METHOD(update:(NSString *)key value:(NSString *)value)
RCT_EXTERN_METHOD(tick)
RCT_EXTERN_METHOD(quickAction:(NSString *)action value:(NSString *)value)
RCT_EXTERN_METHOD(dismissFeedback:(double)identifier)
RCT_EXTERN_METHOD(photo:(NSString *)key resolver:(RCTPromiseResolveBlock)resolve rejecter:(RCTPromiseRejectBlock)reject)
RCT_EXTERN_METHOD(share:(NSString *)text)
RCT_EXTERN_METHOD(copy:(NSString *)text)
@end
