import { NativeModule, requireNativeModule } from 'expo';

import { ExpoGdalPdfiumModuleEvents, VersionInfoResponse, DriversListResponse } from './ExpoGdalPdfium.types';

declare class ExpoGdalPdfiumModule extends NativeModule<ExpoGdalPdfiumModuleEvents> {
  PI: number;
  hello(): string;
  setValueAsync(value: string): Promise<void>;
  getVersionInfo(): Promise<VersionInfoResponse>;
  listDrivers(): Promise<DriversListResponse>;
}

// This call loads the native module object from the JSI.
export default requireNativeModule<ExpoGdalPdfiumModule>('ExpoGdalPdfium');
