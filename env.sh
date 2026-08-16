#!/usr/bin/env bash
# Android toolchain env for bash/sh tooling — source it: `source env.sh`
# Mirror of ~/.config/fish/conf.d/android.fish. Managed by the toolchain-setup agent.

export JAVA_HOME="$HOME/android-dev/jdk17"
export ANDROID_HOME="$HOME/android-dev/sdk"
# Deprecated alias, still read by some emulator/SDK tooling.
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export ANDROID_AVD_HOME="$HOME/.android/avd"
export ANDROID_EMULATOR_HOME="$HOME/.android"

for d in \
    "$JAVA_HOME/bin" \
    "$ANDROID_HOME/cmdline-tools/latest/bin" \
    "$ANDROID_HOME/platform-tools" \
    "$ANDROID_HOME/emulator"
do
    if [ -d "$d" ] && [[ ":$PATH:" != *":$d:"* ]]; then
        PATH="$d:$PATH"
    fi
done
export PATH
