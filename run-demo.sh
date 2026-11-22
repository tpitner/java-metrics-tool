#!/usr/bin/env bash
set -euo pipefail

repo_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
cd "$repo_dir"

classpath_file="target/demo-classpath.txt"
demo_classes="target/demo-classes"

mvn -q compile dependency:build-classpath -Dmdep.outputFile="$classpath_file"
mkdir -p "$demo_classes"

classpath="target/classes:$(<"$classpath_file")"
javac --release 27 -cp "$classpath" -d "$demo_classes" BuiltInDemo.java

exec java -cp "$demo_classes:$classpath" BuiltInDemo "$@"
