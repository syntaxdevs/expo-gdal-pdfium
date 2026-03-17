#import "GDALTransformHelper.h"

#import <GDAL/gdal.h>
#import <GDAL/ogr_srs_api.h>
#import <GDAL/ogr_api.h>

// Objective-C++ helper used to bridge GDAL/OGR coordinate transformation
// and vector-based geospatial metadata extraction into Swift
@implementation GDALTransformHelper

// Transforms a single point from the source projection WKT to WGS84 (EPSG:4326)
// Returns nil if the spatial references or coordinate transformation cannot be created
+ (nullable NSDictionary *)transformPointX:(double)x
                                         y:(double)y
                             projectionWKT:(NSString *)projectionWKT
{
    // Validate that a source projection string was provided
    if (projectionWKT == nil || projectionWKT.length == 0) {
        return nil;
    }

    // Create source and target spatial reference objects
    OGRSpatialReferenceH sourceSRS = OSRNewSpatialReference(NULL);
    OGRSpatialReferenceH targetSRS = OSRNewSpatialReference(NULL);

    if (sourceSRS == NULL || targetSRS == NULL) {
        if (sourceSRS) OSRDestroySpatialReference(sourceSRS);
        if (targetSRS) OSRDestroySpatialReference(targetSRS);
        return nil;
    }

    // Convert the Objective-C projection string into a C string for GDAL/OGR APIs
    const char *projCString = [projectionWKT UTF8String];
    
    // Initialize the source SRS from the provided WKT and the target SRS as WGS84
    OGRErr sourceErr = OSRSetFromUserInput(sourceSRS, projCString);
    OGRErr targetErr = OSRSetFromUserInput(targetSRS, "EPSG:4326");

    if (sourceErr != OGRERR_NONE || targetErr != OGRERR_NONE) {
        OSRDestroySpatialReference(sourceSRS);
        OSRDestroySpatialReference(targetSRS);
        return nil;
    }

    // Create a coordinate transformation from the source SRS to WGS84
    OGRCoordinateTransformationH transform =
        OCTNewCoordinateTransformation(sourceSRS, targetSRS);

    if (transform == NULL) {
        OSRDestroySpatialReference(sourceSRS);
        OSRDestroySpatialReference(targetSRS);
        return nil;
    }

    // Copy input coordinates so they can be transformed in place
    double tx = x;
    double ty = y;
    double tz = 0.0;

    // Transform the point coordinates to WGS84
    int ok = OCTTransform(transform, 1, &tx, &ty, &tz);

    // Release GDAL/OGR resources before returning
    OCTDestroyCoordinateTransformation(transform);
    OSRDestroySpatialReference(sourceSRS);
    OSRDestroySpatialReference(targetSRS);

    if (!ok) {
        return nil;
    }

    return @{
        @"x": @(tx),
        @"y": @(ty)
    };
}

// Attempts to extract geospatial bounds from the PDF using the OGR vector API
// Returns WGS84 corner and center coordinates when a valid layer extent is found
+ (nullable NSDictionary *)extractGeospatialMetadataWithOGR:(NSString *)path
{
    // Convert the Objective-C file path to a C string for GDALOpenEx
    const char *cPath = [path UTF8String];
    if (cPath == NULL) {
        return nil;
    }

    // Register GDAL and OGR drivers before opening the file
    GDALAllRegister();
    OGRRegisterAll();

    // Open the PDF through the vector API to inspect layers and extents
    GDALDatasetH ds = GDALOpenEx(cPath, GDAL_OF_VECTOR, NULL, NULL, NULL);
    if (ds == NULL) {
        return nil;
    }

    // Ensure the vector dataset contains at least one layer
    int layerCount = GDALDatasetGetLayerCount(ds);
    if (layerCount <= 0) {
        GDALClose(ds);
        return nil;
    }

    // Iterate through vector layers and try to extract a valid geographic extent
    for (int i = 0; i < layerCount; i++) {
        OGRLayerH layer = GDALDatasetGetLayer(ds, i);
        if (layer == NULL) {
            continue;
        }
        // Read the layer extent (bounding box) in its native coordinate system
        OGREnvelope env;
        if (OGR_L_GetExtent(layer, &env, 1) != OGRERR_NONE) {
            continue;
        }

        double minX = env.MinX;
        double maxX = env.MaxX;
        double minY = env.MinY;
        double maxY = env.MaxY;

        // Default to native extent values; they may be transformed to WGS84 below
        double finalMinX = minX;
        double finalMaxX = maxX;
        double finalMinY = minY;
        double finalMaxY = maxY;

        // If the layer has a spatial reference, transform the extent to WGS84 when needed
        OGRSpatialReferenceH layerSRS = OGR_L_GetSpatialRef(layer);
        if (layerSRS != NULL) {
            OGRSpatialReferenceH targetSRS = OSRNewSpatialReference(NULL);
            if (targetSRS != NULL) {
                if (OSRImportFromEPSG(targetSRS, 4326) == OGRERR_NONE &&
                    OSRIsSame(layerSRS, targetSRS) == 0) {
                    
                    // Create a transformation from the layer SRS to WGS84 and transform extent corners
                    OGRCoordinateTransformationH transform =
                        OCTNewCoordinateTransformation(layerSRS, targetSRS);

                    if (transform != NULL) {
                        double llx = minX, lly = minY, llz = 0.0;
                        double urx = maxX, ury = maxY, urz = 0.0;

                        int ok1 = OCTTransform(transform, 1, &llx, &lly, &llz);
                        int ok2 = OCTTransform(transform, 1, &urx, &ury, &urz);

                        if (ok1 && ok2) {
                            finalMinX = MIN(llx, urx);
                            finalMaxX = MAX(llx, urx);
                            finalMinY = MIN(lly, ury);
                            finalMaxY = MAX(lly, ury);
                        }

                        OCTDestroyCoordinateTransformation(transform);
                    }
                }

                OSRDestroySpatialReference(targetSRS);
            }
        }
        // Validate that the transformed extent falls within a reasonable WGS84 range
        if (finalMinX >= -180.0 && finalMinX <= 180.0 &&
            finalMaxX >= -180.0 && finalMaxX <= 180.0 &&
            finalMinY >= -90.0 && finalMinY <= 90.0 &&
            finalMaxY >= -90.0 && finalMaxY <= 90.0) {
            
            // Ignore degenerate extents with near-zero width or height
            double width = fabs(finalMaxX - finalMinX);
            double height = fabs(finalMaxY - finalMinY);

            if (width > 0.000001 && height > 0.000001) {
                // Build and return corner and center coordinates in WGS84
                NSDictionary *result = @{
                    @"topLeftX": @(finalMinX),
                    @"topLeftY": @(finalMaxY),
                    @"topRightX": @(finalMaxX),
                    @"topRightY": @(finalMaxY),
                    @"bottomLeftX": @(finalMinX),
                    @"bottomLeftY": @(finalMinY),
                    @"bottomRightX": @(finalMaxX),
                    @"bottomRightY": @(finalMinY),
                    @"centerX": @((finalMinX + finalMaxX) / 2.0),
                    @"centerY": @((finalMinY + finalMaxY) / 2.0)
                };

                GDALClose(ds);
                return result;
            }
        }
    }

    // No valid geographic extent was found in any layer
    GDALClose(ds);
    return nil;
}

@end