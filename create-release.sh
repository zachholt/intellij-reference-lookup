#!/bin/bash

# Script to create a new release
# Usage: ./create-release.sh 1.0.1

if [ -z "$1" ]; then
    echo "Usage: ./create-release.sh <version>"
    echo "Example: ./create-release.sh 1.0.1"
    exit 1
fi

VERSION=$1
TAG="v$VERSION"

echo "Creating release $VERSION..."

# Make sure we're on main branch
git checkout main

# Pull latest changes
git pull origin main

# Create and push tag
git tag -a "$TAG" -m "Release $VERSION"
git push origin "$TAG"

echo "✅ Tag $TAG pushed!"
echo "🚀 GitHub Actions will now build and create the release automatically."
echo "📦 Check https://github.com/zachholt/intellij-reference-lookup/actions for build status."