#!/usr/bin/env bash
# Sinh cặp khóa RSA cho JWT (PKCS#8 private + X.509 public) khi khởi tạo dự án mới từ base này.
# Mỗi dự án PHẢI có khóa riêng — không dùng lại khóa của base/dự án khác.
#
# Dùng:
#   ./scripts/generate-keys.sh                      # ghi vào src/main/resources/keys (dev)
#   ./scripts/generate-keys.sh /duong/dan/keys      # ghi vào thư mục khác
#   FORCE=1 ./scripts/generate-keys.sh              # cho phép ghi đè khóa đã có
set -euo pipefail

OUT_DIR="${1:-src/main/resources/keys}"
BITS="${RSA_BITS:-2048}"
PRIV="$OUT_DIR/private.pem"
PUB="$OUT_DIR/public.pem"

if ! command -v openssl >/dev/null 2>&1; then
  echo "Lỗi: không tìm thấy 'openssl' trong PATH." >&2
  exit 1
fi

if [[ -f "$PRIV" && "${FORCE:-0}" != "1" ]]; then
  echo "Đã tồn tại '$PRIV'. Đặt FORCE=1 để ghi đè (cẩn thận: làm mất hiệu lực token cũ)." >&2
  exit 1
fi

mkdir -p "$OUT_DIR"
openssl genpkey -algorithm RSA -pkeyopt "rsa_keygen_bits:$BITS" -out "$PRIV"
openssl pkey -in "$PRIV" -pubout -out "$PUB"

echo "Đã tạo khóa RSA ${BITS}-bit:"
echo "  private: $PRIV"
echo "  public : $PUB"
echo "Nhắc: file private.pem KHÔNG được commit ở prod — mount qua biến JWT_PRIVATE_KEY_PATH."
