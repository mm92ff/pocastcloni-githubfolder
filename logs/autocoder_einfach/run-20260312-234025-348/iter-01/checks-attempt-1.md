# Checks

## 1. Required: `.\gradlew.bat assemble`
- Exit code: 0
- Status: OK
- Duration: 115.5s

```text
> Task :app:preBuild UP-TO-DATE
> Task :app:preDebugBuild UP-TO-DATE
> Task :app:mergeDebugNativeDebugMetadata NO-SOURCE
> Task :app:checkKotlinGradlePluginConfigurationErrors
> Task :app:generateDebugBuildConfig UP-TO-DATE
> Task :app:checkDebugAarMetadata UP-TO-DATE
> Task :app:generateDebugResValues UP-TO-DATE
> Task :app:mapDebugSourceSetPaths UP-TO-DATE
> Task :app:generateDebugResources UP-TO-DATE
> Task :app:mergeDebugResources UP-TO-DATE
> Task :app:packageDebugResources UP-TO-DATE
> Task :app:parseDebugLocalResources UP-TO-DATE
> Task :app:createDebugCompatibleScreenManifests UP-TO-DATE
> Task :app:extractDeepLinksDebug UP-TO-DATE
> Task :app:processDebugMainManifest UP-TO-DATE
> Task :app:processDebugManifest UP-TO-DATE
> Task :app:processDebugManifestForPackage UP-TO-DATE
> Task :app:processDebugResources UP-TO-DATE
> Task :app:javaPreCompileDebug UP-TO-DATE
> Task :app:mergeDebugShaders UP-TO-DATE
> Task :app:compileDebugShaders NO-SOURCE
> Task :app:generateDebugAssets UP-TO-DATE
> Task :app:mergeDebugAssets UP-TO-DATE
> Task :app:compressDebugAssets UP-TO-DATE
> Task :app:desugarDebugFileDependencies UP-TO-DATE
> Task :app:checkDebugDuplicateClasses UP-TO-DATE
> Task :app:mergeExtDexDebug UP-TO-DATE
> Task :app:mergeLibDexDebug UP-TO-DATE
> Task :app:mergeDebugJniLibFolders UP-TO-DATE
> Task :app:mergeDebugNativeLibs NO-SOURCE
> Task :app:stripDebugDebugSymbols NO-SOURCE
> Task :app:validateSigningDebug UP-TO-DATE
> Task :app:writeDebugAppMetadata UP-TO-DATE
> Task :app:writeDebugSigningConfigVersions UP-TO-DATE
> Task :app:buildKotlinToolingMetadata UP-TO-DATE
> Task :app:preReleaseBuild UP-TO-DATE
> Task :app:generateReleaseBuildConfig UP-TO-DATE
> Task :app:checkReleaseAarMetadata UP-TO-DATE
> Task :app:generateReleaseResValues UP-TO-DATE
> Task :app:mapReleaseSourceSetPaths UP-TO-DATE
> Task :app:generateReleaseResources UP-TO-DATE
> Task :app:mergeReleaseResources UP-TO-DATE
> Task :app:packageReleaseResources UP-TO-DATE
> Task :app:parseReleaseLocalResources UP-TO-DATE
> Task :app:createReleaseCompatibleScreenManifests UP-TO-DATE
> Task :app:extractDeepLinksRelease UP-TO-DATE
> Task :app:processReleaseMainManifest UP-TO-DATE
> Task :app:processReleaseManifest UP-TO-DATE
> Task :app:processReleaseManifestForPackage UP-TO-DATE
> Task :app:processReleaseResources UP-TO-DATE
> Task :app:javaPreCompileRelease UP-TO-DATE
> Task :app:extractProguardFiles UP-TO-DATE
> Task :app:mergeReleaseJniLibFolders UP-TO-DATE
> Task :app:mergeReleaseNativeLibs NO-SOURCE
> Task :app:stripReleaseDebugSymbols NO-SOURCE
> Task :app:extractReleaseNativeSymbolTables NO-SOURCE
> Task :app:mergeReleaseNativeDebugMetadata NO-SOURCE
> Task :app:checkReleaseDuplicateClasses UP-TO-DATE
> Task :app:mergeReleaseArtProfile UP-TO-DATE
> Task :app:mergeReleaseShaders UP-TO-DATE
> Task :app:compileReleaseShaders NO-SOURCE
> Task :app:generateReleaseAssets UP-TO-DATE
> Task :app:mergeReleaseAssets UP-TO-DATE
> Task :app:compressReleaseAssets UP-TO-DATE
> Task :app:extractReleaseVersionControlInfo
> Task :app:processApplicationManifestReleaseForBundle UP-TO-DATE
> Task :app:bundleReleaseResources UP-TO-DATE
> Task :app:collectReleaseDependencies UP-TO-DATE
> Task :app:sdkReleaseDependencyData UP-TO-DATE
> Task :app:writeReleaseAppMetadata UP-TO-DATE
> Task :app:writeReleaseSigningConfigVersions UP-TO-DATE
> Task :app:kspDebugKotlin
> Task :app:kspReleaseKotlin

> Task :app:compileDebugKotlin
w: file:///C:/Users/jemi/Desktop/Github/pocastcloni/app/src/main/java/com/example/pocastcloni/data/remote/RssSmartSyncParser.kt:75:29 Duplicate label in when
w: file:///C:/Users/jemi/Desktop/Github/pocastcloni/app/src/main/java/com/example/pocastcloni/data/remote/RssSmartSyncParser.kt:143:25 Duplicate label in when
w: file:///C:/Users/jemi/Desktop/Github/pocastcloni/app/src/main/java/com/example/pocastcloni/data/repository/PodcastMappers.kt:24:44 Elvis operator (?:) always returns the left operand of non-nullable type Date
w: file:///C:/Users/jemi/Desktop/Github/pocastcloni/app/src/main/java/com/example/pocastcloni/data/worker/DownloadWorker.kt:77:40 Parameter 'guid' is never used
w: file:///C:/Users/jemi/Desktop/Github/pocastcloni/app/src/main/java/com/example/pocastcloni/domain/usecase/episode/GetDownloadedEpisodesWithPodcastInfoUseCase.kt:20:14 This declaration is in a preview state and can be changed in a backwards-incompatible manner with a best-effort migration. Its usage should be marked with '@kotlinx.coroutines.FlowPreview' or '@OptIn(kotlinx.coroutines.FlowPreview::class)' if you accept the drawback of relying on preview API
w: file:///C:/Users/jemi/Desktop/Github/pocastcloni/app/src/main/java/com/example/pocastcloni/ui/common/OrderGrid.kt:118:28 Parameter 'change' is never used, could be renamed to _
w: file:///C:/Users/jemi/Desktop/Github/pocastcloni/app/src/main/java/com/example/pocastcloni/ui/favorites/FavoritesScreen.kt:67:57 'ArrowBack: ImageVector' is deprecated. Use the AutoMirrored version at Icons.AutoMirrored.Filled.ArrowBack
w: file:///C:/Users/jemi/Desktop/Github/pocastcloni/app/src/main/java/com/example/pocastcloni/ui/favorites/FavoritesViewModel.kt:28:48 This declaration is in a preview state and can be changed in a backwards-incompatible manner with a best-effort migration. Its usage should be marked with '@kotlinx.coroutines.FlowPreview' or '@OptIn(kotlinx.coroutines.FlowPreview::class)' if you accept the drawback of relying on preview API
w: file:///C:/Users/jemi/Desktop/Github/pocastcloni/app/src/main/java/com/example/pocastcloni/ui/favorites/FavoritesViewModel.kt:39:35 This declaration is in a preview state and can be changed in a backwards-incompatible manner with a best-effort migration. Its usage should be marked with '@kotlinx.coroutines.FlowPreview' or '@OptIn(kotlinx.coroutines.FlowPreview::class)' if you accept the drawback of relying on preview API
w: file:///C:/Users/jemi/Desktop/Github/pocastcloni/app/src/main/java/com/example/pocastcloni/ui/history/HistoryScreen
...[truncated]...
```
