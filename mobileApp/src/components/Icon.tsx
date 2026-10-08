import React from "react";
import Svg, { Path, Circle, Rect, Line } from "react-native-svg";
import { colors } from "../theme";
const paths: Record<string, string> = {
  home: "M3 10 12 3l9 7v10a1 1 0 0 1-1 1h-5v-7H9v7H4a1 1 0 0 1-1-1Z",
  wallet: "M3 6h16a2 2 0 0 1 2 2v12H3V4l14-1v3M21 11h-6v5h6",
  friends: "M6 20v-3a6 6 0 0 1 12 0v3M5 5a3 3 0 0 0 0 6M19 5a3 3 0 0 1 0 6M2 19v-2a5 5 0 0 1 3-4M22 19v-2a5 5 0 0 0-3-4",
  bell: "M18 8a6 6 0 0 0-12 0c0 7-3 7-3 9h18c0-2-3-2-3-9M10 21h4",
  back: "m14 5-7 7 7 7",
  close: "m6 6 12 12M6 18 18 6",
  search: "m21 21-5-5",
  add: "M12 5v14M5 12h14",
  menu: "M4 6h16M4 12h16M4 18h16",
  check: "m5 12 4 4L19 6",
  room: "M4 4h16v16H4ZM8 8h8M8 12h8M8 16h4",
  food: "M5 3v7M8 3v7M11 3v7M5 8a3 3 0 0 0 6 0M8 11v10M19 3c-3 3-4 7-4 10h4M19 3v18",
  split: "M12 3v18M3 12h5M16 12h5M4 7h4M16 17h4",
  wheel: "M12 2v20M2 12h20M5 5l14 14M5 19 19 5",
  settings:
    "M12 8a4 4 0 1 0 0 8 4 4 0 0 0 0-8M12 2v3M12 19v3M2 12h3M19 12h3M5 5l2 2M17 17l2 2M5 19l2-2M17 7l2-2",
};
export default function Icon({
  name,
  size = 22,
  color = colors.ink,
}: {
  name: string;
  size?: number;
  color?: string;
}) {
  return (
    <Svg
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      stroke={color}
      strokeWidth={1.8}
      strokeLinecap="round"
      strokeLinejoin="round"
    >
      <Path d={paths[name] || paths.room} />
      {name === "friends" && <Circle cx={12} cy={7} r={3} />}
      {name === "search" && <Circle cx={10} cy={10} r={7} />}
      {name === "wheel" && <Circle cx={12} cy={12} r={10} />}
      {name === "wallet" && <Circle cx={17} cy={13.5} r={0.6} fill={color} />}
    </Svg>
  );
}
