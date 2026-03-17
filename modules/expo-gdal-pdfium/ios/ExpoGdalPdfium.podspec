Pod::Spec.new do |s|
  s.name           = 'ExpoGdalPdfium'
  s.version        = '1.0.0'
  s.summary        = 'GDAL/PDFium module for Expo'
  s.description    = 'Expo module for GDAL/PDFium GeoPDF processing'
  s.author         = ''
  s.homepage       = 'https://docs.expo.dev/modules/'
  s.platforms      = {
    :ios => '15.1',
    :tvos => '15.1'
  }
  s.source         = { git: '' }

  s.dependency 'ExpoModulesCore'

  s.vendored_frameworks = 'GDAL.xcframework'
  s.libraries = 'iconv', 'sqlite3', 'c++'

  s.source_files = '*.{swift,h,m,mm}'

  s.pod_target_xcconfig = {
    'DEFINES_MODULE' => 'YES',
    'CLANG_CXX_LANGUAGE_STANDARD' => 'c++17',
    'CLANG_CXX_LIBRARY' => 'libc++',
    'OTHER_LDFLAGS' => '$(inherited) -liconv -lsqlite3'
  }
end