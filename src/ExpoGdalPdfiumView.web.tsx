import * as React from 'react';

import { ExpoGdalPdfiumViewProps } from './ExpoGdalPdfium.types';

export default function ExpoGdalPdfiumView(props: ExpoGdalPdfiumViewProps) {
  return (
    <div>
      <iframe
        style={{ flex: 1 }}
        src={props.url}
        onLoad={() => props.onLoad({ nativeEvent: { url: props.url } })}
      />
    </div>
  );
}
