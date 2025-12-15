# Step-by-Step Guide: Adding New GDAL Functions

This guide explains how to add new GDAL functions to your Expo module, following the established patterns.

## Overview

When adding a new GDAL function, you need to update **4 files**:
1. **Kotlin Module** (`android/src/main/java/expo/modules/gdalpdfium/ExpoGdalPdfiumModule.kt`)
2. **TypeScript Types** (`src/ExpoGdalPdfium.types.ts`)
3. **TypeScript Module Interface** (`src/ExpoGdalPdfiumModule.ts`)
4. **Index Exports** (`index.ts`)

---

## Step 1: Identify the GDAL Function

### 1.1 Check GDAL Java Bindings API
- Determine the package and class (usually `org.gdal.gdal.gdal`)
- Find the method name (e.g., `VersionInfo`, `Open`, `GetDriver`)
- Check what parameters it needs
- Check what it returns

### 1.2 Check if You Need Additional Imports
- Most functions use `org.gdal.gdal.gdal` (already imported)
- If you use specific classes, add imports:
  - `import org.gdal.gdal.Driver` - for Driver objects
  - `import org.gdal.gdal.Dataset` - for Dataset objects
  - `import org.gdal.gdal.Band` - for Band objects
  - etc.

---

## Step 2: Add Function to Kotlin Module

**File:** `modules/expo-gdal-pdfium/android/src/main/java/expo/modules/gdalpdfium/ExpoGdalPdfiumModule.kt`

### 2.1 Add Import (if needed)
```kotlin
// At the top of the file, add any new GDAL classes you'll use
import org.gdal.gdal.Dataset  // Example: if you need Dataset
```

### 2.2 Add the AsyncFunction
Inside the `ModuleDefinition` block, add your function:

```kotlin
// Example: Adding a function to open a dataset
AsyncFunction("openDataset") { 
    filePath: String, 
    promise: Promise 
    ->
    try {
        Log.i("ExpoGdalPdfium", "Opening dataset: $filePath")
        
        // Call GDAL function
        val dataset = gdal.Open(filePath)
        
        if (dataset == null) {
            promise.resolve(
                createResponseMap(
                    "Failed to open dataset",
                    "ERROR",
                    true,
                    mapOf("errorDetails" to "Dataset is null")
                )
            )
            return@AsyncFunction
        }
        
        // Extract information
        val width = dataset.GetRasterXSize()
        val height = dataset.GetRasterYSize()
        val bandCount = dataset.GetRasterCount()
        
        promise.resolve(
            createResponseMap(
                "Dataset opened successfully",
                "SUCCESS",
                false,
                mapOf(
                    "width" to width.toString(),
                    "height" to height.toString(),
                    "bandCount" to bandCount.toString()
                )
            )
        )
    } catch (e: ClassNotFoundException) {
        Log.e("ExpoGdalPdfium", "GDAL classes not found", e)
        promise.resolve(
            createResponseMap(
                "GDAL classes not found",
                "CLASS_NOT_FOUND",
                true,
                mapOf("errorDetails" to (e.message ?: "Unknown error"))
            )
        )
    } catch (e: UnsatisfiedLinkError) {
        Log.e("ExpoGdalPdfium", "GDAL native library not loaded", e)
        promise.resolve(
            createResponseMap(
                "GDAL native library not loaded",
                "NATIVE_LIBRARY_ERROR",
                true,
                mapOf("errorDetails" to (e.message ?: "Unknown error"))
            )
        )
    } catch (e: Exception) {
        Log.e("ExpoGdalPdfium", "Error opening dataset", e)
        promise.resolve(
            createResponseMap(
                "Error opening dataset: ${e.message}",
                "ERROR",
                true,
                mapOf(
                    "errorDetails" to (e.message ?: "Unknown error"),
                    "errorType" to e.javaClass.simpleName
                )
            )
        )
    }
}
```

### 2.3 Function Template Pattern

```kotlin
AsyncFunction("yourFunctionName") { 
    param1: Type1,
    param2: Type2,
    promise: Promise 
    ->
    try {
        Log.i("ExpoGdalPdfium", "Your function description")
        
        // 1. Call GDAL function(s)
        val result = gdal.YourGDALMethod(param1, param2)
        
        // 2. Process/format the result
        val formattedResult = mapOf(
            "key1" to value1,
            "key2" to value2
        )
        
        // 3. Return success response
        promise.resolve(
            createResponseMap(
                "Success message",
                "SUCCESS",
                false,
                formattedResult
            )
        )
    } catch (e: ClassNotFoundException) {
        // Handle missing classes
        promise.resolve(
            createResponseMap(
                "GDAL classes not found",
                "CLASS_NOT_FOUND",
                true,
                mapOf("errorDetails" to (e.message ?: "Unknown error"))
            )
        )
    } catch (e: UnsatisfiedLinkError) {
        // Handle missing native library
        promise.resolve(
            createResponseMap(
                "GDAL native library not loaded",
                "NATIVE_LIBRARY_ERROR",
                true,
                mapOf("errorDetails" to (e.message ?: "Unknown error"))
            )
        )
    } catch (e: Exception) {
        // Handle general errors
        promise.resolve(
            createResponseMap(
                "Error message: ${e.message}",
                "ERROR",
                true,
                mapOf(
                    "errorDetails" to (e.message ?: "Unknown error"),
                    "errorType" to e.javaClass.simpleName
                )
            )
        )
    }
}
```

---

## Step 3: Add TypeScript Type

**File:** `modules/expo-gdal-pdfium/src/ExpoGdalPdfium.types.ts`

### 3.1 Create Response Type
Add a new type for your function's response:

```typescript
export type OpenDatasetResponse = {
  msg: string;
  code: string;
  error: boolean;
  result: {
    width?: string;
    height?: string;
    bandCount?: string;
    errorDetails?: string;
    errorType?: string;
    [key: string]: any;
  } | null;
};
```

### 3.2 Type Template Pattern

```typescript
export type YourFunctionResponse = {
  msg: string;
  code: string;
  error: boolean;
  result: {
    // Add your specific result fields here
    field1?: string;
    field2?: number;
    field3?: boolean;
    // Always include error fields
    errorDetails?: string;
    errorType?: string;
    // Allow additional fields
    [key: string]: any;
  } | null;
};
```

---

## Step 4: Update TypeScript Module Interface

**File:** `modules/expo-gdal-pdfium/src/ExpoGdalPdfiumModule.ts`

### 4.1 Import the New Type
```typescript
import { 
  ExpoGdalPdfiumModuleEvents, 
  VersionInfoResponse, 
  DriversListResponse,
  OpenDatasetResponse  // Add your new type
} from './ExpoGdalPdfium.types';
```

### 4.2 Add Method to Interface
```typescript
declare class ExpoGdalPdfiumModule extends NativeModule<ExpoGdalPdfiumModuleEvents> {
  PI: number;
  hello(): string;
  setValueAsync(value: string): Promise<void>;
  getVersionInfo(): Promise<VersionInfoResponse>;
  listDrivers(): Promise<DriversListResponse>;
  openDataset(filePath: string): Promise<OpenDatasetResponse>;  // Add your new method
}
```

---

## Step 5: Export Wrapper Function

**File:** `modules/expo-gdal-pdfium/index.ts`

### 5.1 Import the Type
```typescript
import { 
  VersionInfoResponse, 
  DriversListResponse,
  OpenDatasetResponse  // Add your new type
} from './src/ExpoGdalPdfium.types';
```

### 5.2 Add Wrapper Function
```typescript
// Convenient wrapper function for opening a dataset
export async function openDataset(filePath: string): Promise<OpenDatasetResponse> {
  return await ExpoGdalPdfiumModule.openDataset(filePath);
}
```

---

## Step 6: Test Your Function

### 6.1 In Your App (App.tsx)
```typescript
import { openDataset } from './modules/expo-gdal-pdfium';

const handleOpenDataset = async () => {
  try {
    const result = await openDataset('/path/to/file.tif');
    console.log('Result:', result);
  } catch (err) {
    console.error('Error:', err);
  }
};
```

### 6.2 Console Logging Pattern
```typescript
console.log('=== GDAL openDataset() called ===');
console.log('Full response:', JSON.stringify(result, null, 2));
console.log('Status Code:', result.code);
console.log('Message:', result.msg);
// Log specific fields from result.result
```

---

## Complete Example: Adding `getDriverInfo` Function

### Step 1: Kotlin Module
```kotlin
// Add import if needed (Driver is already imported)
// import org.gdal.gdal.Driver

AsyncFunction("getDriverInfo") { 
    driverName: String,
    promise: Promise 
    ->
    try {
        Log.i("ExpoGdalPdfium", "Getting driver info: $driverName")
        
        val driver = gdal.GetDriverByName(driverName)
        
        if (driver == null) {
            promise.resolve(
                createResponseMap(
                    "Driver not found",
                    "DRIVER_NOT_FOUND",
                    true,
                    null
                )
            )
            return@AsyncFunction
        }
        
        promise.resolve(
            createResponseMap(
                "Driver info retrieved successfully",
                "SUCCESS",
                false,
                mapOf(
                    "shortName" to (driver.getShortName() ?: "Unknown"),
                    "longName" to (driver.getLongName() ?: "Unknown")
                )
            )
        )
    } catch (e: Exception) {
        Log.e("ExpoGdalPdfium", "Error getting driver info", e)
        promise.resolve(
            createResponseMap(
                "Error: ${e.message}",
                "ERROR",
                true,
                mapOf("errorDetails" to (e.message ?: "Unknown error"))
            )
        )
    }
}
```

### Step 2: TypeScript Type
```typescript
export type DriverInfoResponse = {
  msg: string;
  code: string;
  error: boolean;
  result: {
    shortName?: string;
    longName?: string;
    errorDetails?: string;
    [key: string]: any;
  } | null;
};
```

### Step 3: Module Interface
```typescript
import { DriverInfoResponse } from './ExpoGdalPdfium.types';

declare class ExpoGdalPdfiumModule extends NativeModule<ExpoGdalPdfiumModuleEvents> {
  // ... existing methods
  getDriverInfo(driverName: string): Promise<DriverInfoResponse>;
}
```

### Step 4: Export
```typescript
import { DriverInfoResponse } from './src/ExpoGdalPdfium.types';

export async function getDriverInfo(driverName: string): Promise<DriverInfoResponse> {
  return await ExpoGdalPdfiumModule.getDriverInfo(driverName);
}
```

---

## Checklist for Each New Function

- [ ] **Kotlin**: Added import for any new GDAL classes
- [ ] **Kotlin**: Added AsyncFunction with proper error handling
- [ ] **Kotlin**: Used `createResponseMap()` helper
- [ ] **Kotlin**: Added logging with `Log.i()` or `Log.e()`
- [ ] **Types**: Created response type in `ExpoGdalPdfium.types.ts`
- [ ] **Module**: Added method to interface in `ExpoGdalPdfiumModule.ts`
- [ ] **Module**: Imported new type
- [ ] **Index**: Added wrapper function in `index.ts`
- [ ] **Index**: Imported new type
- [ ] **Test**: Tested function and verified console output

---

## Common Patterns

### Pattern 1: Simple Function (No Parameters)
```kotlin
AsyncFunction("simpleFunction") { promise: Promise ->
    // Implementation
}
```

### Pattern 2: Function with Parameters
```kotlin
AsyncFunction("functionWithParams") { 
    param1: String,
    param2: Int,
    promise: Promise 
    ->
    // Implementation
}
```

### Pattern 3: Function Returning List
```kotlin
val itemsList = mutableListOf<Map<String, Any>>()
// ... populate list
mapOf("items" to itemsList)
```

### Pattern 4: Function with Null Checks
```kotlin
val result = gdal.SomeMethod()
if (result == null) {
    promise.resolve(
        createResponseMap("Result is null", "NULL_RESULT", true, null)
    )
    return@AsyncFunction
}
```

---

## Tips

1. **Always use `createResponseMap()`** for consistent response format
2. **Always handle the three exception types**: `ClassNotFoundException`, `UnsatisfiedLinkError`, `Exception`
3. **Add logging** for debugging: `Log.i()` for info, `Log.e()` for errors
4. **Check GDAL documentation** for correct method names and parameters
5. **Test with console.log** before building UI
6. **Use nullable types** (`?`) when GDAL methods can return null
7. **Convert numbers to strings** in response maps (TypeScript compatibility)

---

## Quick Reference: File Locations

```
modules/expo-gdal-pdfium/
├── android/src/main/java/expo/modules/gdalpdfium/
│   └── ExpoGdalPdfiumModule.kt          ← Step 2: Add AsyncFunction
├── src/
│   ├── ExpoGdalPdfium.types.ts          ← Step 3: Add response type
│   └── ExpoGdalPdfiumModule.ts          ← Step 4: Add to interface
└── index.ts                              ← Step 5: Add wrapper function
```

---

## Need Help?

- Check existing functions (`getVersionInfo`, `listDrivers`) as examples
- Refer to GDAL Java bindings documentation
- Check the example project: `/home/jose/Downloads/expo-msignia-3ds-main`



