param(
    [string]$SupabaseUrl = "https://your-project.supabase.co",
    [string]$ApiUrl = "http://localhost:3000/api"
)

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent $PSScriptRoot

function Read-Secret([string]$Prompt) {
    $secureValue = Read-Host $Prompt -AsSecureString
    $pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secureValue)
    try {
        [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer)
    }
    finally {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer)
    }
}

Write-Host "Configure ECO BRIDGES local environment" -ForegroundColor Green
Write-Host "Use newly rotated credentials. Values are written only to ignored local files."

$anonKey = Read-Secret "Supabase anonymous key"
$serviceRoleKey = Read-Secret "Supabase service-role key"
$databaseUrl = Read-Secret "PostgreSQL connection string"
$groqKey = Read-Secret "Groq API key"

$frontendEnv = @"
VITE_SUPABASE_URL=$SupabaseUrl
VITE_SUPABASE_ANON_KEY=$anonKey
VITE_API_URL=$ApiUrl
"@

$backendEnv = @"
PORT=3000
SUPABASE_URL=$SupabaseUrl
SUPABASE_ANON_KEY=$anonKey
SUPABASE_SERVICE_ROLE_KEY=$serviceRoleKey
DATABASE_URL=$databaseUrl
GROQ_API_KEY=$groqKey
FASTAPI_VOICE_URL=http://localhost:8000
"@

[IO.File]::WriteAllText((Join-Path $repoRoot "frontend/.env.local"), $frontendEnv)
[IO.File]::WriteAllText((Join-Path $repoRoot "backend/nestjs/.env"), $backendEnv)

Write-Host "Environment files created successfully." -ForegroundColor Green
Write-Host "Restart the frontend and backend development servers."
