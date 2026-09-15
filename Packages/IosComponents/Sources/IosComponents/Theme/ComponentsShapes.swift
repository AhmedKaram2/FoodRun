//
//  File.swift
//  
//
//  Created by Mahmoud Elshamy on 23/10/2023.
//

import Foundation
import SwiftUI

public struct ComponentsShapes {
    public var xSmall: RoundedRectangle
    public var small: RoundedRectangle
    public var medium: RoundedRectangle
    public var large: RoundedRectangle
    public var xLarge: RoundedRectangle

    public init(
        xSmall: RoundedRectangle = RoundedRectangle(cornerRadius: 8),
        small: RoundedRectangle = RoundedRectangle(cornerRadius: 10),
        medium: RoundedRectangle = RoundedRectangle(cornerRadius: 12),
        large: RoundedRectangle = RoundedRectangle(cornerRadius: 14),
        xLarge: RoundedRectangle = RoundedRectangle(cornerRadius: 16)
    ) {
        self.xSmall = xSmall
        self.small = small
        self.medium = medium
        self.large = large
        self.xLarge = xLarge
    }
}

