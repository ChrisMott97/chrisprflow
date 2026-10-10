// swift-tools-version:5.9
import PackageDescription

let package = Package(
    name: "Dictation",
    platforms: [.macOS(.v14)],
    dependencies: [.package(url: "https://github.com/FluidInference/FluidAudio.git", exact: "0.17.7")],
    targets: [
        .executableTarget(name: "Dictation", dependencies: [.product(name: "FluidAudio", package: "FluidAudio")], path: "Sources/Dictation"),
        .testTarget(name: "DictationTests", dependencies: ["Dictation"], path: "Tests/DictationTests"),
    ]
)
