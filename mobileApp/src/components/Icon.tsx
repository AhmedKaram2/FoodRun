import React from "react";
import Svg, { Path, Circle, Rect, Line } from "react-native-svg";
import { colors } from "../theme";
import iconPaths from "../../../Branding/Intrvioo/source/icons.json";
const paths: Record<string,string> = iconPaths;
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
      {name !== "more" && <Path d={paths[name] || paths.room} />}
      {name === "more" && [5,12,19].map(x=><Circle key={x} cx={x} cy={12} r={1.7} fill={color} stroke="none"/>)}
      {name === "friends" && <Circle cx={12} cy={7} r={3} />}
      {name === "search" && <Circle cx={10} cy={10} r={7} />}
      {name === "wheel" && <Circle cx={12} cy={12} r={10} />}
      {name === "wallet" && <Circle cx={17} cy={13.5} r={0.6} fill={color} />}
    </Svg>
  );
}
