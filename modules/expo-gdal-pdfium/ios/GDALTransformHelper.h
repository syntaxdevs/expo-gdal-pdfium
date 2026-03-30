#import <Foundation/Foundation.h>

NS_ASSUME_NONNULL_BEGIN

// This helper is used by Swift code to access GDAL/OGR functionality
// that requires C/C++ APIs, such as coordinate transformations and vector metadata extraction
@interface GDALTransformHelper : NSObject

// Transforms a single point (x, y) from a given projection (WKT)
// into WGS84 (EPSG:4326) coordinates
+ (nullable NSDictionary *)transformPointX:(double)x
                                         y:(double)y
                             projectionWKT:(NSString *)projectionWKT;
// Attempts to extract geospatial bounds from a PDF using the OGR vector API
// Returns corner and center coordinates in WGS84 if available
+ (nullable NSDictionary *)extractGeospatialMetadataWithOGR:(NSString *)path;

@end

NS_ASSUME_NONNULL_END