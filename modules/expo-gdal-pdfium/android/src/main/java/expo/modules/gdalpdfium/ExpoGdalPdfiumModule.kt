package expo.modules.gdalpdfium

import android.util.Log
import expo.modules.kotlin.Promise
import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition
import java.net.URL

// GDAL Java bindings import
// Standard GDAL Java bindings use: org.gdal.gdal.gdal
import org.gdal.gdal.gdal
import org.gdal.gdal.Driver
import org.gdal.gdal.Dataset
import org.gdal.gdal.Band

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

