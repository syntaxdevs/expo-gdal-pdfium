// Reexport the native module. On web, it will be resolved to ExpoGdalModule.web.ts
// and on native platforms to ExpoGdalModule.ts
import ExpoGdalModule from './src/ExpoGdalModule';
import { VersionInfoResponse, DriversListResponse, ReadGeoPDFResponse, RenderGeoPDFResponse, ExtractRawMetadataResponse, ProcessGeoPDFResponse } from './src/ExpoGdal.types';

export { default } from './src/ExpoGdalModule';
export { default as ExpoGdalView } from './src/ExpoGdalView';
export * from './src/ExpoGdal.types';

// Convenient wrapper function for getting GDAL version info
export async function getVersionInfo(): Promise<VersionInfoResponse> {
  return await ExpoGdalModule.getVersionInfo();
}

// Convenient wrapper function for listing GDAL drivers
export async function listDrivers(): Promise<DriversListResponse> {
  return await ExpoGdalModule.listDrivers();
}

// Convenient wrapper function for reading GeoPDF
export async function readGeoPDF(filePath: string): Promise<ReadGeoPDFResponse> {
  return await ExpoGdalModule.readGeoPDF(filePath);
}

// Convenient wrapper function for rendering GeoPDF to PNG
export async function renderGeoPDFToPng(inputPath: string, outputPath: string): Promise<RenderGeoPDFResponse> {
  return await ExpoGdalModule.renderGeoPDFToPng(inputPath, outputPath);
}

// Convenient wrapper function for extracting raw metadata from PDF
export async function extractRawMetadata(filePath: string): Promise<ExtractRawMetadataResponse> {
  return await ExpoGdalModule.extractRawMetadata(filePath);
}

export async function processGeoPDF(
  inputPath: string,
  outputPath: string
): Promise<ProcessGeoPDFResponse> {
  return await ExpoGdalModule.processGeoPDF(inputPath, outputPath);
}