#import <CoreLocation/CoreLocation.h>

#import <React/RCTBridge.h>
#import <React/RCTConvert.h>
#import <React/RCTEventDispatcher.h>

#import "RNLocation.h"

@interface RNLocation() <CLLocationManagerDelegate>

@property (strong, nonatomic) CLLocationManager *locationManager;
@property NSInteger appType;

@end

@implementation RNLocation

RCT_EXPORT_MODULE()

@synthesize bridge = _bridge;

#pragma mark Initialization

- (instancetype)init
{
    if (self = [super init]) {
        self.locationManager = [[CLLocationManager alloc] init];

        self.locationManager.delegate = self;

        self.locationManager.distanceFilter = kCLDistanceFilterNone;
        self.locationManager.desiredAccuracy = kCLLocationAccuracyBest;
        self.locationManager.allowsBackgroundLocationUpdates = true;

        self.locationManager.pausesLocationUpdatesAutomatically = NO;
    }
    NSLog(@"react-native-location: requestAlwaysAuthorization");
    [self.locationManager requestAlwaysAuthorization];
    return self;
}

#pragma mark

RCT_EXPORT_METHOD(requestAlwaysAuthorization)
{
    NSLog(@"react-native-location: requestAlwaysAuthorization");
    [self.locationManager requestAlwaysAuthorization];
}

RCT_EXPORT_METHOD(requestWhenInUseAuthorization)
{
    NSLog(@"react-native-location: requestWhenInUseAuthorization");
    [self.locationManager requestWhenInUseAuthorization];
}

RCT_EXPORT_METHOD(getAuthorizationStatus:(RCTResponseSenderBlock)callback)
{
    callback(@[[self nameForAuthorizationStatus:[CLLocationManager authorizationStatus]]]);
}

RCT_EXPORT_METHOD(setDesiredAccuracy:(double) accuracy)
{
    self.locationManager.desiredAccuracy = accuracy;
}

RCT_EXPORT_METHOD(setDistanceFilter:(double) distance)
{
    self.locationManager.distanceFilter = distance;
}

RCT_EXPORT_METHOD(setAllowsBackgroundLocationUpdates:(BOOL) enabled)
{
    self.locationManager.allowsBackgroundLocationUpdates = enabled;
}

RCT_EXPORT_METHOD(startMonitoringSignificantLocationChanges)
{
    NSLog(@"react-native-location: startMonitoringSignificantLocationChanges");
    [self.locationManager startMonitoringSignificantLocationChanges];
}

RCT_EXPORT_METHOD(startUpdatingLocation: (NSInteger) type)
{
    self.appType = type;
    [self.locationManager startUpdatingLocation];
}

RCT_EXPORT_METHOD(checkGooglePlayServices)
{
    NSLog(@"react-native-location: checkng Google Play Services in a IOS Device (^_^) ");
}

RCT_EXPORT_METHOD(startUpdatingHeading)
{
    [self.locationManager startUpdatingHeading];
}

RCT_EXPORT_METHOD(stopMonitoringSignificantLocationChanges)
{
    [self.locationManager stopMonitoringSignificantLocationChanges];
}

RCT_EXPORT_METHOD(stopUpdatingLocation)
{
    [self.locationManager stopUpdatingLocation];
}

RCT_EXPORT_METHOD(stopUpdatingHeading)
{
    [self.locationManager stopUpdatingHeading];
}

-(NSString *)nameForAuthorizationStatus:(CLAuthorizationStatus)authorizationStatus
{
    switch (authorizationStatus) {
        case kCLAuthorizationStatusAuthorizedAlways:
            NSLog(@"Authorization Status: authorizedAlways");
            return @"authorizedAlways";

        case kCLAuthorizationStatusAuthorizedWhenInUse:
            NSLog(@"Authorization Status: authorizedWhenInUse");
            return @"authorizedWhenInUse";

        case kCLAuthorizationStatusDenied:
            NSLog(@"Authorization Status: denied");
            return @"denied";

        case kCLAuthorizationStatusNotDetermined:
            NSLog(@"Authorization Status: notDetermined");
            return @"notDetermined";

        case kCLAuthorizationStatusRestricted:
            NSLog(@"Authorization Status: restricted");
            return @"restricted";
    }
}

- (void)locationManagerDidPauseLocationUpdates:(CLLocationManager *)manager
{
    [self.bridge.eventDispatcher sendDeviceEventWithName:@"locationDisable" body:@{}];
}

- (void)locationManagerDidResumeLocationUpdates:(CLLocationManager *)manager
{
    [self.bridge.eventDispatcher sendDeviceEventWithName:@"locationEnable" body:@{}];
}

- (void)locationManager:(CLLocationManager *)manager didChangeAuthorizationStatus:(CLAuthorizationStatus)status
{
    [self.bridge.eventDispatcher sendDeviceEventWithName:@"locationStatus" body:@{@"status": [self nameForAuthorizationStatus:status]}];
}

- (void)locationManager:(CLLocationManager *)manager didFailWithError:(NSError *)error {
    NSLog(@"Location manager failed: %@", error);
}

- (void)locationManager:(CLLocationManager *)manager didUpdateHeading:(CLHeading *)newHeading {
  if (newHeading.headingAccuracy < 0)
    return;

  // Use the true heading if it is valid.
  CLLocationDirection heading = ((newHeading.trueHeading > 0) ?
    newHeading.trueHeading : newHeading.magneticHeading);

  NSDictionary *headingEvent = @{
    @"heading": @(heading)
  };

  NSLog(@"heading: %f", heading);
  [self.bridge.eventDispatcher sendDeviceEventWithName:@"headingUpdated" body:headingEvent];
}

- (void)locationManager:(CLLocationManager *)manager didUpdateLocations:(NSArray *)locations {
    CLLocation *location = [locations lastObject];
    NSDictionary *locationEvent = @{
        @"latitude": @(location.coordinate.latitude),
        @"longitude": @(location.coordinate.longitude),
        @"altitude": @(location.altitude),
        @"accuracy": @(location.horizontalAccuracy),
        @"altitudeAccuracy": @(location.verticalAccuracy),
        @"course": @(location.course),
        @"speed": @(location.speed),
        @"timestamp": @([location.timestamp timeIntervalSince1970] * 1000) // in ms
    };

    if (self.appType != 1 || location.horizontalAccuracy < 50) {
        NSLog(@"%@: lat: %f, long: %f, altitude: %f", location.timestamp, location.coordinate.latitude, location.coordinate.longitude, location.altitude);
        [self.bridge.eventDispatcher sendDeviceEventWithName:@"locationUpdated" body:locationEvent];
    }
}
@end
