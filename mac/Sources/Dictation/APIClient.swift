import Foundation

struct DictateResponse: Decodable {
    let id: String
    let text: String
    let raw: String
    let cleaned: Bool
    let empty: Bool
}

enum DictateError: Error {
    /// Show a notification; the audio is not worth keeping (401, 413, 415).
    case fatal(String)
    /// Keep the audio and retry later (502, other 5xx, timeout, no network).
    case retryable(String)
}

struct APIClient {
    let config: Config

    func dictate(audio: Data, contentType: String) async throws -> DictateResponse {
        guard !config.token.isEmpty else { throw DictateError.fatal("No API token. Use the menu item Set API Token...") }
        guard let url = URL(string: config.baseURL + "/v1/dictate") else { throw DictateError.fatal("Bad base URL in config.json") }
        var req = URLRequest(url: url, timeoutInterval: 60)
        req.httpMethod = "POST"
        req.setValue("Bearer \(config.token)", forHTTPHeaderField: "Authorization")
        req.setValue(contentType, forHTTPHeaderField: "Content-Type")
        req.setValue("mac", forHTTPHeaderField: "X-Client")
        let data: Data, resp: URLResponse
        do { (data, resp) = try await URLSession.shared.upload(for: req, from: audio) }
        catch { throw DictateError.retryable(error.localizedDescription) }
        let status = (resp as? HTTPURLResponse)?.statusCode ?? 0
        switch status {
        case 200:
            do { return try JSONDecoder().decode(DictateResponse.self, from: data) }
            catch { throw DictateError.retryable("Unreadable response") }
        case 401, 413, 415:
            throw DictateError.fatal(Self.message(data) ?? "Request rejected (\(status))")
        default:
            throw DictateError.retryable(Self.message(data) ?? "Server error (\(status))")
        }
    }

    private static func message(_ data: Data) -> String? {
        (try? JSONSerialization.jsonObject(with: data) as? [String: Any])?["message"] as? String
    }
}
