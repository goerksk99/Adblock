import AdShieldCore
import Foundation

struct ShieldConfiguration: Codable {
  var enabled = true
  var allow = [String]()
  var deny = [String]()
}
struct ShieldStatistics: Codable {
  var day = ""
  var queries = 0
  var blocked = 0
  var failures = 0
}
enum SharedStore {
  static func directory() throws -> URL {
    guard let group = Bundle.main.object(forInfoDictionaryKey: "AdShieldAppGroup") as? String,
      let url = FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: group)
    else {
      throw NSError(
        domain: "AdShield", code: 1,
        userInfo: [
          NSLocalizedDescriptionKey: "Die gemeinsame App-Gruppe fehlt. Bitte die Signierung prüfen."
        ])
    }
    return url
  }
  static func read<T: Decodable>(_ name: String, fallback: T) -> T {
    guard let root = try? directory(),
      let data = try? Data(contentsOf: root.appendingPathComponent(name))
    else { return fallback }
    return (try? JSONDecoder().decode(T.self, from: data)) ?? fallback
  }
  static func write<T: Encodable>(_ value: T, name: String) throws {
    let url = try directory().appendingPathComponent(name)
    try JSONEncoder().encode(value).write(
      to: url, options: [.atomic, .completeFileProtectionUntilFirstUserAuthentication])
  }
  static func rules(bundle: Bundle = .main) throws -> FilterRules {
    let url = try directory().appendingPathComponent("hosts")
    let source: URL
    if FileManager.default.fileExists(atPath: url.path) {
      source = url
    } else if let bundled = bundle.url(forResource: "hosts", withExtension: nil) {
      source = bundled
    } else {
      throw NSError(
        domain: "AdShield", code: 2, userInfo: [NSLocalizedDescriptionKey: "Die Filterliste fehlt."]
      )
    }
    let data = try Data(contentsOf: source)
    guard data.count <= 8_000_000, let text = String(data: data, encoding: .utf8) else {
      throw WireError.malformed
    }
    let hosts = DomainRules.hosts(text)
    guard hosts.count >= 50000 else { throw WireError.malformed }
    let config = read("configuration.json", fallback: ShieldConfiguration())
    return FilterRules(hosts: hosts, allow: config.allow, deny: config.deny)
  }
  static func today() -> String {
    let f = DateFormatter()
    f.locale = Locale(identifier: "en_US_POSIX")
    f.dateFormat = "yyyy-MM-dd"
    return f.string(from: Date())
  }
}
