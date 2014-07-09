#!/bin/bash

 # Script file to instrument an application.
 # Uncomment the lines beginning with ## to instrument the example HelloWorldApp.apk

 source 0_my.config

 for X in \
   ANDROID_SDK_HOME ANDROID_JAR \
   APKTOOL_HOME AAPTTOOL APK_PATH APK_NAME \
   MIN_SDK_LEVEL MAX_SDK_LEVEL
 do
   if [ -z "$(eval echo "\$$X")" ]
   then
     echo "NOTE: This script must be modified so that certain variables are set."
     echo "Please edit this script to set those variables to their proper values."
     echo "The $X variable was not set."
     exit 1
   fi
 done

 MERGER_CLASSPATH="${CLASSPATH}:${ANDROID_JAR}:${PWD}/lib/libForMergingDex/dx.jar:${PWD}/instrumentAndroidApk/mergeDexes/src"
 INSTRUMENTOR_LIB="${PWD}/lib/libForInstrumentor"
 INSTRUMENTOR_CLASSPATH="${CLASSPATH}:${ANDROID_JAR}:${INSTRUMENTOR_LIB}/lib/coffer.jar:${INSTRUMENTOR_LIB}/jasminclasses.jar:\
${INSTRUMENTOR_LIB}/java_cup.jar:${INSTRUMENTOR_LIB}/JFlex.jar:${INSTRUMENTOR_LIB}/pao.jar:\
${INSTRUMENTOR_LIB}/polygot.jar:${INSTRUMENTOR_LIB}/pth.jar:${INSTRUMENTOR_LIB}/soot.jar:\
${INSTRUMENTOR_LIB}/sootclasses.jar:${PWD}/instrumentAndroidApk/instrumentApk/src/myInstrumentor"

 # Extract the .apk file to get classes.dex
 echo Extracting "${APK_PATH}/${APK_NAME}" using apktool.
 mkdir mergerOutput
 cd mergerOutput
 cp -f "${APK_PATH}/${APK_NAME}" "${APK_NAME}.zip"
 unzip -x "${APK_NAME}.zip" -d "extractedApk"
 cd ..

 # Decode the manifest file using apktool (necessary to get original manifest and resource files)
 echo Decoding "${APK_PATH}/${APK_NAME}" using apktool.
 #${APKTOOL_HOME}/apktool d "${APK_PATH}/${APK_NAME}" "${PWD}/mergerOutput/decodedApk"

 echo Merging application dex file with myScheduler dex file to be used in the instrumentation.
 # Merge the classes.dex file with myScheduler.dex file
 javac -cp ${MERGER_CLASSPATH} instrumentAndroidApk/mergeDexes/src/mergeDexFiles/*.java 

 # Merge the classes.dex file with myScheduler.dex file 
 cp -rf "${PWD}/instrumentAndroidApk/instrumentApk/src/myScheduler/MyScheduler.dex" "${PWD}/instrumentAndroidApk/mergeDexes/src/dex/MyScheduler.dex"
 cp -rf "${PWD}/mergerOutput/extractedApk/classes.dex" "${PWD}/instrumentAndroidApk/mergeDexes/src/dex/classes.dex"
 java -cp ${MERGER_CLASSPATH} mergeDexFiles/MergeDexFiles "../dex/classes.dex" "../dex/MyScheduler.dex"
 
 # Recreate .apk file using aapt tool: (if you have more than one res file, add all into the command)
 echo Recreating .apk file in ${PWD}/myMerger/mergerOutput/extractedApk/${APP_NAME}.
 cd mergerOutput
 ${APKTOOL_HOME}/aapt package -f -M "decodedApk/AndroidManifest.xml" -S "decodedApk/res/" -I "${ANDROID_JAR}" --min-sdk-version ${MIN_SDK_LEVEL} --target-sdk-version ${MAX_SDK_LEVEL} -F "${APK_NAME}"

 # add the new classes.dex into the .apk file
 ${AAPTTOOL_HOME}/aapt add -f ${APK_NAME} classes.dex
 cd ..

 echo Instrumenting .apk file ${APK_NAME}.
 # Compile instrumentor
 javac -cp ${INSTRUMENTOR_CLASSPATH} instrumentAndroidApk/instrumentApk/src/myInstrumentor/*.java

 # Run the instrumentor
 java -cp ${INSTRUMENTOR_CLASSPATH} AndroidInstrument ${PWD}/mergerOutput/${APK_NAME} ${ANDROID_SDK_HOME}/platforms ${PWD}/src -output-format dex

 echo Instrumentation of ${APK_NAME} is complete. 
 echo Find the instrumented .apk file in ${PWD}/sootOutput