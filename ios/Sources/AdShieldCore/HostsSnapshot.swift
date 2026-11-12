import Foundation

public enum HostsSnapshot {
  /// Validate before replacing a known-good list. Reject HTML, truncation and count mismatches.
  public static func validate(
    _ data: Data, minimum: Int = 50_000, maximum: Int = 300_000, maxBytes: Int = 8_000_000
  ) throws -> Set<String> {
    guard data.count <= maxBytes, let text = String(data: data, encoding: .utf8) else {
      throw WireError.malformed
    }
    let hosts = DomainRules.hosts(text)
    guard (minimum...maximum).contains(hosts.count),
      let line = text.split(whereSeparator: \.isNewline).first(where: {
        $0.hasPrefix("# Number of unique domains:")
      }),
      let count = line.split(separator: ":").last,
      let declared = Int(count.filter(\.isNumber)), declared == hosts.count
    else { throw WireError.malformed }
    return hosts
  }
}
