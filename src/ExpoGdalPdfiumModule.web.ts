import { registerWebModule, NativeModule } from 'expo';

import { ChangeEventPayload } from './ExpoGdalPdfium.types';

type ExpoGdalPdfiumModuleEvents = {
  onChange: (params: ChangeEventPayload) => void;
}

class ExpoGdalPdfiumModule extends NativeModule<ExpoGdalPdfiumModuleEvents> {
  PI = Math.PI;
  async setValueAsync(value: string): Promise<void> {
    this.emit('onChange', { value });
  }
  hello() {
    return 'Hello world! 👋';
  }
};

export default registerWebModule(ExpoGdalPdfiumModule, 'ExpoGdalPdfiumModule');
