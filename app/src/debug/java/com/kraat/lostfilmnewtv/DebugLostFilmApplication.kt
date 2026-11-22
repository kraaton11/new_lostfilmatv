package com.kraat.lostfilmnewtv

// DebugLostFilmApplication и LostFilmDebugHooks удалены.
// Подмена зависимостей в тестах теперь через @TestInstallIn Hilt-модули:
//   - TestDataModule  — фейковые LostFilmRepository и AuthRepositoryContract
//   - TestNetworkModule — test HTTP client
//
// Smoke-тест: AnonymousBrowsingSmokeTest (@HiltAndroidTest)
