// swift-tools-version:5.9
import PackageDescription

let package = Package(
    name: "AcoesCore",
    platforms: [.iOS(.v16), .macOS(.v13)],
    products: [
        .library(name: "AcoesCore", targets: ["AcoesCore"]),
    ],
    targets: [
        .target(name: "AcoesCore"),
        .testTarget(
            name: "AcoesCoreTests",
            dependencies: ["AcoesCore"],
            resources: [.copy("Fixtures")]
        ),
    ]
)
