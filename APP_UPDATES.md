# GitHub ReleasesによるEssentialの更新

## 現行構成と変更範囲

- 単一appモジュール。Kotlin 2.0.21、Compose、Material 3 1.4.0、liquid glassの既存画面を維持。
- Rust core-rustはJNIのcdylib。Android API、ネットワーク、証明書、PackageInstallerとの連携はKotlin側に独立したupdateパッケージとして追加。
- AGP 8.7.3、Wrapper 8.10.2、Java/Kotlin JVM 17。minSdk 26、targetSdk/compileSdk 35、applicationIdはjp.essential.app。
- Lifecycle同梱lintと解析APIの互換性確保のためlintのみ8.8.2を使用。lintが発見した既存のAPI 26解放処理、API 31専用テーマの配置、CameraX opt-inも修正した。
- 変更前0.4.6 / 10、変更後0.5.0 / 11。app/build.gradle.ktsのversionNameとversionCodeが唯一の版管理元。今後は両方を必ず増やす。
- 4 ABI別APKとuniversalを生成。releaseの縮小設定や既存メディア依存は変更していない。ローカルは署名環境変数がないとunsigned releaseとなり、公開には使えない。

## 仕組み

1. 公開リポジトリの `/repos/owner/repo/releases/latest` をトークンなしで取得。draft/prereleaseは除外し、安定版 `vX.Y.Z` を数値比較する。
2. 新版だけ現在版・最新版・ノート・アップデート／後でを表示。起動時確認は初期値ON、設定で保存。通信はIOスレッド、タイムアウト付き。失敗しても起動を止めず、手動再試行できる。
3. 対応ABIの `Essential-X.Y.Z-ABI.apk` を優先し、なければ `-universal.apk` を選ぶ。debug APKは除外。公開元URLとHTTPS転送先を制限する。
4. アプリ専用noBackup領域へストリーム保存し、進捗表示。サイズ一致、GitHubのdigestがあればSHA-256、applicationId、新しいversionCode、ReleaseとのversionName一致、minSdk、現行署名証明書の一致を確認する。証明書ローテーションは対応せず、同一鍵が前提。最後の真正性検証はAndroid標準インストーラーが行う。
5. 部分ファイルは成功／失敗時に回収。プロセス終了後のバイト単位再開はせず、設定から再確認・再ダウンロードする。回転時はViewModelで状態を保持する。
6. ダウンロード後にインストールを選択すると不明なアプリの許可を確認し、設定へ誘導。戻った後で権限を再確認しPackageInstaller Sessionへコピーする。OSの確認要求・キャンセル・失敗を処理し、再試行可能。

WorkManagerによる定期チェックは追加していない。軽量化方針に合わせ、起動時／手動確認のみで常駐・通知権限を増やさない。アプリ終了中の自動ダウンロード・無確認更新は行わない。

USER_ACTION_NOT_REQUIREDはAndroid 12以降に存在するが、対象SDK、自己更新／更新所有者／インストーラー、UPDATE_PACKAGES_WITHOUT_USER_ACTIONなどの条件がある。対象SDK条件は将来変更される。今回は保守性と利用者の確認を優先しUSER_ACTION_REQUIREDを指定し、通常の確認画面を標準経路とした。

## 手動で必要な設定

1. 公開GitHubリポジトリを作り、このプロジェクトを登録する。現在の作業ディレクトリにはGit管理情報がない。local.properties、署名鍵、個人添付画像、build、録画等をコミットしない。
2. gradle.propertiesの `UPDATE_REPOSITORY=owner/repo` を設定する。未設定では起動時通信を行わず、設定に未設定と表示する。CIは自身のgithub.repositoryを-Pで注入する。privateリポジトリは非対応で、APKにTokenは入れない。
3. 下記Secretsと、必要ならGitHub Environment `release` の承認ルールを設定する。
4. 同一署名鍵を安全に保管し、バックアップする。鍵やパスワードをGitへ保存しない。

### 既存debug版からの移行

これまでの配布APKはdebug署名。異なるrelease鍵のAPKでは上書きできない。正式運用では専用鍵を使うことを推奨するが、その場合は既存データを利用者側で退避してから初回のみ入れ替えが必要になる。アンインストールはデータを失うため自動実行しない。同じdebug鍵の利用は技術的には可能だが、正式配布には推奨しない。署名を維持しないまま無理に上書きする機能はない。

## GitHub Secrets

Environment releaseまたはRepository Secretsへ登録する。

| 名前 | 値 |
| --- | --- |
| ANDROID_KEYSTORE_BASE64 | 署名用JKS/keystoreファイル全体をBase64化した値 |
| ANDROID_KEYSTORE_PASSWORD | キーストアのパスワード |
| ANDROID_KEY_ALIAS | 鍵のalias |
| ANDROID_KEY_PASSWORD | 鍵のパスワード |

公開処理はActionsが発行するGITHUB_TOKENを使い、個人Tokenの登録は不要。秘密情報はビルド工程の環境変数だけに渡す。

## Releaseの公開

1. app/build.gradle.ktsのversionNameを例 `0.5.1`、versionCodeを例 `12` へ増やし、コミットする。
2. `git tag v0.5.1`、`git push origin v0.5.1` を実行。
3. Actionsがタグ一致検査、NDK/Rustの全ABIビルド、単体テスト・lint、署名付きrelease生成、署名検査、16KB zipalign検査を実施する。
4. APK5個とSHA256SUMS.txtをdraft Releaseへ添付し、完了後に公開する。公開途中のReleaseは最新版として露出しない。
5. 旧版端末で「アップデートを確認」から確認する。初回はテスト端末で確認・許可拒否・キャンセル・再試行・正常更新・データ維持を検証する。

既に同じタグのReleaseがある場合、workflowは上書きせず失敗する。失敗したdraftを管理者が確認して整理するか、新しいversionCode／タグで配信する。

既存のTHIRD_PARTY_NOTICES.mdも確認し、FFmpeg等のライセンスに従って必要な対応ソースや表示を配布する。CIの追加だけで再配布義務を満たすわけではない。

## 参照した公式仕様

- https://docs.github.com/en/rest/releases/releases
- https://developer.android.com/reference/android/content/pm/PackageInstaller.SessionParams#setRequireUserAction(int)
- https://developer.android.com/reference/android/content/pm/PackageInstaller

## 検証の境界

実施結果はWork history.txtに記録する。配信リポジトリと正式署名鍵が未提供のため、GitHub Actionsの実公開と正式鍵での端末上書き更新は利用者の設定後に検証が必要。
