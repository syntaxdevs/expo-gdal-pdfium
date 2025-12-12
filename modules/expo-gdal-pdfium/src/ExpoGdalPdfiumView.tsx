import { requireNativeView } from 'expo';
import * as React from 'react';

import { ExpoGdalPdfiumViewProps } from './ExpoGdalPdfium.types';

const NativeView: React.ComponentType<ExpoGdalPdfiumViewProps> =
  requireNativeView('ExpoGdalPdfium');

export default function ExpoGdalPdfiumView(props: ExpoGdalPdfiumViewProps) {
  return <NativeView {...props} />;
}
