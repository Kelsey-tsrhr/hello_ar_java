import Foundation
import simd

struct RoutePlanner {
    func makePlan(from currentPosition: SIMD3<Float>, to target: NavigationNode,
                  nodes: [NavigationNode]) -> NavigationPlan? {
        let pathNodes = nodes.filter { !$0.isDoorplate }
        guard !pathNodes.isEmpty else { return nil }

        let start = pathNodes.min { distance($0.position, currentPosition) < distance($1.position, currentPosition) }
        guard let start else { return nil }

        var lookup = Dictionary(uniqueKeysWithValues: pathNodes.map { ($0.id, $0) })
        lookup[target.id] = target
        var graph: [String: Set<String>] = [:]
        pathNodes.forEach { graph[$0.id] = [] }
        graph[target.id] = []

        for edge in minimumSpanningTree(pathNodes) {
            connect(edge.0.id, edge.1.id, in: &graph)
        }
        if let nearestPath = pathNodes.min(by: {
            distance($0.position, target.position) < distance($1.position, target.position)
        }) {
            connect(target.id, nearestPath.id, in: &graph)
        }

        guard let routeIDs = aStar(start: start.id, goal: target.id, graph: graph, nodes: lookup),
              !routeIDs.isEmpty else { return nil }
        return NavigationPlan(target: target, origin: currentPosition,
                              nodes: routeIDs.compactMap { lookup[$0] })
    }

    private func minimumSpanningTree(_ nodes: [NavigationNode]) -> [(NavigationNode, NavigationNode)] {
        guard nodes.count > 1 else { return [] }
        let sortedEdges = nodes.indices.flatMap { first in
            ((first + 1)..<nodes.count).map { second in
                (nodes[first], nodes[second], distance(nodes[first].position, nodes[second].position))
            }
        }.sorted { lhs, rhs in
            if lhs.2 == rhs.2 { return lhs.0.id + lhs.1.id < rhs.0.id + rhs.1.id }
            return lhs.2 < rhs.2
        }

        var parent = Dictionary(uniqueKeysWithValues: nodes.map { ($0.id, $0.id) })
        func root(_ id: String, _ parents: inout [String: String]) -> String {
            guard let value = parents[id], value != id else { return id }
            let result = root(value, &parents)
            parents[id] = result
            return result
        }

        var result: [(NavigationNode, NavigationNode)] = []
        for edge in sortedEdges {
            let firstRoot = root(edge.0.id, &parent)
            let secondRoot = root(edge.1.id, &parent)
            guard firstRoot != secondRoot else { continue }
            parent[firstRoot] = secondRoot
            result.append((edge.0, edge.1))
            if result.count == nodes.count - 1 { break }
        }
        return result
    }

    private func aStar(start: String, goal: String, graph: [String: Set<String>],
                       nodes: [String: NavigationNode]) -> [String]? {
        guard let goalNode = nodes[goal], nodes[start] != nil else { return nil }
        var frontier: [(id: String, score: Float)] = [(start, 0)]
        var cameFrom: [String: String] = [:]
        var cost: [String: Float] = [start: 0]

        while !frontier.isEmpty {
            frontier.sort { $0.score > $1.score }
            let current = frontier.removeLast().id
            if current == goal {
                var path = [current]
                var cursor = current
                while let previous = cameFrom[cursor] {
                    path.append(previous)
                    cursor = previous
                }
                return path.reversed()
            }

            for neighbor in graph[current, default: []].sorted() {
                guard let currentNode = nodes[current], let neighborNode = nodes[neighbor] else { continue }
                let candidateCost = cost[current, default: .infinity]
                    + distance(currentNode.position, neighborNode.position)
                if candidateCost < cost[neighbor, default: .infinity] {
                    cameFrom[neighbor] = current
                    cost[neighbor] = candidateCost
                    let estimate = candidateCost + distance(neighborNode.position, goalNode.position)
                    frontier.append((neighbor, estimate))
                }
            }
        }
        return nil
    }

    private func connect(_ first: String, _ second: String, in graph: inout [String: Set<String>]) {
        graph[first, default: []].insert(second)
        graph[second, default: []].insert(first)
    }

    private func distance(_ first: SIMD3<Float>, _ second: SIMD3<Float>) -> Float {
        simd_distance(first, second)
    }
}
