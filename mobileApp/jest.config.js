module.exports = {
  preset: "@react-native/jest-preset",
  testMatch: ["<rootDir>/test/**/*.test.tsx"],
  setupFilesAfterEnv: ["<rootDir>/test/setup.js"],
  modulePaths: ["<rootDir>/node_modules"],
};
