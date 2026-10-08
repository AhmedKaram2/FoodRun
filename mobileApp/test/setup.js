jest.mock("react-native-safe-area-context", () => {
  const { View } = require("react-native");
  return { SafeAreaProvider: View, SafeAreaView: View, useSafeAreaInsets:()=>({top:0,bottom:20,left:0,right:0}) };
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
