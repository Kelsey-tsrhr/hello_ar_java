import AVFoundation
import ARKit
import Combine
import CoreImage
import Foundation
import RealityKit
import simd
import UIKit

@MainActor
final class NavigationViewModel: ObservableObject {
    @Published private(set) var status = "将相机对准室内环境，然后开始定位。"
    @Published private(set) var nodes: [NavigationNode] = []
    @Published private(set) var plan: NavigationPlan?
    @Published private(set) var mapRevision = 0
    @Published private(set) var isLocalizing = false
    @Published private(set) var isNavigating = false
    @Published var isDestinationPickerPresented = false
    @Published var isSettingsPresented = false
    @Published var sceneID = String(max(1, UserDefaults.standard.integer(forKey: "currentSceneID")))

    private let planner = RoutePlanner()
    private let imageContext = CIContext()
    private let speechSynthesizer = AVSpeechSynthesizer()
    private let localizationClient: LocalizationClient
    private var currentCameraTransform: simd_float4x4?
    private var waypointIndex = 0
    private var announcedTurnIndex: Int?

    init() {
        let server = Bundle.main.object(forInfoDictionaryKey: "LOCALIZATION_BASE_URL") as? String
        localizationClient = LocalizationClient(baseURLString: server)
    }

    func localize(using arView: ARView) {
        guard !isLocalizing else { return }
        guard let frame = arView.session.currentFrame else {
            status = "AR 相机尚未准备好，请稍等后重试。"
            return
        }
        guard let jpeg = imageContext.jpegRepresentation(
            of: CIImage(cvPixelBuffer: frame.capturedImage),
            colorSpace: CGColorSpaceCreateDeviceRGB(),
            options: [.lossyCompressionQuality: 0.82]
        ) else {
            status = "无法读取当前相机画面。"
            return
        }

        let captureTransform = frame.camera.transform
        let requestedScene = max(1, Int(sceneID) ?? 1)
        isLocalizing = true
        status = "正在上传画面并定位场景 \(requestedScene)…"

        Task {
            defer { isLocalizing = false }
            do {
                let relativeNodes = try await localizationClient.localize(jpeg: jpeg, sceneID: requestedScene)
                let mappedNodes = makeWorldNodes(relativeNodes, cameraTransform: captureTransform)
                guard !mappedNodes.isEmpty else { throw LocalizationError.emptyMap }
                nodes = mappedNodes
                plan = nil
                isNavigating = false
                mapRevision += 1
                status = "定位成功。找到 \(mappedNodes.filter(\.isDoorplate).count) 个门牌，请选择目的地。"
                speak("定位成功，请选择目的地。")
            } catch {
                status = error.localizedDescription
            }
        }
    }

    func updateCamera(transform: simd_float4x4, tracking: ARCamera.TrackingState) {
        currentCameraTransform = transform
        guard isNavigating, tracking == .normal, let plan else { return }
        updateNavigationProgress(position: translation(of: transform), plan: plan)
    }

    func beginNavigation(to target: NavigationNode) {
        guard let currentPosition = currentCameraTransform.map(translation(of:)) else {
            status = "相机跟踪尚未稳定，请缓慢移动手机后重试。"
            return
        }
        guard let newPlan = planner.makePlan(from: currentPosition, to: target, nodes: nodes) else {
            status = "没有找到通往 \(target.name) 的路线。"
            return
        }
        plan = newPlan
        waypointIndex = 0
        announcedTurnIndex = nil
        isNavigating = true
        isDestinationPickerPresented = false
        mapRevision += 1
        status = "开始导航到 \(target.name)。"
        speak("开始导航到\(target.name)。")
    }

    func stopNavigation() {
        isNavigating = false
        plan = nil
        announcedTurnIndex = nil
        speechSynthesizer.stopSpeaking(at: .immediate)
        mapRevision += 1
        status = nodes.isEmpty ? "将相机对准室内环境，然后开始定位。" : "导航已停止。"
    }

    func saveSceneID() {
        let value = max(1, Int(sceneID) ?? 1)
        sceneID = String(value)
        UserDefaults.standard.set(value, forKey: "currentSceneID")
        status = "当前场景编号：\(value)"
    }

    private func makeWorldNodes(_ relativeNodes: [RelativeMapNode],
                                cameraTransform: simd_float4x4) -> [NavigationNode] {
        let cameraPosition = translation(of: cameraTransform)
        let forward = -cameraTransform.columns.2.xyz
        let yaw = atan2(-forward.x, -forward.z)
        let flatRotation = simd_quatf(angle: yaw, axis: SIMD3<Float>(0, 1, 0))
        var flatCameraTransform = matrix_float4x4(flatRotation)
        flatCameraTransform.columns.3 = SIMD4<Float>(cameraPosition.x, cameraPosition.y, cameraPosition.z, 1)

        return relativeNodes.compactMap { relative in
            guard relative.position.count >= 3 else { return nil }
            let relativePosition = SIMD4<Float>(relative.position[0], relative.position[1], relative.position[2], 1)
            let projected = flatCameraTransform * relativePosition
            let name = relative.name?.trimmingCharacters(in: .whitespacesAndNewlines)
            let visibleName = name.flatMap { $0.isEmpty ? nil : $0 } ?? "节点 \(relative.imageID)"
            let nodeID = "\(relative.imageID):\(visibleName)"
            return NavigationNode(
                id: nodeID,
                name: visibleName,
                type: relative.type ?? "camera",
                position: SIMD3<Float>(projected.x, cameraPosition.y - 1.3, projected.z)
            )
        }
    }

    private func updateNavigationProgress(position: SIMD3<Float>, plan: NavigationPlan) {
        let destinationDistance = horizontalDistance(position, plan.target.position)
        if destinationDistance <= 1.5 {
            isNavigating = false
            status = "已到达目的地：\(plan.target.name)"
            speak("已到达目的地。")
            return
        }

        guard plan.nodes.count > 1 else {
            status = "前往 \(plan.target.name)，剩余约 \(Int(destinationDistance.rounded())) 米。"
            return
        }

        if waypointIndex < plan.nodes.count - 1 {
            let nextDistance = horizontalDistance(position, plan.nodes[waypointIndex].position)
            if nextDistance <= 1.2 {
                waypointIndex += 1
                if let direction = turnDirection(at: waypointIndex, nodes: plan.nodes) {
                    announcedTurnIndex = waypointIndex
                    status = direction == .left ? "左转。" : "右转。"
                    speak(direction == .left ? "左转。" : "右转。")
                }
            }
        }

        if let turn = upcomingTurn(from: waypointIndex, nodes: plan.nodes), turn.index != announcedTurnIndex {
            let distance = horizontalDistance(position, plan.nodes[turn.index].position)
            if distance <= 8 {
                announcedTurnIndex = turn.index
                let side = turn.direction == .left ? "左" : "右"
                status = String(format: "前方约 %.0f 米%@转。", distance, side)
                speak("前方约\(Int(distance.rounded()))米，\(side)转。")
                return
            }
        }

        status = "前往 \(plan.target.name)，剩余约 \(Int(destinationDistance.rounded())) 米。"
    }

    private enum TurnDirection { case left, right }

    private func turnDirection(at index: Int, nodes: [NavigationNode]) -> TurnDirection? {
        guard index > 0, index + 1 < nodes.count else { return nil }
        let incoming = nodes[index].position - nodes[index - 1].position
        let outgoing = nodes[index + 1].position - nodes[index].position
        let inLength = simd_length(SIMD2<Float>(incoming.x, incoming.z))
        let outLength = simd_length(SIMD2<Float>(outgoing.x, outgoing.z))
        guard inLength > 0.01, outLength > 0.01 else { return nil }
        let a = SIMD2<Float>(incoming.x / inLength, incoming.z / inLength)
        let b = SIMD2<Float>(outgoing.x / outLength, outgoing.z / outLength)
        let dot = max(-1, min(1, simd_dot(a, b)))
        guard acos(dot) * 180 / .pi >= 42 else { return nil }
        return a.x * b.y - a.y * b.x > 0 ? .right : .left
    }

    private func upcomingTurn(from index: Int, nodes: [NavigationNode]) -> (index: Int, direction: TurnDirection)? {
        guard index + 1 < nodes.count else { return nil }
        for turnIndex in (max(1, index + 1))..<nodes.count {
            if let direction = turnDirection(at: turnIndex, nodes: nodes) {
                return (turnIndex, direction)
            }
        }
        return nil
    }

    private func speak(_ text: String) {
        let utterance = AVSpeechUtterance(string: text)
        utterance.voice = AVSpeechSynthesisVoice(language: "zh-CN")
        utterance.rate = AVSpeechUtteranceDefaultSpeechRate
        speechSynthesizer.speak(utterance)
    }

    private func translation(of transform: simd_float4x4) -> SIMD3<Float> {
        SIMD3<Float>(transform.columns.3.x, transform.columns.3.y, transform.columns.3.z)
    }

    private func horizontalDistance(_ first: SIMD3<Float>, _ second: SIMD3<Float>) -> Float {
        simd_distance(SIMD2<Float>(first.x, first.z), SIMD2<Float>(second.x, second.z))
    }
}

private extension SIMD4 where Scalar == Float {
    var xyz: SIMD3<Float> { SIMD3<Float>(x, y, z) }
}
