#!/usr/bin/env bash
# Tests detect-changes.sh against a throwaway git repository. Exits with a non-zero code when a case fails.
set -eu

script="$(cd "$(dirname "$0")" && pwd)/detect-changes.sh"
repository="$(mktemp -d)"
trap 'rm -rf "$repository"' EXIT
cd "$repository"
git init --quiet
git config user.email "test@example.invalid"
git config user.name "Test"
git config commit.gpgsign false
git config core.autocrlf false

mkdir -p docs module/src/main/resources
echo "readme" > README.md
echo "guide" > docs/guide.md
echo "code" > module/Main.kt
echo "packaged" > module/src/main/resources/notes.md
git add --all
git commit --quiet --message "base"
base="$(git rev-parse HEAD)"

failures=0

# Commits the working tree on top of base, runs the script and compares the answer.
check() {
  local name="$1" expected="$2"
  git add --all
  git commit --quiet --allow-empty --message "$name"
  local actual
  actual="$("$script" "$base" "$(git rev-parse HEAD)")"
  if [ "$actual" = "$expected" ]; then
    echo "ok   $name"
  else
    echo "FAIL $name: expected $expected, got $actual"
    failures=$((failures + 1))
  fi
  git reset --quiet --hard "$base"
}

echo "changed" >> README.md
check "a modified Markdown file skips" false

echo "new" > docs/new.md
echo "changed" >> docs/guide.md
check "modified and added Markdown files skip" false

echo "changed" >> README.md
echo "changed" >> module/Main.kt
check "Markdown with code runs" true

echo "changed" >> module/src/main/resources/notes.md
check "Markdown under src runs" true

git rm --quiet docs/guide.md
check "a deleted Markdown file runs" true

git mv docs/guide.md docs/renamed.md
check "a renamed Markdown file runs" true

check "no changed file runs" true

actual="$("$script" "0000000000000000000000000000000000000000" "$base")"
[ "$actual" = true ] && echo "ok   an all-zero base runs" || { echo "FAIL an all-zero base: got $actual"; failures=$((failures + 1)); }

actual="$("$script" "1234567890123456789012345678901234567890" "$base")"
[ "$actual" = true ] && echo "ok   a missing base runs" || { echo "FAIL a missing base: got $actual"; failures=$((failures + 1)); }

actual="$("$script" "" "$base")"
[ "$actual" = true ] && echo "ok   an empty base runs" || { echo "FAIL an empty base: got $actual"; failures=$((failures + 1)); }

# A pull request compares from the merge base, so changes made on the base branch afterwards do not count.
git checkout --quiet -b feature
echo "changed" >> docs/guide.md
git commit --quiet --all --message "feature docs"
feature="$(git rev-parse HEAD)"
git checkout --quiet -
echo "changed" >> module/Main.kt
git commit --quiet --all --message "main code"
main="$(git rev-parse HEAD)"
actual="$("$script" "$main" "$feature" three-dot)"
[ "$actual" = false ] && echo "ok   a pull request with only Markdown skips" || { echo "FAIL a pull request with only Markdown: got $actual"; failures=$((failures + 1)); }

if [ "$failures" -gt 0 ]; then
  echo "$failures case(s) failed"
  exit 1
fi
echo "All cases passed"
