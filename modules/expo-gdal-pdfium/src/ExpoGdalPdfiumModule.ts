import { NativeModule, requireNativeModule } from 'expo';

import { ExpoGdalPdfiumModuleEvents, VersionInfoResponse, DriversListResponse, ReadGeoPDFResponse, RenderGeoPDFResponse, ProcessGeoPDFResponse, } from './ExpoGdalPdfium.types';

declare class ExpoGdalPdfiumModule extends NativeModule<ExpoGdalPdfiumModuleEvents> {
  PI: number;
  hello(): string;
  setValueAsync(value: string): Promise<void>;
  getVersionInfo(): Promise<VersionInfoResponse>;
  listDrivers(): Promise<DriversListResponse>;
  readGeoPDF(filePath: string): Promise<ReadGeoPDFResponse>;
  renderGeoPDFToPng(inputPath: string, outputPath: string): Promise<RenderGeoPDFResponse>;
  processGeoPDF(inputPath: string, outputPath: string): Promise<ProcessGeoPDFResponse>;
}

// This call loads the native module object from the JSI.
export default requireNativeModule<ExpoGdalPdfiumModule>('ExpoGdalPdfium');
