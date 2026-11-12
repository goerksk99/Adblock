import Foundation

/// Bounded DNS-over-TCP endpoint. Call all methods and resolver completions on one serial queue.
public final class TCPDNS {
  private final class Flow {
    var packet: DNSPacket, receive: UInt32, send: UInt32
    var ready = false, input = [UInt8](), output = [UInt8]()
    var flight: Data?, flightEnd: UInt32 = 0, retries = 0, inProgress = 0
    var touched: Date, sent: Date, peerClosed = false, finSent = false
    var mss = 512, window = 0
    init(_ p: DNSPacket, now: Date) {
      packet = p
      receive = read32(p.segment, 4) &+ 1
      send = UInt32.random(in: 1...UInt32.max - 1)
      touched = now
      sent = now
      window = Int(read16(p.segment, 14))
      var i = 20
      let end = Int(p.segment[12] >> 4) * 4
      while i < end {
        let kind = p.segment[i]
        if kind == 0 { break }
        if kind == 1 {
          i += 1
          continue
        }
        guard i + 1 < end else { break }
        let n = Int(p.segment[i + 1])
        guard n >= 2, i + n <= end else { break }
        if kind == 2 && n == 4 { mss = max(1, min(1200, Int(read16(p.segment, i + 2)))) }
        i += n
      }
    }
  }
  private var flows = [String: Flow]()
  private let write: (Data, Int) -> Void
  private let resolve: (Data, @escaping (Data) -> Void) -> Void
  public init(
    write: @escaping (Data, Int) -> Void,
    resolve: @escaping (Data, @escaping (Data) -> Void) -> Void
  ) {
    self.write = write
    self.resolve = resolve
  }
  public func receive(_ p: DNSPacket, now: Date = Date()) {
    guard p.proto == 6 else { return }
    let flags = p.segment[13]
    let key = p.key
    if flags & 4 != 0 {
      flows.removeValue(forKey: key)
      return
    }
    if flags & 2 != 0 {
      if let f = flows[key] {
        if !f.ready, read32(p.segment, 4) &+ 1 == f.receive, let packet = f.flight {
          write(packet, p.version)
        }
        return
      }
      guard flows.count < 32, flags & 0x10 == 0 else { return }
      let f = Flow(p, now: now)
      flows[key] = f
      emit(f, flags: 0x12, bytes: [], advance: 1, now: now)
      return
    }
    guard let f = flows[key], flags & 0x10 != 0 else { return }
    f.touched = now
    f.packet = p
    f.window = Int(read16(p.segment, 14))
    let ack = read32(p.segment, 8)
    if f.flight != nil, ack == f.flightEnd {
      f.flight = nil
      f.send = ack
      f.ready = true
      f.retries = 0
      if f.finSent {
        flows.removeValue(forKey: key)
        return
      }
    } else if ack != f.send {
      return
    }
    guard f.ready else { return }
    let payload = [UInt8](p.payload)
    if !payload.isEmpty || flags & 1 != 0 {
      guard read32(p.segment, 4) == f.receive, !f.peerClosed else {
        acknowledge(f)
        return
      }
      guard f.input.count + payload.count <= 65537 else {
        reset(key)
        return
      }
      f.input += payload
      f.receive &+= UInt32(payload.count)
      if flags & 1 != 0 {
        f.receive &+= 1
        f.peerClosed = true
      }
      acknowledge(f)
      while f.input.count >= 2 {
        let n = Int(read16(f.input, 0))
        guard n >= 17 else {
          reset(key)
          return
        }
        if f.input.count < n + 2 { break }
        guard f.inProgress < 4 else {
          reset(key)
          return
        }
        let query = Data(f.input[2..<n + 2])
        f.input.removeFirst(n + 2)
        f.inProgress += 1
        resolve(query) { [weak self, weak f] data in
          guard let self, let f, self.flows[key] === f else { return }
          f.inProgress -= 1
          guard data.count <= 65535, f.output.count + data.count + 2 <= 131074 else {
            self.reset(key)
            return
          }
          f.output += [UInt8(data.count >> 8), UInt8(data.count & 255)] + data
          self.pump(f, now: Date())
        }
      }
      if f.peerClosed && !f.input.isEmpty {
        reset(key)
        return
      }
    }
    pump(f, now: now)
  }
  private func acknowledge(_ f: Flow) {
    write(
      f.packet.tcpReply(
        sequence: f.flight == nil ? f.send : f.flightEnd, ack: f.receive, flags: 0x10),
      f.packet.version)
  }
  private func emit(_ f: Flow, flags: UInt8, bytes: [UInt8], advance: Int, now: Date) {
    let packet = f.packet.tcpReply(sequence: f.send, ack: f.receive, flags: flags, payload: bytes)
    f.flight = packet
    f.flightEnd = f.send &+ UInt32(advance)
    f.sent = now
    f.retries = 0
    write(packet, f.packet.version)
  }
  private func pump(_ f: Flow, now: Date) {
    guard f.ready, f.flight == nil, !f.finSent else { return }
    if !f.output.isEmpty && f.window > 0 {
      let n = min(f.mss, f.window, f.output.count)
      let part = Array(f.output.prefix(n))
      f.output.removeFirst(n)
      emit(f, flags: 0x18, bytes: part, advance: n, now: now)
    } else if f.peerClosed && f.inProgress == 0 && f.output.isEmpty {
      f.finSent = true
      emit(f, flags: 0x11, bytes: [], advance: 1, now: now)
    }
  }
  private func reset(_ key: String) {
    if let f = flows.removeValue(forKey: key) {
      write(f.packet.tcpReply(sequence: f.send, ack: f.receive, flags: 0x14), f.packet.version)
    }
  }
  public func tick(now: Date = Date()) {
    for (key, f) in Array(flows) {
      if now.timeIntervalSince(f.touched) > 30 {
        reset(key)
        continue
      }
      if let packet = f.flight, now.timeIntervalSince(f.sent) > 1 {
        if f.retries >= 4 {
          reset(key)
        } else {
          f.retries += 1
          f.sent = now
          write(packet, f.packet.version)
        }
      }
    }
  }
  public func stop() { flows.removeAll() }
}
