import Foundation

public enum WireError: Error { case malformed, unsupported }
public enum DomainRules {
  public static func normalize(_ input: String) -> String? {
    let s = input.trimmingCharacters(in: .whitespacesAndNewlines).lowercased().trimmingCharacters(
      in: CharacterSet(charactersIn: "."))
    guard !s.isEmpty, s.utf8.count <= 253, s.contains("."), !s.contains(":"),
      s.utf8.contains(where: { (97...122).contains($0) })
    else { return nil }
    guard
      s.split(separator: ".", omittingEmptySubsequences: false).allSatisfy({ part in
        !part.isEmpty && part.utf8.count <= 63 && part.first != "-" && part.last != "-"
          && part.utf8.allSatisfy {
            (97...122).contains($0) || (48...57).contains($0) || $0 == 45 || $0 == 95
          }
      })
    else { return nil }
    return s
  }
  public static func matches(_ domain: String, in rules: Set<String>) -> Bool {
    var d = domain
    while true {
      if rules.contains(d) { return true }
      guard let dot = d.firstIndex(of: ".") else { return false }
      d = String(d[d.index(after: dot)...])
    }
  }
  public static func hosts(_ text: String) -> Set<String> {
    var result = Set<String>()
    for raw in text.split(whereSeparator: \.isNewline) {
      let fields = raw.split(separator: "#", omittingEmptySubsequences: false)[0].split(
        whereSeparator: \.isWhitespace)
      guard fields.count >= 2, fields[0] == "0.0.0.0" || fields[0] == "127.0.0.1" else { continue }
      for field in fields.dropFirst() {
        if let d = normalize(String(field)), d != "localhost.localdomain", !d.hasSuffix(".local") {
          result.insert(d)
        }
      }
    }
    return result
  }
}
public struct FilterRules {
  public let hosts: Set<String>, allow: Set<String>, deny: Set<String>
  public init(hosts: Set<String>, allow: [String] = [], deny: [String] = []) {
    self.hosts = hosts
    self.allow = Set(allow.compactMap(DomainRules.normalize))
    self.deny = Set(deny.compactMap(DomainRules.normalize))
  }
  public func blocks(_ domain: String) -> Bool {
    !DomainRules.matches(domain, in: allow)
      && (DomainRules.matches(domain, in: deny) || DomainRules.matches(domain, in: hosts))
  }
}
public struct DNSQuery {
  public let bytes: [UInt8], domain: String, questionEnd: Int, type: UInt16
  public init(_ data: Data) throws {
    let b = [UInt8](data)
    guard b.count >= 17, b.count <= 65535, b[2] & 0xF8 == 0, b[3] & 0x40 == 0, read16(b, 4) == 1,
      read16(b, 6) == 0, read16(b, 8) == 0
    else { throw WireError.malformed }
    var offset = 12
    var labels = [String]()
    // Questions are uncompressed. Reject pointers instead of risking loops or stale offsets in replies.
    while true {
      guard offset < b.count else { throw WireError.malformed }
      let n = Int(b[offset])
      offset += 1
      if n == 0 { break }
      guard n <= 63, offset + n <= b.count,
        let label = String(bytes: b[offset..<offset + n], encoding: .ascii)
      else { throw WireError.malformed }
      labels.append(label)
      offset += n
    }
    guard offset + 4 <= b.count, read16(b, offset + 2) == 1 else { throw WireError.unsupported }
    let d = labels.joined(separator: ".").lowercased()
    guard d.utf8.count <= 253, !d.isEmpty else { throw WireError.malformed }
    bytes = b
    domain = d
    type = read16(b, offset)
    questionEnd = offset + 4
  }
  /// RFC 6891: legacy UDP clients advertise no size and must stay within 512 bytes.
  public var udpLimit: Int {
    guard read16(bytes, 10) == 1, questionEnd + 11 <= bytes.count, bytes[questionEnd] == 0,
      read16(bytes, questionEnd + 1) == 41,
      questionEnd + 11 + Int(read16(bytes, questionEnd + 9)) == bytes.count
    else { return 512 }
    return min(1232, max(512, Int(read16(bytes, questionEnd + 3))))
  }
  public func reply(code: UInt8, truncated: Bool = false) -> Data {
    var b = Array(bytes.prefix(questionEnd))
    b[2] = 0x80 | (bytes[2] & 1) | (truncated ? 2 : 0)
    b[3] = 0x80 | (bytes[3] & 0x10) | (code & 15)
    for i in 6..<12 { b[i] = 0 }
    return Data(b)
  }
  public func validResponse(_ data: Data) -> Bool {
    let b = [UInt8](data)
    guard b.count >= questionEnd, b.count <= 65535, b[0] == bytes[0], b[1] == bytes[1],
      b[2] & 0x80 != 0,
      b[2] & 0x78 == 0, read16(b, 4) == 1
    else { return false }
    var q = b
    q[2] &= 1
    q[3] = 0
    q[6] = 0
    q[7] = 0
    q[8] = 0
    q[9] = 0
    guard let parsed = try? DNSQuery(Data(q)) else { return false }
    return parsed.domain == domain && parsed.type == type
  }
}
public func read16(_ b: [UInt8], _ i: Int) -> UInt16 { UInt16(b[i]) << 8 | UInt16(b[i + 1]) }
public func read32(_ b: [UInt8], _ i: Int) -> UInt32 {
  UInt32(read16(b, i)) << 16 | UInt32(read16(b, i + 2))
}
public func put16(_ b: inout [UInt8], _ i: Int, _ n: UInt16) {
  b[i] = UInt8(n >> 8)
  b[i + 1] = UInt8(n & 255)
}
public func put32(_ b: inout [UInt8], _ i: Int, _ n: UInt32) {
  put16(&b, i, UInt16(n >> 16))
  put16(&b, i + 2, UInt16(n & 65535))
}
