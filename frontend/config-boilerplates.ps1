$apps = "auth", "shop", "account", "admin-core", "admin-shop", "admin-cms"

foreach ($app in $apps) {
  Write-Host "Configuring boilerplate for $app..."

  # 1. Update styles.css
  $stylesPath = "projects/$app/src/styles.css"
  Set-Content -Path $stylesPath -Value '@import "../../shell/src/styles.css";'

  # 2. Rename AppComponent to App in app.ts
  $appTsPath = "projects/$app/src/app/app.ts"
  if (Test-Path $appTsPath) {
    $appTsContent = Get-Content -Path $appTsPath -Raw
    $appTsContent = $appTsContent -replace "class AppComponent", "class App"
    $appTsContent = $appTsContent -replace "selector: 'app-root'", "selector: 'app-$app'"
    Set-Content -Path $appTsPath -Value $appTsContent
  }

  # 3. Update bootstrap.ts
  $bootstrapPath = "projects/$app/src/bootstrap.ts"
  if (Test-Path $bootstrapPath) {
    $bootstrapContent = Get-Content -Path $bootstrapPath -Raw
    $bootstrapContent = $bootstrapContent -replace "AppComponent", "App"
    $bootstrapContent = $bootstrapContent -replace "app\.component", "app"
    Set-Content -Path $bootstrapPath -Value $bootstrapContent
  }

  # 4. Clear app.html
  $appHtmlPath = "projects/$app/src/app/app.html"
  if (Test-Path $appHtmlPath) {
    Set-Content -Path $appHtmlPath -Value "<router-outlet></router-outlet>"
  }
}

Write-Host "Boilerplate configuration complete."
