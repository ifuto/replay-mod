# FPS Replay（1人称視点録画 Mod / Fabric）

Minecraft Fabric 向けの **軽量 1人称視点録画 Mod** です。

動画をリアルタイムに撮るのではなく、**カメラ姿勢・近傍エンティティ・ブロック変更（パケット）・HUD状態をバイナリで保存**し、あとから **MP4 / PNG で任意の解像度・任意の FPS** に再レンダリングします。録画中はほぼ負荷ゼロ、出力時の画質・FPS はいくらでも底上げできます（ReplayMod と同じ発想）。

---

## 主な機能

| 機能 | 内容 |
| --- | --- |
| 🪶 死ぬほど軽量化 | 毎 tick（20/s）に**数バイト**書くだけ。ホットパスは**ゼロアロケーション**（オブジェクト生成・ボクシングなし）。zigzag varint デルタ符号化。 |
| 🎥 視点完全再現 | **実際のレンダーカメラ**（視点移動の揺れ・被ダメ時のロール・FOV 含む）を記録し、再生時に実カメラへ注入。ピクセル単位で一致。 |
| 🧩 他 Mod の要素も描画 | エンティティ/ブロックはレジストリIDで記録し、読み込み時に解決するため、**他 Mod が追加した Mob やブロックもそのまま描画**。レンダーパイプラインは通常どおり通すので、シェーダー系 Mod も適用。 |
| 🖥 HUD 完全再現 | 体力/満腹度/アーマー/空気/経験値、**ステータスエフェクト（位置含む）**、**スコアボード**、**Tab プレイヤーリスト**を記録し、再生時に注入。 |
| 📺 ゲーム内プレビュー | 記録をその場で再生（Space=再生/一時停止、←/→=シーク、Esc=終了）。 |
| 🎞 MP4 出力 | JCodec（純Java H.264）内蔵で **mp4 を好きな解像度・好きな FPS** で直接出力。PNG 連番も可。 |
| 🖱 GUI | **タイトル画面に「リプレイ一覧」ボタン**、**ポーズ画面に「録画開始/停止」ボタン**。 |

### モーションブラーは適用される？

**はい。** 再生・出力は通常のレンダーパイプライン（`GameRenderer`）をそのまま通すため、モーションブラー Mod（や Iris シェーダー等）は**自動的に適用されます**。カメラを毎フレーム動かしているので、モーションブラーも正しく反応します。パイプラインを回避する処理は入れていません。

---

## 仕組み

```
録画（軽量・リアルタイム）                     出力（高負荷・オフライン）
┌──────────────────────────┐            ┌──────────────────────────────┐
│ 毎 tick(20/s) に:         │            │ 記録を 1 tick ずつ再生し、     │
│ ・レンダーカメラを数バイト  │  ──────▶  │ ・オフスクリーン FBO に描画      │
│ ・キーフレームごとに         │   .fpr     │ ・任意の解像度(4K/8K)・FPS     │
│   エンティティ+HUD状態      │  ファイル   │ ・サブtick補間で滑らかに        │
│ ・ブロック変更(パケット)     │            │ ・MP4 / PNG に書き出し         │
└──────────────────────────┘            └──────────────────────────────┘
```

---

## ビルド

要件: **JDK 21**。

```bash
./gradlew build          # jar を build/libs/ に生成
./gradlew runClient      # 開発環境で起動
```

> **GitHub Actions でビルドする場合**: このリポジトリの `.github/workflows/build.yml` をプッシュすると
> 自動でビルドされ、`fps-replay` アーティファクト（jar）が生成されます。
> ラッパー（`gradle-wrapper.jar`）は含まれていないため、ローカルでは
> Gradle 8.10+ を用意して `gradle wrapper` を実行するか、`gradle` を直接使ってください。

### バージョン変更

`gradle.properties` を書き換えるだけで対象バージョンを変更できます:

```properties
minecraft_version=1.21.1
yarn_mappings=1.21.1+build.3
loader_version=0.16.9
fabric_version=0.102.0+1.21.1
```

---

## 使い方

### 録画
```
/record start [name]        録画開始（省略時は日時で命名）
/record stop                録画停止＆保存
/record status              状態確認
```
ポーズ画面の **「● 録画開始 / ■ 録画停止」** ボタンでも操作できます。

### プレビュー
```
/replay preview <name>      その場で再生
/replay list                リプレイ一覧画面を開く（タイトル画面からも開ける）
```
再生中: `Space`=再生/一時停止、`←`/`→`=シーク、`Esc`=終了。

### エクスポート（mp4 / png、好きな解像度・FPS）
```
/replay render <name> [WxH] [fps] [mp4|png]
```
例:
```
/replay render myclip 3840x2160 360        # 4K / 360fps / mp4
/replay render myclip 7680x4320 60 mp4     # 8K / 60fps / mp4
/replay render myclip 1920x1080 60 png     # 1080p / PNG連番
/replay render myclip                       # 設定の既定値 (4K / 360fps / mp4)
```

### 出力先
- 録画: `<ゲームディレクトリ>/replays/<name>.fpr`
- 出力: `<ゲームディレクトリ>/replays/<name>_out/output.mp4`（または `frame_XXXXXXXX.png`）

> 出力速度は実描画 FPS に依存します（オフライン再レンダリングのため）。
> 速くしたい場合は **垂直同期オフ・FPS 制限なし** にすると高速化します。

---

## 設定（`config/fps-replay.properties`）

| キー | 既定値 | 説明 |
| --- | --- | --- |
| `compressionLevel` | `6` | gzip 圧縮レベル (0–9) |
| `keyframeInterval` | `20` | キーフレーム間隔 (tick) |
| `entityRange` | `64` | 記録対象エンティティの半径 (ブロック) |
| `recordBlockChanges` | `true` | ブロック変更パケットを記録するか |
| `renderWidth` / `renderHeight` | `3840` / `2160` | コマンド未指定時の出力解像度 (4K) |
| `renderFps` | `360` | コマンド未指定時の出力 FPS |
| `defaultFormat` | `mp4` | 既定の出力形式 (`mp4` / `png`) |
| `interpolationMode` | `linear` | `linear` or `spline` |

---

## ファイルフォーマット（`.fpr`）

```
[4B マジック "FPRL"][4B バージョン (LE)][メタデータ][gzip 圧縮されたレコード列 ... END]
```

レコード種別（`RecordType`）:

| 種別 | 内容 |
| --- | --- |
| `KEYFRAME` | カメラ完全精度 + HUD状態 + 近傍エンティティ全量（`keyframeInterval` ごと） |
| `TICK` | キーフレームからの zigzag varint デルタ（位置 1/4096 ブロック、角度 0.01°）。tick は差分保存で**1バイト** |
| `BLOCK_CHANGE` | ブロック変更（絶対座標 + レジストリID） |
| `END` | 終端 |

---

## ソース構成

```
src/main/java/dev/ifuto/fpsreplay/
├── replay/            # ファイルフォーマット（MC非依存のコアロジック）
│   ├── ReplayFile.java        # .fpr コンテナ
│   ├── ReplayWriter.java      # zigzag varint デルタ書き込み
│   ├── ReplayReader.java      # 読み取り + ReplayState 構築
│   ├── HudState.java          # 体力/エフェクト/スコアボード/プレイヤーリスト
│   └── Interpolation.java     # サブtick補間（線形 / Catmull-Rom）
├── client/            # Fabric クライアント側
│   ├── Recorder.java          # 軽量録画（毎tick、ゼロアロケーション）
│   ├── CameraCapture.java     # 実レンダーカメラの捕捉
│   ├── HudCapture.java        # HUD状態の捕捉
│   ├── HudApplier.java        # HUD状態の注入
│   ├── Renderer.java          # 出力(MP4/PNG) + プレビュー
│   ├── Mp4Exporter.java       # JCodec ラッパー
│   ├── ReplayListScreen.java  # リプレイ一覧画面
│   └── ReplayCommands.java    # /record, /replay コマンド
└── mixin/             # 描画ループ・カメラ・HUD・画面・キー入力フック
```

---

## 既知の制限と今後の拡張

- **ワールド再現**: 現在は「同じシードのワールドで録画→再生」を前提に、ライブのクライアントワールドへカメラを当てて描画します。記録したブロック変更は保存されますが、シードからの完全なチャンク再構築（`ReplayWorld`）は今後実装予定です。
- **インベントリ/ホットバー/クロスヘア**: HUD のうちスコアボード・エフェクト・プレイヤーリスト・体力等は再現しますが、ホットバーやインベントリの**中身**は記録していません（出力時はライブの内容が映ります）。記録対象を拡張予定です。
- **プレイヤーリストの注入**: データは記録済み（`HudState.playerList`）ですが、`PlayerListS2CPacket.Entry` の再構築が未対応のため、再生中の Tab 表示への注入は今後対応します。
- **テキストの色/装飾**: スコアボード等のテキストは JSON で保存しており、装飾を保持します。

## ライセンス

MIT（`LICENSE` 参照）。JCodec（BSD ライク）は jar 内に同梱されます。
