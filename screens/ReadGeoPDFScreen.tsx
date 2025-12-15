import React, { useState, useEffect } from 'react';
import { StyleSheet, Text, View, Button, ScrollView, ActivityIndicator, TextInput, Alert, Image } from 'react-native';
import { readGeoPDF, ReadGeoPDFResponse, renderGeoPDFToPng, RenderGeoPDFResponse } from '../modules/expo-gdal-pdfium';
import * as FileSystem from 'expo-file-system';
import { Asset } from 'expo-asset';

export default function ReadGeoPDFScreen({ navigation }: any) {
  const [filePath, setFilePath] = useState('');
  const [pdfInfo, setPdfInfo] = useState<ReadGeoPDFResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [loadingAsset, setLoadingAsset] = useState(false);
  const [loadingRender, setLoadingRender] = useState(false);
  const [imagePath, setImagePath] = useState<string | null>(null);
  const [renderError, setRenderError] = useState<string | null>(null);
  const [renderMetadata, setRenderMetadata] = useState<RenderGeoPDFResponse | null>(null);
  const [error, setError] = useState<string | null>(null);

  // Load the local PDF asset on component mount
  useEffect(() => {
    const loadLocalPDF = async () => {
      try {
        setLoadingAsset(true);
        // Require the asset - this will be bundled with the app
        const asset = Asset.fromModule(require('../assets/test-pdfs/HCDA_FATIMA.pdf'));
        await asset.downloadAsync();
        
        if (asset.localUri) {
          // Copy the asset to a readable file system location for GDAL
          const targetPath = `${FileSystem.documentDirectory}HCDA_FATIMA.pdf`;
          
          // Check if file already exists
          const fileInfo = await FileSystem.getInfoAsync(targetPath);
          if (!fileInfo.exists) {
            // Copy from asset to document directory
            await FileSystem.copyAsync({
              from: asset.localUri,
              to: targetPath,
            });
            console.log('PDF copied to:', targetPath);
          }
          
          setFilePath(targetPath);
          console.log('Local PDF asset loaded and ready:', targetPath);
        } else {
          setError('Could not load local PDF asset - no localUri available');
        }
      } catch (err) {
        console.error('Error loading local PDF:', err);
        setError(`Error loading local PDF: ${err instanceof Error ? err.message : 'Unknown error'}`);
      } finally {
        setLoadingAsset(false);
      }
    };

    loadLocalPDF();
  }, []);

  const handleReadGeoPDF = async () => {
    if (!filePath.trim()) {
      Alert.alert('Error', 'Please enter a file path');
      return;
    }

    setLoading(true);
    setError(null);
    setPdfInfo(null);

    // Remove file:// prefix if present (GDAL needs plain file system path)
    let gdalPath = filePath;
    if (gdalPath.startsWith('file://')) {
      gdalPath = gdalPath.replace('file://', '');
    }

    console.log('=== GDAL readGeoPDF() called ===');
    console.log('Original path:', filePath);
    console.log('GDAL path:', gdalPath);
    
    try {
      const result = await readGeoPDF(gdalPath);
      console.log('=== GDAL GeoPDF Read Result ===');
      console.log('Full response:', JSON.stringify(result, null, 2));
      console.log('Status Code:', result.code);
      console.log('Message:', result.msg);
      console.log('Error:', result.error);
      
      if (result.result) {
        console.log('File Path:', result.result.filePath);
        console.log('Width:', result.result.width);
        console.log('Height:', result.result.height);
        console.log('Band Count:', result.result.bandCount);
        console.log('Driver:', result.result.driver);
        console.log('Projection:', result.result.projection);
        console.log('GeoTransform:', result.result.geoTransform);
        if (result.result.bands) {
          console.log('Number of bands:', result.result.bands.length);
          result.result.bands.forEach((band, index) => {
            console.log(`Band ${index + 1}:`, band);
          });
        }
        if (result.result.errorDetails) {
          console.log('Error Details:', result.result.errorDetails);
        }
      }
      console.log('================================');
      
      setPdfInfo(result);
    } catch (err) {
      const errorMessage = err instanceof Error ? err.message : 'Unknown error occurred';
      console.error('=== Error reading GeoPDF ===');
      console.error('Error:', err);
      console.error('Error message:', errorMessage);
      console.error('===================================');
      setError(errorMessage);
    } finally {
      setLoading(false);
    }
  };

  const handleRenderGeoPDF = async () => {
    if (!filePath.trim()) {
      Alert.alert('Error', 'No PDF file loaded');
      return;
    }

    setLoadingRender(true);
    setRenderError(null);
    setImagePath(null);

    // Remove file:// prefix if present (GDAL needs plain file system path)
    let gdalPath = filePath;
    if (gdalPath.startsWith('file://')) {
      gdalPath = gdalPath.replace('file://', '');
    }

    // Output PNG path
    const outputPath = `${FileSystem.documentDirectory}HCDA_FATIMA.png`;
    let gdalOutputPath = outputPath;
    if (gdalOutputPath.startsWith('file://')) {
      gdalOutputPath = gdalOutputPath.replace('file://', '');
    }

    console.log('=== GDAL renderGeoPDFToPng() called ===');
    console.log('Input path:', gdalPath);
    console.log('Output path:', gdalOutputPath);

    try {
      const result: RenderGeoPDFResponse = await renderGeoPDFToPng(gdalPath, gdalOutputPath);
      console.log('=== GDAL Render GeoPDF Result ===');
      console.log('Full response:', JSON.stringify(result, null, 2));
      console.log('Status Code:', result.code);
      console.log('Message:', result.msg);
      console.log('Error:', result.error);

      if (result.result) {
        console.log('Input Path:', result.result.inputPath);
        console.log('Output Path:', result.result.outputPath);
        if (result.result.errorDetails) {
          console.log('Error Details:', result.result.errorDetails);
        }
      }
      console.log('================================');

      if (result.error) {
        setRenderError(result.msg + (result.result?.errorDetails ? `: ${result.result.errorDetails}` : ''));
        setRenderMetadata(null);
      } else if (result.result?.outputPath) {
        // Set image path with file:// prefix for React Native Image component
        const imageUri = result.result.outputPath.startsWith('file://') 
          ? result.result.outputPath 
          : `file://${result.result.outputPath}`;
        setImagePath(imageUri);
        setRenderMetadata(result);
        console.log('Image path set to:', imageUri);
        console.log('Render metadata:', JSON.stringify(result.result, null, 2));
      }
    } catch (err) {
      const errorMessage = err instanceof Error ? err.message : 'Unknown error occurred';
      console.error('=== Error rendering GeoPDF ===');
      console.error('Error:', err);
      console.error('Error message:', errorMessage);
      console.error('===================================');
      setRenderError(errorMessage);
    } finally {
      setLoadingRender(false);
    }
  };

  return (
    <View style={styles.container}>
      <ScrollView contentContainerStyle={styles.scrollContent}>
        <Text style={styles.title}>Read GeoPDF</Text>
        <Text style={styles.subtitle}>Read and display GeoPDF file information</Text>

        <View style={styles.inputContainer}>
          <Text style={styles.label}>File Path:</Text>
          <TextInput
            style={styles.input}
            value={filePath}
            onChangeText={setFilePath}
            placeholder="Loading local PDF..."
            placeholderTextColor="#999"
            editable={!loadingAsset}
          />
          <Text style={styles.hint}>
            Using local PDF: assets/test-pdfs/HCDA_FATIMA.pdf
            {loadingAsset && ' (Loading...)'}
          </Text>
        </View>

        <View style={styles.buttonContainer}>
          <Button
            title={loading ? "Reading..." : "Read GeoPDF"}
            onPress={handleReadGeoPDF}
            disabled={loading || loadingAsset || !filePath}
          />
        </View>

        <View style={styles.buttonContainer}>
          <Button
            title={loadingRender ? "Rendering..." : "Render & Show GeoPDF Image"}
            onPress={handleRenderGeoPDF}
            disabled={loadingRender || loadingAsset || !filePath}
            color="#4CAF50"
          />
        </View>

        <View style={styles.buttonContainer}>
          <Button
            title="Back to Main"
            onPress={() => navigation.goBack()}
            color="#666"
          />
        </View>

        {loadingAsset && (
          <View style={styles.loadingContainer}>
            <ActivityIndicator size="large" color="#007AFF" />
            <Text style={styles.loadingText}>Loading local PDF asset...</Text>
          </View>
        )}

        {loading && (
          <View style={styles.loadingContainer}>
            <ActivityIndicator size="large" color="#007AFF" />
            <Text style={styles.loadingText}>Reading GeoPDF file...</Text>
          </View>
        )}

        {loadingRender && (
          <View style={styles.loadingContainer}>
            <ActivityIndicator size="large" color="#4CAF50" />
            <Text style={styles.loadingText}>Rendering GeoPDF to PNG...</Text>
          </View>
        )}

        {error && (
          <View style={styles.errorContainer}>
            <Text style={styles.errorTitle}>Error:</Text>
            <Text style={styles.errorText}>{error}</Text>
          </View>
        )}

        {renderError && (
          <View style={styles.errorContainer}>
            <Text style={styles.errorTitle}>Render Error:</Text>
            <Text style={styles.errorText}>{renderError}</Text>
          </View>
        )}

        {imagePath && (
          <View style={styles.imageContainer}>
            <Text style={styles.imageTitle}>GeoPDF Rendered Image:</Text>
            <Image
              source={{ uri: imagePath }}
              style={styles.renderedImage}
              resizeMode="contain"
            />
            
            {renderMetadata?.result && (
              <View style={styles.metadataContainer}>
                <Text style={styles.metadataTitle}>Image Coordinates:</Text>
                
                {renderMetadata.result.topLeft && (
                  <View style={styles.coordinateRow}>
                    <Text style={styles.coordinateLabel}>Top-Left:</Text>
                    <Text style={styles.coordinateValue}>
                      ({renderMetadata.result.topLeft.x}, {renderMetadata.result.topLeft.y})
                    </Text>
                  </View>
                )}
                
                {renderMetadata.result.topRight && (
                  <View style={styles.coordinateRow}>
                    <Text style={styles.coordinateLabel}>Top-Right:</Text>
                    <Text style={styles.coordinateValue}>
                      ({renderMetadata.result.topRight.x}, {renderMetadata.result.topRight.y})
                    </Text>
                  </View>
                )}
                
                {renderMetadata.result.bottomLeft && (
                  <View style={styles.coordinateRow}>
                    <Text style={styles.coordinateLabel}>Bottom-Left:</Text>
                    <Text style={styles.coordinateValue}>
                      ({renderMetadata.result.bottomLeft.x}, {renderMetadata.result.bottomLeft.y})
                    </Text>
                  </View>
                )}
                
                {renderMetadata.result.bottomRight && (
                  <View style={styles.coordinateRow}>
                    <Text style={styles.coordinateLabel}>Bottom-Right:</Text>
                    <Text style={styles.coordinateValue}>
                      ({renderMetadata.result.bottomRight.x}, {renderMetadata.result.bottomRight.y})
                    </Text>
                  </View>
                )}
                
                {renderMetadata.result.center && (
                  <View style={styles.coordinateRow}>
                    <Text style={styles.coordinateLabel}>Center:</Text>
                    <Text style={styles.coordinateValue}>
                      ({renderMetadata.result.center.x}, {renderMetadata.result.center.y})
                    </Text>
                  </View>
                )}
                
                {renderMetadata.result.width && renderMetadata.result.height && (
                  <View style={styles.coordinateRow}>
                    <Text style={styles.coordinateLabel}>Dimensions:</Text>
                    <Text style={styles.coordinateValue}>
                      {renderMetadata.result.width} x {renderMetadata.result.height} pixels
                    </Text>
                  </View>
                )}
              </View>
            )}
          </View>
        )}

        {pdfInfo && (
          <View style={styles.resultContainer}>
            <Text style={styles.resultTitle}>GeoPDF Information:</Text>
            <View style={styles.resultBox}>
              <Text style={styles.resultLabel}>Status:</Text>
              <Text style={[styles.resultValue, pdfInfo.error && styles.errorText]}>
                {pdfInfo.code} {pdfInfo.error ? '❌' : '✅'}
              </Text>

              <Text style={styles.resultLabel}>Message:</Text>
              <Text style={styles.resultValue}>{pdfInfo.msg}</Text>

              {pdfInfo.result && (
                <>
                  <Text style={styles.resultLabel}>File Path:</Text>
                  <Text style={styles.resultValue}>{pdfInfo.result.filePath || 'N/A'}</Text>

                  <Text style={styles.resultLabel}>Dimensions:</Text>
                  <Text style={styles.resultValue}>
                    {pdfInfo.result.width || 'N/A'} x {pdfInfo.result.height || 'N/A'}
                  </Text>

                  <Text style={styles.resultLabel}>Band Count:</Text>
                  <Text style={styles.resultValue}>{pdfInfo.result.bandCount || 'N/A'}</Text>

                  <Text style={styles.resultLabel}>Driver:</Text>
                  <Text style={styles.resultValue}>{pdfInfo.result.driver || 'N/A'}</Text>

                  {pdfInfo.result.projection && (
                    <>
                      <Text style={styles.resultLabel}>Projection:</Text>
                      <Text style={styles.resultValueSmall}>{pdfInfo.result.projection}</Text>
                    </>
                  )}

                  {pdfInfo.result.geoTransform && pdfInfo.result.geoTransform.length > 0 && (
                    <>
                      <Text style={styles.resultLabel}>GeoTransform:</Text>
                      <Text style={styles.resultValueSmall}>
                        {pdfInfo.result.geoTransform.join(', ')}
                      </Text>
                    </>
                  )}

                  {pdfInfo.result.bands && pdfInfo.result.bands.length > 0 && (
                    <>
                      <Text style={styles.resultLabel}>Bands ({pdfInfo.result.bands.length}):</Text>
                      <ScrollView style={styles.bandsScrollView} nestedScrollEnabled>
                        {pdfInfo.result.bands.map((band, index) => (
                          <View key={index} style={styles.bandItem}>
                            <Text style={styles.bandNumber}>Band {band.bandNumber}</Text>
                            <Text style={styles.bandDetails}>
                              DataType: {band.dataType} | Block: {band.blockWidth}x{band.blockHeight}
                            </Text>
                          </View>
                        ))}
                      </ScrollView>
                    </>
                  )}

                  {pdfInfo.result.errorDetails && (
                    <>
                      <Text style={styles.resultLabel}>Error Details:</Text>
                      <Text style={styles.errorText}>{pdfInfo.result.errorDetails}</Text>
                    </>
                  )}
                </>
              )}
            </View>

            <View style={styles.rawContainer}>
              <Text style={styles.rawTitle}>Raw Response:</Text>
              <Text style={styles.rawText}>{JSON.stringify(pdfInfo, null, 2)}</Text>
            </View>
          </View>
        )}
      </ScrollView>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#f5f5f5',
  },
  scrollContent: {
    padding: 20,
    paddingTop: 60,
  },
  title: {
    fontSize: 24,
    fontWeight: 'bold',
    marginBottom: 8,
    textAlign: 'center',
    color: '#333',
  },
  subtitle: {
    fontSize: 16,
    color: '#666',
    marginBottom: 30,
    textAlign: 'center',
  },
  inputContainer: {
    marginBottom: 20,
  },
  label: {
    fontSize: 14,
    fontWeight: '600',
    color: '#333',
    marginBottom: 8,
  },
  input: {
    backgroundColor: '#fff',
    borderWidth: 1,
    borderColor: '#e0e0e0',
    borderRadius: 8,
    padding: 12,
    fontSize: 14,
    color: '#333',
    marginBottom: 8,
  },
  hint: {
    fontSize: 12,
    color: '#999',
    fontStyle: 'italic',
  },
  buttonContainer: {
    marginBottom: 15,
  },
  loadingContainer: {
    alignItems: 'center',
    marginVertical: 20,
  },
  loadingText: {
    marginTop: 10,
    color: '#666',
  },
  errorContainer: {
    backgroundColor: '#ffebee',
    padding: 15,
    borderRadius: 8,
    marginBottom: 20,
    borderWidth: 1,
    borderColor: '#f44336',
  },
  errorTitle: {
    fontSize: 16,
    fontWeight: 'bold',
    color: '#d32f2f',
    marginBottom: 8,
  },
  errorText: {
    color: '#d32f2f',
    fontSize: 14,
  },
  resultContainer: {
    marginTop: 20,
  },
  resultTitle: {
    fontSize: 18,
    fontWeight: 'bold',
    marginBottom: 12,
    color: '#333',
  },
  resultBox: {
    backgroundColor: '#fff',
    padding: 15,
    borderRadius: 8,
    marginBottom: 15,
    borderWidth: 1,
    borderColor: '#e0e0e0',
  },
  resultLabel: {
    fontSize: 14,
    fontWeight: '600',
    color: '#666',
    marginTop: 10,
    marginBottom: 4,
  },
  resultValue: {
    fontSize: 14,
    color: '#333',
    marginBottom: 8,
  },
  resultValueSmall: {
    fontSize: 12,
    color: '#666',
    marginBottom: 8,
    fontFamily: 'monospace',
  },
  bandsScrollView: {
    maxHeight: 200,
    marginTop: 8,
  },
  bandItem: {
    padding: 8,
    marginBottom: 8,
    backgroundColor: '#f0f0f0',
    borderRadius: 4,
    borderLeftWidth: 3,
    borderLeftColor: '#007AFF',
  },
  bandNumber: {
    fontSize: 14,
    fontWeight: 'bold',
    color: '#007AFF',
    marginBottom: 4,
  },
  bandDetails: {
    fontSize: 12,
    color: '#666',
  },
  rawContainer: {
    backgroundColor: '#f9f9f9',
    padding: 15,
    borderRadius: 8,
    borderWidth: 1,
    borderColor: '#e0e0e0',
  },
  rawTitle: {
    fontSize: 14,
    fontWeight: '600',
    color: '#666',
    marginBottom: 8,
  },
  rawText: {
    fontSize: 12,
    color: '#666',
    fontFamily: 'monospace',
  },
  imageContainer: {
    marginTop: 20,
    marginBottom: 20,
  },
  imageTitle: {
    fontSize: 16,
    fontWeight: '600',
    color: '#333',
    marginBottom: 12,
  },
  renderedImage: {
    width: '100%',
    height: 400,
    backgroundColor: '#f0f0f0',
    borderRadius: 8,
    borderWidth: 1,
    borderColor: '#e0e0e0',
  },
  metadataContainer: {
    marginTop: 20,
    backgroundColor: '#fff',
    padding: 15,
    borderRadius: 8,
    borderWidth: 1,
    borderColor: '#e0e0e0',
  },
  metadataTitle: {
    fontSize: 16,
    fontWeight: '600',
    color: '#333',
    marginBottom: 12,
  },
  coordinateRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 8,
    paddingVertical: 4,
  },
  coordinateLabel: {
    fontSize: 14,
    fontWeight: '600',
    color: '#666',
    flex: 1,
  },
  coordinateValue: {
    fontSize: 14,
    color: '#333',
    fontFamily: 'monospace',
    flex: 2,
    textAlign: 'right',
  },
});

