import * as React from 'react';

import { ExpoGdalViewProps } from './ExpoGdal.types';

export default function ExpoGdalView(props: ExpoGdalViewProps) {
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
