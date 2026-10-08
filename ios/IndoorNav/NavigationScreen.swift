import SwiftUI
import RealityKit
import UIKit

struct NavigationScreen: View {
    @ObservedObject var navigation: NavigationViewModel
    @State private var arView: ARView?

    var body: some View {
        ZStack {
            IndoorARView(navigation: navigation) { view in
                guard arView !== view else { return }
                DispatchQueue.main.async { arView = view }
            }
                .ignoresSafeArea()

            VStack(spacing: 0) {
                HStack(alignment: .top, spacing: 12) {
                    VStack(alignment: .leading, spacing: 6) {
                        Text("室内 AR 导航")
                            .font(.headline.weight(.semibold))
                            .foregroundStyle(.white)
                        Text(navigation.status)
                            .font(.subheadline)
                            .foregroundStyle(.white)
                            .lineLimit(3)
                            .fixedSize(horizontal: false, vertical: true)
                            .accessibilityAddTraits(.updatesFrequently)
                    }
                    Spacer(minLength: 8)
                    Button {
                        navigation.isSettingsPresented = true
                    } label: {
                        Image(systemName: "gearshape")
                            .font(.title3)
                            .frame(width: 48, height: 48)
                    }
                    .accessibilityLabel("设置")
                    .sheet(isPresented: $navigation.isSettingsPresented) {
                        settingsSheet
                            .presentationDetents([.medium])
                    }
                }
                .padding(12)
                .background(.black.opacity(0.72), in: RoundedRectangle(cornerRadius: 10))
                .padding(.horizontal, 14)
                .padding(.top, 8)

                Spacer()

                HStack(spacing: 10) {
                    Button {
                        guard let arView else { return }
                        navigation.localize(using: arView)
                    } label: {
                        Label(navigation.isLocalizing ? "定位中" : "定位", systemImage: "scope")
                            .frame(maxWidth: .infinity, minHeight: 54)
                    }
                    .disabled(navigation.isLocalizing)
                    .buttonStyle(.borderedProminent)
                    .tint(.teal)

                    Button {
                        navigation.isDestinationPickerPresented = true
                    } label: {
                        Label("目的地", systemImage: "mappin.and.ellipse")
                            .frame(maxWidth: .infinity, minHeight: 54)
                    }
                    .disabled(navigation.nodes.filter(\.isDoorplate).isEmpty || navigation.isNavigating)
                    .buttonStyle(.borderedProminent)
                    .tint(.yellow)

                    if navigation.isNavigating {
                        Button {
                            navigation.stopNavigation()
                        } label: {
                            Label("停止", systemImage: "stop.fill")
                                .frame(maxWidth: .infinity, minHeight: 54)
                        }
                        .buttonStyle(.borderedProminent)
                        .tint(.red)
                    }
                }
                .labelStyle(.titleAndIcon)
                .font(.subheadline.weight(.semibold))
                .padding(.horizontal, 14)
                .padding(.bottom, 10)
            }
        }
        .sheet(isPresented: $navigation.isDestinationPickerPresented) {
            destinationPicker
                .presentationDetents([.medium, .large])
        }
        .onAppear { UIApplication.shared.isIdleTimerDisabled = true }
        .onDisappear { UIApplication.shared.isIdleTimerDisabled = false }
    }

    private var destinationPicker: some View {
        NavigationStack {
            List(navigation.nodes.filter(\.isDoorplate).sorted {
                $0.name.localizedStandardCompare($1.name) == .orderedAscending
            }) { destination in
                Button {
                    navigation.beginNavigation(to: destination)
                } label: {
                    HStack {
                        Image(systemName: "door.left.hand.open")
                            .foregroundStyle(.yellow)
                        Text(destination.name)
                            .foregroundStyle(.primary)
                        Spacer()
                        Image(systemName: "arrow.turn.up.right")
                            .foregroundStyle(.secondary)
                    }
                    .contentShape(Rectangle())
                }
                .accessibilityHint("开始前往此门牌的导航")
            }
            .navigationTitle("选择目的地")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("完成") { navigation.isDestinationPickerPresented = false }
                }
            }
        }
    }

    private var settingsSheet: some View {
        NavigationStack {
            Form {
                Section("场景") {
                    TextField("场景编号", text: $navigation.sceneID)
                        .keyboardType(.numberPad)
                }
                Section {
                    Text("后端地址通过 ios/Config/Backend.xcconfig 设置，发布前需要替换为可从公网访问的 HTTPS 地址。")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
            }
            .navigationTitle("设置")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("保存") {
                        navigation.saveSceneID()
                        navigation.isSettingsPresented = false
                    }
                }
            }
        }
    }
}
