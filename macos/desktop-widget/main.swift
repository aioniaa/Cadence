import AppKit
import SwiftUI

struct WidgetPayload: Decodable {
    let label: String?
    let time: String?
    let meta: String?
    let progress: Double?
    let muted: Bool?
}

final class WidgetModel: ObservableObject {
    @Published var label = "正在读取"
    @Published var time = "—"
    @Published var meta = "班表"
    @Published var progress = 0.0
    @Published var muted = true

    private let dataURL: URL
    private var timer: Timer?

    init(dataURL: URL) {
        self.dataURL = dataURL
        reload()
        timer = Timer.scheduledTimer(withTimeInterval: 0.5, repeats: true) { [weak self] _ in
            self?.reload()
        }
        RunLoop.main.add(timer!, forMode: .common)
    }

    func reload() {
        guard
            let data = try? Data(contentsOf: dataURL),
            let payload = try? JSONDecoder().decode(WidgetPayload.self, from: data)
        else {
            return
        }

        let nextLabel = payload.label ?? "未排班"
        let nextTime = payload.time ?? "—"
        let nextMeta = payload.meta ?? "打开班表安排班次"
        let nextProgress = min(1, max(0, payload.progress ?? 0))
        let nextMuted = payload.muted ?? true

        DispatchQueue.main.async {
            if self.label != nextLabel { self.label = nextLabel }
            if self.time != nextTime { self.time = nextTime }
            if self.meta != nextMeta { self.meta = nextMeta }
            if self.progress != nextProgress { self.progress = nextProgress }
            if self.muted != nextMuted { self.muted = nextMuted }
        }
    }
}

struct WidgetView: View {
    @ObservedObject var model: WidgetModel
    let onClose: () -> Void

    private let accent = Color(red: 0.788, green: 0.961, blue: 0.608)
    private let ink = Color(red: 0.957, green: 1.0, blue: 0.941)
    private let mutedInk = Color(red: 0.92, green: 0.98, blue: 0.90).opacity(0.56)

    var body: some View {
        ZStack {
            RoundedRectangle(cornerRadius: 23, style: .continuous)
                .fill(
                    LinearGradient(
                        colors: [
                            Color(red: 0.063, green: 0.165, blue: 0.106).opacity(0.88),
                            Color(red: 0.024, green: 0.078, blue: 0.051).opacity(0.94),
                        ],
                        startPoint: .topLeading,
                        endPoint: .bottomTrailing
                    )
                )
                .overlay(
                    RoundedRectangle(cornerRadius: 23, style: .continuous)
                        .stroke(accent.opacity(0.17), lineWidth: 1)
                )

            HStack(spacing: 9) {
                DragHandle()
                RingView(progress: model.progress, muted: model.muted, accent: accent)
                VStack(alignment: .leading, spacing: 0) {
                    Text(model.label)
                        .font(.system(size: 8.5, weight: .semibold))
                        .foregroundColor(mutedInk)
                        .lineLimit(1)
                    Text(model.time)
                        .font(.system(size: 21, weight: .semibold, design: .rounded))
                        .monospacedDigit()
                        .foregroundColor(model.muted ? ink.opacity(0.72) : ink)
                        .lineLimit(1)
                        .minimumScaleFactor(0.7)
                        .padding(.top, 1)
                    Text(model.meta)
                        .font(.system(size: 8.5, weight: .regular))
                        .foregroundColor(mutedInk)
                        .lineLimit(1)
                        .truncationMode(.tail)
                        .padding(.top, 2)
                }
                Spacer(minLength: 0)
                Button(action: onClose) {
                    Image(systemName: "xmark")
                        .font(.system(size: 9, weight: .semibold))
                        .foregroundColor(Color.white.opacity(0.55))
                        .frame(width: 20, height: 20)
                        .background(Circle().fill(Color.white.opacity(0.07)))
                }
                .buttonStyle(.plain)
                .help("隐藏桌面小组件")
            }
            .padding(.horizontal, 11)
        }
        .frame(width: 300, height: 84)
        .contentShape(RoundedRectangle(cornerRadius: 23, style: .continuous))
    }
}

struct RingView: View {
    let progress: Double
    let muted: Bool
    let accent: Color

    var body: some View {
        ZStack {
            Circle()
                .stroke(Color.white.opacity(0.13), lineWidth: 4)
            Circle()
                .trim(from: 0, to: max(0.002, progress))
                .stroke(
                    muted ? Color(red: 0.58, green: 0.66, blue: 0.60) : accent,
                    style: StrokeStyle(lineWidth: 4, lineCap: .round)
                )
                .rotationEffect(.degrees(-90))
            Circle()
                .fill(muted ? Color(red: 0.58, green: 0.66, blue: 0.60) : accent)
                .frame(width: 7, height: 7)
        }
        .frame(width: 48, height: 48)
    }
}

struct DragHandle: View {
    var body: some View {
        VStack(spacing: 3) {
            ForEach(0..<3, id: \.self) { _ in
                HStack(spacing: 3) {
                    Circle().fill(Color.white.opacity(0.34)).frame(width: 2.5, height: 2.5)
                    Circle().fill(Color.white.opacity(0.34)).frame(width: 2.5, height: 2.5)
                }
            }
        }
        .frame(width: 10)
    }
}

final class WidgetWindow: NSWindow {
    override var canBecomeKey: Bool { true }
    override var canBecomeMain: Bool { false }
}

final class DraggableHostingView<Content: View>: NSHostingView<Content> {
    override var mouseDownCanMoveWindow: Bool { true }
    override func acceptsFirstMouse(for event: NSEvent?) -> Bool { true }
}

final class AppDelegate: NSObject, NSApplicationDelegate, NSWindowDelegate {
    private var window: WidgetWindow!
    private var model: WidgetModel!
    private var showSignal: DispatchSourceSignal!
    private var hideSignal: DispatchSourceSignal!
    private var parentPID: pid_t = 0
    private let defaults = UserDefaults.standard

    func applicationDidFinishLaunching(_ notification: Notification) {
        let args = CommandLine.arguments
        let dataPath = value(after: "--data", in: args) ?? defaultDataPath()
        parentPID = pid_t(value(after: "--parent", in: args) ?? "0") ?? 0

        model = WidgetModel(dataURL: URL(fileURLWithPath: dataPath))
        let view = WidgetView(model: model) { [weak self] in self?.hide() }
        let hosting = DraggableHostingView(rootView: view)
        hosting.frame = NSRect(x: 0, y: 0, width: 300, height: 84)

        window = WidgetWindow(
            contentRect: NSRect(x: 0, y: 0, width: 300, height: 84),
            styleMask: [.borderless],
            backing: .buffered,
            defer: false
        )
        window.contentView = hosting
        window.isOpaque = false
        window.backgroundColor = .clear
        window.hasShadow = false
        window.isMovableByWindowBackground = true
        window.acceptsMouseMovedEvents = true
        window.isReleasedWhenClosed = false
        window.delegate = self
        window.level = NSWindow.Level(rawValue: Int(CGWindowLevelForKey(.desktopIconWindow)) + 2)
        window.collectionBehavior = [.canJoinAllSpaces, .stationary, .ignoresCycle]
        window.title = "班表桌面小组件"

        restorePosition()
        if args.contains("--show") {
            show()
        } else if defaults.object(forKey: "widgetVisible") as? Bool ?? true {
            show()
        } else {
            window.orderOut(nil)
        }

        installSignals()
        Timer.scheduledTimer(withTimeInterval: 2, repeats: true) { [weak self] _ in
            guard let self, self.parentPID > 0 else { return }
            if kill(self.parentPID, 0) != 0 {
                NSApp.terminate(nil)
            }
        }
    }

    private func value(after flag: String, in args: [String]) -> String? {
        guard let index = args.firstIndex(of: flag), index + 1 < args.count else { return nil }
        return args[index + 1]
    }

    private func defaultDataPath() -> String {
        let support = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask).first!
        return support.appendingPathComponent("班表/desktop-widget-data.json").path
    }

    private func defaultOrigin() -> NSPoint {
        guard let frame = NSScreen.main?.visibleFrame else { return NSPoint(x: 100, y: 100) }
        return NSPoint(
            x: frame.midX - 150,
            y: frame.maxY - 84 - 12
        )
    }

    private func restorePosition() {
        if
            defaults.object(forKey: "widgetX") != nil,
            defaults.object(forKey: "widgetY") != nil
        {
            let point = NSPoint(
                x: defaults.double(forKey: "widgetX"),
                y: defaults.double(forKey: "widgetY")
            )
            if NSScreen.screens.contains(where: { $0.visibleFrame.insetBy(dx: -20, dy: -20).contains(point) }) {
                window.setFrameOrigin(point)
                return
            }
        }
        window.setFrameOrigin(defaultOrigin())
    }

    func windowDidMove(_ notification: Notification) {
        defaults.set(window.frame.origin.x, forKey: "widgetX")
        defaults.set(window.frame.origin.y, forKey: "widgetY")
    }

    private func installSignals() {
        signal(SIGUSR1, SIG_IGN)
        signal(SIGUSR2, SIG_IGN)

        showSignal = DispatchSource.makeSignalSource(signal: SIGUSR1, queue: .main)
        showSignal.setEventHandler { [weak self] in self?.show() }
        showSignal.resume()

        hideSignal = DispatchSource.makeSignalSource(signal: SIGUSR2, queue: .main)
        hideSignal.setEventHandler { [weak self] in self?.hide() }
        hideSignal.resume()
    }

    private func show() {
        defaults.set(true, forKey: "widgetVisible")
        window.orderFrontRegardless()
    }

    private func hide() {
        defaults.set(false, forKey: "widgetVisible")
        window.orderOut(nil)
    }
}

let app = NSApplication.shared
let delegate = AppDelegate()
app.delegate = delegate
app.setActivationPolicy(.accessory)
app.run()
