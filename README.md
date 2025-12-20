# expo-gdal-pdfium

Expo module for processing GeoPDF files using GDAL with coordinate transformation to WGS84.

## Features

- Read GeoPDF metadata and information
- Render GeoPDF files to PNG images
- Transform coordinates from native CRS to WGS84 (EPSG:4326)
- List available GDAL drivers
- Get GDAL version information

## Installation

### From GitHub

```bash
npm install https://github.com/syntaxdevs/expo-gdal-pdfium.git
# or
yarn add https://github.com/syntaxdevs/expo-gdal-pdfium.git
```

### From npm (if published)

```bash
npm install expo-gdal-pdfium
# or
yarn add expo-gdal-pdfium
```

## Prerequisites

This module requires the GDAL native library (`.aar` file for Android). The library file should be placed at:
- Android: `android/libs/gdal-release.aar`

Make sure your project has the GDAL `.aar` file in the correct location.

## Usage

```typescript
import { 
  getVersionInfo, 
  listDrivers, 
  readGeoPDF, 
  renderGeoPDFToPng 
} from 'expo-gdal-pdfium';

// Get GDAL version information
const versionInfo = await getVersionInfo();

// List available GDAL drivers
const drivers = await listDrivers();

// Read GeoPDF metadata
const pdfInfo = await readGeoPDF('/path/to/file.pdf');

// Render GeoPDF to PNG with WGS84 coordinates
const result = await renderGeoPDFToPng(
  '/path/to/input.pdf',
  '/path/to/output.png'
);

// Access transformed coordinates (WGS84)
console.log('Top-left:', result.result?.topLeft); // { x: longitude, y: latitude }
console.log('Center:', result.result?.center);   // { x: longitude, y: latitude }
```

## API

### `getVersionInfo()`

Returns GDAL version information.

**Returns:** `Promise<VersionInfoResponse>`

### `listDrivers()`

Lists all available GDAL drivers.

**Returns:** `Promise<DriversListResponse>`

### `readGeoPDF(filePath: string)`

Reads GeoPDF file metadata and information.

**Parameters:**
- `filePath` (string): Path to the GeoPDF file

**Returns:** `Promise<ReadGeoPDFResponse>`

### `renderGeoPDFToPng(inputPath: string, outputPath: string)`

Renders a GeoPDF file to a PNG image and returns WGS84 coordinates.

**Parameters:**
- `inputPath` (string): Path to the input GeoPDF file
- `outputPath` (string): Path where the PNG will be saved

**Returns:** `Promise<RenderGeoPDFResponse>`

The response includes:
- `width`, `height`: Image dimensions in pixels
- `geoTransform`: GeoTransform matrix
- `topLeft`, `topRight`, `bottomLeft`, `bottomRight`: Corner coordinates in WGS84 (longitude, latitude)
- `center`: Center coordinates in WGS84 (longitude, latitude)

## Coordinate System

All coordinates returned by `renderGeoPDFToPng` are automatically transformed to **WGS84 (EPSG:4326)** format:
- **x**: Longitude in decimal degrees (-180 to 180)
- **y**: Latitude in decimal degrees (-90 to 90)

This makes the coordinates compatible with most mapping libraries and services.

## License

MIT

## Repository

https://github.com/syntaxdevs/expo-gdal-pdfium

