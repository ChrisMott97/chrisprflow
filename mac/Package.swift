// swift-tools-version:5.9
import PackageDescription

let package = Package(
    name: "Dictation",
    platforms: [.macOS(.v13)],
    targets: [
        .executableTarget(name: "Dictation", path: "Sources/Dictation"),
        .testTarget(name: "DictationTests", dependencies: ["Dictation"], path: "Tests/DictationTests"),
    ]
)
