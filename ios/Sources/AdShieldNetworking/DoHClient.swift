import Foundation

#if canImport(FoundationNetworking)
  import FoundationNetworking
#endif

public enum DoHError: LocalizedError {
  case http(Int)
  case invalidContent, oversized, invalidResponse
  public var errorDescription: String? {
    switch self {
    case .http(let code): return "Der DNS-Server antwortet mit HTTP \(code)."
    case .invalidContent: return "Der DNS-Server liefert keine DNS-Nachricht."
    case .oversized: return "Die DNS-Antwort überschreitet die erlaubte Größe."
    case .invalidResponse: return "Die DNS-Antwort ist ungültig."
    }
  }
}

/// No query cache, cookies, disk storage or custom TLS trust. Never follow redirects.
// query()/stop() are called on the provider queue; delegate-owned buffers are locked.
public final class DoHClient: NSObject, URLSessionDataDelegate, URLSessionTaskDelegate, @unchecked
  Sendable
{
  private struct Pending {
    var bytes = Data()
    var accepted = false
    var failure: Error?
    let done: (Result<Data, Error>) -> Void
  }
  private let lock = NSLock()
  private var pending = [Int: Pending]()
  private let configuration: URLSessionConfiguration
  public init(configuration: URLSessionConfiguration = .ephemeral) {
    self.configuration = configuration
    super.init()
  }
  private lazy var session: URLSession = {
    let c = configuration
    c.urlCache = nil
    c.httpCookieStorage = nil
    c.urlCredentialStorage = nil
    c.requestCachePolicy = .reloadIgnoringLocalCacheData
    c.timeoutIntervalForRequest = 6
    c.timeoutIntervalForResource = 8
    c.httpMaximumConnectionsPerHost = 4
    let queue = OperationQueue()
    queue.maxConcurrentOperationCount = 1
    return URLSession(configuration: c, delegate: self, delegateQueue: queue)
  }()
  public func query(_ data: Data, completion: @escaping (Result<Data, Error>) -> Void) {
    var r = URLRequest(url: URL(string: "https://dns.quad9.net/dns-query")!)
    r.httpMethod = "POST"
    r.httpBody = data
    r.setValue("application/dns-message", forHTTPHeaderField: "Content-Type")
    r.setValue("application/dns-message", forHTTPHeaderField: "Accept")
    let task = session.dataTask(with: r)
    lock.lock()
    pending[task.taskIdentifier] = Pending(done: completion)
    lock.unlock()
    task.resume()
  }
  public func stop() { session.invalidateAndCancel() }
  public func urlSession(
    _ session: URLSession, task: URLSessionTask,
    willPerformHTTPRedirection response: HTTPURLResponse, newRequest request: URLRequest,
    completionHandler: @escaping (URLRequest?) -> Void
  ) { completionHandler(nil) }
  public func urlSession(
    _ session: URLSession, dataTask: URLSessionDataTask, didReceive response: URLResponse,
    completionHandler: @escaping (URLSession.ResponseDisposition) -> Void
  ) {
    let failure: Error?
    if let http = response as? HTTPURLResponse {
      if http.statusCode != 200 {
        failure = DoHError.http(http.statusCode)
      } else if http.mimeType?.lowercased() != "application/dns-message" {
        failure = DoHError.invalidContent
      } else if response.expectedContentLength > 65535 {
        failure = DoHError.oversized
      } else {
        failure = nil
      }
    } else {
      failure = DoHError.invalidResponse
    }
    lock.lock()
    if var item = pending[dataTask.taskIdentifier] {
      item.failure = failure
      item.accepted = failure == nil
      pending[dataTask.taskIdentifier] = item
    }
    lock.unlock()
    if failure != nil {
      completionHandler(.cancel)
      return
    }
    completionHandler(.allow)
  }
  public func urlSession(_ session: URLSession, dataTask: URLSessionDataTask, didReceive data: Data)
  {
    lock.lock()
    guard var item = pending[dataTask.taskIdentifier] else {
      lock.unlock()
      return
    }
    guard item.accepted, item.failure == nil else {
      lock.unlock()
      return
    }
    if item.bytes.count + data.count > 65535 {
      item.failure = DoHError.oversized
      pending[dataTask.taskIdentifier] = item
      lock.unlock()
      dataTask.cancel()
      return
    }
    item.bytes.append(data)
    pending[dataTask.taskIdentifier] = item
    lock.unlock()
  }
  public func urlSession(
    _ session: URLSession, task: URLSessionTask, didCompleteWithError error: Error?
  ) {
    lock.lock()
    let item = pending.removeValue(forKey: task.taskIdentifier)
    lock.unlock()
    guard let item else { return }
    if let failure = item.failure {
      item.done(.failure(failure))
    } else if let error {
      item.done(.failure(error))
    } else if item.accepted && item.bytes.count >= 12 {
      item.done(.success(item.bytes))
    } else {
      item.done(.failure(URLError(.badServerResponse)))
    }
  }
}
