import Foundation

#if canImport(FoundationNetworking)
  import FoundationNetworking
#endif

/// No query cache, cookies, disk storage or custom TLS trust. Never follow redirects.
// query()/stop() are called on the provider queue; delegate-owned buffers are locked.
final class DoHClient: NSObject, URLSessionDataDelegate, URLSessionTaskDelegate, @unchecked Sendable
{
  private struct Pending {
    var bytes = Data()
    let done: (Result<Data, Error>) -> Void
  }
  private let lock = NSLock()
  private var pending = [Int: Pending]()
  private lazy var session: URLSession = {
    let c = URLSessionConfiguration.ephemeral
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
  func query(_ data: Data, completion: @escaping (Result<Data, Error>) -> Void) {
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
  func stop() { session.invalidateAndCancel() }
  func urlSession(
    _ session: URLSession, task: URLSessionTask,
    willPerformHTTPRedirection response: HTTPURLResponse, newRequest request: URLRequest,
    completionHandler: @escaping (URLRequest?) -> Void
  ) { completionHandler(nil) }
  func urlSession(
    _ session: URLSession, dataTask: URLSessionDataTask, didReceive response: URLResponse,
    completionHandler: @escaping (URLSession.ResponseDisposition) -> Void
  ) {
    guard let http = response as? HTTPURLResponse, http.statusCode == 200,
      http.mimeType?.lowercased() == "application/dns-message",
      response.expectedContentLength <= 65535
    else {
      completionHandler(.cancel)
      return
    }
    completionHandler(.allow)
  }
  func urlSession(_ session: URLSession, dataTask: URLSessionDataTask, didReceive data: Data) {
    lock.lock()
    guard var item = pending[dataTask.taskIdentifier] else {
      lock.unlock()
      return
    }
    if item.bytes.count + data.count > 65535 {
      lock.unlock()
      dataTask.cancel()
      return
    }
    item.bytes.append(data)
    pending[dataTask.taskIdentifier] = item
    lock.unlock()
  }
  func urlSession(_ session: URLSession, task: URLSessionTask, didCompleteWithError error: Error?) {
    lock.lock()
    let item = pending.removeValue(forKey: task.taskIdentifier)
    lock.unlock()
    guard let item else { return }
    if let error {
      item.done(.failure(error))
    } else if item.bytes.count >= 12 {
      item.done(.success(item.bytes))
    } else {
      item.done(.failure(URLError(.badServerResponse)))
    }
  }
}
