import UIKit
import SwiftUI
import ComposeApp

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

struct ContentView: View {
    var body: some View {
        ComposeView()
            .ignoresSafeArea()
            // 홈 인디케이터 등 compose 가 안 칠하는 세이프에어리어 영역이 시스템 다크(검정) 으로
            //   보이는 것을 방지 — 테마 배경색을 뒤에 깔아 화면 전체를 채운다.
            .background(themeBackground.ignoresSafeArea())
    }

    /// AppColors.Bg 와 동일 — 다크: #121417, 라이트: #FFFFFF (시스템 다크모드 대응).
    private var themeBackground: Color {
        Color(UIColor { traits in
            traits.userInterfaceStyle == .dark
                ? UIColor(red: 0x12 / 255.0, green: 0x14 / 255.0, blue: 0x17 / 255.0, alpha: 1.0)
                : UIColor.white
        })
    }
}



