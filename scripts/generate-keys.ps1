# Sinh cặp khóa RSA cho JWT (PKCS#8 private + X.509 public) trên Windows.
# Yêu cầu có 'openssl' trong PATH (thường đi kèm Git for Windows: C:\Program Files\Git\usr\bin).
#
# Dùng:
#   ./scripts/generate-keys.ps1                       # ghi vào src/main/resources/keys (dev)
#   ./scripts/generate-keys.ps1 -OutDir C:\path\keys  # thư mục khác
#   ./scripts/generate-keys.ps1 -Force                # cho phép ghi đè khóa đã có
param(
    [string]$OutDir = "src/main/resources/keys",
    [int]$Bits = 2048,
    [switch]$Force
)
$ErrorActionPreference = "Stop"

if (-not (Get-Command openssl -ErrorAction SilentlyContinue)) {
    Write-Error "Không tìm thấy 'openssl' trong PATH. Cài openssl (hoặc dùng Git Bash chạy scripts/generate-keys.sh)."
    exit 1
}

$priv = Join-Path $OutDir "private.pem"
$pub = Join-Path $OutDir "public.pem"

if ((Test-Path $priv) -and -not $Force) {
    Write-Error "Đã tồn tại '$priv'. Dùng -Force để ghi đè (cẩn thận: làm mất hiệu lực token cũ)."
    exit 1
}

New-Item -ItemType Directory -Force -Path $OutDir | Out-Null
& openssl genpkey -algorithm RSA -pkeyopt "rsa_keygen_bits:$Bits" -out $priv
& openssl pkey -in $priv -pubout -out $pub

Write-Host "Đã tạo khóa RSA $Bits-bit:"
Write-Host "  private: $priv"
Write-Host "  public : $pub"
Write-Host "Nhắc: file private.pem KHONG commit o prod — mount qua bien JWT_PRIVATE_KEY_PATH."
