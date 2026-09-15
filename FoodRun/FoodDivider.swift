import SwiftUI
import IosComponents

struct FoodDivider: View {
    var body: some View {
        HorizontalDivider(
            height: FoodBorder.thin,
            color: FoodTheme.line
        )
        .environmentObject(FoodTheme.components)
    }
}
