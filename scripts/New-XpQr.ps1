param(
    [switch]$Initialize,
    [int]$Points = 0,
    [int]$Days = 30,
    [string]$OutputDirectory = 'D:\#AI開発\Android\Essential\Essential-XP発行者\配布QR',
    [string]$PublicKeyPath = '',
    [string]$KeyDirectory = 'D:\#AI開発\Android\Essential\Essential-XP発行者\秘密鍵'
)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Security
Add-Type -AssemblyName System.Drawing
$keyPath = Join-Path $KeyDirectory 'issuer.dpapi'
$rsa = New-Object System.Security.Cryptography.RSACryptoServiceProvider 3072
$rsa.PersistKeyInCsp = $false
try {
    if ($Initialize) {
        if (Test-Path -LiteralPath $keyPath) { throw '既存の発行鍵は上書きできません。' }
        if ([string]::IsNullOrWhiteSpace($PublicKeyPath)) { throw '公開鍵の保存先PublicKeyPathが必要です。' }
        New-Item -ItemType Directory -Path $KeyDirectory -Force | Out-Null
        # 秘密鍵は現在のWindowsユーザーだけが復号できるDPAPIで保護する。
        $privateBytes = [Text.Encoding]::UTF8.GetBytes($rsa.ToXmlString($true))
        $protectedBytes = [Security.Cryptography.ProtectedData]::Protect($privateBytes, $null, [Security.Cryptography.DataProtectionScope]::CurrentUser)
        [IO.File]::WriteAllBytes($keyPath, $protectedBytes)
        [Array]::Clear($privateBytes, 0, $privateBytes.Length)
        [IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($PublicKeyPath)) | Out-Null
        [IO.File]::WriteAllText($PublicKeyPath, [Convert]::ToBase64String($rsa.ExportParameters($false).Modulus), [Text.Encoding]::ASCII)
        Write-Output '発行鍵を初期化しました。公開鍵を含めてEssentialを再ビルドしてください。'
        return
    }
    if (!$PSBoundParameters.ContainsKey('Points')) {
        $enteredPoints = Read-Host '配布するXPを入力してください（1〜1000000）'
        if (![int]::TryParse($enteredPoints, [ref]$Points)) { throw 'XPは整数で入力してください。' }
        $enteredDays = Read-Host '有効日数を入力してください（空欄で30日、最大366日）'
        if ($enteredDays -ne '' -and ![int]::TryParse($enteredDays, [ref]$Days)) { throw '日数は整数で入力してください。' }
    }
    if ($Points -lt 1 -or $Points -gt 1000000 -or $Days -lt 1 -or $Days -gt 366) { throw 'Pointsは1〜1000000、Daysは1〜366を指定してください。' }
    $privateBytes = [Security.Cryptography.ProtectedData]::Unprotect([IO.File]::ReadAllBytes($keyPath), $null, [Security.Cryptography.DataProtectionScope]::CurrentUser)
    $rsa.FromXmlString([Text.Encoding]::UTF8.GetString($privateBytes))
    [Array]::Clear($privateBytes, 0, $privateBytes.Length)
    $id = [Guid]::NewGuid().ToString()
    $issued = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
    $expires = $issued + $Days * 86400L
    $payload = [Text.Encoding]::ASCII.GetBytes("1|jp.essential.app|$id|$Points|$issued|$expires")
    $signature = $rsa.SignData($payload, 'SHA256')
    function ConvertTo-Base64Url([byte[]]$Bytes) { [Convert]::ToBase64String($Bytes).TrimEnd('=').Replace('+', '-').Replace('/', '_') }
    $token = 'essential-xp:' + (ConvertTo-Base64Url $payload) + '.' + (ConvertTo-Base64Url $signature)
    # 同梱のZXingでPNGを生成する。配布IDは画像名と記録にも残す。
    New-Item -ItemType Directory -Path $OutputDirectory -Force | Out-Null
    $basePath = Join-Path $OutputDirectory "XP-$Points-$id"
    [IO.File]::WriteAllText("$basePath.txt", $token, [Text.Encoding]::UTF8)
    $java = Join-Path $PSScriptRoot 'runtime\bin\java.exe'
    if (!(Test-Path -LiteralPath $java)) { $java = 'C:\Program Files\Java\jdk-23\bin\java.exe' }
    & $java '-cp' (Join-Path $PSScriptRoot 'zxing-core.jar') (Join-Path $PSScriptRoot 'XpQrImage.java') $token "$basePath.png"
    if ($LASTEXITCODE -ne 0) { throw 'QR画像の生成に失敗しました。' }
    Write-Output "生成完了: $basePath.png（$Points XP、$Days 日間有効）"
} finally { $rsa.Dispose() }
