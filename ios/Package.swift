// swift-tools-version: 5.9
import PackageDescription
let package = Package(name: "AdShieldCore", platforms: [.iOS(.v16), .macOS(.v13)], products: [.library(name: "AdShieldCore", targets: ["AdShieldCore"])], targets: [.target(name: "AdShieldCore"), .testTarget(name: "AdShieldCoreTests", dependencies: ["AdShieldCore"])])
