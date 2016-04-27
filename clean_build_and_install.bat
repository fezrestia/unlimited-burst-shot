@echo off



if "%1" == "" (
    set /p TARGET="TARGET = "
) else (
    set TARGET=%1
)

if "%2" == "" (
    set /p KEYPASS="KEY PASS = "
) else (
    set KEYPASS=%2
)



echo "###### ANT CLEAN ###########################################"
call ant clean



echo "###### ANT RELEASE #########################################"
call ant release



echo "###### COPY UNSIGNED APK ###################################"
copy bin\%TARGET%-release-unsigned.apk .\%TARGET%.apk



echo "###### SIGN TARGET APK #####################################"
call jarsigner -verbose -keystore key/fezrestia.keystore -storepass %KEYPASS% %TARGET%.apk key



echo "###### INSTALL SIGNED APK ##################################"
call adb install -r *.apk
