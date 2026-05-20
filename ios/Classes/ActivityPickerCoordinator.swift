import Foundation
import UIKit
import SwiftUI
import FamilyControls
import Flutter

class ActivityPickerCoordinator: NSObject {
    private var pendingResult: FlutterResult?
    private var hostingController: UIViewController?

    func showPicker(from viewController: UIViewController, result: @escaping FlutterResult) {
        if pendingResult != nil {
            result(FlutterError(code: "UNAVAILABLE", message: "Picker already open", details: nil))
            return
        }

        if #available(iOS 16.0, *) {
            pendingResult = result

            let pickerView = ActivityPickerView(
                onDone: { [weak self] selection in
                    self?.onPickerDone(selection: selection, presenter: viewController)
                },
                onCancel: { [weak self] in
                    self?.onPickerCancelled(presenter: viewController)
                }
            )

            let hosting = UIHostingController(rootView: pickerView)
            hosting.modalPresentationStyle = .fullScreen
            hostingController = hosting

            viewController.present(hosting, animated: true, completion: nil)
        } else {
            result(FlutterError(code: "UNSUPPORTED", message: "Requires iOS 15.0+", details: nil))
        }
    }

    @available(iOS 16.0, *)
    private func onPickerDone(selection: FamilyActivitySelection, presenter: UIViewController) {
        if let shieldManager = ZoAppBlockerPlugin.shared?.shieldManager {
            shieldManager.blockWithSelection(selection: selection)
        }
        
        presenter.dismiss(animated: true) { [weak self] in
            // Return empty list or selected identifiers
            self?.pendingResult?([["packageName": "selected", "appName": "Selected App"]])
            self?.pendingResult = nil
            self?.hostingController = nil
        }
    }

    private func onPickerCancelled(presenter: UIViewController) {
        presenter.dismiss(animated: true) { [weak self] in
            self?.pendingResult?([] as [Any])
            self?.pendingResult = nil
            self?.hostingController = nil
        }
    }
}
