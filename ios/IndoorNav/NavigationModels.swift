import Foundation
import simd

struct RelativeLocalizationResponse: Decodable {
    let code: Int
    let message: String?
    let data: LocalizationData?
}

struct LocalizationData: Decodable {
    let relativeMap: [RelativeMapNode]?

    enum CodingKeys: String, CodingKey {
        case relativeMap = "relative_map"
    }
}

struct RelativeMapNode: Decodable {
    let imageID: Int
    let name: String?
    let position: [Float]
    let type: String?

    enum CodingKeys: String, CodingKey {
        case imageID = "image_id"
        case name
        case position
        case type
    }
}

struct NavigationNode: Identifiable, Hashable {
    let id: String
    let name: String
    let type: String
    let position: SIMD3<Float>

    var isDoorplate: Bool { type.lowercased() == "doorplate" }
}

struct NavigationPlan {
    let target: NavigationNode
    let origin: SIMD3<Float>
    let nodes: [NavigationNode]
}

enum LocalizationError: LocalizedError {
    case invalidServerAddress
    case serverRejected(String)
    case emptyMap

    var errorDescription: String? {
        switch self {
        case .invalidServerAddress:
            return "请先在 Xcode 的 Backend.xcconfig 中填写可访问的 HTTPS 后端地址。"
        case .serverRejected(let message):
            return message.isEmpty ? "服务器未能定位当前场景。" : message
        case .emptyMap:
            return "服务器没有返回可用的地图节点。"
        }
    }
}
