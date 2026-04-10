import { requireNativeView } from 'expo';
import * as React from 'react';

import { ExpoGdalViewProps } from './ExpoGdal.types';

const NativeView: React.ComponentType<ExpoGdalViewProps> =
  requireNativeView('ExpoGdal');

export default function ExpoGdalView(props: ExpoGdalViewProps) {
  return <NativeView {...props} />;
}
