package expo.modules.gdalpdfium

import android.util.Log
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import expo.modules.kotlin.Promise
import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition
import java.net.URL
import java.io.FileOutputStream
import java.io.File
import java.io.RandomAccessFile
import java.nio.charset.StandardCharsets
import java.util.regex.Pattern
import kotlin.math.abs

// Note: PDFBox removed due to AWT dependency not available on Android
// Using improved regex-based parsing instead

// GDAL Java bindings import
// Standard GDAL Java bindings use: org.gdal.gdal.gdal
import org.gdal.gdal.gdal
import org.gdal.gdal.Driver
import org.gdal.gdal.Dataset
import org.gdal.gdal.Band
import org.gdal.gdal.TranslateOptions
import org.gdal.osr.SpatialReference
import org.gdal.osr.CoordinateTransformation
import org.gdal.ogr.ogr
import org.gdal.ogr.DataSource
import java.util.Vector
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.graphics.Canvas
import android.graphics.Paint

class ExpoGdalPdfiumModule : Module() {
  companion object {
    init {
      // Load GDAL native library if required
      // GDAL Java bindings typically require loading the native library
      // Try these common library names (uncomment the one that works):
      try {
        System.loadLibrary("gdaljni")
      } catch (e: UnsatisfiedLinkError) {
        try {
          System.loadLibrary("gdal")
        } catch (e2: UnsatisfiedLinkError) {
          Log.w("ExpoGdalPdfium", "Could not load GDAL native library. Some functions may not work.")
        }
      }
    }
  }

  // Each module class must implement the definition function. The definition consists of components
  // that describes the module's functionality and behavior.
  // See https://docs.expo.dev/modules/module-api for more details about available components.
  override fun definition() = ModuleDefinition {
    // Sets the name of the module that JavaScript code will use to refer to the module. Takes a string as an argument.
    // Can be inferred from module's class name, but it's recommended to set it explicitly for clarity.
    // The module will be accessible from `requireNativeModule('ExpoGdalPdfium')` in JavaScript.
    Name("ExpoGdalPdfium")

    // Defines constant property on the module.
    Constant("PI") {
      Math.PI
    }

    // Defines event names that the module can send to JavaScript.
    Events("onChange")

    // Defines a JavaScript synchronous function that runs the native code on the JavaScript thread.
    Function("hello") {
      "Hello world! 👋"
    }

    // Defines a JavaScript function that always returns a Promise and whose native code
    // is by default dispatched on the different thread than the JavaScript runtime runs on.
    AsyncFunction("setValueAsync") { value: String ->
      // Send an event to JavaScript.
      sendEvent("onChange", mapOf(
        "value" to value
      ))
    }

    // GDAL Version Info Function
    // Returns GDAL library version information
    // Based on GDAL API: https://gdal.org/en/stable/doxygen/functions_func_g.html
    AsyncFunction("getVersionInfo") { promise: Promise ->
      try {
        Log.i("ExpoGdalPdfium", "Getting GDAL version info")
        
        // GDAL VersionInfo() - returns version information (Java bindings use VersionInfo, not GetVersionInfo)
        // Common request values: "--version", "VERSION_NUM", "RELEASE_DATE"
        // Reference: https://gdal.org/api/gdal.html#_CPPv4N4gdal12GetVersionInfoEPKc
        val versionInfo = gdal.VersionInfo("--version")
        val versionNum = gdal.VersionInfo("VERSION_NUM")
        val releaseDate = gdal.VersionInfo("RELEASE_DATE")
        
        promise.resolve(
          createResponseMap(
            "Version info retrieved successfully",
            "SUCCESS",
            false,
            mapOf(
              "version" to (versionInfo ?: "Unknown"),
              "versionNum" to (versionNum ?: "Unknown"),
              "releaseDate" to (releaseDate ?: "Unknown")
            )
          )
        )
      } catch (e: ClassNotFoundException) {
        Log.e("ExpoGdalPdfium", "GDAL classes not found. Check if .aar is properly linked.", e)
        promise.resolve(
          createResponseMap(
            "GDAL classes not found. Please verify the .aar package structure.",
            "CLASS_NOT_FOUND",
            true,
            mapOf("errorDetails" to (e.message ?: "Unknown error"))
          )
        )
      } catch (e: UnsatisfiedLinkError) {
        Log.e("ExpoGdalPdfium", "GDAL native library not loaded.", e)
        promise.resolve(
          createResponseMap(
            "GDAL native library not loaded. Check native library configuration.",
            "NATIVE_LIBRARY_ERROR",
            true,
            mapOf("errorDetails" to (e.message ?: "Unknown error"))
          )
        )
      } catch (e: Exception) {
        Log.e("ExpoGdalPdfium", "Error getting GDAL version info", e)
        promise.resolve(
          createResponseMap(
            "Error getting version info: ${e.message}",
            "ERROR",
            true,
            mapOf("errorDetails" to (e.message ?: "Unknown error"), "errorType" to e.javaClass.simpleName)
          )
        )
      }
    }

    // GDAL List Drivers Function
    // Returns a list of all available GDAL drivers
    AsyncFunction("listDrivers") { promise: Promise ->
      try {
        Log.i("ExpoGdalPdfium", "Listing GDAL drivers")
        
        // Register all drivers first
        gdal.AllRegister()
        
        // Get the number of drivers
        val driverCount = gdal.GetDriverCount()
        
        // Build list of drivers
        val driversList = mutableListOf<Map<String, String>>()
        
        for (i in 0 until driverCount) {
          val driver: Driver? = gdal.GetDriver(i)
          driver?.let {
            driversList.add(
              mapOf(
                "shortName" to (it.getShortName() ?: "Unknown"),
                "longName" to (it.getLongName() ?: "Unknown")
              )
            )
          }
        }
        
        promise.resolve(
          createResponseMap(
            "Drivers retrieved successfully",
            "SUCCESS",
            false,
            mapOf(
              "driverCount" to driverCount.toString(),
              "drivers" to driversList
            )
          )
        )
      } catch (e: ClassNotFoundException) {
        Log.e("ExpoGdalPdfium", "GDAL classes not found. Check if .aar is properly linked.", e)
        promise.resolve(
          createResponseMap(
            "GDAL classes not found. Please verify the .aar package structure.",
            "CLASS_NOT_FOUND",
            true,
            mapOf("errorDetails" to (e.message ?: "Unknown error"))
          )
        )
      } catch (e: UnsatisfiedLinkError) {
        Log.e("ExpoGdalPdfium", "GDAL native library not loaded.", e)
        promise.resolve(
          createResponseMap(
            "GDAL native library not loaded. Check native library configuration.",
            "NATIVE_LIBRARY_ERROR",
            true,
            mapOf("errorDetails" to (e.message ?: "Unknown error"))
          )
        )
      } catch (e: Exception) {
        Log.e("ExpoGdalPdfium", "Error listing GDAL drivers", e)
        promise.resolve(
          createResponseMap(
            "Error listing drivers: ${e.message}",
            "ERROR",
            true,
            mapOf("errorDetails" to (e.message ?: "Unknown error"), "errorType" to e.javaClass.simpleName)
          )
        )
      }
    }

    // GDAL Read GeoPDF Function
    // Reads a GeoPDF file and returns its information
    AsyncFunction("readGeoPDF") { filePath: String, promise: Promise ->
      try {
        Log.i("ExpoGdalPdfium", "Reading GeoPDF: $filePath")
        
        // Register all drivers first
        gdal.AllRegister()
        
        // Open the GeoPDF file
        val dataset: Dataset? = gdal.Open(filePath)
        
        if (dataset == null) {
          promise.resolve(
            createResponseMap(
              "Failed to open GeoPDF file",
              "FILE_NOT_FOUND",
              true,
              mapOf("errorDetails" to "Could not open file: $filePath")
            )
          )
          return@AsyncFunction
        }
        
        try {
          // Get basic dataset information
          val width = dataset.GetRasterXSize()
          val height = dataset.GetRasterYSize()
          val bandCount = dataset.GetRasterCount()
          val projection = dataset.GetProjectionRef() ?: "Unknown"
          val driver = dataset.GetDriver()
          val driverName = driver?.getShortName() ?: "Unknown"
          
          // Get geotransform (spatial reference)
          val geoTransform = DoubleArray(6)
          dataset.GetGeoTransform(geoTransform)
          
          // Get band information
          val bandsInfo = mutableListOf<Map<String, Any>>()
          for (i in 1..bandCount) {
            val band: Band? = dataset.GetRasterBand(i)
            band?.let {
              val dataType = it.GetRasterDataType()
              val blockWidth = it.GetBlockXSize()
              val blockHeight = it.GetBlockYSize()
              bandsInfo.add(
                mapOf(
                  "bandNumber" to i.toString(),
                  "dataType" to dataType.toString(),
                  "blockWidth" to blockWidth.toString(),
                  "blockHeight" to blockHeight.toString()
                )
              )
            }
          }
          
          promise.resolve(
            createResponseMap(
              "GeoPDF read successfully",
              "SUCCESS",
              false,
              mapOf(
                "filePath" to filePath,
                "width" to width.toString(),
                "height" to height.toString(),
                "bandCount" to bandCount.toString(),
                "driver" to driverName,
                "projection" to projection,
                "geoTransform" to geoTransform.map { it.toString() },
                "bands" to bandsInfo
              )
            )
          )
        } finally {
          // Close the dataset to free resources
          dataset.delete()
        }
      } catch (e: ClassNotFoundException) {
        Log.e("ExpoGdalPdfium", "GDAL classes not found", e)
        promise.resolve(
          createResponseMap(
            "GDAL classes not found",
            "CLASS_NOT_FOUND",
            true,
            mapOf("errorDetails" to (e.message ?: "Unknown error"))
          )
        )
      } catch (e: UnsatisfiedLinkError) {
        Log.e("ExpoGdalPdfium", "GDAL native library not loaded", e)
        promise.resolve(
          createResponseMap(
            "GDAL native library not loaded",
            "NATIVE_LIBRARY_ERROR",
            true,
            mapOf("errorDetails" to (e.message ?: "Unknown error"))
          )
        )
      } catch (e: Exception) {
        Log.e("ExpoGdalPdfium", "Error reading GeoPDF", e)
        promise.resolve(
          createResponseMap(
            "Error reading GeoPDF: ${e.message}",
            "ERROR",
            true,
            mapOf(
              "errorDetails" to (e.message ?: "Unknown error"),
              "errorType" to e.javaClass.simpleName
            )
          )
        )
      }
    }

    // GDAL Render GeoPDF to PNG Function
    // Renders a GeoPDF file to a PNG image file
    AsyncFunction("renderGeoPDFToPng") { inputPath: String, outputPath: String, promise: Promise ->
      try {
        Log.i("ExpoGdalPdfium", "[NEW CODE v2] Rendering GeoPDF to PNG: $inputPath -> $outputPath")
        
        // Check if file exists before attempting to open
        val inputFile = java.io.File(inputPath)
        
        // Log detailed file information for debugging
        Log.i("ExpoGdalPdfium", "File check - Path: $inputPath")
        Log.i("ExpoGdalPdfium", "File check - Absolute path: ${inputFile.absolutePath}")
        Log.i("ExpoGdalPdfium", "File check - Exists: ${inputFile.exists()}")
        Log.i("ExpoGdalPdfium", "File check - Is file: ${inputFile.isFile}")
        Log.i("ExpoGdalPdfium", "File check - Is directory: ${inputFile.isDirectory}")
        Log.i("ExpoGdalPdfium", "File check - Can read: ${inputFile.canRead()}")
        Log.i("ExpoGdalPdfium", "File check - Can write: ${inputFile.canWrite()}")
        if (inputFile.exists()) {
          Log.i("ExpoGdalPdfium", "File check - Size: ${inputFile.length()} bytes")
          Log.i("ExpoGdalPdfium", "File check - Last modified: ${inputFile.lastModified()}")
        }
        
        if (!inputFile.exists()) {
          Log.e("ExpoGdalPdfium", "File does not exist: $inputPath")
          promise.resolve(
            createResponseMap(
              "Failed to open GeoPDF file for rendering",
              "FILE_NOT_FOUND",
              true,
              mapOf(
                "errorDetails" to "File does not exist: $inputPath",
                "fileExists" to false,
                "absolutePath" to inputFile.absolutePath,
                "canRead" to inputFile.canRead(),
                "canWrite" to inputFile.canWrite(),
                "isFile" to inputFile.isFile,
                "isDirectory" to inputFile.isDirectory
              )
            )
          )
          return@AsyncFunction
        }
        
        if (inputFile.length() == 0L) {
          Log.e("ExpoGdalPdfium", "File is empty: $inputPath")
          promise.resolve(
            createResponseMap(
              "Failed to open GeoPDF file for rendering",
              "FILE_EMPTY",
              true,
              mapOf("errorDetails" to "File is empty (0 bytes): $inputPath")
            )
          )
          return@AsyncFunction
        }
        
        if (!inputFile.canRead()) {
          Log.e("ExpoGdalPdfium", "File cannot be read (permissions): $inputPath")
          promise.resolve(
            createResponseMap(
              "Failed to open GeoPDF file for rendering",
              "FILE_PERMISSION_DENIED",
              true,
              mapOf("errorDetails" to "File exists but cannot be read: $inputPath")
            )
          )
          return@AsyncFunction
        }
        
        Log.i("ExpoGdalPdfium", "File exists and is readable. Size: ${inputFile.length()} bytes")
        
        // Register all drivers first
        gdal.AllRegister()
        
        // Get GDAL error before opening (to clear any previous errors)
        val previousError = gdal.GetLastErrorMsg()
        if (previousError != null && previousError.isNotEmpty()) {
          Log.w("ExpoGdalPdfium", "Previous GDAL error (clearing): $previousError")
        }
        
        // Open the GeoPDF file
        val dataset: Dataset? = gdal.Open(inputPath)
        
        if (dataset == null) {
          // Get GDAL error message for more details
          val gdalError = gdal.GetLastErrorMsg()
          val errorType = gdal.GetLastErrorType()
          
          Log.e("ExpoGdalPdfium", "[NEW CODE v2] GDAL raster API failed to open file. Error: $gdalError (Type: $errorType)")
          
          // Check if this might be a GEODETIC PDF (GDAL doesn't support GEODETIC projection)
          // Error message format: "Unhandled (yet) value for ProjectionType : GEODETIC"
          val isGeodetic = gdalError != null && (
            gdalError.contains("GEODETIC", ignoreCase = true) ||
            (gdalError.contains("Unhandled", ignoreCase = true) && gdalError.contains("ProjectionType", ignoreCase = true))
          )
          
          Log.i("ExpoGdalPdfium", "[NEW CODE v2] GEODETIC detection: isGeodetic=$isGeodetic, error='$gdalError'")
          
          if (isGeodetic) {
            Log.i("ExpoGdalPdfium", "Detected GEODETIC PDF - attempting fallback with Android PdfRenderer")
            
            // Try to extract metadata by searching PDF file for coordinate patterns
            // Also try OGR vector API as a fallback
            var metadata = tryExtractMetadataWithOGR(inputPath)
            if (metadata == null) {
              Log.i("ExpoGdalPdfium", "Pattern matching failed, trying OGR vector API...")
              metadata = tryExtractMetadataWithOGRVector(inputPath)
            }
            
            // Render using Android PdfRenderer
            val renderResult = renderPDFWithAndroidPdfRenderer(inputPath, outputPath, metadata)
            
            if (renderResult != null) {
              Log.i("ExpoGdalPdfium", "✓ GEODETIC PDF successfully rendered with PdfRenderer fallback")
              promise.resolve(renderResult)
              return@AsyncFunction
            } else {
              Log.w("ExpoGdalPdfium", "Android PdfRenderer fallback also failed")
            }
          }
          
          // If fallback didn't work or not GEODETIC, return error
          promise.resolve(
            createResponseMap(
              "Failed to open GeoPDF file for rendering",
              "GDAL_OPEN_FAILED",
              true,
              mapOf(
                "errorDetails" to "GDAL could not open file: $inputPath",
                "gdalError" to (gdalError ?: "No error message available"),
                "gdalErrorType" to errorType.toString(),
                "fileExists" to inputFile.exists(),
                "fileSize" to inputFile.length().toString(),
                "fileCanRead" to inputFile.canRead(),
                "isGeodetic" to isGeodetic,
                "fallbackAttempted" to isGeodetic
              )
            )
          )
          return@AsyncFunction
        }
        
        try {
          // Get dataset dimensions
          val width = dataset.GetRasterXSize()
          val height = dataset.GetRasterYSize()
          val bandCount = dataset.GetRasterCount()
          
          // Get geotransform for coordinate calculations
          val geoTransform = DoubleArray(6)
          dataset.GetGeoTransform(geoTransform)
          
          Log.i("ExpoGdalPdfium", "Dataset dimensions: ${width}x${height}, bands: $bandCount")
          Log.i("ExpoGdalPdfium", "GeoTransform: ${geoTransform.joinToString(", ")}")
          
          // Calculate corner and center coordinates from GeoTransform
          // GeoTransform format: [topLeftX, pixelWidth, rotationX, topLeftY, rotationY, pixelHeight]
          val topLeftX = geoTransform[0]
          val topLeftY = geoTransform[3]
          val pixelWidth = geoTransform[1]
          val pixelHeight = geoTransform[5]  // Usually negative for north-up images
          
          // Calculate corners (assuming no rotation, i.e., geoTransform[2] and geoTransform[4] are 0)
          val topRightX = topLeftX + width * pixelWidth
          val topRightY = topLeftY
          
          val bottomLeftX = topLeftX
          val bottomLeftY = topLeftY + height * pixelHeight
          
          val bottomRightX = topLeftX + width * pixelWidth
          val bottomRightY = topLeftY + height * pixelHeight
          
          // Calculate center coordinates
          val centerX = topLeftX + (width / 2.0) * pixelWidth
          val centerY = topLeftY + (height / 2.0) * pixelHeight
          
          // Transform coordinates from native CRS to WGS84 (EPSG:4326)
          // Try multiple methods to get projection WKT from dataset
          var projectionWkt: String? = dataset.GetProjectionRef()
          if (projectionWkt == null || projectionWkt.isEmpty()) {
            projectionWkt = dataset.GetProjection()
          }
          
          // Variables to store WGS84 coordinates (longitude, latitude)
          var topLeftLng = topLeftX
          var topLeftLat = topLeftY
          var topRightLng = topRightX
          var topRightLat = topRightY
          var bottomLeftLng = bottomLeftX
          var bottomLeftLat = bottomLeftY
          var bottomRightLng = bottomRightX
          var bottomRightLat = bottomRightY
          var centerLng = centerX
          var centerLat = centerY
          
          // Only transform if projection is available
          if (projectionWkt != null && projectionWkt.isNotEmpty()) {
            try {
              Log.i("ExpoGdalPdfium", "Projection WKT: ${projectionWkt.take(200)}...") // Log first 200 chars
              
              // Create source Spatial Reference System from WKT
              val sourceSRS = SpatialReference()
              val importResult = sourceSRS.ImportFromWkt(projectionWkt)
              if (importResult != 0) {
                Log.w("ExpoGdalPdfium", "Failed to import source SRS from WKT. Error code: $importResult")
                throw Exception("Failed to import source SRS from WKT")
              }
              
              // Create target SRS (WGS84, EPSG:4326) using WKT instead of EPSG code
              // This avoids requiring PROJ database support
              val wgs84Wkt = "GEOGCS[\"WGS 84\",DATUM[\"WGS_1984\",SPHEROID[\"WGS 84\",6378137,298.257223563]],PRIMEM[\"Greenwich\",0],UNIT[\"degree\",0.0174532925199433]]"
              val targetSRS = SpatialReference()
              val wktResult = targetSRS.ImportFromWkt(wgs84Wkt)
              if (wktResult != 0) {
                Log.w("ExpoGdalPdfium", "Failed to import target SRS (WGS84) from WKT. Error code: $wktResult")
                throw Exception("Failed to import target SRS (WGS84) from WKT")
              }
              
              // Create coordinate transformation
              val transform = CoordinateTransformation(sourceSRS, targetSRS)
              
              // Transform all coordinate points
              // TransformPoint returns [longitude, latitude, z] array
              val topLeftTransformed = transform.TransformPoint(topLeftX, topLeftY, 0.0)
              topLeftLng = topLeftTransformed[0]
              topLeftLat = topLeftTransformed[1]
              
              val topRightTransformed = transform.TransformPoint(topRightX, topRightY, 0.0)
              topRightLng = topRightTransformed[0]
              topRightLat = topRightTransformed[1]
              
              val bottomLeftTransformed = transform.TransformPoint(bottomLeftX, bottomLeftY, 0.0)
              bottomLeftLng = bottomLeftTransformed[0]
              bottomLeftLat = bottomLeftTransformed[1]
              
              val bottomRightTransformed = transform.TransformPoint(bottomRightX, bottomRightY, 0.0)
              bottomRightLng = bottomRightTransformed[0]
              bottomRightLat = bottomRightTransformed[1]
              
              val centerTransformed = transform.TransformPoint(centerX, centerY, 0.0)
              centerLng = centerTransformed[0]
              centerLat = centerTransformed[1]
              
              Log.i("ExpoGdalPdfium", "Coordinates transformed to WGS84 (EPSG:4326)")
              Log.i("ExpoGdalPdfium", "TopLeft WGS84: ($topLeftLng, $topLeftLat)")
            } catch (e: Exception) {
              Log.e("ExpoGdalPdfium", "Failed to transform coordinates to WGS84", e)
              Log.w("ExpoGdalPdfium", "Error details: ${e.message}, ${e.javaClass.simpleName}. Using native CRS coordinates.")
              // If transformation fails, use original coordinates (already set above)
            }
          } else {
            Log.w("ExpoGdalPdfium", "No projection information available. Using native coordinates.")
          }
          
          // Read raster data directly and convert to PNG using Android Bitmap API
          // This approach doesn't require GDAL's PNG driver
          
          // Determine bitmap config based on band count
          val bitmapConfig: Bitmap.Config = when {
            bandCount >= 3 -> Bitmap.Config.ARGB_8888  // RGB or RGBA
            bandCount == 1 -> Bitmap.Config.ALPHA_8     // Grayscale
            else -> Bitmap.Config.ARGB_8888
          }
          
          // Create bitmap
          val bitmap = Bitmap.createBitmap(width, height, bitmapConfig)
          
          // Read bands and populate bitmap
          if (bandCount >= 3) {
            // RGB or RGBA image
            val redBand: Band? = dataset.GetRasterBand(1)
            val greenBand: Band? = dataset.GetRasterBand(2)
            val blueBand: Band? = dataset.GetRasterBand(3)
            val alphaBand: Band? = if (bandCount >= 4) dataset.GetRasterBand(4) else null
            
            if (redBand == null || greenBand == null || blueBand == null) {
              promise.resolve(
                createResponseMap(
                  "Failed to render GeoPDF to PNG",
                  "RENDER_ERROR",
                  true,
                  mapOf("errorDetails" to "Could not read RGB bands from dataset")
                )
              )
              return@AsyncFunction
            }
            
            // Read raster data for each band
            val redData = ByteArray(width * height)
            val greenData = ByteArray(width * height)
            val blueData = ByteArray(width * height)
            val alphaData = if (alphaBand != null) ByteArray(width * height) else null
            
            redBand.ReadRaster(0, 0, width, height, redData)
            greenBand.ReadRaster(0, 0, width, height, greenData)
            blueBand.ReadRaster(0, 0, width, height, blueData)
            if (alphaBand != null && alphaData != null) {
              alphaBand.ReadRaster(0, 0, width, height, alphaData)
            }
            
            // Convert to ARGB format for bitmap
            val pixels = IntArray(width * height)
            for (y in 0 until height) {
              for (x in 0 until width) {
                val idx = y * width + x
                val r = redData[idx].toInt() and 0xFF
                val g = greenData[idx].toInt() and 0xFF
                val b = blueData[idx].toInt() and 0xFF
                val a = if (alphaData != null) alphaData[idx].toInt() and 0xFF else 255
                pixels[idx] = (a shl 24) or (r shl 16) or (g shl 8) or b
              }
            }
            
            bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
            
          } else if (bandCount == 1) {
            // Grayscale image - convert to RGB
            val grayBand: Band? = dataset.GetRasterBand(1)
            if (grayBand == null) {
              promise.resolve(
                createResponseMap(
                  "Failed to render GeoPDF to PNG",
                  "RENDER_ERROR",
                  true,
                  mapOf("errorDetails" to "Could not read grayscale band from dataset")
                )
              )
              return@AsyncFunction
            }
            
            val grayData = ByteArray(width * height)
            grayBand.ReadRaster(0, 0, width, height, grayData)
            
            // Convert grayscale to RGB
            val pixels = IntArray(width * height)
            for (y in 0 until height) {
              for (x in 0 until width) {
                val idx = y * width + x
                val gray = grayData[idx].toInt() and 0xFF
                pixels[idx] = (255 shl 24) or (gray shl 16) or (gray shl 8) or gray
              }
            }
            
            bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
          } else {
            promise.resolve(
              createResponseMap(
                "Failed to render GeoPDF to PNG",
                "RENDER_ERROR",
                true,
                mapOf("errorDetails" to "Unsupported band count: $bandCount")
              )
            )
            return@AsyncFunction
          }
          
          // Save bitmap as PNG file
          try {
            val outputFile = java.io.File(outputPath)
            val fos = FileOutputStream(outputFile)
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
            fos.flush()
            fos.close()
            
            if (!outputFile.exists() || outputFile.length() == 0L) {
              promise.resolve(
                createResponseMap(
                  "Failed to render GeoPDF to PNG",
                  "RENDER_ERROR",
                  true,
                  mapOf("errorDetails" to "Failed to write PNG file: $outputPath")
                )
              )
              return@AsyncFunction
            }
            
            Log.i("ExpoGdalPdfium", "Successfully rendered PNG: $outputPath (size: ${outputFile.length()} bytes)")
            
            promise.resolve(
              createResponseMap(
                "GeoPDF rendered to PNG successfully",
                "SUCCESS",
                false,
                mapOf(
                  "inputPath" to inputPath,
                  "outputPath" to outputPath,
                  "width" to width.toString(),
                  "height" to height.toString(),
                  "geoTransform" to geoTransform.map { it.toString() },
                  "topLeft" to mapOf("x" to topLeftLng.toString(), "y" to topLeftLat.toString()),
                  "topRight" to mapOf("x" to topRightLng.toString(), "y" to topRightLat.toString()),
                  "bottomLeft" to mapOf("x" to bottomLeftLng.toString(), "y" to bottomLeftLat.toString()),
                  "bottomRight" to mapOf("x" to bottomRightLng.toString(), "y" to bottomRightLat.toString()),
                  "center" to mapOf("x" to centerLng.toString(), "y" to centerLat.toString())
                )
              )
            )
          } catch (e: Exception) {
            Log.e("ExpoGdalPdfium", "Error writing PNG file", e)
            promise.resolve(
              createResponseMap(
                "Failed to render GeoPDF to PNG",
                "RENDER_ERROR",
                true,
                mapOf("errorDetails" to "Error writing PNG file: ${e.message}")
              )
            )
          } finally {
            bitmap.recycle()
          }
        } finally {
          // Close the input dataset to free resources
          dataset.delete()
        }
      } catch (e: ClassNotFoundException) {
        Log.e("ExpoGdalPdfium", "GDAL classes not found", e)
        promise.resolve(
          createResponseMap(
            "GDAL classes not found",
            "CLASS_NOT_FOUND",
            true,
            mapOf("errorDetails" to (e.message ?: "Unknown error"))
          )
        )
      } catch (e: UnsatisfiedLinkError) {
        Log.e("ExpoGdalPdfium", "GDAL native library not loaded", e)
        promise.resolve(
          createResponseMap(
            "GDAL native library not loaded",
            "NATIVE_LIBRARY_ERROR",
            true,
            mapOf("errorDetails" to (e.message ?: "Unknown error"))
          )
        )
      } catch (e: Exception) {
        Log.e("ExpoGdalPdfium", "Error rendering GeoPDF", e)
        promise.resolve(
          createResponseMap(
            "Error rendering GeoPDF: ${e.message}",
            "ERROR",
            true,
            mapOf(
              "errorDetails" to (e.message ?: "Unknown error"),
              "errorType" to e.javaClass.simpleName
            )
          )
        )
      }
    }

    // Enables the module to be used as a native view. Definition components that are accepted as part of
    // the view definition: Prop, Events.
    View(ExpoGdalPdfiumView::class) {
      // Defines a setter for the `url` prop.
      Prop("url") { view: ExpoGdalPdfiumView, url: URL ->
        view.webView.loadUrl(url.toString())
      }
      // Defines an event that the view can send to JavaScript.
      Events("onLoad")
    }
  }

  // Helper data class for geospatial metadata
  private data class GeoMetadata(
    val topLeftLng: Double,
    val topLeftLat: Double,
    val topRightLng: Double,
    val topRightLat: Double,
    val bottomLeftLng: Double,
    val bottomLeftLat: Double,
    val bottomRightLng: Double,
    val bottomRightLat: Double,
    val centerLng: Double,
    val centerLat: Double
  )
  
  // Extract geospatial metadata from PDF file (GDAL only - no regex parsing)
  private fun extractGeospatialMetadata(pdfFile: File): GeoMetadata {
    // Removed regex-based extraction - restoring to original GitHub version
    // For GEODETIC PDFs, GDAL cannot extract metadata, so we return default coordinates
    try {
      Log.i("ExpoGdalPdfium", "Attempting to extract geospatial metadata using GDAL only...")
      
      // Try to use GDAL to read metadata (even if it can't render)
      // This might work even with GEODETIC projection
      try {
        gdal.AllRegister()
        val dataset: Dataset? = gdal.Open(pdfFile.absolutePath)
        
        if (dataset != null) {
          try {
            // Get geotransform
            val geoTransform = DoubleArray(6)
            dataset.GetGeoTransform(geoTransform)
            // Check if geotransform was populated (first element should be non-zero if valid)
            val hasGeoTransform = geoTransform[0] != 0.0 || geoTransform[3] != 0.0
            
            if (hasGeoTransform) {
              val width = dataset.GetRasterXSize()
              val height = dataset.GetRasterYSize()
              
              // Calculate corners from geotransform
              val topLeftX = geoTransform[0]
              val topLeftY = geoTransform[3]
              val pixelWidth = geoTransform[1]
              val pixelHeight = geoTransform[5]
              
              val topRightX = topLeftX + width * pixelWidth
              val topRightY = topLeftY
              val bottomLeftX = topLeftX
              val bottomLeftY = topLeftY + height * pixelHeight
              val bottomRightX = topLeftX + width * pixelWidth
              val bottomRightY = topLeftY + height * pixelHeight
              
              // Get projection to check if coordinates need transformation
              val projectionWkt = dataset.GetProjectionRef()
              
              var topLeftLng = topLeftX
              var topLeftLat = topLeftY
              var topRightLng = topRightX
              var topRightLat = topRightY
              var bottomLeftLng = bottomLeftX
              var bottomLeftLat = bottomLeftY
              var bottomRightLng = bottomRightX
              var bottomRightLat = bottomRightY
              
              // If projection is available and not WGS84, try to transform
              if (projectionWkt != null && projectionWkt.isNotEmpty() && !projectionWkt.contains("WGS 84", ignoreCase = true)) {
                try {
                  val sourceSRS = SpatialReference()
                  sourceSRS.ImportFromWkt(projectionWkt)
                  
                  val wgs84Wkt = "GEOGCS[\"WGS 84\",DATUM[\"WGS_1984\",SPHEROID[\"WGS 84\",6378137,298.257223563]],PRIMEM[\"Greenwich\",0],UNIT[\"degree\",0.0174532925199433]]"
                  val targetSRS = SpatialReference()
                  targetSRS.ImportFromWkt(wgs84Wkt)
                  
                  val transform = CoordinateTransformation(sourceSRS, targetSRS)
                  
                  val topLeftTransformed = transform.TransformPoint(topLeftX, topLeftY, 0.0)
                  topLeftLng = topLeftTransformed[0]
                  topLeftLat = topLeftTransformed[1]
                  
                  val topRightTransformed = transform.TransformPoint(topRightX, topRightY, 0.0)
                  topRightLng = topRightTransformed[0]
                  topRightLat = topRightTransformed[1]
                  
                  val bottomLeftTransformed = transform.TransformPoint(bottomLeftX, bottomLeftY, 0.0)
                  bottomLeftLng = bottomLeftTransformed[0]
                  bottomLeftLat = bottomLeftTransformed[1]
                  
                  val bottomRightTransformed = transform.TransformPoint(bottomRightX, bottomRightY, 0.0)
                  bottomRightLng = bottomRightTransformed[0]
                  bottomRightLat = bottomRightTransformed[1]
                  
                  Log.i("ExpoGdalPdfium", "Successfully extracted and transformed coordinates from GDAL")
                } catch (e: Exception) {
                  Log.w("ExpoGdalPdfium", "Could not transform coordinates, using native CRS: ${e.message}")
                  // Use native coordinates (might already be WGS84 for GEODETIC)
                }
              } else {
                // For GEODETIC, coordinates are likely already in WGS84
                Log.i("ExpoGdalPdfium", "Using coordinates directly (likely WGS84 for GEODETIC)")
              }
              
              // Validate coordinates are in reasonable range
              if (topLeftLng >= -180 && topLeftLng <= 180 && topLeftLat >= -90 && topLeftLat <= 90) {
                Log.i("ExpoGdalPdfium", "Extracted coordinates from GDAL: TL($topLeftLng, $topLeftLat), BR($bottomRightLng, $bottomRightLat)")
                return GeoMetadata(
                  topLeftLng = topLeftLng,
                  topLeftLat = topLeftLat,
                  topRightLng = topRightLng,
                  topRightLat = topRightLat,
                  bottomLeftLng = bottomLeftLng,
                  bottomLeftLat = bottomLeftLat,
                  bottomRightLng = bottomRightLng,
                  bottomRightLat = bottomRightLat,
                  centerLng = (topLeftLng + bottomRightLng) / 2.0,
                  centerLat = (topLeftLat + bottomRightLat) / 2.0
                )
              }
            }
          } finally {
            dataset.delete()
          }
        }
      } catch (e: Exception) {
        Log.w("ExpoGdalPdfium", "GDAL metadata extraction failed: ${e.message}")
        // For GEODETIC PDFs, GDAL cannot extract metadata
      }
      
      // GDAL failed - return default coordinates (user will need to georeference manually)
      Log.w("ExpoGdalPdfium", "Could not extract geospatial metadata using GDAL. Returning default coordinates (0,0).")
      Log.w("ExpoGdalPdfium", "This PDF may require manual georeferencing.")
      return GeoMetadata(
        topLeftLng = 0.0,
        topLeftLat = 0.0,
        topRightLng = 0.0,
        topRightLat = 0.0,
        bottomLeftLng = 0.0,
        bottomLeftLat = 0.0,
        bottomRightLng = 0.0,
        bottomRightLat = 0.0,
        centerLng = 0.0,
        centerLat = 0.0
      )
    } catch (e: Exception) {
      Log.e("ExpoGdalPdfium", "Error extracting geospatial metadata", e)
      // Return default coordinates on error
      return GeoMetadata(
        topLeftLng = 0.0,
        topLeftLat = 0.0,
        topRightLng = 0.0,
        topRightLat = 0.0,
        bottomLeftLng = 0.0,
        bottomLeftLat = 0.0,
        bottomRightLng = 0.0,
        bottomRightLat = 0.0,
        centerLng = 0.0,
        centerLat = 0.0
      )
    }
  }

  // Try to extract metadata by searching PDF file for coordinate patterns
  // This is a fallback for GEODETIC PDFs where GDAL can't extract metadata
  private fun tryExtractMetadataWithOGR(pdfPath: String): GeoMetadata? {
    try {
      Log.i("ExpoGdalPdfium", "Attempting to extract metadata by searching PDF for coordinate patterns...")
      
      val pdfFile = File(pdfPath)
      if (!pdfFile.exists() || !pdfFile.canRead()) {
        return null
      }
      
      // Read PDF file as bytes (limit to first 1MB to avoid memory issues)
      val maxBytes = 1024 * 1024 // 1MB
      val fileSize = pdfFile.length().toInt()
      val bytesToRead = minOf(fileSize, maxBytes)
      
      val fileBytes = ByteArray(bytesToRead)
      val raf = RandomAccessFile(pdfFile, "r")
      raf.readFully(fileBytes, 0, bytesToRead)
      raf.close()
      
      // Convert to string for pattern matching (may contain binary data, but we'll search anyway)
      val content = String(fileBytes, StandardCharsets.ISO_8859_1)
      
      // Search for coordinate patterns in GEODETIC PDFs
      // GEODETIC PDFs typically have coordinates in WGS84 (lat/lng format)
      // Look for patterns like: /LLX, /LLY, /URX, /URY (Lower Left X/Y, Upper Right X/Y)
      // Or bounding box arrays: [lon1 lat1 lon2 lat2] or similar
      
      // Pattern 1: Look for /LLX, /LLY, /URX, /URY (common in GeoPDF metadata)
      val llxPattern = Pattern.compile("/LLX\\s+([-+]?\\d+\\.?\\d*)")
      val llyPattern = Pattern.compile("/LLY\\s+([-+]?\\d+\\.?\\d*)")
      val urxPattern = Pattern.compile("/URX\\s+([-+]?\\d+\\.?\\d*)")
      val uryPattern = Pattern.compile("/URY\\s+([-+]?\\d+\\.?\\d*)")
      
      val llxMatcher = llxPattern.matcher(content)
      val llyMatcher = llyPattern.matcher(content)
      val urxMatcher = urxPattern.matcher(content)
      val uryMatcher = uryPattern.matcher(content)
      
      if (llxMatcher.find() && llyMatcher.find() && urxMatcher.find() && uryMatcher.find()) {
        try {
          val llx = llxMatcher.group(1).toDouble()
          val lly = llyMatcher.group(1).toDouble()
          val urx = urxMatcher.group(1).toDouble()
          val ury = uryMatcher.group(1).toDouble()
          
          // Validate coordinates are in reasonable WGS84 range
          if (llx >= -180 && llx <= 180 && lly >= -90 && lly <= 90 &&
              urx >= -180 && urx <= 180 && ury >= -90 && ury <= 90) {
            Log.i("ExpoGdalPdfium", "Found coordinates via pattern matching: LL($llx, $lly), UR($urx, $ury)")
            
            // For GEODETIC, coordinates are typically [longitude, latitude] in WGS84
            // LLX/LLY = lower left, URX/URY = upper right
            return GeoMetadata(
              topLeftLng = llx,      // Lower left X = min longitude
              topLeftLat = ury,      // Upper right Y = max latitude
              topRightLng = urx,     // Upper right X = max longitude
              topRightLat = ury,     // Upper right Y = max latitude
              bottomLeftLng = llx,   // Lower left X = min longitude
              bottomLeftLat = lly,   // Lower left Y = min latitude
              bottomRightLng = urx,  // Upper right X = max longitude
              bottomRightLat = lly,  // Lower left Y = min latitude
              centerLng = (llx + urx) / 2.0,
              centerLat = (lly + ury) / 2.0
            )
          }
        } catch (e: NumberFormatException) {
          Log.w("ExpoGdalPdfium", "Could not parse coordinate values: ${e.message}")
        }
      }
      
      // Pattern 2: Look for bounding box arrays: [x1 y1 x2 y2] or [lon1 lat1 lon2 lat2]
      // This is less common but worth trying
      val bboxPattern = Pattern.compile("\\[\\s*([-+]?\\d+\\.?\\d*)\\s+([-+]?\\d+\\.?\\d*)\\s+([-+]?\\d+\\.?\\d*)\\s+([-+]?\\d+\\.?\\d*)\\s*\\]")
      val bboxMatcher = bboxPattern.matcher(content)
      
      // Try to find a bounding box with valid WGS84 coordinates
      while (bboxMatcher.find()) {
        try {
          val coord1 = bboxMatcher.group(1).toDouble()
          val coord2 = bboxMatcher.group(2).toDouble()
          val coord3 = bboxMatcher.group(3).toDouble()
          val coord4 = bboxMatcher.group(4).toDouble()
          
          // Check if these look like WGS84 coordinates
          if (coord1 >= -180 && coord1 <= 180 && coord2 >= -90 && coord2 <= 90 &&
              coord3 >= -180 && coord3 <= 180 && coord4 >= -90 && coord4 <= 90) {
            // Determine if it's [lon1 lat1 lon2 lat2] or [x1 y1 x2 y2]
            val minLng = minOf(coord1, coord3)
            val maxLng = maxOf(coord1, coord3)
            val minLat = minOf(coord2, coord4)
            val maxLat = maxOf(coord2, coord4)
            
            Log.i("ExpoGdalPdfium", "Found bounding box via pattern matching: [$minLng, $minLat] to [$maxLng, $maxLat]")
            
            return GeoMetadata(
              topLeftLng = minLng,
              topLeftLat = maxLat,
              topRightLng = maxLng,
              topRightLat = maxLat,
              bottomLeftLng = minLng,
              bottomLeftLat = minLat,
              bottomRightLng = maxLng,
              bottomRightLat = minLat,
              centerLng = (minLng + maxLng) / 2.0,
              centerLat = (minLat + maxLat) / 2.0
            )
          }
        } catch (e: NumberFormatException) {
          // Continue searching
        }
      }
      
      Log.w("ExpoGdalPdfium", "Could not find coordinate patterns in PDF file")
      return null
    } catch (e: Exception) {
      Log.w("ExpoGdalPdfium", "Metadata extraction via pattern matching failed: ${e.message}")
      return null
    }
  }
  
  // Try to extract metadata using OGR vector API (for GEODETIC PDFs)
  private fun tryExtractMetadataWithOGRVector(pdfPath: String): GeoMetadata? {
    try {
      Log.i("ExpoGdalPdfium", "Attempting to extract metadata using OGR vector API...")
      
      // Register OGR drivers
      ogr.RegisterAll()
      
      // Try to open PDF with OGR vector driver
      val dataSource = ogr.Open(pdfPath, 0)
      if (dataSource == null) {
        Log.w("ExpoGdalPdfium", "OGR vector API could not open PDF file")
        return null
      }
      
      try {
        val layerCount = dataSource.GetLayerCount()
        Log.i("ExpoGdalPdfium", "OGR found $layerCount layers in PDF")
        
        // Iterate through layers to find geometry
        for (i in 0 until layerCount) {
          val layer = dataSource.GetLayer(i)
          if (layer == null) continue
          
          val featureCount = layer.GetFeatureCount()
          Log.i("ExpoGdalPdfium", "Layer $i has $featureCount features")
          
          // Get spatial reference
          val spatialRef = layer.GetSpatialRef()
          if (spatialRef != null) {
            val proj4 = spatialRef.ExportToProj4()
            Log.i("ExpoGdalPdfium", "Layer $i spatial reference: $proj4")
          }
          
          // Get extent (bounding box)
          val extent = layer.GetExtent(true)
          if (extent != null && extent.size >= 4) {
            val minX = extent[0]
            val maxX = extent[1]
            val minY = extent[2]
            val maxY = extent[3]
            
            Log.i("ExpoGdalPdfium", "Layer $i extent: [$minX, $minY] to [$maxX, $maxY]")
            
            // Check if coordinates look valid (WGS84 range)
            if (minX >= -180 && maxX <= 180 && minY >= -90 && maxY <= 90) {
              // Transform to WGS84 if needed
              val targetSRS = org.gdal.osr.SpatialReference()
              targetSRS.ImportFromEPSG(4326) // WGS84
              
              if (spatialRef != null && spatialRef.IsSame(targetSRS) == 0) {
                // Need coordinate transformation
                val transform = org.gdal.osr.CoordinateTransformation(spatialRef, targetSRS)
                if (transform != null) {
                  val minPoint = DoubleArray(3)
                  val maxPoint = DoubleArray(3)
                  minPoint[0] = minX
                  minPoint[1] = minY
                  minPoint[2] = 0.0
                  maxPoint[0] = maxX
                  maxPoint[1] = maxY
                  maxPoint[2] = 0.0
                  
                  transform.TransformPoint(minPoint)
                  transform.TransformPoint(maxPoint)
                  
                  val transformedMinX = minPoint[0]
                  val transformedMinY = minPoint[1]
                  val transformedMaxX = maxPoint[0]
                  val transformedMaxY = maxPoint[1]
                  
                  Log.i("ExpoGdalPdfium", "Transformed extent to WGS84: [$transformedMinX, $transformedMinY] to [$transformedMaxX, $transformedMaxY]")
                  
                  return GeoMetadata(
                    topLeftLng = transformedMinX,
                    topLeftLat = transformedMaxY,
                    topRightLng = transformedMaxX,
                    topRightLat = transformedMaxY,
                    bottomLeftLng = transformedMinX,
                    bottomLeftLat = transformedMinY,
                    bottomRightLng = transformedMaxX,
                    bottomRightLat = transformedMinY,
                    centerLng = (transformedMinX + transformedMaxX) / 2.0,
                    centerLat = (transformedMinY + transformedMaxY) / 2.0
                  )
                }
              } else {
                // Already in WGS84 or no transformation needed
                return GeoMetadata(
                  topLeftLng = minX,
                  topLeftLat = maxY,
                  topRightLng = maxX,
                  topRightLat = maxY,
                  bottomLeftLng = minX,
                  bottomLeftLat = minY,
                  bottomRightLng = maxX,
                  bottomRightLat = minY,
                  centerLng = (minX + maxX) / 2.0,
                  centerLat = (minY + maxY) / 2.0
                )
              }
            }
          }
        }
        
        Log.w("ExpoGdalPdfium", "OGR vector API found layers but no valid coordinates")
        return null
      } finally {
        dataSource.delete()
      }
    } catch (e: Exception) {
      Log.w("ExpoGdalPdfium", "Metadata extraction via OGR vector API failed: ${e.message}")
      e.printStackTrace()
      return null
    }
  }
  
  // Render PDF using Android PdfRenderer (fallback for GEODETIC PDFs)
  private fun renderPDFWithAndroidPdfRenderer(
    inputPath: String,
    outputPath: String,
    metadata: GeoMetadata?
  ): Map<String, Any>? {
    try {
      Log.i("ExpoGdalPdfium", "Rendering PDF with Android PdfRenderer (GEODETIC fallback)...")
      
      val inputFile = File(inputPath)
      if (!inputFile.exists() || !inputFile.canRead()) {
        Log.e("ExpoGdalPdfium", "PDF file not accessible for PdfRenderer")
        return null
      }
      
      // Open PDF with PdfRenderer
      val fileDescriptor = ParcelFileDescriptor.open(inputFile, ParcelFileDescriptor.MODE_READ_ONLY)
      val pdfRenderer = PdfRenderer(fileDescriptor)
      
      try {
        // Get first page (PDFs typically have geospatial data on first page)
        val pageCount = pdfRenderer.pageCount
        if (pageCount == 0) {
          Log.e("ExpoGdalPdfium", "PDF has no pages")
          return null
        }
        
        val page = pdfRenderer.openPage(0)
        
        try {
          // Render at high resolution (match GDAL's typical output)
          // PdfRenderer uses points (1/72 inch), so we need to scale for high DPI
          val scale = 4.0f // Scale factor for high resolution (adjust as needed)
          val width = (page.width * scale).toInt()
          val height = (page.height * scale).toInt()
          
          Log.i("ExpoGdalPdfium", "Rendering PDF page: ${page.width}x${page.height} -> ${width}x${height}")
          
          // Create bitmap
          val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
          val canvas = Canvas(bitmap)
          canvas.drawColor(android.graphics.Color.WHITE)
          
          // Render page to bitmap
          page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
          
          // Save as PNG
          val outputFile = File(outputPath)
          val fos = FileOutputStream(outputFile)
          bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
          fos.flush()
          fos.close()
          bitmap.recycle()
          
          if (!outputFile.exists() || outputFile.length() == 0L) {
            Log.e("ExpoGdalPdfium", "Failed to write PNG file")
            return null
          }
          
          Log.i("ExpoGdalPdfium", "Successfully rendered PDF with PdfRenderer: $outputPath (size: ${outputFile.length()} bytes)")
          
          // Use metadata if available, otherwise return with default coordinates
          // User will need to manually georeference if metadata extraction failed
          val finalMetadata = metadata ?: GeoMetadata(
            topLeftLng = 0.0,
            topLeftLat = 0.0,
            topRightLng = 0.0,
            topRightLat = 0.0,
            bottomLeftLng = 0.0,
            bottomLeftLat = 0.0,
            bottomRightLng = 0.0,
            bottomRightLat = 0.0,
            centerLng = 0.0,
            centerLat = 0.0
          )
          
          return createResponseMap(
            "GeoPDF rendered to PNG successfully (using Android PdfRenderer fallback)",
            "SUCCESS",
            false,
            mapOf(
              "inputPath" to inputPath,
              "outputPath" to outputPath,
              "width" to width.toString(),
              "height" to height.toString(),
              "renderedWith" to "AndroidPdfRenderer",
              "metadataExtracted" to (metadata != null),
              "topLeft" to mapOf("x" to finalMetadata.topLeftLng.toString(), "y" to finalMetadata.topLeftLat.toString()),
              "topRight" to mapOf("x" to finalMetadata.topRightLng.toString(), "y" to finalMetadata.topRightLat.toString()),
              "bottomLeft" to mapOf("x" to finalMetadata.bottomLeftLng.toString(), "y" to finalMetadata.bottomLeftLat.toString()),
              "bottomRight" to mapOf("x" to finalMetadata.bottomRightLng.toString(), "y" to finalMetadata.bottomRightLat.toString()),
              "center" to mapOf("x" to finalMetadata.centerLng.toString(), "y" to finalMetadata.centerLat.toString())
            )
          )
        } finally {
          page.close()
        }
      } finally {
        pdfRenderer.close()
        fileDescriptor.close()
      }
    } catch (e: Exception) {
      Log.e("ExpoGdalPdfium", "Error rendering PDF with Android PdfRenderer", e)
      return null
    }
  }

  // Helper function to create consistent response maps
  private fun createResponseMap(
    msg: String,
    code: String,
    error: Boolean,
    result: Map<String, Any>?
  ): Map<String, Any> {
    val resultMap = result ?: emptyMap<String, Any>()
    return mapOf(
      "msg" to msg,
      "code" to code,
      "error" to error,
      "result" to resultMap
    )
  }
}

