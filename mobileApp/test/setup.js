jest.mock("react-native-safe-area-context", () => {
  const { View } = require("react-native");
  return { SafeAreaProvider: View, SafeAreaView: View };
});
jest.mock("react-native-svg", () => ({
  __esModule: true,
  default: "Svg",
  Svg: "Svg",
  SvgXml: "Svg",
  Path: "Path",
  Circle: "Circle",
  G: "G",
  Text: "SvgText",
  Line: "Line",
  Rect: "Rect",
  Polygon: "Polygon",
}));
