#!/usr/bin/env bash
# CI helper: prints public API signatures (javap) of the classes listed in tools/api-classes.txt
# and searches the compile classpath for class names listed in tools/api-search.txt.
# Only signatures are printed; this helps port the mod when Minecraft/NeoForge APIs change.
set -u
CP=$(./gradlew -q printCompileClasspath --no-configuration-cache "$@" | grep '^CP=' | cut -c4-)
echo "===API-DUMP-BEGIN==="
while read -r cls; do
  [ -z "$cls" ] && continue
  echo "===CLASS $cls"
  if ! javap -cp "$CP" -public "$cls" 2>&1; then
    simple="${cls##*.}"; simple="${simple%%\$*}"
    echo "===MISSING $cls"
    for j in $(echo "$CP" | tr ':' ' '); do
      case "$j" in *.jar) unzip -Z1 "$j" 2>/dev/null | grep -E "/${simple}\.class$" | sed "s|^|  moved-to: |" ;; esac
    done
  fi
done < tools/api-classes.txt
IFS=':' read -ra JARS <<< "$CP"
while read -r pat; do
  [ -z "$pat" ] && continue
  echo "===SEARCH $pat"
  for j in "${JARS[@]}"; do
    case "$j" in *.jar) unzip -Z1 "$j" 2>/dev/null | grep -E "$pat" | grep '\.class$' | grep -v '\$' | sed "s|^|$(basename "$j"): |" ;; esac
  done
done < tools/api-search.txt
echo "===API-DUMP-END==="
