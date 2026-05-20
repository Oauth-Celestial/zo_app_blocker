import Flutter
import UIKit

@available(iOS 16.0, *)
public class ZoAppBlockerPlugin: NSObject, FlutterPlugin {
    var shieldManager: ShieldManager?
    private var permissionManager: PermissionManager?
    private var activityPickerCoordinator: ActivityPickerCoordinator?

    static var shared: ZoAppBlockerPlugin?

    public static func register(with registrar: FlutterPluginRegistrar) {
        let channel = FlutterMethodChannel(name: "zo_app_blocker", binaryMessenger: registrar.messenger())
        let instance = ZoAppBlockerPlugin()
        ZoAppBlockerPlugin.shared = instance
        
        instance.shieldManager = ShieldManager()
        instance.permissionManager = PermissionManager()
        instance.activityPickerCoordinator = ActivityPickerCoordinator()

        registrar.addMethodCallDelegate(instance, channel: channel)
    }

    public func handle(_ call: FlutterMethodCall, result: @escaping FlutterResult) {
        switch call.method {
        case "getPlatformVersion":
            result("iOS " + UIDevice.current.systemVersion)
        case "checkPermission":
            result(permissionManager?.checkPermission() ?? "denied")
        case "requestPermission":
            permissionManager?.requestPermission(result: result)
        case "getApps":
            guard let coordinator = activityPickerCoordinator else {
                result(FlutterError(code: "UNAVAILABLE", message: "Coordinator unavailable", details: nil))
                return
            }
            guard let rootVC = Self.findRootViewController() else {
                result(FlutterError(code: "UNAVAILABLE", message: "Root VC unavailable", details: nil))
                return
            }
            coordinator.showPicker(from: rootVC, result: result)
        case "getBlockedApps":
            result(shieldManager?.getSelectionInfo() ?? [])
        case "blockApps":
            if let args = call.arguments as? [String: Any], let identifiers = args["identifiers"] as? [String] {
                shieldManager?.blockApps(identifiers: identifiers)
            }
            result(nil)
        case "unblockApps":
            if let args = call.arguments as? [String: Any], let identifiers = args["identifiers"] as? [String] {
                shieldManager?.unblockApps(identifiers: identifiers)
            }
            result(nil)
        case "blockAll":
            shieldManager?.blockAll()
            result(nil)
        case "unblockAll":
            shieldManager?.unblockAll()
            result(nil)
        default:
            result(FlutterMethodNotImplemented)
        }
    }

    private static func findRootViewController() -> UIViewController? {
        return UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .flatMap { $0.windows }
            .first { $0.isKeyWindow }?
            .rootViewController
    }
}
