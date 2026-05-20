import Foundation
import FamilyControls
import Flutter

class PermissionManager: NSObject {
    func checkPermission() -> String {
        if #available(iOS 16.0, *) {
            switch AuthorizationCenter.shared.authorizationStatus {
            case .approved: return "granted"
            case .denied, .notDetermined: return "denied"
            @unknown default: return "restricted"
            }
        }
        return "restricted"
    }

    func requestPermission(result: @escaping FlutterResult) {
        if #available(iOS 16.0, *) {
            Task {
                do {
                    try await AuthorizationCenter.shared.requestAuthorization(for: .individual)
                    await MainActor.run {
                        result(self.checkPermission())
                    }
                } catch {
                    await MainActor.run {
                        result(self.checkPermission())
                    }
                }
            }
        } else {
            result("restricted")
        }
    }
}
