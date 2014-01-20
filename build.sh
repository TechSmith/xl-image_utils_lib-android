#!/bin/bash -xe

echo "Building xl-image_utils_lib-android"
android update lib-project --target android-19 --path .
ant clean
ant release

