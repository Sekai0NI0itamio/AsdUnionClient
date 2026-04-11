#!/usr/bin/env bash
set -euo pipefail

usage() {
    cat <<'EOF'
Usage: scripts/github-remote-build.sh [--ref <git-ref>] [--workflow <workflow-file>] [--artifact <artifact-name>] [--commit-message <message>] [--no-push]

By default this script:
- stages build-relevant "vital" repo paths
- commits them if needed
- pushes HEAD to the selected ref on origin
- triggers the GitHub Actions build remotely
- waits for it to finish
- downloads the build outputs

Downloaded outputs include:
- the extracted workflow artifact
- a local bundle zip of that artifact
- the built jar(s) from build/libs
- the uploaded build reports
- the workflow log

Outputs are written to:
  github-build-output/latest/

Defaults:
  --ref <current-branch> (falls back to main)
  --workflow build.yml
  --artifact AsdUnionClient-build
  --commit-message "chore: sync vital files for remote build"
EOF
}

die() {
    echo "Error: $*" >&2
    exit 1
}

require_command() {
    command -v "$1" >/dev/null 2>&1 || die "Required command not found: $1"
}

parse_repo_slug() {
    local remote_url="$1"

    case "$remote_url" in
        git@github.com:*.git)
            printf '%s\n' "${remote_url#git@github.com:}" | sed 's/\.git$//'
            ;;
        git@github.com:*)
            printf '%s\n' "${remote_url#git@github.com:}"
            ;;
        https://github.com/*.git)
            printf '%s\n' "${remote_url#https://github.com/}" | sed 's/\.git$//'
            ;;
        https://github.com/*)
            printf '%s\n' "${remote_url#https://github.com/}"
            ;;
        *)
            return 1
            ;;
    esac
}

contains_line() {
    local needle="$1"
    local haystack="${2:-}"

    if [[ -z "$haystack" ]]; then
        return 1
    fi

    grep -Fqx "$needle" <<<"$haystack"
}

print_path_list() {
    local path

    for path in "$@"; do
        printf '  %s\n' "$path"
    done
}

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd -- "$SCRIPT_DIR/.." && pwd)"

CURRENT_BRANCH="$(git -C "$REPO_ROOT" branch --show-current 2>/dev/null || true)"
REF="${CURRENT_BRANCH:-main}"
WORKFLOW_FILE="build.yml"
ARTIFACT_NAME="AsdUnionClient-build"
COMMIT_MESSAGE="chore: sync vital files for remote build"
AUTO_PUSH=1
POLL_SECONDS=5
RUN_DISCOVERY_TIMEOUT=120
VITAL_PATHS=(
    ".github"
    ".gitignore"
    "README.md"
    "README_RECOVERY.md"
    "settings.gradle"
    "build.gradle"
    "gradle.properties"
    "scripts"
    "FDPClient-b12-modified-main"
)

while [[ $# -gt 0 ]]; do
    case "$1" in
        --ref)
            [[ $# -ge 2 ]] || die "--ref requires a value"
            REF="$2"
            shift 2
            ;;
        --workflow)
            [[ $# -ge 2 ]] || die "--workflow requires a value"
            WORKFLOW_FILE="$2"
            shift 2
            ;;
        --artifact)
            [[ $# -ge 2 ]] || die "--artifact requires a value"
            ARTIFACT_NAME="$2"
            shift 2
            ;;
        --commit-message)
            [[ $# -ge 2 ]] || die "--commit-message requires a value"
            COMMIT_MESSAGE="$2"
            shift 2
            ;;
        --no-push)
            AUTO_PUSH=0
            shift
            ;;
        -h|--help)
            usage
            exit 0
            ;;
        *)
            die "Unknown argument: $1"
            ;;
    esac
done

require_command gh
require_command git
require_command zip
require_command find

gh auth status >/dev/null 2>&1 || die "gh is not authenticated"

REMOTE_URL="$(git -C "$REPO_ROOT" remote get-url origin 2>/dev/null || true)"
[[ -n "$REMOTE_URL" ]] || die "Git remote 'origin' is not configured"

REPO_SLUG="$(parse_repo_slug "$REMOTE_URL" || true)"
[[ -n "$REPO_SLUG" ]] || die "Could not parse a GitHub repo slug from: $REMOTE_URL"

has_vital_changes() {
    [[ -n "$(git -C "$REPO_ROOT" status --porcelain --untracked-files=all -- "${VITAL_PATHS[@]}")" ]]
}

ensure_no_unrelated_staged_changes() {
    local exclude_paths=()
    local extra_staged
    local path

    for path in "${VITAL_PATHS[@]}"; do
        exclude_paths+=(":(exclude)$path")
    done

    extra_staged="$(git -C "$REPO_ROOT" diff --cached --name-only -- . "${exclude_paths[@]}" || true)"
    [[ -z "$extra_staged" ]] || die "There are staged changes outside the vital path set. Commit or unstage them first."
}

sync_vital_changes() {
    local post_add_changes=""
    local head_sha=""

    ensure_no_unrelated_staged_changes

    if has_vital_changes; then
        echo "Detected local changes in vital build paths:"
        git -C "$REPO_ROOT" status --short -- "${VITAL_PATHS[@]}"

        echo "Staging vital paths..."
        git -C "$REPO_ROOT" add -A -- "${VITAL_PATHS[@]}"

        post_add_changes="$(git -C "$REPO_ROOT" diff --cached --name-only -- "${VITAL_PATHS[@]}" || true)"
        if [[ -n "$post_add_changes" ]]; then
            echo "Creating commit:"
            echo "  $COMMIT_MESSAGE"
            git -C "$REPO_ROOT" commit -m "$COMMIT_MESSAGE"
        else
            echo "No commit was needed after staging vital paths."
        fi
    else
        echo "No uncommitted changes found in vital build paths."
    fi

    echo "Pushing HEAD to origin/$REF..."
    git -C "$REPO_ROOT" push origin "HEAD:${REF}"
    head_sha="$(git -C "$REPO_ROOT" rev-parse HEAD)"
    echo "Push complete at commit:"
    echo "  $head_sha"
}

OUTPUT_ROOT="$REPO_ROOT/github-build-output"
LATEST_DIR="$OUTPUT_ROOT/latest"
EXTRACTED_DIR="$LATEST_DIR/artifact-extracted"
BUNDLE_DIR="$LATEST_DIR/artifact-bundle"
LIBS_DIR="$LATEST_DIR/libs"
REPORTS_DIR="$LATEST_DIR/reports"
LOGS_DIR="$LATEST_DIR/logs"
RUN_JSON="$LATEST_DIR/run.json"
RUN_LOG="$LOGS_DIR/run.log"
SUMMARY_FILE="$LATEST_DIR/summary.txt"
BUNDLE_ZIP="$BUNDLE_DIR/${ARTIFACT_NAME}.zip"

mkdir -p "$OUTPUT_ROOT"

echo "Vital paths considered for auto-push:"
print_path_list "${VITAL_PATHS[@]}"

if [[ "$AUTO_PUSH" -eq 1 ]]; then
    sync_vital_changes
else
    echo "Skipping auto-push because --no-push was provided."
fi

echo "Collecting existing workflow_dispatch runs for $WORKFLOW_FILE on $REF..."
EXISTING_RUN_IDS="$(
    gh run list \
        --repo "$REPO_SLUG" \
        --workflow "$WORKFLOW_FILE" \
        --branch "$REF" \
        --event workflow_dispatch \
        --limit 20 \
        --json databaseId \
        --jq '.[].databaseId' 2>/dev/null || true
)"

echo "Triggering remote build for $REPO_SLUG ($REF)..."
gh workflow run "$WORKFLOW_FILE" --repo "$REPO_SLUG" --ref "$REF" >/dev/null

RUN_ID=""
RUN_URL=""

for ((i = 0; i < RUN_DISCOVERY_TIMEOUT / POLL_SECONDS; i++)); do
    while IFS=$'\t' read -r candidate_id candidate_url; do
        [[ -n "$candidate_id" ]] || continue

        if ! contains_line "$candidate_id" "$EXISTING_RUN_IDS"; then
            RUN_ID="$candidate_id"
            RUN_URL="$candidate_url"
            break 2
        fi
    done < <(
        gh run list \
            --repo "$REPO_SLUG" \
            --workflow "$WORKFLOW_FILE" \
            --branch "$REF" \
            --event workflow_dispatch \
            --limit 20 \
            --json databaseId,url \
            --jq '.[] | [.databaseId, .url] | @tsv'
    )

    sleep "$POLL_SECONDS"
done

[[ -n "$RUN_ID" ]] || die "Timed out while waiting for the new workflow run to appear"

echo "Watching run $RUN_ID..."
WATCH_EXIT=0
if ! gh run watch "$RUN_ID" --repo "$REPO_SLUG" --interval "$POLL_SECONDS" --exit-status; then
    WATCH_EXIT=$?
fi

rm -rf "$LATEST_DIR"
mkdir -p "$EXTRACTED_DIR" "$BUNDLE_DIR" "$LIBS_DIR" "$REPORTS_DIR" "$LOGS_DIR"

echo "Saving run metadata..."
gh run view \
    "$RUN_ID" \
    --repo "$REPO_SLUG" \
    --json databaseId,name,displayTitle,event,status,conclusion,url,headBranch,headSha,createdAt,updatedAt \
    > "$RUN_JSON"

echo "Saving workflow log..."
gh run view "$RUN_ID" --repo "$REPO_SLUG" --log > "$RUN_LOG"

ARTIFACT_DOWNLOAD_EXIT=0
echo "Downloading artifact '$ARTIFACT_NAME'..."
if ! gh run download "$RUN_ID" --repo "$REPO_SLUG" --name "$ARTIFACT_NAME" --dir "$EXTRACTED_DIR"; then
    ARTIFACT_DOWNLOAD_EXIT=$?
    echo "Warning: artifact '$ARTIFACT_NAME' could not be downloaded." >&2
fi

if [[ "$ARTIFACT_DOWNLOAD_EXIT" -eq 0 ]]; then
    echo "Creating local artifact bundle zip..."
    (
        cd "$EXTRACTED_DIR"
        zip -qr "$BUNDLE_ZIP" .
    )

    while IFS= read -r jar_path; do
        cp -f "$jar_path" "$LIBS_DIR/"
    done < <(find "$EXTRACTED_DIR" -type f -name '*.jar' | sort)

    report_source="$(find "$EXTRACTED_DIR" -type d -name reports | head -n 1 || true)"
    if [[ -n "$report_source" ]]; then
        cp -R "$report_source"/. "$REPORTS_DIR"/
    fi
fi

{
    echo "repo=$REPO_SLUG"
    echo "ref=$REF"
    echo "auto_push=$AUTO_PUSH"
    echo "workflow=$WORKFLOW_FILE"
    echo "artifact=$ARTIFACT_NAME"
    echo "commit_message=$COMMIT_MESSAGE"
    echo "run_id=$RUN_ID"
    echo "run_url=$RUN_URL"
    echo "metadata=$RUN_JSON"
    echo "log=$RUN_LOG"
    echo "bundle_zip=$BUNDLE_ZIP"
    echo "libs_dir=$LIBS_DIR"
    echo "reports_dir=$REPORTS_DIR"
} > "$SUMMARY_FILE"

echo
echo "Remote build files saved to:"
echo "  $LATEST_DIR"
echo "Run URL:"
echo "  ${RUN_URL:-unknown}"

if [[ "$WATCH_EXIT" -ne 0 ]]; then
    echo "The remote workflow finished with a non-zero status. Check:"
    echo "  $RUN_LOG"
    exit "$WATCH_EXIT"
fi

echo "Remote build completed successfully."
