#!/usr/bin/env bash
# FinanceHub 開發環境變數
# 用法: source ./setenv.sh

export JAVA_HOME="/opt/homebrew/opt/openjdk@21"
export PATH="$JAVA_HOME/bin:$PATH"

echo "JAVA_HOME=$JAVA_HOME"
java -version
