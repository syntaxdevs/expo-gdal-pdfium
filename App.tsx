import { StatusBar } from 'expo-status-bar';
import { StyleSheet, Text, View, Button, ScrollView, ActivityIndicator } from 'react-native';
import { useState } from 'react';
import { getVersionInfo, listDrivers, VersionInfoResponse, DriversListResponse } from './modules/expo-gdal-pdfium';
import ReadGeoPDFScreen from './screens/ReadGeoPDFScreen';

export default function App() {
  const [currentScreen, setCurrentScreen] = useState<'main' | 'readPdf'>('main');
  const [versionInfo, setVersionInfo] = useState<VersionInfoResponse | null>(null);
  const [driversList, setDriversList] = useState<DriversListResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [loadingDrivers, setLoadingDrivers] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [driversError, setDriversError] = useState<string | null>(null);

  const handleGetVersionInfo = async () => {
    setLoading(true);
    setError(null);
    setVersionInfo(null);

    console.log('=== GDAL getVersionInfo() called ===');
    
    try {
      const result = await getVersionInfo();
      console.log('=== GDAL Version Info Result ===');
      console.log('Full response:', JSON.stringify(result, null, 2));
      console.log('Status Code:', result.code);
      console.log('Message:', result.msg);
      console.log('Error:', result.error);
      
      if (result.result) {
        console.log('Version:', result.result.version);
        console.log('Version Number:', result.result.versionNum);
        console.log('Release Date:', result.result.releaseDate);
        if (result.result.errorDetails) {
          console.log('Error Details:', result.result.errorDetails);
        }
      }
      console.log('================================');
      
      setVersionInfo(result);
    } catch (err) {
      const errorMessage = err instanceof Error ? err.message : 'Unknown error occurred';
      console.error('=== Error getting GDAL version info ===');
      console.error('Error:', err);
      console.error('Error message:', errorMessage);
      console.error('========================================');
      setError(errorMessage);
    } finally {
      setLoading(false);
    }
  };

  const handleListDrivers = async () => {
    setLoadingDrivers(true);
    setDriversError(null);
    setDriversList(null);

    console.log('=== GDAL listDrivers() called ===');
    
    try {
      const result = await listDrivers();
      console.log('=== GDAL Drivers List Result ===');
      console.log('Full response:', JSON.stringify(result, null, 2));
      console.log('Status Code:', result.code);
      console.log('Message:', result.msg);
      console.log('Error:', result.error);
      
      if (result.result) {
        console.log('Driver Count:', result.result.driverCount);
        if (result.result.drivers) {
          console.log('Number of drivers:', result.result.drivers.length);
          result.result.drivers.forEach((driver, index) => {
            console.log(`Driver ${index + 1}: ${driver.shortName} - ${driver.longName}`);
          });
        }
        if (result.result.errorDetails) {
          console.log('Error Details:', result.result.errorDetails);
        }
      }
      console.log('================================');
      
      setDriversList(result);
    } catch (err) {
      const errorMessage = err instanceof Error ? err.message : 'Unknown error occurred';
      console.error('=== Error listing GDAL drivers ===');
      console.error('Error:', err);
      console.error('Error message:', errorMessage);
      console.error('===================================');
      setDriversError(errorMessage);
    } finally {
      setLoadingDrivers(false);
    }
  };

  // Simple navigation handler
  const navigation = {
    goBack: () => setCurrentScreen('main'),
    navigate: (screen: 'readPdf') => setCurrentScreen(screen),
  };

  // Show ReadGeoPDF screen if selected
  if (currentScreen === 'readPdf') {
    return <ReadGeoPDFScreen navigation={navigation} />;
  }

  return (
    <View style={styles.container}>
      <StatusBar style="auto" />
      <ScrollView contentContainerStyle={styles.scrollContent}>
        <Text style={styles.title}>GDAL Version Info Test</Text>
        <Text style={styles.subtitle}>Test the GDAL getVersionInfo function</Text>

        <View style={styles.buttonContainer}>
          <Button
            title="Read GeoPDF"
            onPress={() => setCurrentScreen('readPdf')}
            color="#007AFF"
          />
        </View>

        <View style={styles.buttonContainer}>
          <Button
            title={loading ? "Loading..." : "Get GDAL Version Info"}
            onPress={handleGetVersionInfo}
            disabled={loading || loadingDrivers}
          />
        </View>

        <View style={styles.buttonContainer}>
          <Button
            title={loadingDrivers ? "Loading..." : "List GDAL Drivers"}
            onPress={handleListDrivers}
            disabled={loading || loadingDrivers}
          />
        </View>

        {(loading || loadingDrivers) && (
          <View style={styles.loadingContainer}>
            <ActivityIndicator size="large" color="#007AFF" />
            <Text style={styles.loadingText}>
              {loading ? 'Fetching version info...' : 'Fetching drivers list...'}
            </Text>
          </View>
        )}

        {(error || driversError) && (
          <View style={styles.errorContainer}>
            <Text style={styles.errorTitle}>Error:</Text>
            <Text style={styles.errorText}>{error || driversError}</Text>
          </View>
        )}

        {versionInfo && (
          <View style={styles.resultContainer}>
            <Text style={styles.resultTitle}>Result:</Text>
            <View style={styles.resultBox}>
              <Text style={styles.resultLabel}>Status:</Text>
              <Text style={[styles.resultValue, versionInfo.error && styles.errorText]}>
                {versionInfo.code} {versionInfo.error ? '❌' : '✅'}
              </Text>

              <Text style={styles.resultLabel}>Message:</Text>
              <Text style={styles.resultValue}>{versionInfo.msg}</Text>

              {versionInfo.result && (
                <>
                  <Text style={styles.resultLabel}>Version:</Text>
                  <Text style={styles.resultValue}>{versionInfo.result.version || 'N/A'}</Text>

                  <Text style={styles.resultLabel}>Version Number:</Text>
                  <Text style={styles.resultValue}>{versionInfo.result.versionNum || 'N/A'}</Text>

                  <Text style={styles.resultLabel}>Release Date:</Text>
                  <Text style={styles.resultValue}>{versionInfo.result.releaseDate || 'N/A'}</Text>

                  {versionInfo.result.errorDetails && (
                    <>
                      <Text style={styles.resultLabel}>Error Details:</Text>
                      <Text style={styles.errorText}>{versionInfo.result.errorDetails}</Text>
                    </>
                  )}
                </>
              )}
            </View>

            <View style={styles.rawContainer}>
              <Text style={styles.rawTitle}>Raw Response:</Text>
              <Text style={styles.rawText}>{JSON.stringify(versionInfo, null, 2)}</Text>
            </View>
          </View>
        )}

        {driversList && (
          <View style={styles.resultContainer}>
            <Text style={styles.resultTitle}>Drivers List:</Text>
            <View style={styles.resultBox}>
              <Text style={styles.resultLabel}>Status:</Text>
              <Text style={[styles.resultValue, driversList.error && styles.errorText]}>
                {driversList.code} {driversList.error ? '❌' : '✅'}
              </Text>

              <Text style={styles.resultLabel}>Message:</Text>
              <Text style={styles.resultValue}>{driversList.msg}</Text>

              {driversList.result && (
                <>
                  <Text style={styles.resultLabel}>Driver Count:</Text>
                  <Text style={styles.resultValue}>{driversList.result.driverCount || 'N/A'}</Text>

                  {driversList.result.drivers && driversList.result.drivers.length > 0 && (
                    <>
                      <Text style={styles.resultLabel}>Drivers ({driversList.result.drivers.length}):</Text>
                      <ScrollView style={styles.driversScrollView} nestedScrollEnabled>
                        {driversList.result.drivers.map((driver, index) => (
                          <View key={index} style={styles.driverItem}>
                            <Text style={styles.driverShortName}>{driver.shortName}</Text>
                            <Text style={styles.driverLongName}>{driver.longName}</Text>
                          </View>
                        ))}
                      </ScrollView>
                    </>
                  )}

                  {driversList.result.errorDetails && (
                    <>
                      <Text style={styles.resultLabel}>Error Details:</Text>
                      <Text style={styles.errorText}>{driversList.result.errorDetails}</Text>
                    </>
                  )}
                </>
              )}
            </View>

            <View style={styles.rawContainer}>
              <Text style={styles.rawTitle}>Raw Response:</Text>
              <Text style={styles.rawText}>{JSON.stringify(driversList, null, 2)}</Text>
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
  buttonContainer: {
    marginBottom: 20,
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
  driversScrollView: {
    maxHeight: 300,
    marginTop: 8,
  },
  driverItem: {
    padding: 8,
    marginBottom: 8,
    backgroundColor: '#f0f0f0',
    borderRadius: 4,
    borderLeftWidth: 3,
    borderLeftColor: '#007AFF',
  },
  driverShortName: {
    fontSize: 14,
    fontWeight: 'bold',
    color: '#007AFF',
    marginBottom: 4,
  },
  driverLongName: {
    fontSize: 12,
    color: '#666',
  },
});
