import SwiftUI

struct ContentView: View {
    @Bindable var store: WheelStore
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        GeometryReader { geometry in
            ZStack {
                FoodTheme.cream.ignoresSafeArea()
                FoodRunContent(
                    store: store,
                    wheelWidth: min(
                        geometry.size.width - FoodSpacing.s74,
                        FoodSpacing.s355
                    )
                )

                if store.showWinner, let winner = store.winner {
                    WinnerOverlay(person: winner, reduceMotion: reduceMotion) {
                        withAnimation(.easeOut(duration: 0.2)) { store.dismiss() }
                    }
                    .transition(.opacity)
                    .zIndex(2)
                }
            }
            .animation(reduceMotion ? nil : .spring(response: 0.45, dampingFraction: 0.82), value: store.showWinner)
        }
        .sheet(isPresented: Binding(
            get: { store.crewPresented },
            set: { if !$0 && store.crewPresented { store.dismiss() } }
        )) {
            CrewSheet(store: store)
        }
        .sheet(isPresented: Binding(
            get: { store.historyPresented },
            set: { if !$0 && store.historyPresented { store.dismiss() } }
        )) {
            HistorySheet(history: store.history, onDone: store.dismiss)
        }
        .alert(FoodStrings.text.appName, isPresented: Binding(
            get: {
                store.state.persistenceError &&
                    (store.state.destination == .main || store.state.destination == .winner)
            },
            set: { if !$0 { store.dismissError() } }
        )) {
            Button(FoodStrings.text.done, action: store.dismissError)
        } message: {
            Text(store.state.persistenceErrorMessage ?? "")
        }
    }
}
