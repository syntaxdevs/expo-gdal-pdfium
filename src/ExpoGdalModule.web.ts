import { registerWebModule, NativeModule } from 'expo';

import { ChangeEventPayload } from './ExpoGdal.types';

type ExpoGdalModuleEvents = {
  onChange: (params: ChangeEventPayload) => void;
}

class ExpoGdalModule extends NativeModule<ExpoGdalModuleEvents> {
  PI = Math.PI;
  async setValueAsync(value: string): Promise<void> {
    this.emit('onChange', { value });
  }
  hello() {
    return 'Hello world! 👋';
  }
};

export default registerWebModule(ExpoGdalModule, 'ExpoGdalModule');
