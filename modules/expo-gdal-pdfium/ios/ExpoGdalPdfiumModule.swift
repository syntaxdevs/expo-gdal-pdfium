import ExpoModulesCore
import GDAL
import PDFKit
import UniformTypeIdentifiers

public class ExpoGdalPdfiumModule: Module {
  public func definition() -> ModuleDefinition {
    Name("ExpoGdalPdfium")

    Constant("PI") {
      Double.pi
    }

    Events("onChange")

    Function("hello") {
      return "Hello world! 👋"
    }

    AsyncFunction("setValueAsync") { (value: String) in
      self.sendEvent("onChange", [
        "value": value
      ])
    }

    // GDAL Version Info Function
    // Returns GDAL library version information
    AsyncFunction("getVersionInfo") {
      let versionPtr = GDALVersionInfo("--version")
      let versionNumPtr = GDALVersionInfo("VERSION_NUM")
      let releaseDatePtr = GDALVersionInfo("RELEASE_DATE")

      let version = versionPtr != nil ? String(cString: versionPtr!) : "Unknown"
      let versionNum = versionNumPtr != nil ? String(cString: versionNumPtr!) : "Unknown"
      let releaseDate = releaseDatePtr != nil ? String(cString: releaseDatePtr!) : "Unknown"

      return [
        "msg": "Version info retrieved successfully",
        "code": "SUCCESS",
        "error": false,
        "result": [
          "version": version,
          "versionNum": versionNum,
          "releaseDate": releaseDate
        ]
      ]
    }

    // GDAL List Drivers Function
    // Returns a list of all available GDAL drivers
    AsyncFunction("listDrivers") {
      GDALAllRegister()

      let driverCount = GDALGetDriverCount()
      var drivers: [[String: String]] = []

      if driverCount > 0 {
        for i in 0..<driverCount {
          if let driver = GDALGetDriver(i) {
            let shortName = GDALGetDriverShortName(driver).map { String(cString: $0) } ?? "Unknown"
            let longName = GDALGetDriverLongName(driver).map { String(cString: $0) } ?? "Unknown"

            drivers.append([
              "shortName": shortName,
              "longName": longName
            ])
          }
        }
      }

      return [
        "msg": "Drivers retrieved successfully",
        "code": "SUCCESS",
        "error": false,
        "result": [
          "driverCount": String(driverCount),
          "drivers": drivers
        ]
      ]
    }

    // GDAL Read GeoPDF Function
    // Reads a GeoPDF file and returns its basic dataset information
    AsyncFunction("readGeoPDF") { (filePath: String) in
      // Register all GDAL drivers before opening the dataset
      GDALAllRegister()

      // Normalize file path by removing the file:// prefix if present
      let normalizedPath = filePath.replacingOccurrences(of: "file://", with: "")
      let fileInfo = getFileInfo(path: normalizedPath)
      let fileExists = fileInfo["fileExists"] as? Bool ?? false
      let canRead = fileInfo["canRead"] as? Bool ?? false
      let isFile = fileInfo["isFile"] as? Bool ?? false
      let fileSizeString = fileInfo["fileSize"] as? String ?? "0"
      let fileSize = UInt64(fileSizeString) ?? 0

      if !fileExists || !isFile {
        return createResponse(
          msg: "Failed to open GeoPDF file",
          code: "FILE_NOT_FOUND",
          error: true,
          result: [
            "errorDetails": "File does not exist: \(normalizedPath)"
          ].merging(fileInfo) { _, new in new }
        )
      }

      if fileSize == 0 {
        return createResponse(
          msg: "Failed to open GeoPDF file",
          code: "FILE_EMPTY",
          error: true,
          result: [
            "errorDetails": "File is empty (0 bytes): \(normalizedPath)"
          ].merging(fileInfo) { _, new in new }
        )
      }

      if !canRead {
        return createResponse(
          msg: "Failed to open GeoPDF file",
          code: "FILE_PERMISSION_DENIED",
          error: true,
          result: [
            "errorDetails": "File exists but cannot be read: \(normalizedPath)"
          ].merging(fileInfo) { _, new in new }
        )
      }
      
      // Open the GeoPDF dataset in read-only mode
      guard let dataset = GDALOpen(normalizedPath, GA_ReadOnly) else {
        let gdalError = CPLGetLastErrorMsg().map { String(cString: $0) } ?? "No error message available"
        let gdalErrorType = "\(CPLGetLastErrorType().rawValue)"

        return createResponse(
          msg: "Failed to open GeoPDF file",
          code: "GDAL_OPEN_FAILED",
          error: true,
          result: [
            "errorDetails": "GDAL could not open file: \(normalizedPath)",
            "gdalError": gdalError,
            "gdalErrorType": gdalErrorType
          ].merging(fileInfo) { _, new in new }
        )
      }

      // Ensure the dataset is closed to free native resources
      defer {
        GDALClose(dataset)
      }

      let width = GDALGetRasterXSize(dataset)
      let height = GDALGetRasterYSize(dataset)
      let bandCount = GDALGetRasterCount(dataset)

      let driver = GDALGetDatasetDriver(dataset)
      let driverName = driver != nil
        ? (GDALGetDriverShortName(driver).map { String(cString: $0) } ?? "Unknown")
        : "Unknown"

      let projectionPtr = GDALGetProjectionRef(dataset)
      let projection = projectionPtr != nil ? String(cString: projectionPtr!) : "Unknown"
      // Read the dataset geotransform; return all six values to match Android behavior
      var geoTransform = [Double](repeating: 0.0, count: 6)
      _ = GDALGetGeoTransform(dataset, &geoTransform)
      let geoTransformStrings = geoTransform.map { String($0) }

      var bandsInfo: [[String: Any]] = []

      // Collect basic information for each raster band
      if bandCount > 0 {
        for i in 1...bandCount {
          guard let band = GDALGetRasterBand(dataset, i) else { continue }

          let dataType = GDALGetRasterDataType(band)
          let dataTypeName = GDALGetDataTypeName(dataType).map { String(cString: $0) } ?? "Unknown"

          var blockX: Int32 = 0
          var blockY: Int32 = 0
          GDALGetBlockSize(band, &blockX, &blockY)

          bandsInfo.append([
            "bandNumber": String(i),
            "dataType": dataTypeName,
            "blockWidth": String(blockX),
            "blockHeight": String(blockY)
          ])
        }
      }

      return createResponse(
        msg: "GeoPDF read successfully",
        code: "SUCCESS",
        error: false,
        result: [
          "filePath": normalizedPath,
          "width": String(width),
          "height": String(height),
          "bandCount": String(bandCount),
          "driver": driverName,
          "projection": projection,
          "geoTransform": geoTransformStrings,
          "bands": bandsInfo
        ]
      )
    }

    // GDAL Render GeoPDF to PNG Function
    // Renders a GeoPDF file to a PNG image file
    AsyncFunction("renderGeoPDFToPng") { (inputPath: String, outputPath: String) in

      // Register all GDAL drivers before opening the dataset
      GDALAllRegister()

      let normalizedInputPath = inputPath.replacingOccurrences(of: "file://", with: "")
      let normalizedOutputPath = outputPath.replacingOccurrences(of: "file://", with: "")

      let fileInfo = getFileInfo(path: normalizedInputPath)
      let fileExists = fileInfo["fileExists"] as? Bool ?? false
      let canRead = fileInfo["canRead"] as? Bool ?? false
      let isFile = fileInfo["isFile"] as? Bool ?? false
      let fileSizeString = fileInfo["fileSize"] as? String ?? "0"
      let fileSize = UInt64(fileSizeString) ?? 0

      if !fileExists || !isFile {
        return createResponse(
          msg: "Failed to open GeoPDF file for rendering",
          code: "FILE_NOT_FOUND",
          error: true,
          result: [
            "errorDetails": "File does not exist: \(normalizedInputPath)"
          ].merging(fileInfo) { _, new in new }
        )
      }

      if fileSize == 0 {
        return createResponse(
          msg: "Failed to open GeoPDF file for rendering",
          code: "FILE_EMPTY",
          error: true,
          result: [
            "errorDetails": "File is empty (0 bytes): \(normalizedInputPath)"
          ].merging(fileInfo) { _, new in new }
        )
      }

      if !canRead {
        return createResponse(
          msg: "Failed to open GeoPDF file for rendering",
          code: "FILE_PERMISSION_DENIED",
          error: true,
          result: [
            "errorDetails": "File exists but cannot be read: \(normalizedInputPath)"
          ].merging(fileInfo) { _, new in new }
        )
      }

      // Try to open the GeoPDF dataset using GDAL raster API
      guard let dataset = GDALOpen(normalizedInputPath, GA_ReadOnly) else {
        let gdalError = CPLGetLastErrorMsg().map { String(cString: $0) } ?? "No error message available"
        let gdalErrorType = "\(CPLGetLastErrorType().rawValue)"

        // Detect GEODETIC projection errors and route those cases to the PDFKit fallback path
        let lowerError = gdalError.lowercased()
        let isGeodetic =
          lowerError.contains("geodetic") ||
          (lowerError.contains("unhandled") && lowerError.contains("projectiontype"))

        if isGeodetic {
          return attemptGeodeticFallback(
            inputPath: normalizedInputPath,
            outputPath: normalizedOutputPath,
            fileInfo: fileInfo,
            gdalError: gdalError,
            gdalErrorType: gdalErrorType
          )
        }

        return createResponse(
          msg: "Failed to open GeoPDF file for rendering",
          code: "GDAL_OPEN_FAILED",
          error: true,
          result: [
            "errorDetails": "GDAL could not open file: \(normalizedInputPath)",
            "gdalError": gdalError,
            "gdalErrorType": gdalErrorType,
            "isGeodetic": isGeodetic,
            "fallbackAttempted": false
          ].merging(fileInfo) { _, new in new }
        )
      }

      defer {
        GDALClose(dataset)
      }

      // Read raster dimensions and band count from the opened dataset
      let width = GDALGetRasterXSize(dataset)
      let height = GDALGetRasterYSize(dataset)
      let bandCount = GDALGetRasterCount(dataset)

      if width <= 0 || height <= 0 {
        return createResponse(
          msg: "Failed to render GeoPDF to PNG",
          code: "RENDER_ERROR",
          error: true,
          result: [
            "errorDetails": "Invalid raster dimensions",
            "width": String(width),
            "height": String(height)
          ]
        )
      }

      if bandCount < 1 {
        return createResponse(
          msg: "Failed to render GeoPDF to PNG",
          code: "RENDER_ERROR",
          error: true,
          result: [
            "errorDetails": "Dataset has no raster bands",
            "bandCount": String(bandCount)
          ]
        )
      }

      // Read geotransform values used to calculate image corner and center coordinates
      var geoTransform = [Double](repeating: 0.0, count: 6)
      let gtResult = GDALGetGeoTransform(dataset, &geoTransform)

      let geoTransformStrings: [String]
      if gtResult == CE_None {
        geoTransformStrings = geoTransform.map { String($0) }
      } else {
        geoTransformStrings = []
      }

      // Calculate corner and center coordinates from the dataset geotransform
      // Assumes no rotation, matching the Android implementation
      let topLeftX = geoTransform[0]
      let topLeftY = geoTransform[3]
      let pixelWidth = geoTransform[1]
      let pixelHeight = geoTransform[5]

      let topRightX = topLeftX + Double(width) * pixelWidth
      let topRightY = topLeftY

      let bottomLeftX = topLeftX
      let bottomLeftY = topLeftY + Double(height) * pixelHeight

      let bottomRightX = topLeftX + Double(width) * pixelWidth
      let bottomRightY = topLeftY + Double(height) * pixelHeight

      let centerX = topLeftX + (Double(width) / 2.0) * pixelWidth
      let centerY = topLeftY + (Double(height) / 2.0) * pixelHeight

      // Read projection information and transform coordinates to WGS84 when possible
      let projectionPtr = GDALGetProjectionRef(dataset)
      let projection = projectionPtr != nil ? String(cString: projectionPtr!) : ""

      var finalTopLeftX = topLeftX
      var finalTopLeftY = topLeftY
      var finalTopRightX = topRightX
      var finalTopRightY = topRightY
      var finalBottomLeftX = bottomLeftX
      var finalBottomLeftY = bottomLeftY
      var finalBottomRightX = bottomRightX
      var finalBottomRightY = bottomRightY
      var finalCenterX = centerX
      var finalCenterY = centerY

      if let transformed = transformPointToWGS84(x: topLeftX, y: topLeftY, projection: projection) {
        finalTopLeftX = transformed.x
        finalTopLeftY = transformed.y
      }
      if let transformed = transformPointToWGS84(x: topRightX, y: topRightY, projection: projection) {
        finalTopRightX = transformed.x
        finalTopRightY = transformed.y
      }
      if let transformed = transformPointToWGS84(x: bottomLeftX, y: bottomLeftY, projection: projection) {
        finalBottomLeftX = transformed.x
        finalBottomLeftY = transformed.y
      }
      if let transformed = transformPointToWGS84(x: bottomRightX, y: bottomRightY, projection: projection) {
        finalBottomRightX = transformed.x
        finalBottomRightY = transformed.y
      }
      if let transformed = transformPointToWGS84(x: centerX, y: centerY, projection: projection) {
        finalCenterX = transformed.x
        finalCenterY = transformed.y
      }
      
      // Get the PNG driver and create the output PNG file from the source dataset
      guard let pngDriver = GDALGetDriverByName("PNG") else {
        return createResponse(
          msg: "Failed to render GeoPDF to PNG",
          code: "RENDER_ERROR",
          error: true,
          result: [
            "errorDetails": "PNG driver not available"
          ]
        )
      }

      guard let outDataset = GDALCreateCopy(
        pngDriver,
        normalizedOutputPath,
        dataset,
        0,
        nil,
        nil,
        nil
      ) else {
        let gdalError = CPLGetLastErrorMsg().map { String(cString: $0) } ?? "No error message available"
        let gdalErrorType = "\(CPLGetLastErrorType().rawValue)"

        return createResponse(
          msg: "Failed to render GeoPDF to PNG",
          code: "RENDER_ERROR",
          error: true,
          result: [
            "errorDetails": "GDALCreateCopy failed for output: \(normalizedOutputPath)",
            "gdalError": gdalError,
            "gdalErrorType": gdalErrorType
          ]
        )
      }

      GDALClose(outDataset)

      if !FileManager.default.fileExists(atPath: normalizedOutputPath) {
        return createResponse(
          msg: "Failed to render GeoPDF to PNG",
          code: "RENDER_ERROR",
          error: true,
          result: [
            "errorDetails": "PNG file was not created: \(normalizedOutputPath)"
          ]
        )
      }

      return [
        "msg": "GeoPDF rendered to PNG successfully",
        "code": "SUCCESS",
        "error": false,
        "result": [
          "inputPath": normalizedInputPath,
          "outputPath": normalizedOutputPath,
          "width": String(width),
          "height": String(height),
          "geoTransform": geoTransformStrings,
          "topLeft": [
            "x": String(finalTopLeftX),
            "y": String(finalTopLeftY)
          ],
          "topRight": [
            "x": String(finalTopRightX),
            "y": String(finalTopRightY)
          ],
          "bottomLeft": [
            "x": String(finalBottomLeftX),
            "y": String(finalBottomLeftY)
          ],
          "bottomRight": [
            "x": String(finalBottomRightX),
            "y": String(finalBottomRightY)
          ],
          "center": [
            "x": String(finalCenterX),
            "y": String(finalCenterY)
          ]
        ]
      ]
    }

    View(ExpoGdalPdfiumView.self) {
      Prop("url") { (view: ExpoGdalPdfiumView, url: URL) in
        if view.webView.url != url {
          view.webView.load(URLRequest(url: url))
        }
      }

      Events("onLoad")
    }
  }

  // Helper function that transforms a point from the dataset CRS to WGS84 using the Objective-C++ GDAL bridge
  private func transformPointToWGS84(x: Double, y: Double, projection: String?) -> (x: Double, y: Double)? {
    guard let projection = projection, !projection.isEmpty else {
      return nil
    }

    guard let result = GDALTransformHelper.transformPointX(x, y: y, projectionWKT: projection) else {
      return nil
    }

    guard let tx = result["x"] as? NSNumber,
          let ty = result["y"] as? NSNumber else {
      return nil
    }

    return (x: tx.doubleValue, y: ty.doubleValue)
  }
  
  // Fallback renderer that uses PDFKit to rasterize the first PDF page into a PNG image
  private func renderFirstPagePdfToPng(inputPath: String, outputPath: String) -> (width: Int, height: Int)? {
    let inputURL = URL(fileURLWithPath: inputPath)

    guard let pdfDocument = PDFDocument(url: inputURL),
          let page = pdfDocument.page(at: 0) else {
      return nil
    }

    let pageRect = page.bounds(for: .mediaBox)
    let scale: CGFloat = 2.0
    let width = Int(pageRect.width * scale)
    let height = Int(pageRect.height * scale)

    let renderer = UIGraphicsImageRenderer(size: CGSize(width: width, height: height))
    let image = renderer.image { context in
      UIColor.white.set()
      context.fill(CGRect(x: 0, y: 0, width: width, height: height))

      context.cgContext.saveGState()

      context.cgContext.translateBy(x: 0, y: CGFloat(height))
      context.cgContext.scaleBy(x: scale, y: -scale)

      page.draw(with: .mediaBox, to: context.cgContext)

      context.cgContext.restoreGState()
    }

    guard let pngData = image.pngData() else {
      return nil
    }

    do {
      try pngData.write(to: URL(fileURLWithPath: outputPath))
      return (width: width, height: height)
    } catch {
      return nil
    }
  }

  // Attempts to extract geospatial bounds directly from raw PDF content using common GeoPDF metadata patterns
  private func extractGeospatialMetadataFromPdf(path: String) -> [String: Double]? {
    guard let data = try? Data(contentsOf: URL(fileURLWithPath: path)),
          let content = String(data: data, encoding: .isoLatin1) else {
      return nil
    }

    // Helper used to extract the first numeric match for a given PDF metadata pattern
    func firstMatch(_ pattern: String, in text: String) -> Double? {
      guard let regex = try? NSRegularExpression(pattern: pattern, options: []) else {
        return nil
      }

      let range = NSRange(text.startIndex..., in: text)
      guard let match = regex.firstMatch(in: text, options: [], range: range),
            match.numberOfRanges > 1,
            let valueRange = Range(match.range(at: 1), in: text) else {
        return nil
      }

      return Double(String(text[valueRange]))
    }

    let llx = firstMatch("/LLX\\s+([-+]?\\d+\\.?\\d*)", in: content)
    let lly = firstMatch("/LLY\\s+([-+]?\\d+\\.?\\d*)", in: content)
    let urx = firstMatch("/URX\\s+([-+]?\\d+\\.?\\d*)", in: content)
    let ury = firstMatch("/URY\\s+([-+]?\\d+\\.?\\d*)", in: content)

    if let llx, let lly, let urx, let ury,
      (-180...180).contains(llx),
      (-90...90).contains(lly),
      (-180...180).contains(urx),
      (-90...90).contains(ury) {
      let minLng = min(llx, urx)
      let maxLng = max(llx, urx)
      let minLat = min(lly, ury)
      let maxLat = max(lly, ury)

      let width = abs(maxLng - minLng)
      let height = abs(maxLat - minLat)

      if width > 0.000001, height > 0.000001 {
        return [
          "topLeftX": minLng,
          "topLeftY": maxLat,
          "topRightX": maxLng,
          "topRightY": maxLat,
          "bottomLeftX": minLng,
          "bottomLeftY": minLat,
          "bottomRightX": maxLng,
          "bottomRightY": minLat,
          "centerX": (minLng + maxLng) / 2.0,
          "centerY": (minLat + maxLat) / 2.0
        ]
      }
    }

    guard let regex = try? NSRegularExpression(
      pattern: "\\[\\s*([-+]?\\d+\\.?\\d*)\\s+([-+]?\\d+\\.?\\d*)\\s+([-+]?\\d+\\.?\\d*)\\s+([-+]?\\d+\\.?\\d*)\\s*\\]",
      options: []
    ) else {
      return nil
    }

    let range = NSRange(content.startIndex..., in: content)
    let matches = regex.matches(in: content, options: [], range: range)

    for match in matches where match.numberOfRanges == 5 {
      guard
        let r1 = Range(match.range(at: 1), in: content),
        let r2 = Range(match.range(at: 2), in: content),
        let r3 = Range(match.range(at: 3), in: content),
        let r4 = Range(match.range(at: 4), in: content),
        let c1 = Double(String(content[r1])),
        let c2 = Double(String(content[r2])),
        let c3 = Double(String(content[r3])),
        let c4 = Double(String(content[r4]))
      else {
        continue
      }

      if (-180...180).contains(c1),
        (-90...90).contains(c2),
        (-180...180).contains(c3),
        (-90...90).contains(c4) {
        let minLng = min(c1, c3)
        let maxLng = max(c1, c3)
        let minLat = min(c2, c4)
        let maxLat = max(c2, c4)

        let width = abs(maxLng - minLng)
        let height = abs(maxLat - minLat)

        if width > 0.000001, height > 0.000001 {
          return [
            "topLeftX": minLng,
            "topLeftY": maxLat,
            "topRightX": maxLng,
            "topRightY": maxLat,
            "bottomLeftX": minLng,
            "bottomLeftY": minLat,
            "bottomRightX": maxLng,
            "bottomRightY": minLat,
            "centerX": (minLng + maxLng) / 2.0,
            "centerY": (minLat + maxLat) / 2.0
          ]
        }
      }
    }

    return nil
  }

  // Handles GeoPDF rendering fallback for GEODETIC projection cases not supported by GDAL raster opening
  // Tries metadata extraction first, then renders the first page with PDFKit
  private func attemptGeodeticFallback(
    inputPath: String,
    outputPath: String,
    fileInfo: [String: Any],
    gdalError: String,
    gdalErrorType: String
  ) -> [String: Any] {
    var metadata = extractGeospatialMetadataFromPdf(path: inputPath)

    if metadata == nil,
      let ogrMetadata = GDALTransformHelper.extractGeospatialMetadata(withOGR: inputPath) as? [String: NSNumber] {
      metadata = [
        "topLeftX": ogrMetadata["topLeftX"]?.doubleValue ?? 0.0,
        "topLeftY": ogrMetadata["topLeftY"]?.doubleValue ?? 0.0,
        "topRightX": ogrMetadata["topRightX"]?.doubleValue ?? 0.0,
        "topRightY": ogrMetadata["topRightY"]?.doubleValue ?? 0.0,
        "bottomLeftX": ogrMetadata["bottomLeftX"]?.doubleValue ?? 0.0,
        "bottomLeftY": ogrMetadata["bottomLeftY"]?.doubleValue ?? 0.0,
        "bottomRightX": ogrMetadata["bottomRightX"]?.doubleValue ?? 0.0,
        "bottomRightY": ogrMetadata["bottomRightY"]?.doubleValue ?? 0.0,
        "centerX": ogrMetadata["centerX"]?.doubleValue ?? 0.0,
        "centerY": ogrMetadata["centerY"]?.doubleValue ?? 0.0
      ]
    }

    let metadataExtracted = metadata != nil

    guard let renderResult = renderFirstPagePdfToPng(inputPath: inputPath, outputPath: outputPath) else {
      return createResponse(
        msg: "Failed to render GeoPDF to PNG",
        code: "RENDER_ERROR",
        error: true,
        result: [
          "errorDetails": "Fallback PDF rendering failed",
          "gdalError": gdalError,
          "gdalErrorType": gdalErrorType,
          "isGeodetic": true,
          "fallbackAttempted": true,
          "metadataExtracted": metadataExtracted
        ].merging(fileInfo) { _, new in new }
      )
    }

    let topLeftX = metadata?["topLeftX"] ?? 0.0
    let topLeftY = metadata?["topLeftY"] ?? 0.0
    let topRightX = metadata?["topRightX"] ?? 0.0
    let topRightY = metadata?["topRightY"] ?? 0.0
    let bottomLeftX = metadata?["bottomLeftX"] ?? 0.0
    let bottomLeftY = metadata?["bottomLeftY"] ?? 0.0
    let bottomRightX = metadata?["bottomRightX"] ?? 0.0
    let bottomRightY = metadata?["bottomRightY"] ?? 0.0
    let centerX = metadata?["centerX"] ?? 0.0
    let centerY = metadata?["centerY"] ?? 0.0

    return createResponse(
      msg: "GeoPDF rendered to PNG successfully (using PDFKit fallback)",
      code: "SUCCESS",
      error: false,
      result: [
        "inputPath": inputPath,
        "outputPath": outputPath,
        "width": String(renderResult.width),
        "height": String(renderResult.height),
        "geoTransform": [],
        "renderedWith": "PDFKit",
        "gdalError": gdalError,
        "gdalErrorType": gdalErrorType,
        "isGeodetic": true,
        "fallbackAttempted": true,
        "metadataExtracted": metadataExtracted,
        "topLeft": [
          "x": String(topLeftX),
          "y": String(topLeftY)
        ],
        "topRight": [
          "x": String(topRightX),
          "y": String(topRightY)
        ],
        "bottomLeft": [
          "x": String(bottomLeftX),
          "y": String(bottomLeftY)
        ],
        "bottomRight": [
          "x": String(bottomRightX),
          "y": String(bottomRightY)
        ],
        "center": [
          "x": String(centerX),
          "y": String(centerY)
        ]
      ].merging(fileInfo) { old, _ in old }
    )
  }

  // Helper function to create a consistent response object for all module methods
  private func createResponse(
    msg: String,
    code: String,
    error: Bool,
    result: [String: Any]
  ) -> [String: Any] {
    return [
      "msg": msg,
      "code": code,
      "error": error,
      "result": result
    ]
  }

  // Helper function that returns file existence, permissions, type, and size information for debugging and validation
  private func getFileInfo(path: String) -> [String: Any] {
    let fileManager = FileManager.default
    let absolutePath = URL(fileURLWithPath: path).path

    var isDirectory: ObjCBool = false
    let exists = fileManager.fileExists(atPath: path, isDirectory: &isDirectory)
    let canRead = fileManager.isReadableFile(atPath: path)
    let canWrite = fileManager.isWritableFile(atPath: path)

    var fileSize: UInt64 = 0
    if exists, let attrs = try? fileManager.attributesOfItem(atPath: path),
      let size = attrs[.size] as? NSNumber {
      fileSize = size.uint64Value
    }

    return [
      "absolutePath": absolutePath,
      "fileExists": exists,
      "canRead": canRead,
      "canWrite": canWrite,
      "isFile": exists && !isDirectory.boolValue,
      "isDirectory": isDirectory.boolValue,
      "fileSize": String(fileSize)
    ]
  }
}