$ErrorActionPreference = 'Stop'
$outputPath = $env:HERMES_SPEECH_OUTPUT
$utf8 = New-Object System.Text.UTF8Encoding($false)
function Write-Result($result) {
    if ($outputPath) { [IO.File]::WriteAllText($outputPath, ($result | ConvertTo-Json -Compress), $utf8) }
}
try {
    Add-Type -AssemblyName System.Speech
    $culture = [Globalization.CultureInfo]::GetCultureInfo($env:HERMES_SPEECH_LANGUAGE)
    if ($env:HERMES_SPEECH_MODE -eq 'speak') {
        $voice = New-Object System.Speech.Synthesis.SpeechSynthesizer
        try {
            $installed = @($voice.GetInstalledVoices() | Where-Object { $_.Enabled })
            $selected = $installed | Where-Object { $_.VoiceInfo.Culture.Name -eq $culture.Name } | Select-Object -First 1
            if (-not $selected) { $selected = $installed | Where-Object { $_.VoiceInfo.Culture.TwoLetterISOLanguageName -eq $culture.TwoLetterISOLanguageName } | Select-Object -First 1 }
            if (-not $selected) { throw "No installed Windows voice for $($culture.Name). Select the server engine or install a matching speech voice in Windows Settings." }
            $voice.SelectVoice($selected.VoiceInfo.Name)
            $ratio = [double]::Parse($env:HERMES_SPEECH_RATE, [Globalization.CultureInfo]::InvariantCulture)
            $voice.Rate = [Math]::Max(-10, [Math]::Min(10, [int][Math]::Round([Math]::Log($ratio, 2) * 4)))
            $voice.Speak([IO.File]::ReadAllText($env:HERMES_SPEECH_INPUT, [Text.Encoding]::UTF8))
            Write-Result @{ text = ''; provider = 'Windows' }
        } finally { $voice.Dispose() }
    } elseif ($env:HERMES_SPEECH_MODE -eq 'transcribe') {
        # C# event handlers do not depend on a PowerShell runspace on recognizer worker threads.
        Add-Type -ReferencedAssemblies 'System.Speech' -TypeDefinition @'
using System;
using System.Collections.Generic;
using System.Globalization;
using System.Speech.Recognition;
using System.Threading;
public static class HermesWaveRecognizer {
    public static string Transcribe(string path, string language) {
        RecognizerInfo selected = null;
        var culture = CultureInfo.GetCultureInfo(language);
        foreach (var info in SpeechRecognitionEngine.InstalledRecognizers()) {
            if (info.Culture.Name == culture.Name) { selected = info; break; }
        }
        if (selected == null) throw new InvalidOperationException("No installed Windows recognizer for " + language + ". Select the server engine or install a matching speech language in Windows Settings.");
        using (var engine = new SpeechRecognitionEngine(selected))
        using (var done = new ManualResetEvent(false)) {
            var parts = new List<string>();
            Exception failure = null;
            engine.LoadGrammar(new DictationGrammar());
            engine.SetInputToWaveFile(path);
            engine.SpeechRecognized += (sender, e) => { if (e.Result != null) { lock (parts) parts.Add(e.Result.Text); } };
            engine.RecognizeCompleted += (sender, e) => { failure = e.Error; done.Set(); };
            engine.RecognizeAsync(RecognizeMode.Multiple);
            if (!done.WaitOne(85000)) {
                engine.RecognizeAsyncCancel();
                done.WaitOne(3000);
                throw new TimeoutException("Windows speech recognition timed out. The recording is retained in Hermes.");
            }
            if (failure != null) throw failure;
            lock (parts) return string.Join(" ", parts);
        }
    }
}
'@
        $text = [HermesWaveRecognizer]::Transcribe($env:HERMES_SPEECH_INPUT, $culture.Name)
        Write-Result @{ text = $text; provider = 'Windows' }
    } else { throw 'Unsupported speech operation.' }
    exit 0
} catch {
    Write-Result @{ error = $_.Exception.Message }
    exit 1
}
