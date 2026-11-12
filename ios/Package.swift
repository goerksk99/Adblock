// swift-tools-version: 5.9
import PackageDescription

let package = Package(
  name: "AdShieldCore",
  platforms: [.iOS(.v16), .macOS(.v13)],
  products: [
    .library(name: "AdShieldCore", targets: ["AdShieldCore"]),
    .library(name: "AdShieldNetworking", targets: ["AdShieldNetworking"]),
  ],
  targets: [
    .target(name: "AdShieldCore"),
    .target(name: "AdShieldNetworking"),
    .testTarget(name: "AdShieldCoreTests", dependencies: ["AdShieldCore", "AdShieldNetworking"]),
  ])
