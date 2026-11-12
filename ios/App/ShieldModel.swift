import AdShieldCore
import Combine
import Foundation
import NetworkExtension

@MainActor final class ShieldModel: ObservableObject {
  @Published var status: NEVPNStatus = .invalid
  @Published var configuration = ShieldConfiguration()
  @Published var statistics = ShieldStatistics()
  @Published var domainCount = 0
  @Published var error: String?
  @Published var busy = false
  @Published var updating = false
  @Published var updated: Date?
  private var manager: NETunnelProviderManager?
  private var observer: NSObjectProtocol?
  private var poller: Task<Void, Never>?
  var connected: Bool { status == .connected }
  var statusText: String {
    switch status {
    case .connected: return "DNS-Schutz aktiv"
    case .connecting: return "Verbindung wird aufgebaut …"
    case .reasserting: return "Netzwerk wird gewechselt …"
    case .disconnecting: return "Wird beendet …"
    default: return "DNS-Schutz inaktiv"
    }
  }
  init() {
    configuration = SharedStore.read("configuration.json", fallback: ShieldConfiguration())
    refreshStatistics()
    Task { [weak self] in
      let count = await Task.detached(priority: .utility) {
        (try? SharedStore.rules().hosts.count) ?? 0
      }.value
      self?.domainCount = count
    }
    updated = SharedStore.read("updated.json", fallback: Optional<Date>.none)
    observer = NotificationCenter.default.addObserver(
      forName: .NEVPNStatusDidChange, object: nil, queue: .main
    ) { [weak self] _ in
      Task { @MainActor in
        guard let self else { return }
        self.status = self.manager?.connection.status ?? .invalid
        if self.status == .disconnected {
          let last = SharedStore.read("last-error.json", fallback: "")
          if !last.isEmpty { self.error = last }
        }
      }
    }
    poller = Task { [weak self] in
      await self?.load()
      while !Task.isCancelled {
        try? await Task.sleep(nanoseconds: 3_000_000_000)
        guard !Task.isCancelled else { break }
        self?.refreshStatistics()
      }
    }
  }
  private func load() async {
    do {
      let managers = try await NETunnelProviderManager.loadAllFromPreferences()
      let identifier = Bundle.main.bundleIdentifier! + ".Tunnel"
      manager = managers.first {
        ($0.protocolConfiguration as? NETunnelProviderProtocol)?.providerBundleIdentifier
          == identifier
      }
      status = manager?.connection.status ?? .invalid
    } catch { self.error = error.localizedDescription }
  }
  func refreshStatistics() {
    let s = SharedStore.read("statistics.json", fallback: ShieldStatistics())
    statistics = s.day == SharedStore.today() ? s : ShieldStatistics(day: SharedStore.today())
  }
  func toggle() async {
    guard !busy else { return }
    busy = true
    defer { busy = false }
    error = nil
    if connected || status == .connecting || status == .reasserting {
      manager?.connection.stopVPNTunnel()
      return
    }
    do {
      let m = manager ?? NETunnelProviderManager()
      let p = NETunnelProviderProtocol()
      p.providerBundleIdentifier = Bundle.main.bundleIdentifier! + ".Tunnel"
      p.serverAddress = "Lokaler AdShield-DNS-Filter"
      p.disconnectOnSleep = false
      m.protocolConfiguration = p
      m.localizedDescription = "AdShield DNS-Schutz"
      m.isEnabled = true
      // No full-device routes, kill switch or remote VPN endpoint.
      m.isOnDemandEnabled = false
      try await m.saveToPreferences()
      try await m.loadFromPreferences()
      manager = m
      try SharedStore.write("", name: "last-error.json")
      try m.connection.startVPNTunnel()
      status = m.connection.status
    } catch { self.error = error.localizedDescription }
  }
  @discardableResult func save() -> Bool {
    do {
      try SharedStore.write(configuration, name: "configuration.json")
      reloadTunnel()
      return true
    } catch {
      self.error = error.localizedDescription
      configuration = SharedStore.read("configuration.json", fallback: ShieldConfiguration())
      return false
    }
  }
  private func reloadTunnel() {
    guard connected, let session = manager?.connection as? NETunnelProviderSession else { return }
    do {
      try session.sendProviderMessage(Data("reload".utf8)) { [weak self] response in
        if response != Data("ok".utf8) {
          Task { @MainActor in
            self?.error =
              "Die neuen Filter konnten nicht geladen werden. Bitte den Schutz erneut starten."
          }
        }
      }
    } catch { self.error = error.localizedDescription }
  }
  func add(_ input: String, allow: Bool) -> Bool {
    guard let domain = DomainRules.normalize(input) else {
      error =
        "Bitte einen Domainnamen eingeben, zum Beispiel example.org. Keine URL und keine Platzhalter."
      return false
    }
    if allow {
      if !configuration.allow.contains(domain) {
        configuration.allow.append(domain)
        configuration.allow.sort()
      }
    } else {
      if !configuration.deny.contains(domain) {
        configuration.deny.append(domain)
        configuration.deny.sort()
      }
    }
    return save()
  }
  func updateHosts() async {
    guard !updating else { return }
    updating = true
    defer { updating = false }
    do {
      var request = URLRequest(
        url: URL(string: "https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts")!)
      request.timeoutInterval = 30
      request.cachePolicy = .reloadIgnoringLocalCacheData
      let session = URLSession(configuration: .ephemeral)
      defer { session.invalidateAndCancel() }
      let (stream, response) = try await session.bytes(for: request)
      guard let http = response as? HTTPURLResponse, http.statusCode == 200,
        response.expectedContentLength <= 8_000_000
      else { throw URLError(.badServerResponse) }
      var data = Data()
      for try await byte in stream {
        guard data.count < 8_000_000 else { throw URLError(.dataLengthExceedsMaximum) }
        data.append(byte)
      }
      let downloaded = data
      let count = try await Task.detached(priority: .utility) {
        try HostsSnapshot.validate(downloaded).count
      }.value
      let target = try SharedStore.directory().appendingPathComponent("hosts")
      try data.write(
        to: target, options: [.atomic, .completeFileProtectionUntilFirstUserAuthentication])
      domainCount = count
      updated = Date()
      try SharedStore.write(updated, name: "updated.json")
      reloadTunnel()
    } catch {
      self.error =
        "Filterupdate fehlgeschlagen. Die bisherige Liste bleibt erhalten. \(error.localizedDescription)"
    }
  }
}
