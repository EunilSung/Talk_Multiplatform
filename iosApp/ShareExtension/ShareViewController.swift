//
//  ShareViewController.swift
//  ShareExtension
//
//  최소 UI (안내 + "Talk 에서 보내기" 버튼 + 취소) 패턴 — UIViewController 기반.
//
//  iOS 17+ 에서 ShareExtension 의 자동 URL launch (`extensionContext.open`) 은 success=false 로
//  차단됨. 따라서 사용자가 버튼을 명시적으로 탭하는 액션을 거쳐야 iOS 가 URL 호출을 허용함.
//
//   - viewDidLoad: handleSharedItems 백그라운드 자동 실행 → shared.json 저장
//   - 사용자가 "Talk 에서 보내기" 탭 → completeRequest + responder chain 으로 메인 앱 launch 시도
//   - "취소" 탭 → shared.json 제거 + cancelRequest
//

import UIKit
import UniformTypeIdentifiers

class ShareViewController: UIViewController {

    private let appGroupID = "group.com.eunilsung.talk"
    private let sharedJsonName = "shared.json"
    private let sharedFilesDirName = "SharedFiles"

    // MARK: - UI

    private let dimView = UIView()
    private let cardView = UIView()
    private let titleLabel = UILabel()
    private let subtitleLabel = UILabel()
    private let openButton = UIButton(type: .system)
    private let cancelButton = UIButton(type: .system)

    /// handleSharedItems 완료 여부 — true 가 되어야 openButton 활성화.
    private var dataReady = false

    override func viewDidLoad() {
        super.viewDidLoad()
        writeDiagnostic("[lifecycle] viewDidLoad")
        NSLog("[ShareExt] ▶ viewDidLoad — appGroupID=%@", appGroupID)
        setupUI()

        Task {
            await handleSharedItems()
            await MainActor.run {
                self.dataReady = true
                self.openButton.isEnabled = true
                self.openButton.alpha = 1.0
                self.subtitleLabel.text = "준비 완료. 아래 버튼을 눌러 Talk 로 이동."
            }
        }
    }

    private func setupUI() {
        view.backgroundColor = .clear

        // Dim background
        dimView.translatesAutoresizingMaskIntoConstraints = false
        dimView.backgroundColor = UIColor.black.withAlphaComponent(0.5)
        view.addSubview(dimView)

        // Card
        cardView.translatesAutoresizingMaskIntoConstraints = false
        cardView.backgroundColor = .systemBackground
        cardView.layer.cornerRadius = 16
        cardView.layer.masksToBounds = true
        view.addSubview(cardView)

        // Title
        titleLabel.translatesAutoresizingMaskIntoConstraints = false
        titleLabel.text = "Talk 로 공유"
        titleLabel.font = .boldSystemFont(ofSize: 18)
        titleLabel.textAlignment = .center
        titleLabel.textColor = .label
        cardView.addSubview(titleLabel)

        // Subtitle (상태 텍스트)
        subtitleLabel.translatesAutoresizingMaskIntoConstraints = false
        subtitleLabel.text = "공유 내용 처리 중..."
        subtitleLabel.font = .systemFont(ofSize: 13)
        subtitleLabel.textAlignment = .center
        subtitleLabel.textColor = .secondaryLabel
        subtitleLabel.numberOfLines = 0
        cardView.addSubview(subtitleLabel)

        // Open button — Talk 메인 색상 (노랑/메인 톤)
        openButton.translatesAutoresizingMaskIntoConstraints = false
        openButton.setTitle("Talk 에서 보내기", for: .normal)
        openButton.titleLabel?.font = .systemFont(ofSize: 16, weight: .semibold)
        openButton.setTitleColor(.black, for: .normal)
        openButton.backgroundColor = UIColor(red: 0.97, green: 0.78, blue: 0.0, alpha: 1.0)
        openButton.layer.cornerRadius = 10
        openButton.isEnabled = false  // 데이터 준비 전 비활성
        openButton.alpha = 0.5
        openButton.addTarget(self, action: #selector(openButtonTapped), for: .touchUpInside)
        cardView.addSubview(openButton)

        // Cancel button
        cancelButton.translatesAutoresizingMaskIntoConstraints = false
        cancelButton.setTitle("취소", for: .normal)
        cancelButton.titleLabel?.font = .systemFont(ofSize: 15)
        cancelButton.setTitleColor(.secondaryLabel, for: .normal)
        cancelButton.addTarget(self, action: #selector(cancelButtonTapped), for: .touchUpInside)
        cardView.addSubview(cancelButton)

        NSLayoutConstraint.activate([
            // dim 전체
            dimView.topAnchor.constraint(equalTo: view.topAnchor),
            dimView.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            dimView.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            dimView.bottomAnchor.constraint(equalTo: view.bottomAnchor),

            // card 중앙 + 좌우 24pt 여백
            cardView.centerXAnchor.constraint(equalTo: view.centerXAnchor),
            cardView.centerYAnchor.constraint(equalTo: view.centerYAnchor),
            cardView.leadingAnchor.constraint(greaterThanOrEqualTo: view.leadingAnchor, constant: 24),
            cardView.trailingAnchor.constraint(lessThanOrEqualTo: view.trailingAnchor, constant: -24),
            cardView.widthAnchor.constraint(lessThanOrEqualToConstant: 320),

            // title
            titleLabel.topAnchor.constraint(equalTo: cardView.topAnchor, constant: 24),
            titleLabel.leadingAnchor.constraint(equalTo: cardView.leadingAnchor, constant: 20),
            titleLabel.trailingAnchor.constraint(equalTo: cardView.trailingAnchor, constant: -20),

            // subtitle
            subtitleLabel.topAnchor.constraint(equalTo: titleLabel.bottomAnchor, constant: 8),
            subtitleLabel.leadingAnchor.constraint(equalTo: cardView.leadingAnchor, constant: 20),
            subtitleLabel.trailingAnchor.constraint(equalTo: cardView.trailingAnchor, constant: -20),

            // open button
            openButton.topAnchor.constraint(equalTo: subtitleLabel.bottomAnchor, constant: 24),
            openButton.leadingAnchor.constraint(equalTo: cardView.leadingAnchor, constant: 20),
            openButton.trailingAnchor.constraint(equalTo: cardView.trailingAnchor, constant: -20),
            openButton.heightAnchor.constraint(equalToConstant: 48),

            // cancel button
            cancelButton.topAnchor.constraint(equalTo: openButton.bottomAnchor, constant: 12),
            cancelButton.centerXAnchor.constraint(equalTo: cardView.centerXAnchor),
            cancelButton.bottomAnchor.constraint(equalTo: cardView.bottomAnchor, constant: -16),
            cancelButton.heightAnchor.constraint(equalToConstant: 36),
        ])
    }

    // MARK: - Actions

    @objc private func openButtonTapped() {
        writeDiagnostic("[lifecycle] openButtonTapped — user tap")
        NSLog("[ShareExt] ▶ openButtonTapped")

        guard dataReady, let url = URL(string: "talk://share") else {
            extensionContext?.completeRequest(returningItems: nil, completionHandler: nil)
            return
        }

        // 사용자 명시적 액션 직후 — iOS 17+ 에서도 URL 호출 허용 가능성 있음.
        // 여러 경로 동시 시도 (어느 하나라도 통과하면 성공):
        //   1) extensionContext.open  (정식 API, 이전엔 자동 호출 시 success=0)
        //   2) UIWindowScene.open     (iOS 13+, scene-based API)
        //   3) responder chain        (deprecated openURL, fallback)
        // 어떤 경로가 통과하든 사용자 입장에서는 launch 1회만 됨.
        tryOpenMainApp(url: url) {
            // 모든 시도 후 Extension dismiss — completeRequest 가 URL 호출보다 먼저면
            // self 가 사라져 다음 호출이 실패할 수 있어 마지막에.
            self.extensionContext?.completeRequest(returningItems: nil, completionHandler: nil)
        }
    }

    /// 여러 경로로 메인 앱 자동 launch 시도. 모두 실패해도 사용자는 Talk 직접 열면 됨 (fallback).
    private func tryOpenMainApp(url: URL, completion: @escaping () -> Void) {
        // 시도 1 — UIWindowScene.open (iOS 13+, 사용자 액션 컨텍스트 보존)
        if let windowScene = view.window?.windowScene {
            windowScene.open(url, options: nil) { success in
                NSLog("[ShareExt] windowScene.open success=%d", success ? 1 : 0)
            }
        } else {
            NSLog("[ShareExt] windowScene not available")
        }

        // 시도 2 — extensionContext.open (정식 ExtensionContext API)
        extensionContext?.open(url) { success in
            NSLog("[ShareExt] extensionContext.open success=%d", success ? 1 : 0)
        }

        // 시도 3 — responder chain + openURL: selector (deprecated, iOS 17+ 차단 가능)
        openURLViaResponderChain(url)

        // 모든 시도가 비동기 — completion 은 약간의 delay 후 호출해서
        // 위 시도들의 system 처리 시간 확보.
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) {
            completion()
        }
    }

    @objc private func cancelButtonTapped() {
        writeDiagnostic("[lifecycle] cancelButtonTapped")
        NSLog("[ShareExt] ▶ cancelButtonTapped — discarding shared.json")
        // 취소 — 이미 저장된 shared.json 제거 (메인 앱이 못 읽도록).
        if let containerURL = FileManager.default.containerURL(
            forSecurityApplicationGroupIdentifier: appGroupID
        ) {
            let jsonURL = containerURL.appendingPathComponent(sharedJsonName)
            try? FileManager.default.removeItem(at: jsonURL)
        }
        extensionContext?.cancelRequest(
            withError: NSError(domain: "ShareExtension", code: -1, userInfo: nil)
        )
    }

    /// responder chain 으로 UIApplication 찾아 `openURL:` selector perform.
    /// iOS 17+ 에서 자동 호출은 차단됐지만 사용자 명시적 탭 직후엔 허용되는 경우 보고됨.
    /// 차단되면 BUG IN CLIENT OF UIKIT 경고 + no-op — 사용자가 Talk 직접 열면 됨 (fallback).
    private func openURLViaResponderChain(_ url: URL) {
        var responder: UIResponder? = self
        let selector = sel_registerName("openURL:")
        while let r = responder {
            if r.responds(to: selector) {
                _ = r.perform(selector, with: url)
                NSLog("[ShareExt] openURL via responder chain — invoked")
                return
            }
            responder = r.next
        }
        NSLog("[ShareExt] openURL via responder chain — UIApplication not found in chain")
    }

    // MARK: - Diagnostic

    /// 진단용 — App Group container 의 share_diag.log 에 단계별 진입 기록 append.
    /// 메인 앱이 시작 시 읽어서 Console 에 출력하면 ShareExtension 실행 여부 확인 가능.
    private func writeDiagnostic(_ message: String) {
        guard let containerURL = FileManager.default.containerURL(
            forSecurityApplicationGroupIdentifier: appGroupID
        ) else {
            NSLog("[ShareExt] container unavailable for diagnostic: %@", message)
            return
        }
        let url = containerURL.appendingPathComponent("share_diag.log")
        let line = "\(ISO8601DateFormatter().string(from: Date())): \(message)\n"
        let existing = (try? String(contentsOf: url, encoding: .utf8)) ?? ""
        try? (existing + line).write(to: url, atomically: true, encoding: .utf8)
    }

    // MARK: - Data Handling

    /// 외부 앱에서 받은 NSItemProvider 들을 순회하며 App Group container 에 저장.
    /// 우선순위: fileURL > image > movie > url > text.
    private func handleSharedItems() async {
        var text = ""
        var filePaths: [String] = []

        guard let containerURL = FileManager.default.containerURL(
            forSecurityApplicationGroupIdentifier: appGroupID
        ) else {
            NSLog("[ShareExt] ❌ App Group container not available — %@", appGroupID)
            return
        }
        NSLog("[ShareExt] ✓ container=%@", containerURL.path)

        let sharedFilesDir = containerURL.appendingPathComponent(sharedFilesDirName)
        try? FileManager.default.createDirectory(at: sharedFilesDir, withIntermediateDirectories: true)

        let extensionItems = (extensionContext?.inputItems as? [NSExtensionItem]) ?? []
        NSLog("[ShareExt]   extensionItems count=%d", extensionItems.count)
        writeDiagnostic("[lifecycle] handleSharedItems items=\(extensionItems.count)")

        for item in extensionItems {
            guard let attachments = item.attachments else { continue }
            for provider in attachments {
                if provider.hasItemConformingToTypeIdentifier(UTType.fileURL.identifier) {
                    if let url = await loadFileURL(provider, UTType.fileURL.identifier),
                       let copied = copyToContainer(srcURL: url, destDir: sharedFilesDir) {
                        filePaths.append(copied)
                    }
                } else if provider.hasItemConformingToTypeIdentifier(UTType.image.identifier) {
                    if let url = await loadFileURL(provider, UTType.image.identifier),
                       let copied = copyToContainer(srcURL: url, destDir: sharedFilesDir) {
                        filePaths.append(copied)
                    }
                } else if provider.hasItemConformingToTypeIdentifier(UTType.movie.identifier) {
                    if let url = await loadFileURL(provider, UTType.movie.identifier),
                       let copied = copyToContainer(srcURL: url, destDir: sharedFilesDir) {
                        filePaths.append(copied)
                    }
                } else if provider.hasItemConformingToTypeIdentifier(UTType.url.identifier) {
                    if let url = await loadURL(provider) {
                        if !text.isEmpty { text += "\n" }
                        text += url.absoluteString
                    }
                } else if provider.hasItemConformingToTypeIdentifier(UTType.text.identifier) {
                    if let str = await loadText(provider) {
                        if !text.isEmpty { text += "\n" }
                        text += str
                    }
                }
            }
        }

        let payload: [String: Any] = [
            "text": text,
            "filePaths": filePaths,
            "timestamp": Date().timeIntervalSince1970
        ]
        let jsonURL = containerURL.appendingPathComponent(sharedJsonName)
        if let data = try? JSONSerialization.data(withJSONObject: payload, options: []) {
            do {
                try data.write(to: jsonURL, options: .atomic)
                NSLog("[ShareExt] ✓ wrote shared.json text=%d files=%d", text.count, filePaths.count)
                writeDiagnostic("[lifecycle] wrote shared.json text=\(text.count) files=\(filePaths.count)")
            } catch {
                NSLog("[ShareExt] ❌ write shared.json failed: %@", "\(error)")
            }
        }
    }

    // MARK: - NSItemProvider loaders (async wrappers)

    private func loadFileURL(_ provider: NSItemProvider, _ typeIdentifier: String) async -> URL? {
        await withCheckedContinuation { cont in
            provider.loadItem(forTypeIdentifier: typeIdentifier) { item, _ in
                if let url = item as? URL {
                    cont.resume(returning: url)
                } else if let data = item as? Data {
                    let tmp = FileManager.default.temporaryDirectory
                        .appendingPathComponent(UUID().uuidString)
                    do { try data.write(to: tmp, options: .atomic); cont.resume(returning: tmp) }
                    catch { cont.resume(returning: nil) }
                } else if let image = item as? UIImage,
                          let data = image.jpegData(compressionQuality: 0.9) {
                    let tmp = FileManager.default.temporaryDirectory
                        .appendingPathComponent("\(UUID().uuidString).jpg")
                    do { try data.write(to: tmp, options: .atomic); cont.resume(returning: tmp) }
                    catch { cont.resume(returning: nil) }
                } else {
                    cont.resume(returning: nil)
                }
            }
        }
    }

    private func loadURL(_ provider: NSItemProvider) async -> URL? {
        await withCheckedContinuation { cont in
            provider.loadItem(forTypeIdentifier: UTType.url.identifier) { item, _ in
                if let url = item as? URL { cont.resume(returning: url) }
                else if let data = item as? Data, let str = String(data: data, encoding: .utf8) {
                    cont.resume(returning: URL(string: str))
                } else { cont.resume(returning: nil) }
            }
        }
    }

    private func loadText(_ provider: NSItemProvider) async -> String? {
        await withCheckedContinuation { cont in
            provider.loadItem(forTypeIdentifier: UTType.text.identifier) { item, _ in
                if let str = item as? String { cont.resume(returning: str) }
                else if let data = item as? Data {
                    cont.resume(returning: String(data: data, encoding: .utf8))
                } else { cont.resume(returning: nil) }
            }
        }
    }

    /// Share Extension 의 임시 파일을 App Group container 로 복사.
    /// `copyItem` 은 modification date 를 그대로 유지 — 메인 앱의 cleanupOldSharedFiles 가
    /// 오래된 사진(1시간 이상 된 modification date) 을 즉시 삭제하는 문제를 막기 위해
    /// 복사 직후 setAttributes 로 mod date 를 현재 시각으로 갱신.
    private func copyToContainer(srcURL: URL, destDir: URL) -> String? {
        let stamp = Int(Date().timeIntervalSince1970 * 1000)
        let fileName = "\(stamp)_\(srcURL.lastPathComponent)"
        let destURL = destDir.appendingPathComponent(fileName)
        do {
            if FileManager.default.fileExists(atPath: destURL.path) {
                try FileManager.default.removeItem(at: destURL)
            }
            try FileManager.default.copyItem(at: srcURL, to: destURL)
            try? FileManager.default.setAttributes(
                [.modificationDate: Date()],
                ofItemAtPath: destURL.path
            )
            return destURL.absoluteString
        } catch {
            NSLog("[ShareExt] copyToContainer failed: %@", "\(error)")
            return nil
        }
    }
}
