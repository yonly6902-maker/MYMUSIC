# Velvet Music

An Android Studio project for an offline local-music player.

## Build on your computer
Open this folder in Android Studio and allow Gradle sync to finish. Then use Build > Build Bundle(s) / APK(s) > Build APK(s).

## Build an APK online with GitHub Actions
This project includes `.github/workflows/build-apk.yml`.

1. Create a GitHub repository.
2. Upload the project files, including the `.github` folder and this workflow file.
3. Open the repository's **Actions** tab and enable workflows if prompted.
4. Select **Build Velvet Music APK** and click **Run workflow** (or push the files to the `main` or `master` branch).
5. When the run succeeds, open the run page and download the **VelvetMusic-debug-apk** artifact.
6. Extract the downloaded artifact ZIP to get `app-debug.apk`.

The workflow uses Gradle 8.9 and Java 17 to build the debug APK. A successful workflow run is required before an APK is available.
