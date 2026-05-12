#!/bin/bash

# =================================================================
# Git Worktree 作成とlocal.propertiesのコピー
# =================================================================

CONFIG_FILE="local.properties"
REPO_ROOT_PATH="$(git rev-parse --show-toplevel)"
REPO_PARENT_PATH="$(dirname "$REPO_ROOT_PATH")"
REPO_DIR_NAME="$(basename "$REPO_ROOT_PATH")"

echo "--- Git Worktree 設定開始 ---"

# --- 1. worktree 名入力 ---
echo ""
echo "1. Worktree 名を入力してください。"
echo "   例: bugfix-13458"
read -p "   Worktree 名: " WORKTREE_NAME

if [ -z "$WORKTREE_NAME" ]; then
    echo "エラー: Worktree 名が入力されていません。"
    exit 1
fi

# worktree は元 repo の親ディレクトリ配下に sibling で作成する
WORKTREE_DIR_NAME="${REPO_DIR_NAME}-${WORKTREE_NAME}"
WORKTREE_PATH="${REPO_PARENT_PATH}/${WORKTREE_DIR_NAME}"

echo ""

# --- 2. ブランチ名入力 ---
read -p "2. チェックアウト/作成するブランチ名を入力してください: " BRANCH_NAME

if [ -z "$BRANCH_NAME" ]; then
    echo "エラー: ブランチ名が入力されていません。"
    exit 1
fi

echo ""

# --- 3. Worktreeの追加 ---
echo "3. Git Worktree を作成します: $WORKTREE_PATH をブランチ $BRANCH_NAME で作成"
git fetch
git worktree add "$WORKTREE_PATH" "$BRANCH_NAME"
if [ $? -ne 0 ]; then
    echo "エラー: Worktreeの作成に失敗しました。"
    exit 1
fi

# --- 4. 設定ファイルのコピー ---
if [ -f "$CONFIG_FILE" ]; then
    echo "4. 設定ファイル ($CONFIG_FILE) を Worktree にコピーします。"
    DESTINATION_PATH="$WORKTREE_PATH/$CONFIG_FILE"
    
    cp "$CONFIG_FILE" "$DESTINATION_PATH"
    if [ $? -eq 0 ]; then
        echo "   コピーが完了しました。"
    else
        echo "   警告: ファイルのコピーに失敗しました。手動で確認してください。"
    fi
else
    echo "4. 警告: 設定ファイル ($CONFIG_FILE) がメインのルートで見つかりませんでした。スキップします。"
fi

# --- 5. 新しい Worktree に移動 ---
echo ""
echo "5. 新しい Worktree に移動します..."
cd "$WORKTREE_PATH"
echo "--- 作業ディレクトリへ移動しました ($PWD) ---"

echo "--- 設定完了 ---"
