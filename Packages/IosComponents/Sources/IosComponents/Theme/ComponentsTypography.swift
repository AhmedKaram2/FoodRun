//
//  File.swift
//
//
//  Created by Mahmoud Elshamy on 24/10/2023.
//

import Foundation
import SwiftUI





public enum ComponentsTypographyFontWeights{
    case ultraLight
    case thin
    case light
    case regular
    case medium
    case semibold
    case bold
    case heavy
    case black
}

public struct ComponentsTypography {

    var headline: (size: CGFloat, weight: ComponentsTypographyFontWeights)
    var labelMedium: (size: CGFloat, weight: ComponentsTypographyFontWeights)
    var labelSmall: (size: CGFloat, weight: ComponentsTypographyFontWeights)
    var bodyLarge: (size: CGFloat, weight: ComponentsTypographyFontWeights)
    var bodyMedium: (size: CGFloat, weight: ComponentsTypographyFontWeights)
    var bodyMediumBold: (size: CGFloat, weight: ComponentsTypographyFontWeights)
    var bodySmall: (size: CGFloat, weight: ComponentsTypographyFontWeights)

   public  init(
        languageIsRTL:Bool = false ,
        headline: (size: CGFloat, weight: ComponentsTypographyFontWeights) = (24,.bold),
        labelMedium: (size: CGFloat, weight: ComponentsTypographyFontWeights) = (14,.regular),
        labelSmall: (size: CGFloat, weight: ComponentsTypographyFontWeights) = (12,.regular),
        bodyLarge: (size: CGFloat, weight: ComponentsTypographyFontWeights) = (16,.medium),
        bodyMedium: (size: CGFloat, weight: ComponentsTypographyFontWeights) = (14,.medium),
        bodyMediumBold: (size: CGFloat, weight: ComponentsTypographyFontWeights) = (14,.bold),
        bodySmall: (size: CGFloat, weight: ComponentsTypographyFontWeights) = (12,.medium)
    ) {


        self.languageIsRTL = languageIsRTL
        self.headline = headline
        self.labelMedium = labelMedium
        self.labelSmall = labelSmall
        self.bodyLarge = bodyLarge
        self.bodyMedium = bodyMedium
        self.bodySmall = bodySmall
        self.bodyMediumBold = bodyMediumBold
    }

    public var languageIsRTL:Bool = false


    var headLineFont:Font{
        return  resolveComponentsTypographyFontWeights(fontDesc: headline)
    }

    var labelMediumFont:Font{
        return  resolveComponentsTypographyFontWeights(fontDesc: labelMedium)
    }

    var labelSmallFont:Font{
        return  resolveComponentsTypographyFontWeights(fontDesc: labelSmall)
    }
    var bodyLargeFont:Font{
        return  resolveComponentsTypographyFontWeights(fontDesc: bodyLarge)
    }
    var bodyMediumFont:Font{
        return  resolveComponentsTypographyFontWeights(fontDesc: bodyMedium)
    }
    var bodyMediumBoldFont:Font{
        return  resolveComponentsTypographyFontWeights(fontDesc: bodyMediumBold)
    }
    var bodySmallFont:Font{
        return  resolveComponentsTypographyFontWeights(fontDesc: bodySmall)
    }




    func resolveComponentsTypographyFontWeights(fontDesc:(size: CGFloat, weight: ComponentsTypographyFontWeights)) -> Font{
        switch fontDesc.weight {
        case .ultraLight:
            let systemFontWeight:Font.Weight = .ultraLight

            return .system(size: fontDesc.size, weight: systemFontWeight)
        case .thin:
            let systemFontWeight:Font.Weight = .thin

            return .system(size: fontDesc.size, weight: systemFontWeight)

        case .light:
            let systemFontWeight:Font.Weight = .light

            return .system(size: fontDesc.size, weight: systemFontWeight)

        case .regular:
            let systemFontWeight:Font.Weight = .regular

            return .system(size: fontDesc.size, weight: systemFontWeight)
        case .medium:
            let systemFontWeight:Font.Weight = .medium

            return .system(size: fontDesc.size, weight: systemFontWeight)

        case .semibold:
            let systemFontWeight:Font.Weight = .semibold

            return .system(size: fontDesc.size, weight: systemFontWeight)

        case .bold:
            let systemFontWeight:Font.Weight = .bold

            return .system(size: fontDesc.size, weight: systemFontWeight)
        case .heavy:
            let systemFontWeight:Font.Weight = .heavy

            return .system(size: fontDesc.size, weight: systemFontWeight)
        case .black:
            let systemFontWeight:Font.Weight = .black

            return .system(size: fontDesc.size, weight: systemFontWeight)
        }
    }


    func customFont(size:CGFloat,weight:ComponentsTypographyFontWeights) -> Font{
        resolveComponentsTypographyFontWeights(fontDesc: (size,weight))
    }
}

