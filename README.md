# FPS Replay（1人称視点録画 Mod / Fabric）

Minecraft Fabric 向けの **軽量 1人称視点録画 Mod** です。

動画をリアルタイムに撮るのではなく、**カメラ姿勢・近傍エンティティ・ブロック変更（パケット）をバイナリで保存**し、あとから **4K・8K・360fps など任意の解像度/フレームレートで再レンダリング** します。録画中はほぼ負荷ゼロ、出力時の画質・FPS はいくらでも底上げできます（ReplayMod と同じ発想）。

---

## 仕組み

```
録画（軽量・リアルタイム）                     出力（高負荷・オフライン）
┌──────────────────────────┐            ┌──────────────────────────────┐
│ 毎 tick(20/s) に:         │            │ 記録を 1 tick ずつ再生し、     │
│ ・カメラ(視点)を数バイト記録 │  ──────▶  │ ・オフスクリーン FBO に描画      │
│ ・キーフレームごとに近傍     │   .fpr     │ ・任意の解像度(4K/8K)で撮影      │
│   エンティティを記録         │  ファイル   │ ・サブ tick を補間して         │
│ ・ブロック変更を記録         │            │   360fps 等に底上げ            │
└──────────────────────────┘            └──────────────────────────────┘
```

- **軽量化の全ツッパ**: 録画中は 1 tick に数バイト書くだけ。フルスクリーンの描画キャプチャも、毎フレームの PNG 化も行わないため FPS への影響はほぼありません。
- **パケット等々保存式**: 独自の `.fpr` バイナリ形式（キーフレーム＋量子化デルタ＋ gzip 圧縮）。録画データは小さいまま、ブロック変更の「パケット」も記録します。
- **底上げ自由**: 出力解像度・FPS はレンダリング時に指定。サブ tick 補間（線形 or Catmull-Rom スプライン）で滑らかに補間します。

---

## ビルド

要件: **JDK 21**。

```bash
./gradlew build          # jar を build/libs/ に生成
./gradlew runClient      # 開発環境で起動
```

> 注: このリポジトリには `gradlew` ラッパー（`gradle-wrapper.jar`）は含まれていません。
> 初回は Gradle 8.10+ を用意して `gradle wrapper` を実行するか、
> [Gradle 公式](https://gradle.org/releases/) から `gradle-wrapper.jar` を `gradle/wrapper/` に配置してください。

### バージョン変更

`gradle.properties` を書き換えるだけで対象バージョンを変更できます:

```properties
minecraft_version=1.21.1
yarn_mappings=1.21.1+build.3
loader_version=0.16.9
fabric_version=0.102.0+1.21.1
```

---

## 使い方（ゲーム内コマンド）

```
/record start [name]        録画開始（省略時は日時で命名）
/record stop                録画停止＆保存
/record status              状態確認

/replay list                保存済みリプレイ一覧
/replay render <name> [WxH] [fps]   再レンダリング開始
/replay stop                レンダリング中止
```

例:

```
/record start myclip
... プレイ ...
/record stop

/replay render myclip 3840x2160 360     # 4K / 360fps で出力
/replay render myclip 7680x4320 60      # 8K / 60fps で出力
```

### 出力先

- 録画ファイル: `<ゲームディレクトリ>/replays/<name>.fpr`
- 出力フレーム: `<ゲームディレクトリ>/replays/<name>_out/frame_XXXXXXXX.png`

PNG フレーム列から動画にする例（ffmpeg）:

```bash
ffmpeg -framerate 360 -i frame_%08d.png -c:v libx264 -crf 16 out.mp4
```

---

## 設定（`config/fps-replay.properties`）

| キー | 既定値 | 説明 |
| --- | --- | --- |
| `compressionLevel` | `6` | gzip 圧縮レベル (0–9) |
| `keyframeInterval` | `20` | キーフレーム間隔 (tick)。小さいほどエンティティ記録が密に |
| `entityRange` | `64` | 記録対象エンティティの半径 (ブロック) |
| `recordBlockChanges` | `true` | ブロック変更パケットを記録するか |
| `renderWidth` / `renderHeight` | `3840` / `2160` | コマンド未指定時の出力解像度 (4K) |
| `renderFps` | `360` | コマンド未指定時の出力 FPS |
| `interpolationMode` | `linear` | `linear` or `spline` |

---

## ファイルフォーマット（`.fpr`）

```
[4B マジック "FPRL"][4B バージョン (LE)][メタデータ][gzip 圧縮されたレコード列 ... END]
```

レコード種別（`RecordType`）:

| 種別 | 内容 |
| --- | --- |
| `KEYFRAME` | カメラ完全精度 + 近傍エンティティ全量（`keyframeInterval` ごと） |
| `TICK` | キーフレームからの量子化デルタ（位置 1/256 ブロック, 角度 0.01°）。**約15バイト/チック** |
| `BLOCK_CHANGE` | ブロック変更（絶対座標 + レジストリID） |
| `END` | 終端 |

---

## ソース構成

```
src/main/java/dev/ifuto/fpsreplay/
├── replay/          # ファイルフォーマット（書き込み/読み取り/補間）… コアロジック
│   ├── ReplayFile.java       # .fpr コンテナ
│   ├── ReplayWriter.java     # 量子化デルタ書き込み
│   ├── ReplayReader.java     # 読み取り + ReplayState 構築
│   ├── ReplayState.java      # メモリ上のリプレイ表現
│   └── Interpolation.java    # サブtick補間（線形 / Catmull-Rom）
├── client/          # Fabric クライアント側
│   ├── Recorder.java         # 軽量録画（毎tickサンプリング）
│   ├── Renderer.java         # オフスクリーン再レンダリング
│   ├── ReplayCommands.java   # /record, /replay コマンド
│   └── ReplayConfig.java     # 設定
└── mixin/           # 描画ループ・ブロック変更フック
```

---

## 既知の制限と今後の拡張

- **ワールド再現**: 現在のレンダリングは「同じシードのワールドで録画→再生」を前提に、
  ライブのクライアントワールドへカメラを当てて描画します。記録したブロック変更は保存されますが、
  レンダリング時に完全なワールド再構築（シードからのチャンク再生成）を行う `ReplayWorld` は今後実装予定です。
- **エンティティ描画**: カメラ（1人称）は tick 単位で完全再現。他エンティティはキーフレーム間を補間して再現します。
- **マルチスレッド/ストリーミング読み込み**: 現在は全レコードをメモリに展開してから描画します。
- **Zstd**: 圧縮は標準 gzip を使用（依存なし）。Zstd 対応で更に小型化できます。

## ライセンス

MIT（`LICENSE` 参照）。
