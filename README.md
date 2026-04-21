# Shell Box

散らばっているシェルスクリプトを一覧化し、簡単に実行できるようにするためのツールです。

## 使い方

1. shellbox repositoryをcloneする。
2. release noteから最新のDMGファイルをダウンロードし、インストールする。
3. Shell Boxを起動し、cloneしたrepositoryのrootを指定する。`shellbox/scripts`
   にあるスクリプトが一覧に表示・実行できるようになる。

## スクリプトの追加方法

1. `shellbox/scripts`
   以下にスクリプトIDのディレクトリを作成する。スクリプトIDは一意である前提で、半角英数字とハイフンのみを使用すること。:
   例）`hello-world`
2. 作成したスクリプトIDのディレクトリに、スクリプトファイル（sh, command, batなど）を配置する。: 例）
   `hello-world.sh`
3. 同じディレクトリに、スクリプトの説明を記載した`README.md`を配置する。`scripts/template/README.md`
   をコピーして使用すること。
4. 共有したい場合は作成したスクリプトIDのディレクトリをgit pushする。

---

## 開発者向け

### 開発環境

- Android Studio
- Kotlin Multiplatform Desktopアプリ（Windows動作未確認）

### Build 方法

- on macOS/Linux
  ```shell
  ./gradlew :composeApp:run
  ```
- on Windows
  ```shell
  .\gradlew.bat :composeApp:run
  ```

### DMGファイルの生成方法

- on macOS/Linux
    ```shell
    ./gradlew :composeApp:packageDmg
    ```
- on Windows
    ```shell
    .\gradlew.bat :composeApp:packageDmg
    ```

実行後、`root/composeApp/build/compose/binaries/main/dmg/`に`ShellBox.dmg`が生成されます。
