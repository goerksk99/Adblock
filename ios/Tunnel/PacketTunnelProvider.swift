import AdShieldCore
import Darwin
import Foundation
import NetworkExtension

final class PacketTunnelProvider: NEPacketTunnelProvider {
  private let queue = DispatchQueue(label: "de.adshield.dns")
  private var client: DoHClient?
  private var rules = FilterRules(hosts: [])
  private var configuration = ShieldConfiguration()
  private var statistics = ShieldStatistics()
  private var generation = UUID()
  private var startupCompletion: ((Error?) -> Void)?
  private var active = false
  private var pending = 0
  private var timer: DispatchSourceTimer?
  private var lastSave = Date.distantPast
  private lazy var tcp = TCPDNS(
    write: { [weak self] data, version in self?.write(data, version: version) },
    resolve: { [weak self] data, done in self?.resolve(data, completion: done) })

  override func startTunnel(
    options: [String: NSObject]?, completionHandler: @escaping (Error?) -> Void
  ) {
    queue.async {
      let token = UUID()
      self.generation = token
      self.startupCompletion = completionHandler
      self.pending = 0
      do {
        self.rules = try SharedStore.rules()
        self.configuration = SharedStore.read("configuration.json", fallback: ShieldConfiguration())
        self.statistics = SharedStore.read("statistics.json", fallback: ShieldStatistics())
        self.client = DoHClient()
      } catch {
        self.finishStartup(error)
        return
      }
      let settings = NEPacketTunnelNetworkSettings(tunnelRemoteAddress: "127.0.0.1")
      let v4 = NEIPv4Settings(addresses: ["10.111.0.1"], subnetMasks: ["255.255.255.255"])
      v4.includedRoutes = [
        NEIPv4Route(destinationAddress: "10.111.0.2", subnetMask: "255.255.255.255")
      ]
      let v6 = NEIPv6Settings(addresses: ["fdad:51e1::1"], networkPrefixLengths: [128])
      v6.includedRoutes = [
        NEIPv6Route(destinationAddress: "fdad:51e1::2", networkPrefixLength: 128)
      ]
      let dns = NEDNSSettings(servers: ["10.111.0.2", "fdad:51e1::2"])
      dns.matchDomains = [""]
      dns.matchDomainsNoSearch = true
      settings.ipv4Settings = v4
      settings.ipv6Settings = v6
      settings.dnsSettings = dns
      settings.mtu = 1280
      self.setTunnelNetworkSettings(settings) { error in
        self.queue.async {
          guard self.generation == token else { return }
          if let error {
            self.client?.stop()
            self.client = nil
            self.finishStartup(error)
            return
          }
          // A real encrypted resolver request must work before the UI reports Connected.
          let probe = Data([
            0xAD, 0x51, 1, 0, 0, 1, 0, 0, 0, 0, 0, 0, 3, 100, 110, 115, 5, 113, 117, 97, 100, 57, 3,
            110, 101, 116, 0, 0, 1, 0, 1,
          ])
          self.client?.query(probe) { result in
            self.queue.async {
              guard self.generation == token else { return }
              guard case .success(let response) = result, let query = try? DNSQuery(probe),
                query.validResponse(response), response[3] & 15 == 0
              else {
                let e = NSError(
                  domain: "AdShield", code: 3,
                  userInfo: [
                    NSLocalizedDescriptionKey:
                      "Quad9 ist über verschlüsseltes DNS nicht erreichbar. Netzwerk oder VPN-Konflikt prüfen."
                  ])
                try? SharedStore.write(e.localizedDescription, name: "last-error.json")
                self.client?.stop()
                self.client = nil
                self.setTunnelNetworkSettings(nil) { _ in
                  self.queue.async {
                    guard self.generation == token else { return }
                    self.finishStartup(e)
                  }
                }
                return
              }
              self.active = true
              try? SharedStore.write("", name: "last-error.json")
              self.readPackets()
              let timer = DispatchSource.makeTimerSource(queue: self.queue)
              timer.schedule(deadline: .now() + 1, repeating: 1)
              timer.setEventHandler { [weak self] in
                self?.tcp.tick()
                self?.saveStatistics()
              }
              self.timer = timer
              timer.resume()
              self.finishStartup(nil)
            }
          }
        }
      }
    }
  }
  private func finishStartup(_ error: Error?) {
    let completion = startupCompletion
    startupCompletion = nil
    completion?(error)
  }
  private func readPackets() {
    guard active else { return }
    let token = generation
    packetFlow.readPackets { packets, _ in
      self.queue.async {
        guard self.active, self.generation == token else { return }
        for bytes in packets {
          guard let packet = try? DNSPacket(bytes) else { continue }
          if packet.proto == 6 {
            self.tcp.receive(packet)
          } else {
            self.resolve(packet.payload) { response in
              guard self.active, let query = try? DNSQuery(packet.payload) else { return }
              // Never fragment IP: force standards-compliant TCP retry for larger DNS replies.
              let reply =
                response.count > query.udpLimit ? query.reply(code: 0, truncated: true) : response
              self.write(packet.udpReply(reply), version: packet.version)
            }
          }
        }
        self.readPackets()
      }
    }
  }
  private func write(_ data: Data, version: Int) {
    if active {
      packetFlow.writePackets(
        [data], withProtocols: [NSNumber(value: version == 4 ? AF_INET : AF_INET6)])
    }
  }
  private func resolve(_ data: Data, completion: @escaping (Data) -> Void) {
    guard active, let query = try? DNSQuery(data) else { return }
    if statistics.day != SharedStore.today() {
      statistics = ShieldStatistics(day: SharedStore.today())
    }
    statistics.queries += 1
    if configuration.enabled && rules.blocks(query.domain) {
      statistics.blocked += 1
      completion(query.reply(code: 3))
      return
    }
    guard pending < 32 else {
      statistics.failures += 1
      completion(query.reply(code: 2))
      return
    }
    pending += 1
    let token = generation
    client?.query(data) { result in
      self.queue.async {
        guard self.generation == token else { return }
        self.pending -= 1
        guard self.active else { return }
        if case .success(let response) = result, query.validResponse(response) {
          completion(response)
        } else {
          self.statistics.failures += 1
          completion(query.reply(code: 2))
        }
      }
    }
  }
  private func saveStatistics(force: Bool = false) {
    if force || Date().timeIntervalSince(lastSave) >= 3 {
      try? SharedStore.write(statistics, name: "statistics.json")
      lastSave = Date()
    }
  }
  override func stopTunnel(
    with reason: NEProviderStopReason, completionHandler: @escaping () -> Void
  ) {
    queue.async {
      self.generation = UUID()
      self.finishStartup(URLError(.cancelled))
      self.active = false
      self.timer?.cancel()
      self.timer = nil
      self.tcp.stop()
      self.client?.stop()
      self.client = nil
      self.saveStatistics(force: true)
      completionHandler()
    }
  }
  override func handleAppMessage(_ messageData: Data, completionHandler: ((Data?) -> Void)?) {
    queue.async {
      do {
        self.rules = try SharedStore.rules()
        self.configuration = SharedStore.read("configuration.json", fallback: ShieldConfiguration())
        completionHandler?(Data("ok".utf8))
      } catch { completionHandler?(Data("error".utf8)) }
    }
  }
}
