# DonutVision-New (Fabric 26.1.2)

Это **исходный код** клиента DonutVision 1.9.9, разложенный **в корне проекта** специально для чистого GitHub-репозитория `DonutVision-New`.

В новой сборке предусмотрены пункты **Schematic Printer** (Modules, первый переключатель), Auto Attack Macro, Block ESP, Base Finder и другие модули.

## Загрузка файлов на GitHub (Windows)

1. Скачайте ZIP `DonutVision-New-ROOT-1.9.9.zip` и **распакуйте**.
2. Откройте репозиторий `https://github.com/DarkEntity32/DonutVision-New`.
3. Выберите **Add file → Upload files**.
4. Перетащите **всё содержимое** распакованной папки: `.github`, `src`, `build.gradle`, `settings.gradle`, `gradle.properties`, `README.md`. **Не загружайте сам ZIP и не создавайте сверху ещё одну папку**.
5. Нажмите **Commit changes**. Если появится запрос о замене README, подтвердите.
6. Проверьте: на главной странице GitHub сразу видны `build.gradle`, `gradle.properties`, `src` и `.github/workflows` — **не** папка `DonutVision-26.1.2`.
7. Откройте **Actions → Build DonutVision-New 1.9.9 → Run workflow** (или дождитесь сборки после загрузки файлов).
8. Если все шаги зелёные, откройте Summary → **Artifacts** → `DonutVision-New-1.9.9-JAR`. Скачайте ZIP артефакта, распакуйте и положите находящийся внутри **JAR** в `mods` своего профиля Prism Launcher.
9. Уберите все старые JAR `DonutVision` из этого же `mods`, иначе два одинаковых mod ID конфликтуют.

## Проверка в игре

- При входе в мир должно появиться: `v1.9.9 loaded! Printer ON/OFF...`.
- **Right Shift** или **F10** открывает меню. **Modules → Schematic Printer** — первый переключатель ON/OFF. **F12** переключает то же самое.
- Установите совместимые Litematica, MaLiLib и Fabric API. Загрузите и разместите схему; кладите материалы в хотбар. Держите ПКМ недалеко от схемы.
- Код Printer не использует Litematica Easy Place; пытается читать виртуальный мир схемы и ставить блоки обычным способом, ограниченным серверной дальностью.

## Что гарантируется и что нет

GitHub Actions проверяет **компиляцию, метаданные версии и наличие Java-классов**. Это **не** тест функциональности Litematica в Minecraft: если Printer не сработает, откройте меню и пришлите скриншот `SCHEMATIC PRINTER STATUS` и `latest.log`.

Никакой клиентский мод не может гарантировать бесконечную дальность установки на обычном сервере. Код на момент упаковки не был протестирован в запущенном Minecraft, и компиляция в GitHub может потребовать исправлений.
