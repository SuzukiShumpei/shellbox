#!/bin/bash
# =================================================================
# Git Worktree 削除スクリプト
# =================================================================

set -e

REPO_ROOT_PATH="$(pwd)"  # メインリポジトリの絶対パス

# --- 1. 既存Worktreeパスの一覧抽出 ---
echo "--- Git Worktree 情報取得 ---"

WORKTREE_LIST=$(git worktree list | awk '{print $1}')

if [ -z "$WORKTREE_LIST" ]; then
    echo "エラー: 現在worktreeが1つもありません。削除できるworktreeがありません。"
    exit 1
fi

WORKTREE_ARRAY=()
for WT_PATH in $WORKTREE_LIST; do
    # メインリポジトリのルートは除外
    echo "$WT_PATH" | grep -v "^$REPO_ROOT_PATH$" > /dev/null && WORKTREE_ARRAY+=("$WT_PATH")
done

if [ ${#WORKTREE_ARRAY[@]} -eq 0 ]; then
    echo "エラー: メインディレクトリ以外に削除できるworktreeがありません。"
    exit 1
fi

# --- 一覧表示 ---
echo "\n削除できる Worktree ディレクトリ一覧:"
for i in "${!WORKTREE_ARRAY[@]}"; do
    echo "  $((i+1))) ${WORKTREE_ARRAY[$i]}"
done

# --- 2. ユーザによる削除対象選択 ---
while true; do
    read -p "削除対象の番号を入力してください (1-${#WORKTREE_ARRAY[@]}): " CHOICE
    if [[ $CHOICE =~ ^[0-9]+$ ]] && [ $CHOICE -ge 1 ] && [ $CHOICE -le ${#WORKTREE_ARRAY[@]} ]; then
        TARGET_WT="${WORKTREE_ARRAY[$((CHOICE-1))]}"
        break
    else
        echo "無効な入力です。1～${#WORKTREE_ARRAY[@]}の番号を指定してください。"
    fi
done

# --- 3. 削除確認プロンプト ---
echo "\n選択されたWorktree: $TARGET_WT"
while true; do
    read -p "本当にこのworktreeを削除しますか？ (yes/no): " yn
    case $yn in
        [Yy]*|[Yy][Ee][Ss]*)
            break
            ;;
        [Nn]*|[Nn][Oo]*)
            echo "キャンセルしました。"
            exit 0
            ;;
        *)
            echo "'yes' か 'no' でお答えください。"
            ;;
    esac
done

# --- 4. git worktree remove実行・ハンドリング ---
echo "\n削除処理を実行します..."
git worktree remove "$TARGET_WT"
REMOVE_RESULT=$?

if [ $REMOVE_RESULT -eq 0 ]; then
    if [ ! -d "$TARGET_WT" ]; then
        echo "削除が完了しました: $TARGET_WT"
    else
        echo "警告: worktreeディレクトリがまだ存在しています。手動で$TARGET_WT を削除してください。"
    fi
else
    echo "エラー: git worktree remove コマンドに失敗しました。"
    echo "考えられる理由: \n - 対象worktreeに未コミットのファイルが存在する\n - 使用中のプロセスがある\n - ディレクトリのパーミッション等"
    echo "詳細は上記エラーメッセージを確認してください。"
    exit 1
fi
