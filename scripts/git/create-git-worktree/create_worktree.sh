#!/bin/bash

# =================================================================
# Git Worktree 作成とlocal.propertiesのコピー
# =================================================================

CONFIG_FILE="local.properties"
REPO_ROOT_PATH="$(pwd)" # メインリポジトリの絶対パスを定義

echo "--- Git Worktree 設定開始 ---"

# --- 1. 既存のWorktreeパスのプレフィックスを抽出 ---
echo "--- 既存の Worktree パスを解析中 ---"

# git worktree list の出力から Worktree 名を抽出・整形する
EXISTING_PREFIXES=$(git worktree list | \
    # 1列目のパスのみを抽出
    awk '{print $1}' | \
    # メインリポジトリの絶対パス部分を除去し、Worktreeの相対パスのみを残す
    sed "s|^${REPO_ROOT_PATH}/||" | \
    # パスから最初のディレクトリ名（プレフィックス）を抽出
    awk -F/ '{print $1}' | \
    # '..' や空行を除去し、一意にする
    grep -v '^\.\.$' | grep -v '^$' | sort -u)

PREFIX_ARRAY=()
# 【修正箇所】: mapfile の代わりに while read ループで配列に格納
while IFS= read -r LINE; do
    PREFIX_ARRAY+=("$LINE")
done <<< "$EXISTING_PREFIXES"

# --- 2. パス選択または新規入力 ---

echo ""
if [ ${#PREFIX_ARRAY[@]} -gt 0 ]; then
    echo "1. Worktree を作成するパスの**プレフィックス**を選択してください:"
    
    # 選択肢の表示
    for i in "${!PREFIX_ARRAY[@]}"; do
        echo "   $((i+1))) ${PREFIX_ARRAY[i]}"
    done
    echo "   0) 新しいプレフィックスを入力する"

    read -p "選択肢の番号を入力 (0-${#PREFIX_ARRAY[@]}): " CHOICE

    if [ "$CHOICE" -gt 0 ] 2>/dev/null && [ "$CHOICE" -le ${#PREFIX_ARRAY[@]} ] 2>/dev/null; then
        SELECTED_PREFIX="${PREFIX_ARRAY[$((CHOICE-1))]}"
        read -p "   選択: ${SELECTED_PREFIX}/<新しいディレクトリ名> → 新しいディレクトリ名を入力: " NEW_DIR_NAME
        WORKTREE_PATH="${SELECTED_PREFIX}/${NEW_DIR_NAME}"
    else
        read -p "   新しいプレフィックスを入力してください: " WORKTREE_PREFIX
        read -p "   新しいディレクトリ名を入力してください: " NEW_DIR_NAME
        WORKTREE_PATH="${WORKTREE_PREFIX}/${NEW_DIR_NAME}"
    fi
else
    # 既存のWorktreeがない場合
    read -p "1. Worktree のプレフィックスを入力してください (例: 202602-os-update): " WORKTREE_PREFIX
    read -p "   新しいディレクトリ名を入力してください (例: bugfix-13458): " NEW_DIR_NAME
    WORKTREE_PATH="${WORKTREE_PREFIX}/${NEW_DIR_NAME}"
fi

if [ -z "$WORKTREE_PATH" ]; then
    echo "エラー: Worktree パスが入力されていません。"
    exit 1
fi

echo ""

# --- 3. ブランチ名入力 ---
read -p "2. チェックアウト/作成するブランチ名を入力してください: " BRANCH_NAME

if [ -z "$BRANCH_NAME" ]; then
    echo "エラー: ブランチ名が入力されていません。"
    exit 1
fi

echo ""

# --- 4. Worktreeの追加 ---
echo "3. Git Worktree を作成します: $WORKTREE_PATH をブランチ $BRANCH_NAME で作成"
git fetch
git worktree add "$WORKTREE_PATH" "$BRANCH_NAME"
if [ $? -ne 0 ]; then
    echo "エラー: Worktreeの作成に失敗しました。"
    exit 1
fi

# --- 5. 設定ファイルのコピー ---
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

# --- 6. 新しい Worktree に移動 ---
echo ""
echo "5. 新しい Worktree に移動します..."
cd "$WORKTREE_PATH"
echo "--- 作業ディレクトリへ移動しました ($PWD) ---"

echo "--- 設定完了 ---"
