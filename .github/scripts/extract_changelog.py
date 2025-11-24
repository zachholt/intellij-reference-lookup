import re
import sys
import os

def extract_changelog(version, changelog_path):
    with open(changelog_path, 'r') as f:
        content = f.read()

    # Escape dots in version for regex
    version_pattern = re.escape(version)
    
    # Regex to find the section for the specific version
    # Matches "## [version]" until the next "## [" or end of file
    pattern = rf"## \[{version_pattern}\].*?\n(.*?)(?=\n## \[|$)"
    
    match = re.search(pattern, content, re.DOTALL)
    
    if match:
        return match.group(1).strip()
    else:
        return f"Release notes for version {version} not found in CHANGELOG.md"

if __name__ == "__main__":
    if len(sys.argv) != 3:
        print("Usage: python extract_changelog.py <version> <path_to_changelog>")
        sys.exit(1)
        
    version = sys.argv[1]
    changelog_path = sys.argv[2]
    
    notes = extract_changelog(version, changelog_path)
    print(notes)
