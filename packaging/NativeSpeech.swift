import Foundation
import AppKit
import Speech
import Darwin

// Optional macOS fallback. Receives only a local recording path and language tag.
@main struct HermesSpeech {
    static func finish(text: String? = nil, error: String? = nil) -> Never {
        let payload: [String: String] = text.map { ["text": $0] } ?? ["error": error ?? "语音识别失败"]
        if let data = try? JSONSerialization.data(withJSONObject: payload) {
            FileHandle.standardOutput.write(data)
        }
        exit(text == nil ? 1 : 0)
    }

    static func main() {
        guard CommandLine.arguments.count == 3 else { finish(error: "缺少录音文件或识别语言") }
        let url = URL(fileURLWithPath: CommandLine.arguments[1])
        guard FileManager.default.fileExists(atPath: url.path) else { finish(error: "录音文件不存在") }
        _ = NSApplication.shared
        NSApp.setActivationPolicy(.accessory)
        let language = CommandLine.arguments[2]
        var task: SFSpeechRecognitionTask?
        SFSpeechRecognizer.requestAuthorization { status in
            DispatchQueue.main.async {
                guard status == .authorized else { finish(error: "请在系统设置中允许 Hermes 使用语音识别") }
                guard let recognizer = SFSpeechRecognizer(locale: Locale(identifier: language)), recognizer.isAvailable else {
                    finish(error: "当前语言的系统识别服务不可用，请检查系统听写设置")
                }
                let request = SFSpeechURLRecognitionRequest(url: url)
                request.shouldReportPartialResults = false
                if recognizer.supportsOnDeviceRecognition { request.requiresOnDeviceRecognition = true }
                task = recognizer.recognitionTask(with: request) { result, error in
                    if let result = result, result.isFinal { finish(text: result.bestTranscription.formattedString) }
                    if let error = error { finish(error: error.localizedDescription) }
                }
            }
        }
        DispatchQueue.main.asyncAfter(deadline: .now() + 90) {
            task?.cancel()
            finish(error: "系统识别超时，录音可以重试")
        }
        RunLoop.main.run()
    }
}
