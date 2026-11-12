import SwiftUI

@main struct AdShieldApp: App {
  @StateObject private var model = ShieldModel()
  var body: some Scene {
    WindowGroup { ShieldTabs().environmentObject(model).preferredColorScheme(.dark).tint(.mint) }
  }
}
struct ShieldTabs: View {
  @EnvironmentObject var model: ShieldModel
  var body: some View {
    TabView {
      Overview().tabItem { Label("Übersicht", systemImage: "shield.lefthalf.filled") }
      Statistics().tabItem { Label("Statistiken", systemImage: "chart.bar") }
      RulesView().tabItem { Label("Ausnahmen", systemImage: "list.bullet") }
      SettingsView().tabItem { Label("Einstellungen", systemImage: "gearshape") }
    }
    .alert(
      "AdShield",
      isPresented: Binding(get: { model.error != nil }, set: { if !$0 { model.error = nil } })
    ) {
      Button("OK", role: .cancel) { model.error = nil }
    } message: {
      Text(model.error ?? "")
    }
  }
}
struct Overview: View {
  @EnvironmentObject var model: ShieldModel
  var body: some View {
    NavigationStack {
      ScrollView {
        VStack(alignment: .leading, spacing: 24) {
          Text("Dein Internet. Ruhiger.").font(.largeTitle.bold())
          Text("Werbe- und Trackingdomains lokal filtern – in Apps und Browsern.").foregroundStyle(
            .secondary)
          Button {
            Task { await model.toggle() }
          } label: {
            VStack(spacing: 12) {
              Image(systemName: model.connected ? "checkmark.shield.fill" : "power").font(
                .system(size: 64))
              Text(model.connected ? "Deaktivieren" : "Aktivieren").font(.headline)
            }
            .frame(width: 240, height: 240)
            .background(
              model.connected ? Color.mint.opacity(0.2) : Color.gray.opacity(0.2), in: Circle()
            )
            .frame(maxWidth: .infinity)
          }.buttonStyle(.plain).disabled(model.busy || model.status == .disconnecting)
          Text(model.statusText).font(.headline).frame(maxWidth: .infinity)
          HStack {
            metric("Blockiert heute", value: "\(model.statistics.blocked)")
            metric("Filterdomains", value: model.domainCount.formatted())
          }
          Text(
            "AdShield nutzt die VPN-Freigabe für einen lokalen DNS-Tunnel. Du brauchst keinen externen VPN-Anbieter."
          ).font(.footnote).foregroundStyle(.secondary)
        }.padding(24)
      }.navigationTitle("AdShield")
    }
  }
  func metric(_ label: String, value: String) -> some View {
    VStack(alignment: .leading, spacing: 8) {
      Text(value).font(.title.bold())
      Text(label).font(.caption).foregroundStyle(.secondary)
    }
    .frame(maxWidth: .infinity, alignment: .leading).padding().background(
      .quaternary, in: RoundedRectangle(cornerRadius: 20))
  }
}
struct Statistics: View {
  @EnvironmentObject var model: ShieldModel
  var body: some View {
    NavigationStack {
      List {
        Section("Heute · \(model.statistics.day)") {
          LabeledContent("DNS-Anfragen", value: "\(model.statistics.queries)")
          LabeledContent("Blockiert", value: "\(model.statistics.blocked)")
          LabeledContent("Fehlgeschlagen", value: "\(model.statistics.failures)")
          LabeledContent(
            "Blockierungsquote",
            value:
              "\(model.statistics.queries == 0 ? 0 : model.statistics.blocked * 100 / model.statistics.queries) %"
          )
        }
        Section {
          Text(
            "Statistiken bleiben auf deinem iPhone. Es werden keine angefragten Domains protokolliert. Die Zähler werden nach lokaler Tageszeit zurückgesetzt."
          ).foregroundStyle(.secondary)
        }
      }.navigationTitle("Statistiken")
    }
  }
}
struct RulesView: View {
  @EnvironmentObject var model: ShieldModel
  @State private var allow = true
  @State private var domain = ""
  var body: some View {
    NavigationStack {
      List {
        Section {
          Picker("Regelliste", selection: $allow) {
            Text("Erlauben").tag(true)
            Text("Blockieren").tag(false)
          }.pickerStyle(.segmented)
          TextField("example.org", text: $domain).textInputAutocapitalization(.never)
            .autocorrectionDisabled().keyboardType(.URL)
          Button("Domain hinzufügen") { if model.add(domain, allow: allow) { domain = "" } }
            .disabled(domain.trimmingCharacters(in: .whitespaces).isEmpty)
        } footer: {
          Text(
            "Eine Regel gilt auch für Subdomains. Erlaubte Domains haben Vorrang. Internationale Domains bitte als Punycode eingeben."
          )
        }
        Section(allow ? "Erlaubte Domains" : "Eigene Blockierungen") {
          ForEach(allow ? model.configuration.allow : model.configuration.deny, id: \.self) {
            Text($0)
          }
          .onDelete { offsets in
            if allow {
              model.configuration.allow.remove(atOffsets: offsets)
            } else {
              model.configuration.deny.remove(atOffsets: offsets)
            }
            model.save()
          }
        }
      }.navigationTitle("Ausnahmen").toolbar { EditButton() }
    }
  }
}
struct SettingsView: View {
  @EnvironmentObject var model: ShieldModel
  var body: some View {
    NavigationStack {
      Form {
        Section("DNS-Filter") {
          Toggle(
            "Werbe- und Trackingfilter",
            isOn: Binding(
              get: { model.configuration.enabled },
              set: {
                model.configuration.enabled = $0
                model.save()
              }))
          LabeledContent("Resolver", value: "Quad9 · DNS über HTTPS")
          Button {
            Task { await model.updateHosts() }
          } label: {
            HStack {
              Text("Filterliste aktualisieren")
              if model.updating { ProgressView().padding(.leading) }
            }
          }.disabled(model.updating)
          if let date = model.updated {
            LabeledContent(
              "Aktualisiert", value: date.formatted(date: .abbreviated, time: .shortened))
          }
        }
        Section("Schutzumfang") {
          Text(
            "Systemweiter DNS-Schutz für Apps und Browser, die das iOS-DNS verwenden. Apps mit eigenem DNS oder festen IP-Adressen können ihn umgehen. Werbung von derselben Domain wie Inhalte lässt sich nicht zuverlässig trennen."
          )
          Text(
            "iOS erlaubt in privaten Apps keine allgemeine Liste anderer installierter Apps und keine freien Per-App-VPN-Ausnahmen. Ein weiteres VPN kann mit AdShield kollidieren."
          )
        }
        Section("Datenschutz") {
          Text(
            "Kein Konto, keine Telemetrie, kein eigener VPN-Server. Quad9 verarbeitet die weitergeleiteten DNS-Anfragen. Filterupdates werden von GitHub geladen."
          )
          Link("Quad9-Datenschutz", destination: URL(string: "https://quad9.net/privacy/policy/")!)
        }
        Section("Über AdShield") {
          LabeledContent("iOS-Version", value: "0.1.0 · Entwicklung")
          Text(
            "Filter: StevenBlack/hosts. Lizenzen und Hinweise liegen im Projekt und in den App-Ressourcen."
          )
          Link(
            "StevenBlack/hosts", destination: URL(string: "https://github.com/StevenBlack/hosts")!)
          NavigationLink("Filterlizenz") { LicenseView() }
        }
      }.navigationTitle("Einstellungen")
    }
  }
}
struct LicenseView: View {
  private var license: String {
    guard let url = Bundle.main.url(forResource: "STEVENBLACK-LICENSE", withExtension: "txt"),
      let text = try? String(contentsOf: url, encoding: .utf8)
    else { return "Lizenz im Quellprojekt verfügbar." }
    return text
  }
  var body: some View {
    ScrollView { Text(license).font(.footnote.monospaced()).padding() }.navigationTitle(
      "Filterlizenz")
  }
}
