$apps = @{
  "auth" = 4202;
  "shop" = 4203;
  "account" = 4204;
  "admin-core" = 4205;
  "admin-shop" = 4206;
  "admin-cms" = 4207;
}

foreach ($app in $apps.Keys) {
  Write-Host "Generating application $app..."
  npx ng g application $app --routing=true --style=css --skip-tests=true --defaults

  Write-Host "Adding Native Federation to $app on port $($apps[$app])..."
  npx ng add @angular-architects/native-federation --project $app --port $apps[$app] --type remote --skip-confirmation
}
