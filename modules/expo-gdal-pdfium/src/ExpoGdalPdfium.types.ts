import type { StyleProp, ViewStyle } from 'react-native';

export type OnLoadEventPayload = {
  url: string;
};

export type ExpoGdalPdfiumModuleEvents = {
  onChange: (params: ChangeEventPayload) => void;
};

export type ChangeEventPayload = {
  value: string;
};

export type VersionInfoResponse = {
  msg: string;
  code: string;
  error: boolean;
  result: {
    version?: string;
    versionNum?: string;
    releaseDate?: string;
    errorDetails?: string;
    errorType?: string;
    [key: string]: any;
  } | null;
};

export type DriversListResponse = {
  msg: string;
  code: string;
  error: boolean;
  result: {
    driverCount?: string;
    drivers?: Array<{
      shortName: string;
      longName: string;
    }>;
    errorDetails?: string;
    errorType?: string;
    [key: string]: any;
  } | null;
};

export type ReadGeoPDFResponse = {
  msg: string;
  code: string;
  error: boolean;
  result: {
    filePath?: string;
    width?: string;
    height?: string;
    bandCount?: string;
    driver?: string;
    projection?: string;
    geoTransform?: string[];
    bands?: Array<{
      bandNumber: string;
      dataType: string;
      blockWidth: string;
      blockHeight: string;
    }>;
    errorDetails?: string;
    errorType?: string;
    [key: string]: any;
  } | null;
};

export type ExpoGdalPdfiumViewProps = {
  url: string;
  onLoad: (event: { nativeEvent: OnLoadEventPayload }) => void;
  style?: StyleProp<ViewStyle>;
};
