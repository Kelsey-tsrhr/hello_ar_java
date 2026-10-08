import Foundation

struct LocalizationClient {
    private let baseURL: URL?
    private let session: URLSession

    init(baseURLString: String?, session: URLSession = .shared) {
        if let value = baseURLString?.trimmingCharacters(in: .whitespacesAndNewlines),
           let url = URL(string: value),
           let scheme = url.scheme?.lowercased(),
           let host = url.host?.lowercased(),
           scheme == "https",
           !host.hasSuffix(".example") {
            self.baseURL = url
        } else {
            self.baseURL = nil
        }
        self.session = session
    }

    func localize(jpeg: Data, sceneID: Int) async throws -> [RelativeMapNode] {
        guard let baseURL else { throw LocalizationError.invalidServerAddress }
        let endpoint = baseURL
            .appendingPathComponent("api")
            .appendingPathComponent("v1")
            .appendingPathComponent("localization")
            .appendingPathComponent("query_relative")
            .appendingPathComponent(String(max(1, sceneID)))

        let boundary = "IndoorNav-\(UUID().uuidString)"
        var request = URLRequest(url: endpoint)
        request.httpMethod = "POST"
        request.timeoutInterval = 30
        request.setValue("multipart/form-data; boundary=\(boundary)", forHTTPHeaderField: "Content-Type")
        request.httpBody = multipartBody(jpeg: jpeg, boundary: boundary)

        let (data, response) = try await session.data(for: request)
        guard let httpResponse = response as? HTTPURLResponse else {
            throw LocalizationError.serverRejected("服务器响应无效。")
        }
        guard (200..<300).contains(httpResponse.statusCode) else {
            throw LocalizationError.serverRejected("定位服务返回 HTTP \(httpResponse.statusCode)。")
        }

        let result = try JSONDecoder().decode(RelativeLocalizationResponse.self, from: data)
        guard result.code == 200 else {
            throw LocalizationError.serverRejected(result.message ?? "定位服务拒绝了请求。")
        }
        guard let nodes = result.data?.relativeMap, !nodes.isEmpty else {
            throw LocalizationError.emptyMap
        }
        return nodes
    }

    private func multipartBody(jpeg: Data, boundary: String) -> Data {
        var body = Data()
        body.appendUTF8("--\(boundary)\r\n")
        body.appendUTF8("Content-Disposition: form-data; name=\"file\"; filename=\"query.jpg\"\r\n")
        body.appendUTF8("Content-Type: image/jpeg\r\n\r\n")
        body.append(jpeg)
        body.appendUTF8("\r\n--\(boundary)--\r\n")
        return body
    }
}

private extension Data {
    mutating func appendUTF8(_ string: String) {
        append(contentsOf: string.utf8)
    }
}
