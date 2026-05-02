# Kotlin 開發與 AI 協作規範

## 核心協作原則
- 嚴格遵守「先討論、後修改、等指令、再行動」。
- 在沒有明確說「執行下一步」前，請不要自行展開後續動作。

## 程式碼註釋要求
- 語言：**必須使用繁體中文（Traditional Chinese）** 撰寫所有註解與 KDoc。
- 風格：以「客觀描述」功能為主，嚴禁主觀語氣或對話式口吻。
- 完整性：所有 Kotlin 代碼必須包含完整的註釋與 Kotlin Doc。

## Kotlin 開發規範
- 體系結構：以 Clean Architecture 為原則，保持代碼簡潔有力。
- 測試框架：**嚴禁使用 JUnit**。僅允許使用 `kotlin.test.Test`。
- 測試命名：方法名必須使用反引號格式，例如：`test sorting with different regional orders`。

## Git 提交規範
- **語言**: **必須使用英文（English）** 撰寫所有提交訊息。
- **格式**: 嚴格遵守 **Conventional Commits** 規範。
  - 格式為：`<type>(<scope>): <subject>`
  - 常見 `type` 包括：
    - `feat`: 新增功能 (A new feature)
    - `fix`: 修復錯誤 (A bug fix)
    - `refactor`: 重構程式碼，未新增功能或修復錯誤 (A code change that neither fixes a bug nor adds a feature)
    - `style`: 不影響程式碼意義的變更（空白、格式、分號等）
    - `docs`: 僅文件變更 (Documentation only changes)
    - `test`: 新增或修改測試
    - `build`: 影響建置系統或外部依賴的變更（例如：Gradle）
    - `ci`: CI/CD 設定檔與腳本的變更

## 輸出格式
- 完整性：必須提供「完整的檔案內容」，不得有任何省略（如 // ... existing code ...）。
- 路徑標註：在對話中的代碼區塊上方必須註明該檔案的存放路徑。

## 專案模組架構 (Project Architecture)
本專案遵循 Clean Architecture 原則，將程式碼劃分為獨立的模組，並在內部採用 **Package by Feature (PBF)** 策略進行組織，以實現關注點分離、高內聚性與高可維護性。

- **`:domain`**: 核心領域層。
  - **用途**: 包含最純粹的麻將核心業務規則、實體 (Entities) 和值物件 (Value Objects)（如手牌邏輯、規則配置數據類別）。
  - **特點**: 這是一個純 Kotlin 模組，不依賴任何外部框架或平台 API (包括 Minecraft, Koin, Coroutines, Serialization)。

- **`:application`**: 應用服務層。
  - **用途**: 負責編排業務流程，包含具體的業務實現 (Use Cases) 以及定義資料存取的契約介面 (Repositories)。
  - **特點**:
    - 依賴 `:domain` 模組。
    - 此層級為非同步流程的核心，引入 Coroutines 以非同步的業務操作。
    - 僅定義 Repository 介面，具體實作則交由外層處理

- **`:infrastructure`**: 技術基礎設施層。
  - **用途**: 負責技術細節的實現與系統組裝。包含依賴注入 (DI) 的模組配置、資料持久化實作、DTO 以及序列化邏輯。
  - **特點**: 依賴 `:application` 與 `:domain`，作為整個系統的組裝中心 (Composition Root)。

- **`:testing`**: 測試輔助工具模組。
  - **用途**: 提供跨模組共享的測試物件（如 Fake 物件、`TestCoroutineDispatchers`）。
  - **結構**: 採用與產品對稱的階層化子模組結構（如 `:testing-domain`, `:testing-application` 等），以確保依賴鏈單向且清晰。
  - **特點**:
    - **依賴原則**: 各測試子模組依賴對應層級的生產代碼模組（例如 `:testing-domain` 依賴 `:domain`），嚴禁反向依賴。
    - **跨平台支持**: 嚴禁使用 JVM 特有的 `testFixtures`，以純 Kotlin 模組建構以支援未來 Kotlin Multiplatform 擴展。
    - **零污染**: 僅包含測試專用程式碼，不影響生產環境（Production）的依賴方向。

- **`:platform`**: 平台適配層。
  - **用途**: 包含所有與特定平台（如 Minecraft, Hytale）相關的具體實現 (Adapters)。它負責實現 `:application` 層定義的介面，並作為最終的執行進入點。
  - **結構**:
    - `platform/{platform}/common`: 平台通用程式碼。
    - `platform/{platform}/{version}/common`: 特定版本的通用程式碼。
    - `platform/{platform}/{version}/{loader}`: 特定版本與載入器的入口點和實作。

## 依賴規範
為確保架構的清晰與穩定，所有模組必須嚴格遵守以下依賴規則，形成單向依賴鏈。

- **依賴方向**: `platform` -> `:infrastructure` -> `:application` -> `:domain`
- **禁止反向依賴**: `:domain` 嚴禁依賴任何外層模組。`:application` 嚴禁依賴 `:infrastructure` 或 `platform`。
- **禁止跨層依賴**: `platform` 模組嚴禁直接依賴 `:application` 或 `:domain` 模組，必須透過 `:infrastructure` 層提供的組裝結果進行交互。
- **同層依賴**:
  - 在 `platform` 層內部，具體實現模組 (如 `fabric`) 應依賴其對應的通用模組 (如 `common`)。
  - 範例: `:minecraft_v1_20_1_fabric` -> `:minecraft_v1_20_1_common` -> `:minecraft_common`。

## 臨時文件管理
- 所有開發過程中產生的臨時性指令、邏輯草稿或一次性 Prompt 檔案，必須統一生成於 `docs/temp/` 目錄。
- 禁止在專案根目錄或 `src/` 目錄下直接產生非代碼性質的 `.md` 檔案。
- `docs/temp/` 目錄已加入 `.gitignore`，不會被 git 追蹤。