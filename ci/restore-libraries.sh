#!/usr/bin/env bash
set -euo pipefail

FILE_NAME="${LIBRARIES_SECURE_FILE:-libraries.zip}"

if ! command -v curl >/dev/null; then
  echo "curl is required" >&2
  exit 1
fi

FILES_JSON=$(curl --silent --header "JOB-TOKEN: $CI_JOB_TOKEN" \
  "${CI_API_V4_URL}/projects/${CI_PROJECT_ID}/secure_files")

FILE_ID=$(echo "$FILES_JSON" | grep -oE "\"id\":[0-9]+,\"name\":\"${FILE_NAME}\"" | head -n1 | grep -oE '[0-9]+' | head -n1)

if [ -z "$FILE_ID" ]; then
  echo "Upload ${FILE_NAME} in GitLab: Settings → CI/CD → Secure Files" >&2
  exit 1
fi

curl --silent --header "JOB-TOKEN: $CI_JOB_TOKEN" \
  "${CI_API_V4_URL}/projects/${CI_PROJECT_ID}/secure_files/${FILE_ID}/download" \
  -o /tmp/libraries.zip

mkdir -p Libraries
unzip -o /tmp/libraries.zip -d Libraries
