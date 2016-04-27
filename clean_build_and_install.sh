#!/bin/bash



if [ $# -ne 2 ]; then
    echo "ILLEGAL ARGUMENT EXCEPTION"
    exit 1
fi



TARGET=$1
PASS=$2



echo "###### ANT CLEAN ###########################################"
ant clean



echo "###### ANT RELEASE #########################################"
ant release



echo "###### COPY UNSIGNED APK ###################################"
cp bin\\$TARGET-release-unsigned.apk .\\$TARGET.apk



echo "###### SIGN TARGET APK #####################################"
jarsigner -verbose -keystore key/fezrestia.keystore -storepass $PASS $TARGET.apk key



echo "###### INSTALL SIGNED APK ##################################"
adb install -r *.apk

