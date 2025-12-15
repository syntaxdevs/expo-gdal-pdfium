package expo.modules.gdalpdfium

import android.util.Log
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import expo.modules.kotlin.Promise
import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition
import java.net.URL
import java.io.FileOutputStream

// GDAL Java bindings import
// Standard GDAL Java bindings use: org.gdal.gdal.gdal
import org.gdal.gdal.gdal
import org.gdal.gdal.Driver
import org.gdal.gdal.Dataset
import org.gdal.gdal.Band
import org.gdal.gdal.TranslateOptions
import java.util.Vector

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
        Log.i("ExpoGdalPdfium", "Rendering GeoPDF to PNG: $inputPath -> $outputPath")
        
        // Register all drivers first
        gdal.AllRegister()
        
        // Open the GeoPDF file
        val dataset: Dataset? = gdal.Open(inputPath)
        
        if (dataset == null) {
          promise.resolve(
            createResponseMap(
              "Failed to open GeoPDF file for rendering",
              "FILE_NOT_FOUND",
              true,
              mapOf("errorDetails" to "Could not open file: $inputPath")
            )
          )
          return@AsyncFunction
        }
        
        try {
          // Get dataset dimensions
          val width = dataset.GetRasterXSize()
          val height = dataset.GetRasterYSize()
          val bandCount = dataset.GetRasterCount()
          
          Log.i("ExpoGdalPdfium", "Dataset dimensions: ${width}x${height}, bands: $bandCount")
          
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
                  "outputPath" to outputPath
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

