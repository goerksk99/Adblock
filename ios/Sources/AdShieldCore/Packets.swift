import Foundation

public struct DNSPacket {
  public let version: Int, source: [UInt8], destination: [UInt8], sourcePort: UInt16, proto: UInt8
  public let segment: [UInt8]
  public init(_ data: Data) throws {
    let b = [UInt8](data)
    guard b.count >= 20 else { throw WireError.malformed }
    var start: Int
    switch b[0] >> 4 {
    case 4:
      start = Int(b[0] & 15) * 4
      guard start >= 20, start <= b.count, Int(read16(b, 2)) == b.count, read16(b, 6) & 0x3FFF == 0,
        Self.checksum(Array(b[0..<start])) == 0
      else { throw WireError.unsupported }
      version = 4
      source = Array(b[12..<16])
      destination = Array(b[16..<20])
      proto = b[9]
    case 6:
      guard b.count >= 40, Int(read16(b, 4)) + 40 == b.count else { throw WireError.malformed }
      start = 40
      version = 6
      source = Array(b[8..<24])
      destination = Array(b[24..<40])
      proto = b[6]
    default: throw WireError.unsupported
    }
    let v4: [UInt8] = [10, 111, 0, 2]
    let v6: [UInt8] = [0xfd, 0xad, 0x51, 0xe1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 2]
    guard version == 4 ? destination == v4 : destination == v6, proto == 17 || proto == 6,
      b.count >= start + (proto == 17 ? 8 : 20), read16(b, start + 2) == 53
    else { throw WireError.unsupported }
    segment = Array(b[start...])
    sourcePort = read16(segment, 0)
    if proto == 17 {
      guard Int(read16(segment, 4)) == segment.count else { throw WireError.malformed }
    } else {
      let header = Int(segment[12] >> 4) * 4
      guard header >= 20, header <= segment.count else { throw WireError.malformed }
    }
    var pseudo = source + destination
    if version == 4 {
      pseudo += [0, proto, UInt8(segment.count >> 8), UInt8(segment.count & 255)]
    } else {
      pseudo += [0, 0, UInt8(segment.count >> 8), UInt8(segment.count & 255), 0, 0, 0, proto]
    }
    if !(version == 4 && proto == 17 && read16(segment, 6) == 0) {
      guard Self.checksum(pseudo + segment) == 0 else { throw WireError.malformed }
    }
  }
  public var payload: Data { Data(segment.dropFirst(proto == 17 ? 8 : Int(segment[12] >> 4) * 4)) }
  public var key: String {
    "\(version):\(source.map(String.init).joined(separator: ".")):\(sourcePort)"
  }
  public func udpReply(_ payload: Data) -> Data {
    var s = [UInt8](repeating: 0, count: 8)
    put16(&s, 0, 53)
    put16(&s, 2, sourcePort)
    put16(&s, 4, UInt16(payload.count + 8))
    s += payload
    return wrap(s)
  }
  public func tcpReply(sequence: UInt32, ack: UInt32, flags: UInt8, payload: [UInt8] = []) -> Data {
    var s = [UInt8](repeating: 0, count: 20)
    put16(&s, 0, 53)
    put16(&s, 2, sourcePort)
    put32(&s, 4, sequence)
    put32(&s, 8, ack)
    s[12] = 0x50
    s[13] = flags
    put16(&s, 14, 32768)
    s += payload
    return wrap(s)
  }
  private func wrap(_ segment: [UInt8]) -> Data {
    var s = segment
    var pseudo = destination + source
    let n = s.count
    if version == 4 {
      pseudo += [0, proto, UInt8(n >> 8), UInt8(n & 255)]
    } else {
      pseudo += [0, 0, UInt8(n >> 8), UInt8(n & 255), 0, 0, 0, proto]
    }
    let c = Self.checksum(pseudo + s)
    put16(&s, proto == 17 ? 6 : 16, c == 0 ? 65535 : c)
    var ip = [UInt8](repeating: 0, count: version == 4 ? 20 : 40)
    if version == 4 {
      ip[0] = 0x45
      put16(&ip, 2, UInt16(n + 20))
      ip[8] = 64
      ip[9] = proto
      ip.replaceSubrange(12..<16, with: destination)
      ip.replaceSubrange(16..<20, with: source)
      put16(&ip, 10, Self.checksum(ip))
    } else {
      ip[0] = 0x60
      put16(&ip, 4, UInt16(n))
      ip[6] = proto
      ip[7] = 64
      ip.replaceSubrange(8..<24, with: destination)
      ip.replaceSubrange(24..<40, with: source)
    }
    return Data(ip + s)
  }
  public static func checksum(_ b: [UInt8]) -> UInt16 {
    var sum: UInt32 = 0
    var i = 0
    while i + 1 < b.count {
      sum += UInt32(read16(b, i))
      i += 2
    }
    if i < b.count { sum += UInt32(b[i]) << 8 }
    while sum >> 16 != 0 { sum = (sum & 65535) + (sum >> 16) }
    return ~UInt16(sum)
  }
}
