// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "IosComponents",
    platforms: [.iOS(.v17)],
    products: [
        .library(
            name: "IosComponents",
            targets: ["IosComponents"]
        )
    ],
    targets: [.target(name: "IosComponents")]
)
