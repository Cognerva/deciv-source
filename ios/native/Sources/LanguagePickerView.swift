import SwiftUI
import UIKit

/// Native language picker, shown once on first launch and from Options. Languages show their
/// own name, so a player can find theirs without reading English.
struct LanguagePickerView: View {
    let state: LanguagesState
    let perform: (String, UIImpactFeedbackGenerator.FeedbackStyle) -> Void

    var body: some View {
        ZStack {
            FormBackground()
            VStack(spacing: 0) {
                VStack(alignment: .leading, spacing: 10) {
                    Emblem(size: 52)
                    Text(state.title)
                        .font(Theme.display(30))
                    Text(state.disclaimer)
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                        .fixedSize(horizontal: false, vertical: true)
                }
                .frame(maxWidth: 640, alignment: .leading)
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.horizontal, 20)
                .padding(.top, 20)
                .padding(.bottom, 14)

                ScrollViewReader { scroller in
                    ScrollView(showsIndicators: false) {
                        LazyVGrid(columns: [GridItem(.adaptive(minimum: 300), spacing: 10)], spacing: 10) {
                            ForEach(state.languages) { language in
                                row(language).id(language.id)
                            }
                        }
                        .padding(.horizontal, 16)
                        .padding(.bottom, 110)
                        .frame(maxWidth: 980)
                        .frame(maxWidth: .infinity)
                    }
                    .onAppear { scroller.scrollTo(state.chosen, anchor: .center) }
                }
            }

            VStack {
                Spacer()
                Button { perform("confirm", .medium) } label: {
                    Text(state.confirm)
                        .font(.headline)
                        .frame(maxWidth: .infinity)
                        .frame(height: 56)
                        .modifier(PrimaryCapsule())
                }
                .buttonStyle(PressableStyle())
                .disabled(state.chosen.isEmpty)
                .opacity(state.chosen.isEmpty ? 0.5 : 1)
                .padding(.horizontal, 20)
                .padding(.top, 14)
                .padding(.bottom, 10)
                .frame(maxWidth: 520)
                .frame(maxWidth: .infinity)
                .background(
                    LinearGradient(colors: [Theme.ink.opacity(0), Theme.ink.opacity(0.92)], startPoint: .top, endPoint: .center)
                        .ignoresSafeArea()
                )
            }
        }
    }

    private func row(_ language: LanguagesState.Language) -> some View {
        let chosen = language.id == state.chosen
        return Button { perform("choose:" + language.id, .light) } label: {
            let nativeName = language.nativeName
            HStack(spacing: 14) {
                GameIcon(name: "FlagIcons/" + language.id, fallback: "globe")
                    .frame(width: 40, height: 40)
                    .clipShape(Circle())
                    .overlay(Circle().strokeBorder(Color.white.opacity(0.15), lineWidth: 1))
                VStack(alignment: .leading, spacing: 3) {
                    Text(nativeName ?? language.name)
                        .font(.body.weight(.semibold))
                        .foregroundStyle(.white)
                        .lineLimit(1)
                    HStack(spacing: 8) {
                        if let nativeName, nativeName != language.name {
                            Text(language.name)
                                .font(.caption)
                                .foregroundStyle(.secondary)
                                .lineLimit(1)
                        }
                        Meter(value: Double(language.percent) / 100, color: chosen ? Theme.accent : Color.white.opacity(0.45), height: 4)
                            .frame(width: 44)
                        Text("\(language.percent)%")
                            .font(Theme.number(.caption2, weight: .medium))
                            .foregroundStyle(.secondary)
                    }
                }
                Spacer(minLength: 4)
                Image(systemName: chosen ? "checkmark.circle.fill" : "circle")
                    .font(.system(size: 22))
                    .foregroundStyle(chosen ? Theme.accent : .white.opacity(0.25))
            }
            .padding(.horizontal, 14)
            .frame(minHeight: 64)
            .background(
                ZStack {
                    CardBackground(shape: RoundedRectangle(cornerRadius: 18, style: .continuous))
                    RoundedRectangle(cornerRadius: 18, style: .continuous)
                        .strokeBorder(chosen ? Theme.accent : .clear, lineWidth: 2)
                }
            )
            .contentShape(Rectangle())
        }
        .buttonStyle(PressableStyle())
    }
}
