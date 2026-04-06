# Test PDFs Directory

Place your GeoPDF test files in this directory. Files placed here will be bundled with the app and automatically loaded.

## Current Test File

- `HCDA_FATIMA.pdf` - This file is automatically loaded when you open the Read GeoPDF screen

## How It Works

1. The PDF file is bundled with the app as an asset
2. When the Read GeoPDF screen loads, it automatically:
   - Loads the asset using Expo Asset API
   - Copies it to the app's document directory
   - Makes it available for GDAL to read

## File Location

The app will automatically use: `assets/test-pdfs/HCDA_FATIMA.pdf`

## Note

The app will read GeoPDF files using GDAL. Make sure your PDF files are GeoPDF format (georeferenced PDFs) for best results.

