import Foundation
import XCTest

@testable import AdShieldCore

final class HostsSnapshotTests: XCTestCase {
  let valid = Data(
    "# Number of unique domains: 2\n0.0.0.0 ads.example.com\n0.0.0.0 tracker.example.com\n".utf8)
  func testValidDeclaredCount() throws {
    XCTAssertEqual(try HostsSnapshot.validate(valid, minimum: 1, maximum: 3).count, 2)
  }
  func testRejectTruncatedHTMLAndInvalidEncoding() {
    for data in [Data("<html>error</html>".utf8), Data(valid.dropLast(25)), Data([0xFF, 0xFE])] {
      XCTAssertThrowsError(try HostsSnapshot.validate(data, minimum: 1, maximum: 3))
    }
  }
  func testRejectWrongCountMissingHeaderAndSize() {
    let wrong = Data("# Number of unique domains: 3\n0.0.0.0 ads.example.com".utf8)
    XCTAssertThrowsError(try HostsSnapshot.validate(wrong, minimum: 1, maximum: 3))
    XCTAssertThrowsError(
      try HostsSnapshot.validate(Data("0.0.0.0 ads.example.com".utf8), minimum: 1, maximum: 3))
    XCTAssertThrowsError(try HostsSnapshot.validate(valid, minimum: 1, maximum: 3, maxBytes: 10))
    XCTAssertThrowsError(try HostsSnapshot.validate(valid, minimum: 3, maximum: 4))
  }
}
