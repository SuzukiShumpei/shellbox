# Shell Box

散らばっているシェルスクリプトを一覧化し、簡単に実行できるようにするためのツールです。

## 使い方

1. shellbox repositoryをcloneする。
2. [Releases](https://github.com/SuzukiShumpei/shellbox/releases) から最新のDMGファイルをダウンロードし、インストールする。
3. Shell Boxを起動し、cloneしたrepositoryのpathを指定する。
4. 3によって`shellbox/カテゴリー/scripts`にあるスクリプトが一覧に表示・実行できるようになる。

Shell Boxを起動すると上部メニューにアイコンが追加され、アイコンをクリックすると以下のメニューが表示されます。

- アプリWindowを最上位に表示
- アプリWindowを非表示（アプリは起動継続）
- 実行スクリプト履歴の高頻度5件（クリックすると実行されるショートカット機能）
- Shell Boxの終了

## スクリプトの追加方法

### 通常

1. `shellbox/scripts` 以下にカテゴリーのディレクトリを作成する。（既にある場合は作成不要）
    - 例）`shellbox/scripts/android`
2. カテゴリーディレクトリの下に、スクリプトIDのディレクトリを作成する。
    - スクリプトIDは一意である前提で、半角英数字とハイフンのみを使用すること。:例）`hello-world`
3. 作成したスクリプトIDのディレクトリに、スクリプトファイル（sh, command, batなど）を配置する。
    - 例）`hello-world.sh`
4. 同じディレクトリに、スクリプトの説明を記載した`README.md`を配置する。
    - `scripts/template/README.md` をコピーして使用すること。
5. 共有したい場合は作成したスクリプトIDのディレクトリをgit pushする。

### 外部スクリプトのimport

別ディレクトリにあるスクリプトをimportすることができます。別repositoryにあるスクリプトを操作したい場合に便利です。

1. 外部スクリプトを登録ボタンでpathを設定し、追加する。
2. importした外部スクリプトに関するREADME.mdはpushしてOK。
3. 他者が追加した外部スクリプトを利用する場合は詳細画面でpathの指定をすれば利用できるようになる。

---

## 開発者向け

### 開発環境

- Android Studio
- Kotlin Multiplatform Desktopアプリ(Mac OS向け)

### Build 方法

```shell
./gradlew :composeApp:run
```

### DMGファイルの生成方法

```shell
./gradlew :composeApp:packageDmg
```

実行後、`root/composeApp/build/compose/binaries/main/dmg/`に`ShellBox.dmg`が生成されます。
