<p align="center"><img src="docs/assets/lifehub-icon.png" alt="Original LifeHub sprout app icon" width="112" /></p>
<h1 align="center">Lifehub Plus</h1>
<p align="center">Built on LifeHub. Reimagined with local AI.</p>

<p align="center">
  <img src="https://img.shields.io/badge/Android-Java%20%2B%20Kotlin-19675D?style=flat-square" alt="Android Java and Kotlin" />
  <img src="https://img.shields.io/badge/Storage-Room%20%2F%20SQLite-19675D?style=flat-square" alt="Room and SQLite" />
  <img src="https://img.shields.io/badge/Local%20AI-Ollama%20%2B%20Qwen3-19675D?style=flat-square" alt="Ollama and Qwen3" />
  <img src="https://img.shields.io/badge/Language-English%20%2F%20Chinese-19675D?style=flat-square" alt="English and Simplified Chinese" />
</p>

<p align="center"><b>Describe it. Review it. Make it happen.</b><br/>Turn natural language into everyday actions.</p>
<p align="center"><a href="#project-background">Background</a> · <a href="#app-preview">Preview</a> · <a href="#from-lifehub-to-plus">Improvements</a> · <a href="#run-locally">Getting Started</a> · <a href="#project-structure">Project Structure</a></p>

## Project Background

**Lifehub Plus is my personal extension of [LifeHub](https://github.com/lana0323/LifeHub), a project I previously helped develop.** The original application provided the life-management modules, login flow and navigation framework. Plus builds on that foundation with local AI integration, consistent interactions and more reliable data handling.

My contributions to the original project included product planning, application structure, login, UI and module integration. In this iteration, I focused on making AI-generated actions reviewable, saved records verifiable and failures recoverable. The comparison below distinguishes the original foundation from the improvements introduced in Plus.

> This project demonstrates **Android engineering and AI application integration**. It uses an existing Qwen3 model for local inference; it does not involve training or fine-tuning a model and does not require a paid cloud model service.

## App Preview

A connected workspace for notes, money, plans and digital wellbeing — with AI to turn requests into reviewable actions.

<table align="center">
  <tr><th align="center">Home</th><th align="center">AI Assistant</th><th align="center">Memo</th></tr>
  <tr>
    <td align="center" valign="top"><img src="docs/assets/home.png" width="240" alt="Current LifeHub home with all four module cards" /></td>
    <td align="center" valign="top"><img src="docs/assets/ai.png" width="240" alt="Current AI assistant with automatic module selection" /></td>
    <td align="center" valign="top"><img src="docs/assets/memo.png" width="240" alt="Current Memo screen with priorities and sample tasks" /></td>
  </tr>
  <tr><th align="center">Finance</th><th align="center">Schedule</th><th align="center">Health</th></tr>
  <tr>
    <td align="center" valign="top"><img src="docs/assets/finance.png" width="240" alt="Current Finance dashboard with monthly totals and category charts" /></td>
    <td align="center" valign="top"><img src="docs/assets/schedule.png" width="240" alt="Current Schedule calendar with a sample event and dated memo" /></td>
    <td align="center" valign="top"><img src="docs/assets/health.png" width="240" alt="Current Health screen showing Android emulator usage statistics" /></td>
  </tr>
</table>

Captured from the current Android build on October 2, 2026. Memo, Finance and Schedule use fictional demonstration records in an isolated account. Health displays the emulator's actual Android usage data, not sample statistics. See the [capture notes](docs/assets/README.md) for reproduction details.

**Same LifeHub identity, expanded capabilities.** Lifehub Plus retains the original LifeHub sprout icon, application branding and package name.

## From LifeHub to Plus

| Area | Original LifeHub foundation | Improvements in Plus |
|---|---|---|
| AI integration | Existing AI screen and module handlers | Local model inference, structured drafts, module detection, field validation and user confirmation before execution |
| Connected modules | Separate Memo, Finance, Schedule and Health features | Natural-language requests open the appropriate form or usage page; dated memos also appear in the schedule |
| Request experience | Existing screen interactions | Elapsed-time feedback, cancellation, preserved input, consistent state across navigation and protection against stale responses |
| Interface and forms | Original login, navigation and module screens | Consistent colors, icons, cards and inputs; short transitions; a draggable bottom sheet for adding financial records |
| Finance | Basic income and expense records | Monthly and yearly views, category breakdowns, trend charts, custom categories and integer minor-unit storage |
| Health statistics | Device usage screens | Foreground-event aggregation by local date, handling of midnight and screen-lock boundaries, and explicit missing-data states |
| Data reliability | Local Room / SQLite storage | Data-preserving migrations, transactions and unique draft IDs to prevent duplicate saves, followed by read-back verification |
| Accounts and recovery | Local login foundation | Separate business databases, drafts and categories for each account; stale-session protection, salted password verification and private avatar copies |
| Language and startup | Existing resources and splash screen | English / Simplified Chinese settings, clearer messages and contrast, and a shorter intentional splash delay |
| Quality assurance | Original testing foundation | Additional backend, unit and device regression coverage, plus documented model evaluations and limitations |

## AI That Connects to Real Features

| Module | Example request | What the user reviews |
|---|---|---|
| **Memo** | Finish the machine learning assignment by next Friday, high priority. | An editable task draft with a title, notes, due date and priority |
| **Finance** | I spent 28 yuan on lunch today and paid with Alipay. | A prefilled transaction form with an amount, date, type, category and account |
| **Schedule** | Schedule a group meeting tomorrow at 3 PM. | An editable event draft with a date, time and description |
| **Health** | Show my screen time today. | The existing device usage page, populated with Android system data |

### From request to action

**Describe → Review → Confirm**

1. **Describe** a task, transaction or event in your own words, or ask about screen time.
2. **Review** the suggested module and draft. Edit any details before continuing.
3. **Confirm** to save the record, or open Health to view device usage. Saved records are read back to verify the result.

- **The model cannot write directly to the database.** Records require user confirmation. An incorrectly detected module can be changed manually.
- **Missing information stays visible.** Ambiguous dates remain unset, required fields must be completed, and requests containing multiple actions are prompted to be split.
- **Repeated confirmation does not create duplicates.** A draft ID identifies a single saved record within its account. Newly generated drafts and manual entries are not deduplicated by content.
- **Failures are recoverable.** Input is preserved, with cancellation, retry and manual-entry options. Cancelling stops the client from waiting; inference already running in the local model may continue.
- **Weekdays use calendar weeks.** `this Tuesday` means Tuesday of the current Monday-based week; `next Tuesday` means Tuesday of the following week, using your timezone. Schedule drafts keep explicit past dates for review instead of silently moving them forward.
- **Health is a query.** The application displays system usage statistics rather than generating fictional usage figures or inserting health records.

## Engineering

| Layer | Technologies and responsibilities |
|---|---|
| Android | Java / Kotlin, XML / Material Components, Navigation, ViewModel, coroutines and StateFlow |
| Persistence | Room for Memo / Finance, SQLite for Schedule, and account-specific preferences |
| Local AI | Python HTTP backend, structured parsing and validation, Ollama and Qwen3 4B |
| Verification | Python unittest, JUnit, Android instrumentation, Espresso and fixed field evaluations |

**Reliable storage.** Finance stores amounts as integer minor units using `long amountMinor`. Migrations retain original values for comparison and verify record counts, amounts and primary keys; failures roll back. Memo, Finance and Schedule use transactions and unique draft IDs to handle repeated submissions.

**Account boundaries.** Each local account has separate business databases and draft preferences. Database instances remain bound to the account that created them, so an earlier background operation cannot write into a newly signed-in account. On upgrade, legacy shared records are assigned to the account signed in at initialization. If no account is signed in, the records remain preserved but unassigned.

**Lifecycle handling.** An Activity-scoped ViewModel owns AI requests. Navigation and Activity recreation do not resend an in-flight request. After process interruption, the application restores the input and asks the user to retry instead of automatically replaying it.

## Run Locally

You will need Android Studio, Android SDK 34, JDK 21, Python 3.11+ and Ollama. Model weights are not included in the repository and must be downloaded before first use.

**1. Start local Ollama.** Exit any existing Ollama background instance, then run the following in PowerShell from the project root:

```powershell
$env:OLLAMA_NO_CLOUD = '1'
$env:OLLAMA_HOST = '127.0.0.1:11434'
$env:OLLAMA_MODELS = "$PWD\.local\models"
ollama serve
```

**2. Open another terminal, download the model and start the backend.**

```powershell
ollama pull qwen3:4b
python -m pip install -r backend/requirements.txt
.\scripts\start-local.ps1
```

**3. Open the project in Android Studio and run the debug build.** The emulator connects to `http://10.0.2.2:8080/` by default. Register a local account, open AI, describe one action, review the draft and confirm the save.

Manual module features remain available when the backend or model is offline. The four-module endpoint uses local Ollama and does not automatically fall back to a paid cloud service. Downloading and running the model still requires disk space, memory, electricity and network bandwidth.

USB device setup, environment variables and additional instructions are available in the [Development Guide](docs/DEVELOPMENT_GUIDE.md) (Chinese).

## Project Structure

```text
app/src/main/java/com/lifeHub/
  main/       Home and navigation
  ai/         AI requests, draft review and module routing
  todo/       Memos and tasks
  finance/    Transactions and financial charts
  schedule/   Calendar and events
  usage/      Device usage statistics
  login/      Local accounts, sessions and data isolation
  ui/         Shared interface components
app/src/main/res/     Layouts, icons and bilingual resources
app/schemas/          Database version definitions
backend/              Local Python AI service
scripts/              Startup tools
docs/                 Development guides and engineering notes
```

The project includes the Android client, backend service and local persistence layer. Supporting verification code lives separately in Android's `src/test` and `src/androidTest` directories and the backend's `test_*.py` files.

<details>
<summary><b>Developer Documentation and Quality Assurance</b></summary>

The [240-input bilingual evaluation](docs/evaluation-v2/REPORT.md) reports 120 Chinese and 120 English inputs separately, covering routing, required fields, Android workflow entry, clarification and latency. Download the [Chinese results](docs/evaluation-v2/chinese-120.csv) or [English results](docs/evaluation-v2/english-120.csv).

The original [120-case frozen evaluation report](docs/evaluation/REPORT.md) and its [case-level results](docs/evaluation/app-evaluation.csv) remain available. The original 40-case set remains a development/regression set.

Subsequent [AI validation improvements and regression results](docs/evaluation-improvements/README.md) document financial field recovery, ambiguity handling and supported-action checks. These follow-up results use seen development cases and do not replace the original frozen evaluation.

Automated checks cover database migrations, account isolation, repeated submissions and screen restoration. Commands, recorded results and model evaluation scope are documented in [Quality Assurance](docs/QUALITY.md), the [Development Guide](docs/DEVELOPMENT_GUIDE.md) and the [Engineering Review](docs/ENGINEERING_REVIEW.md). These detailed development documents are currently in Chinese.

</details>

## Current Scope and Next Steps

- Account isolation applies to local data. There is no server-side authentication, cloud synchronization, cross-device session management or password recovery. Local databases are not encrypted.
- Health statistics describe the entire device. Local accounts see the same device statistics, subject to Android permissions and event retention.
- Financial amounts currently use CNY, and each AI request produces at most one action draft.
- Next priorities are broader financial vocabulary, remaining model routing/date failures, a new independent evaluation set and reducing main-thread work in schedule persistence.

## Acknowledgements and Provenance

Original project: [lana0323/LifeHub](https://github.com/lana0323/LifeHub). This repository packages the original local baseline `353a9e1` and subsequent improvements as a separate Plus edition. The original package name and module structure are retained for upgrade compatibility.

Existing work in the original project and third-party dependencies remains attributable to its respective authors and rights holders. No new open-source license has been assigned to the original project here; public source visibility does not itself grant unrestricted redistribution rights. Model use is subject to the provider's terms.
