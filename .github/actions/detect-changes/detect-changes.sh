#!/usr/bin/env bash
# Decides whether a change needs the full CI. Prints "true" to run it or "false" to skip the heavy jobs.
#
# Usage: detect-changes.sh <base> <head> [three-dot]
#   base       The commit to compare from. Empty or all zeros means there is nothing to compare with.
#   head       The commit to compare to.
#   three-dot  Compare from the merge base of base and head, as a pull request does.
#
# It prints "false" only when every changed file is a Markdown file outside any src/ folder that was modified or
# added. Everything else prints "true":
# - Deleted or renamed files: verifyModuleReadmes checks that every module keeps its README.md.
# - Markdown under src/: it may be a resource that is packaged.
# - No changed file, a base that is missing or not a commit, or any git failure.
# A build script that packages Markdown from outside src/ must also exclude those files from this rule.
set -u

base="${1:-}"
head="${2:-}"
mode="${3:-}"

if [ -z "$base" ] || [ -z "$head" ] || [[ "$base" =~ ^0+$ ]]; then
  echo true
  exit 0
fi
if ! git cat-file -e "${base}^{commit}" 2>/dev/null || ! git cat-file -e "${head}^{commit}" 2>/dev/null; then
  echo true
  exit 0
fi

if [ "$mode" = "three-dot" ]; then
  range="${base}...${head}"
else
  range="${base}..${head}"
fi
if ! changes="$(git diff --name-status --find-renames "$range" 2>/dev/null)"; then
  echo true
  exit 0
fi
if [ -z "$changes" ]; then
  echo true
  exit 0
fi

while IFS=$'\t' read -r status path _; do
  case "$status" in
    M | A) ;;
    *)
      echo true
      exit 0
      ;;
  esac
  case "$path" in
    src/* | */src/*)
      echo true
      exit 0
      ;;
    *.md) ;;
    *)
      echo true
      exit 0
      ;;
  esac
done <<< "$changes"

echo false
