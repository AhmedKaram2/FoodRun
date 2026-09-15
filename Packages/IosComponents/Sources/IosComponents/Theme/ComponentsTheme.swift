//
//  File.swift
//
//
//  Created by Karem on 10/23/23.
//

import Foundation
import SwiftUI

public class ComponentsTheme : ObservableObject {
    @Published public var colors: ComponentsColors
    @Published public var spacings: ComponentsSpacings
    @Published public var shapes: ComponentsShapes
    @Published public var typography: ComponentsTypography

    public init(
        colors: ComponentsColors = ComponentsColors(),
        spacings: ComponentsSpacings = ComponentsSpacings(),
        shapes: ComponentsShapes = ComponentsShapes(),
        typography: ComponentsTypography = ComponentsTypography()
    ) {
        self.colors = colors
        self.spacings = spacings
        self.shapes = shapes
        self.typography = typography
    }
}
