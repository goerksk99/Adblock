import Foundation
import XCTest

@testable import AdShieldNetworking

#if canImport(FoundationNetworking)
  import FoundationNetworking
#endif

final class StubProtocol: URLProtocol, @unchecked Sendable {
  static let lock = NSLock()
  static var handler: ((URLRequest) -> (Int, String, Data))?
  override class func canInit(with request: URLRequest) -> Bool { true }
  override class func canonicalRequest(for request: URLRequest) -> URLRequest { request }
  override func startLoading() {
    Self.lock.lock()
    let handler = Self.handler
    Self.lock.unlock()
    guard let (status, mime, data) = handler?(request) else { return }
    let response = HTTPURLResponse(
      url: request.url!, statusCode: status, httpVersion: "HTTP/1.1",
      headerFields: ["Content-Type": mime, "Content-Length": String(data.count)])!
    client?.urlProtocol(self, didReceive: response, cacheStoragePolicy: .notAllowed)
    client?.urlProtocol(self, didLoad: data)
    client?.urlProtocolDidFinishLoading(self)
  }
  override func stopLoading() {}
}
final class DoHClientTests: XCTestCase {
  func perform(
    status: Int = 200, mime: String = "application/dns-message", data: Data,
    assertion: @escaping (Result<Data, Error>) -> Void
  ) {
    let config = URLSessionConfiguration.ephemeral
    config.protocolClasses = [StubProtocol.self]
    StubProtocol.lock.lock()
    StubProtocol.handler = { request in
      XCTAssertEqual(request.url?.absoluteString, "https://dns.quad9.net/dns-query")
      XCTAssertEqual(request.httpMethod, "POST")
      XCTAssertEqual(request.value(forHTTPHeaderField: "Accept"), "application/dns-message")
      XCTAssertEqual(request.value(forHTTPHeaderField: "Content-Type"), "application/dns-message")
      return (status, mime, data)
    }
    StubProtocol.lock.unlock()
    let client = DoHClient(configuration: config)
    let done = expectation(description: "HTTP DNS completed")
    client.query(Data(repeating: 0, count: 20)) { result in
      assertion(result)
      done.fulfill()
    }
    wait(for: [done], timeout: 3)
    client.stop()
    StubProtocol.lock.lock()
    StubProtocol.handler = nil
    StubProtocol.lock.unlock()
  }
  func testValidMIMEAndBinaryBody() {
    let expected = Data(repeating: 42, count: 100)
    perform(data: expected) { XCTAssertEqual(try? $0.get(), expected) }
  }
  func testRejectHTTPError() {
    perform(status: 503, data: Data(repeating: 0, count: 20)) { result in
      if case .success = result { XCTFail("HTTP errors must not become DNS answers") }
    }
  }
  func testRejectHTMLAndShortDNS() {
    perform(mime: "text/html", data: Data(repeating: 0, count: 20)) { result in
      if case .success = result { XCTFail("Wrong MIME type") }
    }
    perform(data: Data([1, 2])) { result in
      if case .success = result { XCTFail("Short body") }
    }
  }
  func testRejectOversizedBody() {
    perform(data: Data(repeating: 0, count: 65536)) { result in
      if case .success = result { XCTFail("Unbounded reply") }
    }
  }
}
