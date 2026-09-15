//
//  File.swift
//  
//
//  Created by Karem on 10/23/23.
//

import Foundation
import SwiftUI

public struct ComponentsSpacings {
    var noSpacing: CGFloat
    var stroke: CGFloat
    var tabIndicatorSize: CGFloat

    var paddingXSmall: CGFloat
    var paddingSmall: CGFloat
    var paddingMedium: CGFloat
    var paddingLarge: CGFloat
    var paddingXLarge: CGFloat

    var iconSmall: CGFloat
    var iconMedium: CGFloat
    var iconLarge: CGFloat

    var switchBtnHeight: CGFloat
    var switchBtnWidth: CGFloat
    var btnMinHeightSmall: CGFloat
    var btnMinHeightNormal: CGFloat

    var progressSizeNormal: CGFloat
    var progressSizeSmall: CGFloat
    var progressStrokeNormal: CGFloat
    var progressStrokeSmall: CGFloat

    var popupIconLarge: CGFloat
    var popupPadding: CGFloat
    var popupSpacingLarge: CGFloat
    var popupSpacingMedium: CGFloat

    var sheetElevation: CGFloat = 12
    var sheetPaddingHorizontal: CGFloat = 24
    var sheetPaddingVertical: CGFloat = 22
    var sheetHandleWidth: CGFloat = 26
    var sheetHandlePadding: CGFloat = 10

    public init(
        noSpacing: CGFloat = 0,
        stroke: CGFloat = 1,
        tabIndicatorSize: CGFloat = 8,
        paddingXSmall: CGFloat = 4,
        paddingSmall: CGFloat = 6,
        paddingMedium: CGFloat = 10,
        paddingLarge: CGFloat = 12,
        paddingXLarge: CGFloat = 16,
        iconSmall: CGFloat = 18,
        iconMedium: CGFloat = 20,
        iconLarge: CGFloat = 24,
        switchBtnHeight: CGFloat = 20,
        switchBtnWidth: CGFloat = 40,
        btnMinHeightSmall: CGFloat = 42,
        btnMinHeightNormal: CGFloat = 44,
        progressSizeNormal: CGFloat = 44,
        progressSizeSmall: CGFloat = 24,
        progressStrokeNormal: CGFloat = 8,
        progressStrokeSmall: CGFloat = 6,
        popupIconLarge: CGFloat = 100,
        popupPadding: CGFloat = 30,
        popupSpacingLarge: CGFloat = 32,
        popupSpacingMedium: CGFloat = 24,
        sheetElevation: CGFloat = 12,
        sheetPaddingHorizontal: CGFloat = 24,
        sheetPaddingVertical: CGFloat = 22,
        sheetHandleWidth: CGFloat = 26,
        sheetHandlePadding: CGFloat = 10

    ) {
        self.noSpacing = noSpacing
        self.stroke = stroke
        self.tabIndicatorSize = tabIndicatorSize
        self.paddingXSmall = paddingXSmall
        self.paddingSmall = paddingSmall
        self.paddingMedium = paddingMedium
        self.paddingLarge = paddingLarge
        self.paddingXLarge = paddingXLarge
        self.iconSmall = iconSmall
        self.iconMedium = iconMedium
        self.iconLarge = iconLarge
        self.switchBtnHeight = switchBtnHeight
        self.switchBtnWidth = switchBtnWidth
        self.btnMinHeightSmall = btnMinHeightSmall
        self.btnMinHeightNormal = btnMinHeightNormal
        self.progressSizeNormal = progressSizeNormal
        self.progressSizeSmall = progressSizeSmall
        self.progressStrokeNormal = progressStrokeNormal
        self.progressStrokeSmall = progressStrokeSmall
        self.popupIconLarge = popupIconLarge
        self.popupPadding = popupPadding
        self.popupSpacingLarge = popupSpacingLarge
        self.popupSpacingMedium = popupSpacingMedium
        self.sheetElevation = sheetElevation
        self.sheetPaddingHorizontal = sheetPaddingHorizontal
        self.sheetPaddingVertical = sheetPaddingVertical
        self.sheetHandleWidth = sheetHandleWidth
        self.sheetHandlePadding = sheetHandlePadding
    }
}
