import Foundation
import XCTest

@testable import AdShieldCore

final class CoreTests: XCTestCase {
  let query = Data([
    0x12, 0x34, 1, 0, 0, 1, 0, 0, 0, 0, 0, 0, 3, 97, 100, 115, 7, 101, 120, 97, 109, 112, 108, 101,
    3, 99, 111, 109, 0, 0, 1, 0, 1,
  ])
  func testRulesNormalizeAndMatchBoundaries() {
    XCTAssertEqual(DomainRules.normalize(" EXAMPLE.COM. "), "example.com")
    for input in [
      "https://example.com", "*.example.com", "127.0.0.1", "a..com", "-ads.com",
      String(repeating: "a", count: 64) + ".com",
    ] { XCTAssertNil(DomainRules.normalize(input)) }
    let rules = FilterRules(
      hosts: ["example.com"], allow: ["safe.example.com"], deny: ["blocked.net"])
    XCTAssertTrue(rules.blocks("ads.example.com"))
    XCTAssertFalse(rules.blocks("notexample.com"))
    XCTAssertFalse(rules.blocks("cdn.safe.example.com"))
    XCTAssertTrue(rules.blocks("cdn.blocked.net"))
  }
  func testHostsCommentsAndAliases() {
    XCTAssertEqual(
      DomainRules.hosts(
        "# header\n0.0.0.0 ads.example.com tracker.example.com # text\n127.0.0.1 localhost\n::1 localhost\n0.0.0.0 ads.example.com\n"
      ), ["ads.example.com", "tracker.example.com"])
  }
  func testBundledHostsCountAndLicenses() throws {
    let base = URL(fileURLWithPath: #filePath).deletingLastPathComponent()
      .deletingLastPathComponent().deletingLastPathComponent()
    let hosts = try String(
      contentsOf: base.appendingPathComponent("Resources/hosts"), encoding: .utf8)
    XCTAssertEqual(DomainRules.hosts(hosts).count, 78215)
    XCTAssertTrue(
      FileManager.default.fileExists(
        atPath: base.appendingPathComponent("Resources/STEVENBLACK-LICENSE.txt").path))
  }
  func testDNSBlockAndServfailPreserveQuestion() throws {
    let q = try DNSQuery(query)
    XCTAssertEqual(q.domain, "ads.example.com")
    XCTAssertEqual(q.type, 1)
    let nx = q.reply(code: 3)
    XCTAssertEqual(nx[3] & 15, 3)
    XCTAssertEqual(nx[2] & 0x80, 0x80)
    XCTAssertEqual(nx[0..<2], query[0..<2])
    XCTAssertEqual(nx[12...], query[12...])
    XCTAssertTrue(q.validResponse(nx))
    XCTAssertEqual(q.reply(code: 2)[3] & 15, 2)
    XCTAssertEqual(q.reply(code: 0, truncated: true)[2] & 2, 2)
  }
  func testRootDomainQueriesAreForwardable() throws {
    let root = Data([0x12, 0x34, 1, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 2, 0, 1])
    let parsed = try DNSQuery(root)
    XCTAssertEqual(parsed.domain, ".")
    XCTAssertFalse(FilterRules(hosts: ["example.com"]).blocks(parsed.domain))
    XCTAssertTrue(parsed.validResponse(parsed.reply(code: 0)))
  }
  func testEDNSUDPLimits() throws {
    XCTAssertEqual(try DNSQuery(query).udpLimit, 512)
    var edns = query
    edns[11] = 1
    edns.append(contentsOf: [0, 0, 41, 0x10, 0, 0, 0, 0, 0, 0, 0])
    XCTAssertEqual(try DNSQuery(edns).udpLimit, 1232)
    edns[query.count + 3] = 2
    edns[query.count + 4] = 88
    XCTAssertEqual(try DNSQuery(edns).udpLimit, 600)
    edns.removeLast()
    XCTAssertEqual(try DNSQuery(edns).udpLimit, 512)
  }
  func testRejectMalformedOrCrossQueryDNS() throws {
    for n in 0..<query.count { XCTAssertThrowsError(try DNSQuery(query.prefix(n))) }
    var compressed = [UInt8](query)
    compressed[12] = 0xc0
    XCTAssertThrowsError(try DNSQuery(Data(compressed)))
    var multi = query
    multi[5] = 2
    XCTAssertThrowsError(try DNSQuery(multi))
    var response = try DNSQuery(query).reply(code: 0)
    response[1] ^= 1
    XCTAssertFalse(try DNSQuery(query).validResponse(response))
    response = try DNSQuery(query).reply(code: 0)
    response[13] = 98
    XCTAssertFalse(try DNSQuery(query).validResponse(response))
  }
  func testUDPRoundTripBothFamiliesAndChecksums() throws {
    for version in [4, 6] {
      let bytes = packet(version: version, proto: 17, payload: query)
      let parsed = try DNSPacket(bytes)
      XCTAssertEqual(parsed.payload, query)
      XCTAssertEqual(parsed.sourcePort, 45000)
      let reply = parsed.udpReply(try DNSQuery(query).reply(code: 3))
      let raw = [UInt8](reply)
      let offset = version == 4 ? 20 : 40
      XCTAssertEqual(read16(raw, offset), 53)
      XCTAssertEqual(read16(raw, offset + 2), 45000)
      XCTAssertEqual(raw[offset + 8 + 3] & 15, 3)
      if version == 4 { XCTAssertEqual(DNSPacket.checksum(Array(raw[0..<20])), 0) }
      var pseudo = parsed.destination + parsed.source
      let length = raw.count - offset
      if version == 4 {
        pseudo += [0, 17, UInt8(length >> 8), UInt8(length & 255)]
      } else {
        pseudo += [0, 0, UInt8(length >> 8), UInt8(length & 255), 0, 0, 0, 17]
      }
      XCTAssertEqual(DNSPacket.checksum(pseudo + raw[offset...]), 0)
      var corrupt = bytes
      corrupt[corrupt.count - 1] ^= 1
      XCTAssertThrowsError(try DNSPacket(corrupt))
    }
  }
  func testRejectFragmentsWrongDestinationAndShortPackets() {
    for n in 0..<20 { XCTAssertThrowsError(try DNSPacket(Data(repeating: 0, count: n))) }
    var raw = [UInt8](packet(version: 4, proto: 17, payload: query))
    raw[6] = 0x20
    put16(&raw, 10, 0)
    put16(&raw, 10, DNSPacket.checksum(Array(raw[0..<20])))
    XCTAssertThrowsError(try DNSPacket(Data(raw)))
    raw = [UInt8](packet(version: 6, proto: 17, payload: query))
    raw[39] = 3
    XCTAssertThrowsError(try DNSPacket(Data(raw)))
  }
  func testTCPHandshakeSplitQueryAndMSS() throws {
    for version in [4, 6] {
      var written = [Data]()
      var received = [Data]()
      let bridge = TCPDNS(
        write: { data, _ in written.append(data) },
        resolve: { data, done in
          received.append(data)
          done((try! DNSQuery(data)).reply(code: 3))
        })
      let start = Date()
      bridge.receive(
        try DNSPacket(
          packet(version: version, proto: 6, payload: Data(), seq: 100, ack: 0, flags: 2, mss: 12)),
        now: start)
      XCTAssertEqual(written.count, 1)
      let offset = version == 4 ? 20 : 40
      let synack = [UInt8](written[0])
      let server = read32(synack, offset + 4)
      XCTAssertEqual(synack[offset + 13], 0x12)
      XCTAssertEqual(read32(synack, offset + 8), 101)
      bridge.receive(
        try DNSPacket(
          packet(
            version: version, proto: 6, payload: Data(), seq: 101, ack: server &+ 1, flags: 0x10)))
      var frame = Data([0, UInt8(query.count)])
      frame.append(query)
      bridge.receive(
        try DNSPacket(
          packet(
            version: version, proto: 6, payload: frame.prefix(7), seq: 101, ack: server &+ 1,
            flags: 0x18)))
      XCTAssertEqual(received.count, 0)
      bridge.receive(
        try DNSPacket(
          packet(
            version: version, proto: 6, payload: frame.dropFirst(7), seq: 108, ack: server &+ 1,
            flags: 0x18)))
      XCTAssertEqual(received, [query])
      var result = Data()
      var next = server &+ 1
      var consumed = 0
      while consumed < frame.count {
        let segment = [UInt8](written.last!)
        let size = segment.count - offset - 20
        XCTAssertGreaterThan(size, 0)
        XCTAssertLessThanOrEqual(size, 12)
        XCTAssertEqual(read32(segment, offset + 4), next)
        result.append(contentsOf: segment[(offset + 20)...])
        consumed += size
        next &+= UInt32(size)
        bridge.receive(
          try DNSPacket(
            packet(
              version: version, proto: 6, payload: Data(), seq: 101 &+ UInt32(frame.count),
              ack: next, flags: 0x10)))
      }
      XCTAssertEqual(result.count, frame.count)
      XCTAssertEqual(result[5] & 15, 3)
      // Replayed payload is acknowledged, never resolved twice.
      bridge.receive(
        try DNSPacket(
          packet(version: version, proto: 6, payload: frame, seq: 101, ack: next, flags: 0x18)))
      XCTAssertEqual(received.count, 1)
      bridge.stop()
    }
  }
  func testTCPRetransmitsThenExpires() throws {
    var written = [Data]()
    let bridge = TCPDNS(
      write: { data, _ in written.append(data) },
      resolve: { _, _ in XCTFail("No query during handshake") })
    let start = Date()
    let syn = try DNSPacket(
      packet(version: 4, proto: 6, payload: Data(), seq: 100, ack: 0, flags: 2))
    bridge.receive(syn, now: start)
    bridge.tick(now: start.addingTimeInterval(2))
    XCTAssertEqual(written.count, 2)
    XCTAssertEqual(written[0], written[1])
    bridge.tick(now: start.addingTimeInterval(31))
    XCTAssertEqual([UInt8](written.last!)[33], 0x14)
  }
  func testTCPConnectionLimitAndReset() throws {
    var count = 0
    let bridge = TCPDNS(write: { _, _ in count += 1 }, resolve: { _, _ in })
    for port in 45000..<45070 {
      bridge.receive(
        try DNSPacket(
          packet(
            version: 4, proto: 6, payload: Data(), seq: 100, ack: 0, flags: 2, port: UInt16(port))))
    }
    XCTAssertEqual(count, 32)
    bridge.receive(try DNSPacket(packet(version: 4, proto: 6, payload: Data(), flags: 4)))
    bridge.receive(
      try DNSPacket(
        packet(version: 4, proto: 6, payload: Data(), seq: 100, ack: 0, flags: 2, port: 50000)))
    XCTAssertEqual(count, 33)
  }
  func packet(
    version: Int, proto: UInt8, payload: Data, seq: UInt32 = 101, ack: UInt32 = 0,
    flags: UInt8 = 0x10, mss: UInt16? = nil, port: UInt16 = 45000
  ) -> Data {
    let src: [UInt8] =
      version == 4
      ? [10, 111, 0, 1] : [0xfd, 0xad, 0x51, 0xe1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1]
    let dst: [UInt8] =
      version == 4
      ? [10, 111, 0, 2] : [0xfd, 0xad, 0x51, 0xe1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 2]
    var s = [UInt8](repeating: 0, count: proto == 17 ? 8 : (mss == nil ? 20 : 24))
    put16(&s, 0, port)
    put16(&s, 2, 53)
    if proto == 17 {
      put16(&s, 4, UInt16(payload.count + 8))
    } else {
      put32(&s, 4, seq)
      put32(&s, 8, ack)
      s[12] = mss == nil ? 0x50 : 0x60
      s[13] = flags
      put16(&s, 14, 32768)
      if let mss {
        s[20] = 2
        s[21] = 4
        put16(&s, 22, mss)
      }
    }
    s += payload
    var pseudo = src + dst
    let n = s.count
    if version == 4 {
      pseudo += [0, proto, UInt8(n >> 8), UInt8(n & 255)]
    } else {
      pseudo += [0, 0, UInt8(n >> 8), UInt8(n & 255), 0, 0, 0, proto]
    }
    let c = DNSPacket.checksum(pseudo + s)
    put16(&s, proto == 17 ? 6 : 16, c == 0 ? 65535 : c)
    var ip = [UInt8](repeating: 0, count: version == 4 ? 20 : 40)
    if version == 4 {
      ip[0] = 0x45
      put16(&ip, 2, UInt16(n + 20))
      ip[8] = 64
      ip[9] = proto
      ip.replaceSubrange(12..<16, with: src)
      ip.replaceSubrange(16..<20, with: dst)
      put16(&ip, 10, DNSPacket.checksum(ip))
    } else {
      ip[0] = 0x60
      put16(&ip, 4, UInt16(n))
      ip[6] = proto
      ip[7] = 64
      ip.replaceSubrange(8..<24, with: src)
      ip.replaceSubrange(24..<40, with: dst)
    }
    return Data(ip + s)
  }
}
