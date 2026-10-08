import ARKit
import RealityKit
import SwiftUI
import UIKit

struct IndoorARView: UIViewRepresentable {
    @ObservedObject var navigation: NavigationViewModel
    let onViewAvailable: (ARView) -> Void

    func makeCoordinator() -> Coordinator {
        Coordinator(navigation: navigation)
    }

    func makeUIView(context: Context) -> ARView {
        let view = ARView(frame: .zero)
        let configuration = ARWorldTrackingConfiguration()
        configuration.planeDetection = [.horizontal]
        view.automaticallyConfigureSession = false
        view.session.delegate = context.coordinator
        view.session.run(configuration, options: [.resetTracking, .removeExistingAnchors])
        context.coordinator.arView = view
        onViewAvailable(view)
        return view
    }

    func updateUIView(_ view: ARView, context: Context) {
        context.coordinator.arView = view
        onViewAvailable(view)
        if context.coordinator.renderedRevision != navigation.mapRevision {
            context.coordinator.renderMap(nodes: navigation.nodes, plan: navigation.plan)
            context.coordinator.renderedRevision = navigation.mapRevision
        }
    }

    final class Coordinator: NSObject, ARSessionDelegate {
        weak var arView: ARView?
        private weak var navigation: NavigationViewModel?
        var renderedRevision = -1

        init(navigation: NavigationViewModel) {
            self.navigation = navigation
        }

        func session(_ session: ARSession, didUpdate frame: ARFrame) {
            let transform = frame.camera.transform
            let tracking = frame.camera.trackingState
            DispatchQueue.main.async { [weak self] in
                self?.navigation?.updateCamera(transform: transform, tracking: tracking)
            }
        }

        func renderMap(nodes: [NavigationNode], plan: NavigationPlan?) {
            guard let arView else { return }
            arView.scene.anchors.removeAll()

            for node in nodes {
                let anchor = AnchorEntity(world: node.position)
                let color: UIColor = node.isDoorplate ? .systemYellow : .systemTeal
                let marker = ModelEntity(
                    mesh: .generateSphere(radius: node.isDoorplate ? 0.09 : 0.055),
                    materials: [SimpleMaterial(color: color, isMetallic: false)]
                )
                anchor.addChild(marker)

                if node.isDoorplate {
                    let textMesh = MeshResource.generateText(
                        node.name,
                        extrusionDepth: 0.001,
                        font: .systemFont(ofSize: 0.08),
                        containerFrame: .zero,
                        alignment: .center,
                        lineBreakMode: .byTruncatingTail
                    )
                    let label = ModelEntity(
                        mesh: textMesh,
                        materials: [SimpleMaterial(color: .white, isMetallic: false)]
                    )
                    label.position = SIMD3<Float>(-0.15, 0.13, 0)
                    anchor.addChild(label)
                }
                arView.scene.addAnchor(anchor)
            }

            guard let plan, plan.nodes.count > 0 else { return }
            let routePoints = [plan.origin] + plan.nodes.map(\.position)
            guard routePoints.count > 1 else { return }
            for index in 0..<(routePoints.count - 1) {
                let start = routePoints[index]
                let end = routePoints[index + 1]
                let vector = end - start
                let length = simd_length(vector)
                guard length > 0.01 else { continue }
                let anchor = AnchorEntity(world: (start + end) / 2)
                let segment = ModelEntity(
                    mesh: .generateBox(size: SIMD3<Float>(0.045, 0.045, length)),
                    materials: [SimpleMaterial(color: .systemGreen, isMetallic: false)]
                )
                segment.transform.rotation = simd_quatf(
                    from: SIMD3<Float>(0, 0, 1),
                    to: simd_normalize(vector)
                )
                anchor.addChild(segment)
                arView.scene.addAnchor(anchor)
            }
        }
    }
}
