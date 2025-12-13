# VitaNest 🪹

**Your holistic daily companion for body, mind, soul, nature, and play.**

VitaNest is an Android app that brings together wellness, mindfulness, spirituality, environmental awareness, entertainment, and intelligent decision-making — all in one nurturing hub.

Inspired by real-life balance and powered by modern AI, VitaNest helps you live more intentionally every day.

## 🌟 Features

### Current & Planned Modules
- **sicksense** – AI-powered health insights (Whoop-style tracking & analysis)
- **flow** – Guided yoga flows and movement videos
- **soul** – Daily prayers, meditations, and spiritual reflections
- **sky** – Beautiful weather, air quality, and location-aware insights
- **playnest** – Curated gaming hub for relaxation and fun
- **council** – *Coming soon* – Multi-LLM AI Council for smarter decisions and family debates

### The AI Council (Council Module)
A "chain of debate" system where multiple free LLMs collaborate to give balanced, thoughtful answers.

- **Chair**: Google Gemini (free API)
- **Council Members**:
  - DeepSeek (deep reasoning)
  - Groq-hosted Llama (speed & quality)
  - Grok (via OpenRouter free tier – witty & truthful)
  - Gemma 3 (offline, on-device privacy)

Perfect for family decisions, personal analysis, or exploring complex topics with reduced bias.

## 🏗️ Architecture

VitaNest/ ├── app/ # Shell: launcher, theme, navigation host ├── core/ │ ├── ui/ # Shared theme, composables, icons │ ├── data/ # API clients, Room, WorkManager │ └── common/ # Extensions, constants, utilities ├── features/ │ ├── sicksense/ # Health AI module │ ├── flow/ # Yoga & movement │ ├── soul/ # Prayers & spirituality │ ├── sky/ # Weather + GPS │ ├── playnest/ # Gaming hub │ └── council/ # ← Multi-LLM debate council (in progress) ├── buildSrc/ # Version catalog & dependencies └── settings.gradle.kts


- Built with **Kotlin** + **Jetpack Compose**
- Modular, clean architecture
- Offline-first where possible

## 🚀 Getting Started

### Prerequisites
- Android Studio (latest stable)
- Google AI Studio API key (for Gemini)
- API keys for DeepSeek, Groq, OpenRouter (all have free tiers)

### Setup
1. Clone the repo
   ```bash
   git clone https://github.com/yourusername/VitaNest.git
