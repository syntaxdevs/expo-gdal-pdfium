const { getDefaultConfig } = require('expo/metro-config');
const path = require('path');

const projectRoot = __dirname;
const repoRoot = path.resolve(projectRoot, '..');

const config = getDefaultConfig(projectRoot);

// Make Metro watch the repo root so it can see the local module
config.watchFolders = [repoRoot];

// Resolve dependencies from the example first, then from the repo root
config.resolver.nodeModulesPaths = [
  path.resolve(projectRoot, 'node_modules'),
  path.resolve(repoRoot, 'node_modules'),
];

// Make the package name point to the module in the repo root
config.resolver.extraNodeModules = {
  'expo-gdal': repoRoot,
};

module.exports = config;