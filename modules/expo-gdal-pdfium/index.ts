// Reexport the native module. On web, it will be resolved to ExpoGdalPdfiumModule.web.ts
// and on native platforms to ExpoGdalPdfiumModule.ts
import ExpoGdalPdfiumModule from './src/ExpoGdalPdfiumModule';
import { VersionInfoResponse, DriversListResponse, ReadGeoPDFResponse } from './src/ExpoGdalPdfium.types';

export { default } from './src/ExpoGdalPdfiumModule';
export { default as ExpoGdalPdfiumView } from './src/ExpoGdalPdfiumView';
export * from './src/ExpoGdalPdfium.types';

// Convenient wrapper function for getting GDAL version info
export async function getVersionInfo(): Promise<VersionInfoResponse> {
  return await ExpoGdalPdfiumModule.getVersionInfo();
}

// Convenient wrapper function for listing GDAL drivers
export async function listDrivers(): Promise<DriversListResponse> {
  return await ExpoGdalPdfiumModule.listDrivers();
}

// Convenient wrapper function for reading GeoPDF
export async function readGeoPDF(filePath: string): Promise<ReadGeoPDFResponse> {
  return await ExpoGdalPdfiumModule.readGeoPDF(filePath);
}
